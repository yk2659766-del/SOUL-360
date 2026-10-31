"""SOUL 360 DESIGN AI gateway.

Keeps provider keys off the phone. The Android app authenticates with a shared
APP_TOKEN; the gateway calls OpenAI (photo render) and Meshy (image -> 3D GLB).

Standard library only. Run:  APP_TOKEN=... OPENAI_API_KEY=... MESHY_API_KEY=... python3 gateway.py
Deploy behind HTTPS (the app refuses plain http).

Endpoints (all except /health need  Authorization: Bearer <APP_TOKEN>):
  GET  /health
  POST /v1/render   {"image_base64": "<png>", "prompt": "..."}  -> 202 {"job_id"}
  POST /v1/model    {"image_base64": "<png>"}                   -> 202 {"job_id"}
  GET  /v1/jobs/<id>          -> {"id","kind","status","progress","error"}
  GET  /v1/jobs/<id>/result   -> image/png or model/gltf-binary
"""
from __future__ import annotations

import base64
import binascii
import hmac
import json
import os
import re
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 25 * 1024 * 1024
JOB_TTL = 60 * 60          # results kept for one hour
MAX_ACTIVE_JOBS = 4


class ProviderError(Exception):
    pass


def http_request(url, method="GET", headers=None, body=None, timeout=300):
    """Small urllib wrapper. Returns (status, headers, bytes). Replaced in tests."""
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, dict(resp.headers), resp.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers or {}), e.read()


def _multipart(fields, files):
    boundary = "----soul360" + uuid.uuid4().hex
    out = bytearray()
    for name, value in fields.items():
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode()
    for name, (filename, content, ctype) in files.items():
        out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"\r\n"
                f"Content-Type: {ctype}\r\n\r\n").encode()
        out += content + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


class OpenAIRenderer:
    """Photo render via OpenAI Images edit endpoint (GPT Image models return base64)."""

    def __init__(self, api_key, model=None, http=None):
        self.api_key = api_key
        self.model = model or os.environ.get("OPENAI_IMAGE_MODEL", "gpt-image-2")
        self.http = http or http_request

    def run(self, png: bytes, prompt: str, progress):
        if not self.api_key:
            raise ProviderError("OPENAI_API_KEY is not configured on the gateway.")
        progress(10)
        body, ctype = _multipart({"model": self.model, "prompt": prompt, "n": "1"},
                                 {"image[]": ("room.png", png, "image/png")})
        status, _, data = self.http("https://api.openai.com/v1/images/edits", "POST",
                                    {"Authorization": f"Bearer {self.api_key}", "Content-Type": ctype}, body, 600)
        if status != 200:
            raise ProviderError(f"OpenAI error {status}: {_err_text(data)}")
        try:
            b64 = json.loads(data)["data"][0]["b64_json"]
            return base64.b64decode(b64), "image/png"
        except (KeyError, IndexError, ValueError, TypeError) as e:
            raise ProviderError(f"Unexpected OpenAI response: {e}") from e


class MeshyModeler:
    """Furniture image -> textured GLB via Meshy image-to-3D."""
    BASE = "https://api.meshy.ai/openapi/v1/image-to-3d"

    def __init__(self, api_key, http=None, poll_seconds=5.0, timeout=20 * 60, sleep=time.sleep):
        self.api_key = api_key
        self.http = http or http_request
        self.poll_seconds = poll_seconds
        self.timeout = timeout
        self.sleep = sleep

    def run(self, png: bytes, prompt, progress):
        if not self.api_key:
            raise ProviderError("MESHY_API_KEY is not configured on the gateway.")
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        payload = {"image_url": "data:image/png;base64," + base64.b64encode(png).decode(),
                   "should_texture": True}
        status, _, data = self.http(self.BASE, "POST", headers, json.dumps(payload).encode(), 120)
        if status not in (200, 201, 202):
            raise ProviderError(f"Meshy error {status}: {_err_text(data)}")
        task_id = json.loads(data).get("result")
        if not task_id:
            raise ProviderError("Meshy did not return a task id.")
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            self.sleep(self.poll_seconds)
            status, _, data = self.http(f"{self.BASE}/{task_id}", "GET", {"Authorization": headers["Authorization"]}, None, 60)
            if status != 200:
                raise ProviderError(f"Meshy status error {status}: {_err_text(data)}")
            task = json.loads(data)
            progress(int(task.get("progress") or 0))
            state = task.get("status")
            if state == "SUCCEEDED":
                url = (task.get("model_urls") or {}).get("glb") or task.get("model_url")
                if not url:
                    raise ProviderError("Meshy finished without a GLB download.")
                status, _, model = self.http(url, "GET", {}, None, 300)
                if status != 200 or not model.startswith(b"glTF"):
                    raise ProviderError("Could not download the GLB model from Meshy.")
                return model, "model/gltf-binary"
            if state in ("FAILED", "CANCELED", "EXPIRED"):
                msg = (task.get("task_error") or {}).get("message") or state
                raise ProviderError(f"Meshy task {state.lower()}: {msg}")
        raise ProviderError("Meshy task timed out.")


def _err_text(data: bytes) -> str:
    try:
        o = json.loads(data)
        e = o.get("error") if isinstance(o, dict) else None
        if isinstance(e, dict):
            return str(e.get("message", e))[:300]
        return str(e or o.get("message") or o)[:300]
    except Exception:
        return data[:300].decode("utf-8", "replace")


