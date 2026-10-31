import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js';

const bridge = window.Android || {
  onStatus: (m) => console.log('[status]', m),
  onFurnitureLoaded: (j) => console.log('[furniture]', j),
  onRoomLoaded: (j) => console.log('[room]', j),
  onExport: (b) => console.log('[export bytes b64]', b.length),
  onError: (m) => console.error('[error]', m),
};

const canvas = document.getElementById('c');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene();
scene.background = new THREE.Color(0xf1e8d9);
const camera = new THREE.PerspectiveCamera(55, 1, 0.01, 500);
camera.position.set(4, 4, 6);
const controls = new OrbitControls(camera, canvas);
controls.enableDamping = true;

scene.add(new THREE.HemisphereLight(0xffffff, 0xa39383, 1.6));
const sun = new THREE.DirectionalLight(0xffffff, 1.4);
sun.position.set(3, 8, 5);
scene.add(sun);
const grid = new THREE.GridHelper(20, 20, 0xab9f8a, 0xe7dccb);
scene.add(grid);

const content = new THREE.Group();    // exported
scene.add(content);
let room = null;
const furnitureAnchor = new THREE.Group();
content.add(furnitureAnchor);
let furniture = null;
const loader = new GLTFLoader();

function resize() {
  const w = window.innerWidth, h = window.innerHeight;
  renderer.setSize(w, h, false);
  camera.aspect = w / Math.max(h, 1);
  camera.updateProjectionMatrix();
}
window.addEventListener('resize', resize);
resize();
(function loop() { requestAnimationFrame(loop); controls.update(); renderer.render(scene, camera); })();

function load(url) {
  return new Promise((resolve, reject) => loader.load(url, resolve, undefined, reject));
}
function frame(box) {
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const d = Math.max(size.x, size.y, size.z, 1);
  controls.target.copy(center);
  camera.position.set(center.x + d * 0.9, center.y + d * 0.9, center.z + d * 1.2);
  camera.near = d / 500; camera.far = d * 50; camera.updateProjectionMatrix();
}
function err(e) { bridge.onError(String(e && e.message || e)); throw e; }

window.S360 = {
  async loadRoom(url) {
    try {
      bridge.onStatus('Loading room…');
      const gltf = await load(url);
      if (room) content.remove(room);
      room = gltf.scene; room.name = 'Room';
      content.add(room);
      const box = new THREE.Box3().setFromObject(room);
      grid.position.y = box.min.y;
      frame(box);
      const s = box.getSize(new THREE.Vector3());
      bridge.onRoomLoaded(JSON.stringify({ width: s.x, height: s.y, depth: s.z }));
      bridge.onStatus('');
    } catch (e) { err(e); }
  },
  async loadFurniture(url) {
    try {
      bridge.onStatus('Loading furniture…');
      const gltf = await load(url);
      if (furniture) furnitureAnchor.remove(furniture);
      furniture = gltf.scene; furniture.name = 'Furniture';
      // Put the model's base centre at the anchor origin.
      const box = new THREE.Box3().setFromObject(furniture);
      const c = box.getCenter(new THREE.Vector3());
      furniture.position.set(-c.x, -box.min.y, -c.z);
      furnitureAnchor.add(furniture);
      const size = box.getSize(new THREE.Vector3());
      const maxDim = Math.max(size.x, size.y, size.z) || 1;
      const scale = (maxDim > 5 || maxDim < 0.05) ? 1 / maxDim : 1;
      let x = 0, y = 0, z = 0;
      if (room) {
        const rb = new THREE.Box3().setFromObject(room);
        const rc = rb.getCenter(new THREE.Vector3());
        x = rc.x; y = rb.min.y; z = rc.z;
      }
      this.setTransform(x, y, z, scale, 0);
      bridge.onFurnitureLoaded(JSON.stringify({ x, y, z, scale, rotation: 0,
        width: size.x, height: size.y, depth: size.z }));
      bridge.onStatus('');
    } catch (e) { err(e); }
  },
  setTransform(x, y, z, scale, rotationDeg) {
    furnitureAnchor.position.set(x, y, z);
    furnitureAnchor.scale.setScalar(Math.max(scale, 0.001));
    furnitureAnchor.rotation.set(0, rotationDeg * Math.PI / 180, 0);
  },
  resetView() { frame(new THREE.Box3().setFromObject(content.children.length ? content : grid)); },
  hasContent() { return !!(room || furniture); },
  exportGLB() {
    bridge.onStatus('Exporting…');
    new GLTFExporter().parse(content, (buf) => {
      const bytes = new Uint8Array(buf);
      let bin = '';
      for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
      bridge.onExport(btoa(bin));
      bridge.onStatus('');
    }, (e) => bridge.onError('Export failed: ' + e), { binary: true });
  },
};
bridge.onStatus('ready');
