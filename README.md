# J.A.R.V.I.S. Android starter project

An Iron Man-inspired Android Compose interface with real text chat routed through a small server to the OpenAI Responses API, Android TextToSpeech, Android speech recognition, and a microphone foreground service that listens for the phrase **“Hey JARVIS”**.

## Important status

This is a source project, not a prebuilt APK. This package includes a GitHub Actions cloud-build workflow so you can request an APK using a phone browser; no Windows PC or Android Studio is required. The APK still needs a deployed backend URL to communicate with the AI.

The wake phrase implementation uses Android `SpeechRecognizer` in a foreground service. It is a practical prototype, not a dedicated low-power DSP wake-word engine. Recognition availability, offline language packs, battery optimization, vendor firmware, and Android background microphone rules affect reliability. Start listening while the app is visible; Android may require the user to tap the wake notification to return to the app. The app does not secretly listen after the service is stopped.

## 1. Run the AI backend

Requirements: Node.js 20+ and an OpenAI API key.

```bash
cd server
npm install
# Linux/macOS:
export OPENAI_API_KEY='your-key'
export OPENAI_MODEL='gpt-6-astra'
npm start
```

Deploy the backend to a host with HTTPS. Do not put the OpenAI key in the Android app, APK, Git repository, or client-side source. The server uses the OpenAI Responses API and keeps the key in its environment.

## 2. Point Android app to your backend

Open `app/src/main/java/com/kimmuel/jarvis/MainActivity.kt` and replace:

```kotlin
private object BuildConfigField { const val API_BASE_URL = "https://YOUR-BACKEND-DOMAIN.example.com" }
```

with your deployed HTTPS backend base URL, for example `https://jarvis-api.example.com` (no trailing slash).

## 3. Build the APK using only your Android phone

This repository includes `.github/workflows/build-apk.yml`. GitHub's cloud runner compiles the APK; your phone only needs a browser.

1. Extract `JARVIS-Android-Phone-Build.zip` using your Android Files app.
2. In your browser, sign in to GitHub and create a **new public or private repository** named `JARVIS-Android`. Do not add a README, license, or .gitignore during creation.
3. Open the new repository, choose **Add file → Upload files**, and upload the extracted project contents, including the hidden `.github` folder and `render.yaml`. If your browser hides dot-folders, enable **show hidden files** in the file picker. Commit the upload to the `main` branch.
4. Open the repository's **Actions** tab. Select **Build JARVIS APK**, choose **Run workflow**, and run it.
5. When the run completes, open it and download the **JARVIS-debug-APK** artifact. Extract the downloaded artifact ZIP and tap `app-debug.apk` to install. Allow installation from your browser/files app if Android asks.

If the GitHub mobile website does not expose folder upload, switch your browser to **Desktop site**. GitHub Actions runs the Android build on its own cloud computer.

## 4. Deploy the AI backend

1. Create a Render account and choose **New → Blueprint**.
2. Connect the GitHub repository containing this project and deploy the included `render.yaml`.
3. In the Render service environment settings, set `OPENAI_API_KEY` to your OpenAI API key. Keep it private; never paste it into GitHub source files or the APK.
4. After deployment, copy the service's HTTPS URL.
5. In GitHub, open **Settings → Secrets and variables → Actions → Variables → New repository variable**. Name it `JARVIS_API_BASE_URL` and set the value to your Render HTTPS URL (no trailing slash).
6. Re-run **Build JARVIS APK** so the new APK contains the correct backend URL.

The Render free service may sleep when idle and can take time to wake. OpenAI API usage is billed separately and requires an API account with API billing enabled.

## 5. Install and activate

Install the downloaded APK, grant microphone and notification permissions, open JARVIS, and tap **ENABLE / HEY JARVIS** while the app is visible. Android may stop or restrict background microphone services; keep the persistent notification enabled. This prototype does not provide guaranteed always-on, low-power wake-word detection.

## Included features

- Iron Man-inspired dark cyan HUD / reactor interface
- OpenAI Responses API chat through a backend
- Recent-turn conversational context in the app session
- Android speech-to-text command input
- Android TextToSpeech replies
- Start/stop wake-word foreground service
- “Hey JARVIS” phrase detection and tap-to-open notification
- Basic API request size/rate limits and Helmet headers

## Not included yet

- True always-on DSP wake-word detection (e.g. a licensed on-device wake-word engine and its model file)
- Background auto-launch of the activity (restricted by Android)
- Device-control actions, smart-home integrations, alarms, contacts, or calendar access
- User accounts, server-side per-user quotas, and persistent encrypted conversation storage

## Security

Use HTTPS, keep `OPENAI_API_KEY` only on the server, add authentication before exposing the backend publicly, and set spending/rate limits in your OpenAI project. The sample rate limiter is a basic safeguard, not a complete production abuse-prevention system.
