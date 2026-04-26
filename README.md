# ARIA — AI Voice Assistant (Android APK)

A standalone Android AI assistant app. No Termux needed after install.
Powered by OpenRouter AI with native Android mic, TTS, flashlight, and more.

---

## HOW TO BUILD YOUR APK (step by step)

### STEP 1 — Create a GitHub account
If you don't have one: https://github.com/signup

---

### STEP 2 — Create a new GitHub repo
1. Go to https://github.com/new
2. Name it: `ARIA`
3. Set to **Public**
4. **Do NOT** tick "Add README" or any other files
5. Click **Create repository**

---

### STEP 3 — Upload files from Termux

Open Termux and run these commands one by one:

```bash
# Install git if you don't have it
pkg install git -y

# Set your git identity (use your GitHub email)
git config --global user.email "youremail@example.com"
git config --global user.name "YourGitHubUsername"

# Go to home folder
cd ~

# Clone this project (after you've put the files there)
# OR if starting fresh, create the folder:
mkdir ARIA-app && cd ARIA-app

# Copy all the project files into this folder
# (see Step 3b below for how to get the files)

# Initialize git
git init
git add .
git commit -m "Initial ARIA app"

# Add your GitHub repo as remote (replace YOURUSERNAME)
git remote add origin https://github.com/YOURUSERNAME/ARIA.git
git branch -M main
git push -u origin main
```

**Step 3b — Getting the project files into Termux:**

Option A — Download the ZIP from Claude and extract:
```bash
pkg install unzip -y
cd ~
# Move the downloaded zip to termux storage first:
termux-setup-storage
cp /sdcard/Download/ARIA.zip ~/
unzip ARIA.zip
cd ARIA
```

Option B — Use `termux-setup-storage` and copy the extracted folder from your Downloads.

---

### STEP 4 — Watch GitHub Actions build your APK
1. Go to your repo on GitHub: `https://github.com/YOURUSERNAME/ARIA`
2. Click the **Actions** tab
3. You'll see a workflow running called **"Build ARIA APK"**
4. Wait ~4-6 minutes for it to finish (green checkmark)
5. Click on the completed run
6. Scroll down to **Artifacts**
7. Click **ARIA-APK** to download the zip
8. Extract it — inside is `ARIA-v1.0-debug.apk`

---

### STEP 5 — Install the APK on your phone
1. On your Android phone, go to **Settings → Apps → Special app access → Install unknown apps**
2. Allow your browser or file manager to install unknown apps
3. Open the APK file
4. Tap **Install**
5. Open **ARIA** from your app drawer

---

### STEP 6 — Set your API key inside the app
1. Open ARIA
2. Tap the **⚙️ gear icon** in the bottom right
3. Paste your **OpenRouter API key** (get one free at https://openrouter.ai)
4. Choose your model (free models are listed)
5. Tap **SAVE SETTINGS**
6. Start talking!

---

## FEATURES

| Feature | How to use |
|---|---|
| Voice input | Hold the 🎤 button, speak, release |
| Wake word | Enable in the bar at top, say "Hey ARIA" |
| Text input | Type in the box and press Enter |
| Flashlight | Say "turn on flashlight" / "turn off flashlight" |
| Battery check | Say "what's my battery level" |
| Open settings | Say "open settings" |
| Make a call | Say "call 555-1234" |
| Send a text | Say "text 555-1234" |
| Notifications | Say "send me a notification" |
| Open a website | Say "open youtube.com" |
| Share text | Say "share this: hello world" |

---

## CUSTOMIZATION

In Settings (⚙️) you can change:
- **API Key** — your OpenRouter key
- **Model** — which AI model ARIA uses (free options available)
- **Name** — rename ARIA to anything you like

---

## FREE AI MODELS (no cost)
- `nvidia/nemotron-super-49b-v1:free` — powerful, recommended
- `meta-llama/llama-3.1-8b-instruct:free` — fast and lightweight
- `google/gemma-3-27b-it:free` — good for conversation
- `mistralai/mistral-7b-instruct:free` — reliable fallback

Get your free API key at: https://openrouter.ai

---

## TROUBLESHOOTING

**Build fails on GitHub Actions?**
- Check the Actions log for the error
- Most common fix: make sure all files were pushed correctly
- Try running the workflow again manually (Actions → Run workflow)

**Mic not working in app?**
- Make sure you granted microphone permission when prompted
- Go to Settings → Apps → ARIA → Permissions → enable Microphone

**AI not responding?**
- Check your API key is correct in Settings
- Make sure you have internet
- Try a different model in Settings

**Flashlight not working?**
- Some phones need Camera permission for flashlight
- Go to Settings → Apps → ARIA → Permissions → enable Camera

---

## PROJECT STRUCTURE

```
ARIA/
├── .github/workflows/build.yml    ← GitHub Actions build script
├── app/
│   ├── src/main/
│   │   ├── assets/aria.html       ← The entire UI (HTML/CSS/JS)
│   │   ├── java/com/aria/assistant/
│   │   │   └── MainActivity.java  ← Android bridges (mic, TTS, torch...)
│   │   ├── res/                   ← Icons, layouts, themes
│   │   └── AndroidManifest.xml    ← Permissions
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## UPDATING THE APP

To change the UI or AI behaviour, edit `app/src/main/assets/aria.html` and push again:
```bash
git add .
git commit -m "Update ARIA"
git push
```
GitHub Actions will automatically rebuild the APK.
