## Summary

Describe the user-visible or architectural change.

## Verification

- [ ] `dart format --output=none --set-exit-if-changed lib test tool`
- [ ] `flutter analyze`
- [ ] `flutter test`
- [ ] Android/Kotlin unit tests
- [ ] Debug APK build

## Safety and privacy

- [ ] No credentials, keystores, private traffic logs, device identifiers, or local SDK paths are included.
- [ ] New filter domains have ownership/purpose evidence and matching metadata.
- [ ] Broad legitimate domains and `UNKNOWN -> ALLOW` behavior remain protected.

## Notes

List manual device checks, known limitations, screenshots, or follow-up work.
