# Contributing to ArmaNet

Thank you for helping improve ArmaNet. Keep changes focused, explain user-visible behavior, and avoid including real traffic logs that expose private domains, package names, device identifiers, or IP addresses.

## Development setup

Required toolchain:

- Flutter 3.41.7 with Dart 3.11.5
- Android SDK
- Java 17 or Java 21

Run the local checks before opening a pull request:

```text
flutter pub get
dart format --output=none --set-exit-if-changed lib test tool
flutter analyze
flutter test
flutter build apk --debug
```

Run the native Android tests as well:

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
```

## Filter rule contributions

Rules must remain data-driven. Do not add provider-name substring checks to Kotlin or Dart.

For every proposed domain:

1. Use a canonical exact domain or the narrowest safe provider-owned parent domain.
2. Provide a primary provider document, SDK log, reputable research source, or well-established maintained filter source that verifies ownership and purpose.
3. Do not infer domains from provider names.
4. Do not block broad functional roots such as `google.com` or `facebook.com`.
5. Add the domain to exactly one of `assets/filters/ads.txt` or `assets/filters/trackers.txt`.
6. Add matching provider/category metadata to `assets/filters/networks.json`.
7. Increment `filterVersion`, update counts, and add regression coverage.
8. Preserve `UNKNOWN -> ALLOW` and allowlist precedence.

## Pull requests

- Keep unrelated formatting or generated-file churn out of the change.
- Add or update tests for behavior changes.
- Update README, checklist, roadmap, or history when scope changes.
- Do not commit local SDK paths, APKs, keystores, credentials, device logs, or production traffic data.
- Production signing and publishing changes require an explicit maintainer decision.