class JobStore:
    def __init__(self):
        self.jobs = {}
        self.lock = threading.Lock()

    def create(self, kind):
        self.cleanup()
        with self.lock:
            active = sum(1 for j in self.jobs.values() if j["status"] in ("queued", "running"))
            if active >= MAX_ACTIVE_JOBS:
                return None
            jid = uuid.uuid4().hex
            self.jobs[jid] = {"id": jid, "kind": kind, "status": "queued", "progress": None,
                              "error": None, "result": None, "content_type": None, "created": time.time()}
            return jid

    def update(self, jid, **kw):
        with self.lock:
            if jid in self.jobs:
                self.jobs[jid].update(kw)

    def get(self, jid):
        with self.lock:
            j = self.jobs.get(jid)
            return dict(j) if j else None

    def cleanup(self):
        cutoff = time.time() - JOB_TTL
        with self.lock:
            for jid in [k for k, j in self.jobs.items() if j["created"] < cutoff]:
                del self.jobs[jid]


class Gateway:
    def __init__(self, app_token, renderer, modeler):
        if not app_token or len(app_token) < 16:
            raise ValueError("APP_TOKEN must be set (at least 16 characters).")
        self.app_token = app_token
        self.providers = {"render": renderer, "model": modeler}
        self.jobs = JobStore()

    def authorized(self, header):
        if not header or not header.startswith("Bearer "):
            return False
        return hmac.compare_digest(header[7:].encode(), self.app_token.encode())

    def start(self, kind, png, prompt):
        jid = self.jobs.create(kind)
        if jid is None:
            return None
        threading.Thread(target=self._work, args=(jid, kind, png, prompt), daemon=True).start()
        return jid

    def _work(self, jid, kind, png, prompt):
        self.jobs.update(jid, status="running", progress=0)
        try:
            data, ctype = self.providers[kind].run(png, prompt, lambda p: self.jobs.update(jid, progress=p))
            self.jobs.update(jid, status="succeeded", progress=100, result=data, content_type=ctype)
        except ProviderError as e:
            self.jobs.update(jid, status="failed", error=str(e))
        except Exception as e:  # network errors etc.
            self.jobs.update(jid, status="failed", error=f"{type(e).__name__}: {e}")


DEFAULT_PROMPT = ("Photorealistic interior photograph. Keep the room architecture and camera angle. "
                  "Blend the added furniture naturally with matching perspective, lighting and soft contact shadows.")
JOB_PATH = re.compile(r"^/v1/jobs/([0-9a-f]{32})(/result)?$")


def make_handler(gw: Gateway):
    class Handler(BaseHTTPRequestHandler):
        server_version = "Soul360Gateway/1.0"

        def log_message(self, fmt, *args):  # keep logs free of request bodies
            if os.environ.get("GATEWAY_QUIET") != "1":
                super().log_message(fmt, *args)

        def _send(self, code, obj=None, raw=None, ctype="application/json"):
            body = raw if raw is not None else json.dumps(obj).encode()
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def _auth(self):
            if not gw.authorized(self.headers.get("Authorization")):
                self._send(401, {"error": "Unauthorized"})
                return False
            return True

        def do_GET(self):
            if self.path == "/health":
                return self._send(200, {"ok": True})
            if not self._auth():
                return
            m = JOB_PATH.match(self.path)
            if not m:
                return self._send(404, {"error": "Not found"})
            job = gw.jobs.get(m.group(1))
            if not job:
                return self._send(404, {"error": "Unknown or expired job"})
            if m.group(2):
                if job["status"] != "succeeded":
                    return self._send(409, {"error": f"Job is {job['status']}"})
                return self._send(200, raw=job["result"], ctype=job["content_type"])
            return self._send(200, {k: job[k] for k in ("id", "kind", "status", "progress", "error")})

        def do_POST(self):
            if not self._auth():
                return
            kind = {"/v1/render": "render", "/v1/model": "model"}.get(self.path)
            if not kind:
                return self._send(404, {"error": "Not found"})
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = 0
            if length <= 0 or length > MAX_BODY:
                return self._send(413 if length > MAX_BODY else 400, {"error": "Body missing or too large"})
            try:
                payload = json.loads(self.rfile.read(length))
                png = base64.b64decode(payload["image_base64"], validate=True)
            except (ValueError, KeyError, TypeError, binascii.Error):
                return self._send(400, {"error": "Expected JSON with image_base64"})
            if not (png.startswith(b"\x89PNG") or png.startswith(b"\xff\xd8")):
                return self._send(400, {"error": "Image must be PNG or JPEG"})
            prompt = payload.get("prompt") if kind == "render" else None
            if kind == "render":
                prompt = (prompt or DEFAULT_PROMPT).strip()[:4000]
            jid = gw.start(kind, png, prompt)
            if jid is None:
                return self._send(429, {"error": "Too many jobs running, try again shortly"})
            return self._send(202, {"job_id": jid})

    return Handler


def main():
    gw = Gateway(os.environ.get("APP_TOKEN", ""),
                 OpenAIRenderer(os.environ.get("OPENAI_API_KEY")),
                 MeshyModeler(os.environ.get("MESHY_API_KEY")))
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0", port), make_handler(gw))
    print(f"SOUL 360 gateway listening on :{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
