# Zip-and-drop deploy (PauzFirst)

1. Get a zip whose folders match the repo root (`www/...`, `android-native-tts-plugin/...`). A wrapper folder is removed automatically.
2. On github.com open the `incoming/` folder -> Add file -> Upload files -> upload the zip (any name) -> Commit to `main`.
3. Actions tab -> "Build PauzFirst APK" unpacks it, commits, deletes the zip, builds. Green = done; download the APK from the run's Artifacts.

- To delete files, add `DELETE_FILES.txt` at the zip root (one path per line, `#` comments ok).
- Files under `.github/workflows/` inside a zip are ignored (GitHub blocks it). Edit those on github.com directly.
- Never delete the `incoming/` folder or `.github/workflows/build-apk.yml`.
