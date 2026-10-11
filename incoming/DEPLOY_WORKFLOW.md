# Zip-and-drop deploy (PauzFirst native app)

1. Get a zip whose folders match the repo root (`app/...`). A wrapper folder is removed automatically.
2. On github.com open `incoming/` -> Add file -> Upload files -> upload the zip (any name) -> Commit to `main`.
3. Actions tab -> "Build PauzFirst APK (Native)" unpacks it, commits, deletes the zip and builds. Green = done.
   Download the APK from the repo's Releases page (or the run's Artifacts).

- To delete files add `DELETE_FILES.txt` at the zip root (one path per line, `#` comments ok).
- Files under `.github/workflows/` inside a zip are ignored by GitHub. Edit those on github.com directly.
- Never delete `incoming/` or `.github/workflows/build-apk.yml`.
- `www/` is the website (invite and password-reset links). The Android app does not use it.
- Android only installs over the old app if the same signing key is used (secrets: ANDROID_KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD).
