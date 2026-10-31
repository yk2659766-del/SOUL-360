# SOUL 360 DESIGN — Android

Native Android interior-design app (Kotlin + Jetpack Compose) in a cream/taupe palette with orange accents. It includes an optional Python AI gateway.

## Status: read this first

This is source code, not a finished app from the Play Store. It has **not been compiled or run on a phone yet**, because the build environment had no access to the Android SDK. Expect to fix a few small compile errors the first time you build. Android Studio points to each one.

Tested so far:
- **3D Studio page** (the three.js scene the app shows): tested in headless Chromium. It loads a room, loads furniture, auto-scales models made in centimetres, and exports a valid GLB.
- **AI gateway**: 8 unit tests pass, with mocked OpenAI and Meshy calls. No real or paid API calls were made.

**Logo:** `app/src/main/res/drawable/logo_mark.xml` is a placeholder. To replace it with your logo, right-click `res` → *New → Image Asset* (app icon) and *Vector Asset* (home-screen logo).

## Install it on your Android phone (from Windows)

### Option A: Android Studio (recommended)
1. Install **Android Studio** (free, runs on Windows): developer.android.com/studio
2. Unzip this folder and choose *File → Open* → select the `Soul360Design-Android` folder. Wait for Gradle sync to finish. The first sync downloads the SDK and libraries.
3. Prepare the phone: *Settings → About phone →* tap **Build number** 7 times. Then go to *Settings → Developer options →* turn on **USB debugging**.
4. Connect the phone with USB and accept the "Allow USB debugging" prompt.
5. Pick your phone in the device menu and press the green **Run ▶** button. The app installs and opens.

### Option B: no Android Studio (GitHub builds the APK)
1. Create a free GitHub account and a new repository, then upload this folder's contents.
2. Open the **Actions** tab. The *Build Android APK* workflow runs by itself.
3. When it turns green, open the run and download **Soul360Design-debug-apk** under *Artifacts*.
4. Copy the `.apk` to your phone, open it, and allow "Install unknown apps" when asked.

If the build fails, the error appears in the workflow log. Paste it to me and I'll fix it.

Requirements: Android 8.0+ and Google Play services, which handles background removal.

## What it does

**Photo design**
- Branded home screen with a list of saved projects.
- Take a room photo with the camera, or pick one from the gallery.
- Add several furniture images (JPG/PNG, including transparent PNGs).
- Drag with one finger; pinch to resize; twist to rotate. Sliders for size, rotation, brightness and shadow. Flip horizontally and bring to front.
- **Remove background** on the phone itself with Google ML Kit Subject Segmentation. The first use downloads the model through Play services, so you need to be online.
- Toggle **Original** to compare with the empty room.
- Export with **Share** or **Save to gallery** (Pictures › SOUL 360 DESIGN). Exports are capped at 2400 px on the longest side.
- Projects save automatically when you close the editor or leave the app. The **Save** button reports any error.
- **Find online** opens Google Images or Pinterest in your browser. Save the image itself, then tap *Add furniture*.

**3D Studio**
- Import a **room scan as GLB**. Android has no equivalent of Apple's LiDAR RoomPlan, so scan the room with any 3D scanning app that exports GLB, then import the file.
- Orbit, zoom and pan the room. Place **one** furniture model with X/Y/Z in metres, scale and rotation.
- Furniture models come from **Make 3D** (AI) in the photo editor, or from any GLB file.
- Export the furnished scene as GLB through the share sheet.

**AI Render (ChatGPT or Gemini)**
- Tap **✦ Render** in a design. The app sends the room with all placed furniture, plus each furniture image as a reference, to **ChatGPT** (OpenAI `gpt-image-2`) or **Gemini** (`gemini-2.5-flash-image`). You can change the model in Settings.
- Style presets: Just make it real, Modern Minimal, Japandi, Luxury Contemporary, Scandinavian, Mid-Century, Modern Arabic and Industrial Loft. Lighting presets, an optional "refresh walls & decor" setting, and free-text wishes.
- Before/after slider; save to gallery or share.
- Uses **your own API key**, entered on the render screen or in Settings. The key is encrypted with the Android Keystore and only sent to that provider. Each render uses your credits.

**Make 3D (advanced, optional)**: sends a furniture cutout to Meshy through the optional gateway in `backend/` and saves a GLB.

**Install / update:** download https://github.com/yk2659766-del/SOUL-360/releases/download/latest/Soul360Design.apk on the phone and install. Builds from version 0.2.0 onward share one signing key, so each update installs over the previous one.

## Limits

- A PNG stays a flat overlay. There is no automatic perspective warping or floor detection.
- Image-to-3D guesses the hidden sides of an object; it does not give you exact dimensions. Model sizes and orientation vary, so use the sliders (models over 5 m or under 5 cm are auto-scaled to 1 m).
- The 3D Studio holds one furniture model at a time. The 3D arrangement is **not saved**, so export it before you leave. Live AR placement is not implemented.
- Background removal works best with one clear item on a simple background.
- Shadows are soft drop shadows. Realistic lighting needs AI render, and AI can change details.
- AI jobs need the screen to stay open. *Stop waiting* only stops the app from waiting; the provider may still finish the job and charge you. Interrupted jobs are not recovered.

## Connect the AI gateway (optional)

`backend/gateway.py` uses only the Python standard library.

```
cd backend
python -m unittest discover -s tests -v                    # tests (mocked providers)
APP_TOKEN=<long random string> OPENAI_API_KEY=sk-... MESHY_API_KEY=msy-... python gateway.py
```

- Host it behind **HTTPS** on any service that runs Python or Docker (a `Dockerfile` is included). Set the three environment variables there. Optional: `OPENAI_IMAGE_MODEL` (default `gpt-image-2`) and `PORT` (default 8080).
- You need your own OpenAI and/or Meshy account with credits.
- In the app, go to **Settings** and enter the HTTPS origin (e.g. `https://my-gateway.example.com`) and the same `APP_TOKEN`.
- Endpoints: `POST /v1/render`, `POST /v1/model`, `GET /v1/jobs/<id>`, `GET /v1/jobs/<id>/result`, `GET /health`. Jobs are kept in memory for 1 hour, with at most 4 running at once.

## Project layout

```
app/src/main/java/com/soul360/design/
  MainActivity.kt          screen switching
  data/                    projects, file storage, settings (Keystore-encrypted token)
  image/                   compositor (export), shadows/brightness, ML Kit cutout
  ai/GatewayClient.kt      upload + poll + download
  ui/                      Home, Editor, 3D Studio, Settings
app/src/main/assets/studio/  bundled three.js 3D viewer (built from tools/studio)
backend/                   AI gateway + tests + Dockerfile
.github/workflows/         APK build + backend tests
```

To rebuild the 3D viewer after editing `tools/studio/studio.js`, run `cd tools/studio && npm install && npm run build`.

## References
- ML Kit Subject Segmentation: developers.google.com/ml-kit/vision/subject-segmentation/android
- OpenAI image edits: platform.openai.com/docs/api-reference/images/createEdit
- Meshy image-to-3D: docs.meshy.ai/en/api/image-to-3d
