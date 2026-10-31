"""Gateway tests with mocked provider HTTP. No real API calls are made."""
import base64
import json
import os
import sys
import threading
import time
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
os.environ["GATEWAY_QUIET"] = "1"
import gateway  # noqa: E402

TOKEN = "test-token-0123456789"
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 64
GLB = b"glTF" + b"\x02\x00\x00\x00" + b"1" * 32


def fake_openai(url, method="GET", headers=None, body=None, timeout=0):
    assert url == "https://api.openai.com/v1/images/edits"
    assert headers["Authorization"] == "Bearer sk-test"
    assert b'name="image[]"' in body and b"gpt-image-2" in body
    return 200, {}, json.dumps({"data": [{"b64_json": base64.b64encode(b"RENDERED").decode()}]}).encode()


class FakeMeshy:
    def __init__(self, fail=False):
        self.polls = 0
        self.fail = fail

    def __call__(self, url, method="GET", headers=None, body=None, timeout=0):
        if url == gateway.MeshyModeler.BASE and method == "POST":
            payload = json.loads(body)
            assert payload["image_url"].startswith("data:image/png;base64,")
            return 202, {}, b'{"result": "task-1"}'
        if url.endswith("/task-1"):
            self.polls += 1
            if self.fail:
                return 200, {}, json.dumps({"status": "FAILED", "task_error": {"message": "bad image"}}).encode()
            if self.polls < 2:
                return 200, {}, b'{"status": "IN_PROGRESS", "progress": 40}'
            return 200, {}, json.dumps({"status": "SUCCEEDED", "progress": 100,
                                        "model_urls": {"glb": "https://assets.example/model.glb"}}).encode()
        if url == "https://assets.example/model.glb":
            return 200, {}, GLB
        raise AssertionError(url)


class GatewayTest(unittest.TestCase):
    def start(self, meshy=None):
        gw = gateway.Gateway(TOKEN, gateway.OpenAIRenderer("sk-test", model="gpt-image-2", http=fake_openai),
                             gateway.MeshyModeler("msy-test", http=meshy or FakeMeshy(), poll_seconds=0, sleep=lambda s: None))
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), gateway.make_handler(gw))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}"

    def tearDown(self):
        if hasattr(self, "server"):
            self.server.shutdown()
            self.server.server_close()

    def call(self, path, body=None, token=TOKEN):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method="POST" if data else "GET")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, r.headers.get("Content-Type"), r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Content-Type"), e.read()

    def wait(self, jid):
        for _ in range(200):
            _, _, data = self.call(f"/v1/jobs/{jid}")
            job = json.loads(data)
            if job["status"] in ("succeeded", "failed"):
                return job
            time.sleep(0.02)
        self.fail("job did not finish")

    def test_health_is_public(self):
        self.start()
        self.assertEqual(self.call("/health", token=None)[0], 200)

    def test_rejects_missing_or_wrong_token(self):
        self.start()
        self.assertEqual(self.call("/v1/render", {"image_base64": base64.b64encode(PNG).decode()}, token=None)[0], 401)
        self.assertEqual(self.call("/v1/jobs/" + "0" * 32, token="wrong-token-xxxxxxxx")[0], 401)

    def test_rejects_bad_image(self):
        self.start()
        self.assertEqual(self.call("/v1/render", {"image_base64": "not base64!!"})[0], 400)
        self.assertEqual(self.call("/v1/render", {"image_base64": base64.b64encode(b"GIF89a").decode()})[0], 400)

    def test_render_flow(self):
        self.start()
        code, _, data = self.call("/v1/render", {"image_base64": base64.b64encode(PNG).decode(), "prompt": "cosy"})
        self.assertEqual(code, 202)
        jid = json.loads(data)["job_id"]
        self.assertEqual(self.wait(jid)["status"], "succeeded")
        code, ctype, result = self.call(f"/v1/jobs/{jid}/result")
        self.assertEqual((code, ctype, result), (200, "image/png", b"RENDERED"))

    def test_model_flow_returns_glb(self):
        meshy = FakeMeshy()
        self.start(meshy)
        code, _, data = self.call("/v1/model", {"image_base64": base64.b64encode(PNG).decode()})
        self.assertEqual(code, 202)
        jid = json.loads(data)["job_id"]
        self.assertEqual(self.wait(jid)["status"], "succeeded")
        code, ctype, result = self.call(f"/v1/jobs/{jid}/result")
        self.assertEqual((code, ctype), (200, "model/gltf-binary"))
        self.assertTrue(result.startswith(b"glTF"))
        self.assertGreaterEqual(meshy.polls, 2)

    def test_model_failure_is_reported(self):
        self.start(FakeMeshy(fail=True))
        _, _, data = self.call("/v1/model", {"image_base64": base64.b64encode(PNG).decode()})
        job = self.wait(json.loads(data)["job_id"])
        self.assertEqual(job["status"], "failed")
        self.assertIn("bad image", job["error"])
        self.assertEqual(self.call(f"/v1/jobs/{job['id']}/result")[0], 409)

    def test_unknown_job(self):
        self.start()
        self.assertEqual(self.call("/v1/jobs/" + "a" * 32)[0], 404)

    def test_requires_strong_app_token(self):
        with self.assertRaises(ValueError):
            gateway.Gateway("short", None, None)


if __name__ == "__main__":
    unittest.main()
