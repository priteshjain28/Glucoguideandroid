# GlucoGuide — Native Android App

Your diabetes management app as a real Android APK, built free in the cloud by GitHub Actions.
No Android Studio needed. Includes: daily reminder notifications, voice logging (microphone),
meal photos (camera), Pixel Watch step sync via Health Connect, family sharing, and optional
AI features.

## How to get your APK (one-time, ~10 minutes)

1. Create a free account at github.com (skip if you have one).
2. Create a new repository, e.g. `glucoguide-android` (private is fine).
3. Upload this ENTIRE folder, keeping the structure. Easiest ways:
   - On a computer: open the repo -> "Add file" -> "Upload files" -> drag the whole
     unzipped folder contents in (folders upload with their structure) -> Commit.
   - Hidden folder note: the `.github` folder is critical. If your file manager hides it,
     use "Add file" -> "Create new file", type `.github/workflows/build-apk.yml` as the
     name and paste that file's contents.
4. Open the repo's **Actions** tab -> the "Build GlucoGuide APK" workflow runs
   automatically (or press "Run workflow"). Wait ~3-5 minutes.
5. Click the finished run -> **Artifacts** -> download **GlucoGuide-APK** (a zip
   containing GlucoGuide.apk).
6. Copy GlucoGuide.apk to your phone, tap it, allow "Install unknown apps" for your
   file manager when asked, and install.

## What's native in this version

- **Reminder notifications** (Settings -> Reminders): morning glucose check, every
  medication time, pre-gym safety check, save-day-record — fire daily even when the
  app is closed, and survive phone restarts.
- **Voice logging**: tap "Log with voice" and say "sugar 145", "walked 30 minutes",
  or "steps 6400".
- **Meal photos**: attach a camera photo or gallery image to any meal.
- **Health Connect**: "Sync from Health Connect" on the Steps card reads today's
  step total from your Pixel Watch / Fitbit / Google Fit data (asks permission once).
- **Native sharing** to WhatsApp/SMS for the family summary.
- **AI features** (optional): add your own Anthropic API key in Settings to enable
  the AI carb estimator and the Ask-AI helper.

## Updating the app

All app logic lives in `app/src/main/assets/index.html`. Replace that file with a
newer version, commit, let Actions rebuild, download and install the new APK over
the old one. Your data is stored on the phone and survives updates.

## Notes

- This builds a debug-signed APK: perfect for personal use; Play Store publishing
  would need a release keystore (happy path for later).
- All health data stays on the phone. Nothing is uploaded anywhere.
- Not a medical device; not medical advice.
