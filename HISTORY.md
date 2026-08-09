# Development History

## 2026-08-09 - Initial Filtering Foundation

Completed:

* Created the Flutter project with an Android Kotlin target.
* Added version 1 local advertisement and tracker domain lists.
* Added domain-to-provider/category metadata with evidence references.
* Added native domain normalization, ruleset loading, validation, matching, allowlist precedence, and unknown-domain allow behavior.
* Added an atomic version upgrade seam for future remote rulesets.
* Added eight native ruleset/FilterEngine tests covering exact, subdomain, tracker, allowlist, unknown, normalization, legitimate-domain, and integrity behavior.
* Passed native unit tests, Flutter analysis, Flutter tests, and a debug APK build.
* Verified all three filter files are packaged under the APK's Flutter asset directory.
* Documented current scope, limitations, checklist, and roadmap.

Known Issues:

* VPN, DNS relay, traffic history, app attribution, and production UI are not implemented.
* Meta Audience Network ad delivery cannot be comprehensively blocked at DNS level without blocking broad Meta infrastructure; only narrow measurement endpoints are included.

Next:

* Implement the native Flutter bridge and VPN lifecycle foundation.
* Add DNS parsing and relay before connecting FilterEngine to live traffic.

## 2026-08-09 - Native Flutter Bridge

Completed:

* Registered lifecycle-safe MethodChannel and EventChannel endpoints from `MainActivity`.
* Defined the complete MVP native method contract in Kotlin and Dart.
* Added typed Dart models for VPN status, filter information, filter decisions, and native events.
* Connected filter information and domain evaluation calls to the real bundled ruleset.
* Made unavailable VPN, traffic, history, statistics, and persistence calls fail explicitly instead of returning fabricated data.
* Added four Dart bridge contract tests.
* Passed Flutter analysis, five Flutter tests, eight native unit tests, Kotlin compilation, and a debug APK build.

Known Issues:

* `VpnService` permission and lifecycle are not implemented; VPN status truthfully reports `notImplemented`.
* The event channel is registered but has no producers until native VPN and traffic phases are implemented.

Next:

* Implement the Android VPN permission flow and service state machine without establishing a traffic-breaking tunnel.
* Add foreground notification lifecycle and explicit readiness/error states.

## 2026-08-09 - DNS Filtering MVP Pipeline

Completed:

* Added Android VPN and notification permission flow with foreground service lifecycle.
* Added a DNS-only `/32` TUN route so ordinary application traffic remains on the underlying network.
* Added IPv4/IPv6 UDP parsing, DNS A/AAAA parsing, NXDOMAIN/SERVFAIL responses, checksum generation, and protected upstream relay.
* Added Android 10+ UID owner lookup, package/app/icon resolution, confidence metadata, and honest Unknown App fallback.
* Added SQLite traffic history, allowlist persistence, retention pruning, SQL statistics, app/domain aggregation, and AndroidX DataStore settings.
* Connected all MVP bridge data methods to real native repositories.
* Added Home, Apps, App Detail, Traffic/History, Settings, Allowlist, filter information, and debug-mode Flutter screens.
* Added event-driven UI refresh with 400 ms batching and throttled app-icon refresh.

Known Issues:

* Physical-device DNS routing, OEM VPN behavior, and attribution accuracy are not yet manually verified.
* Private DNS, application DoH, TCP DNS, cached DNS, and direct IP traffic are outside the DNS-only MVP visibility boundary.
* Android versions below API 29 cannot use connection-owner UID lookup and will record Unknown App.

Next:

* Add DNS/IP packet unit tests and update Flutter UI tests.
* Run debug and release validation, then perform a physical-device acceptance pass.

## 2026-08-09 - MVP Code Complete

Completed:

* Added five DNS parser/response tests and four IPv4/IPv6 UDP packet tests.
* Updated the Flutter smoke test to exercise the production shell and navigation.
* Completed domain, app, decision/category, and date filtering in Traffic/History.
* Exposed history retention, separate clear-history/statistics actions, and complete available debug metadata in Settings.
* Passed all 17 native Kotlin unit tests and all 5 Flutter tests with zero failures.
* Passed `flutter analyze` with zero issues.
* Produced both debug and minified release APKs with the complete versioned ruleset bundled.
* Reconciled the checklist and roadmap with the implemented DNS-filtering MVP boundary.

Known Issues:

* Physical-device VPN/DNS behavior and UID attribution still require manual acceptance testing on representative Android/OEM versions.
* The current release APK uses the development debug signing key. A private production keystore and release signing configuration are required before store distribution.
* Private DNS, application DoH, TCP DNS, cached answers, and direct-IP traffic remain outside this DNS-only MVP.

Next:

* Install the debug APK on a physical Android 10+ device and complete the remaining acceptance checks.
* Configure production application identity, versioning, signing, and store metadata before distribution.

## 2026-08-09 - VPN Stability and Live Diagnostics

Completed:

* Moved ruleset, DataStore, SQLite, and pruning work out of the service main-thread startup path.
* Contained packet-reader, worker dispatch, TUN write, repository, and foreground-service failures so a single runtime error cannot crash-loop the Android process.
* Added bounded start/stop confirmation instead of treating a service that is still starting as stopped.
* Replaced starvation-prone trailing debounce with non-overlapping 400 ms live-event batching.
* Added live inspected/blocked DNS counters, packet-error diagnostics, last-DNS timestamp, and a Private DNS visibility warning.
* Cached and resized application icons to reduce repeated MethodChannel memory pressure.
* Moved SQLite WAL configuration to the supported helper initialization path.
* Added VPN health-state unit coverage; all 18 native tests and all 5 Flutter tests pass.
* Bumped the Android package version to `1.0.1+2` for device update testing.

Known Issues:

* The original device crash stack trace could not be collected because no authorized Android device was connected to ADB during diagnosis.
* Physical-device verification is still required to determine whether system Private DNS or app-specific DoH bypasses DNS visibility on the affected device.

Next:

* Install `1.0.1+2`, enable Debug Mode, and verify that the inspected DNS counter increases while browsing.
* If the process still exits, collect the new `ShieldBlockVpn` logcat entry from the connected device.

## 2026-08-09 - Physical Device Crash Root Cause

Completed:

* Connected a Samsung SM-A556E through ADB and captured the repeated fatal exception.
* Confirmed the crash was a `SecurityException` from `ConnectivityManager.getAllNetworks()` because `ACCESS_NETWORK_STATE` was missing.
* Added the required Android network-state permission used by upstream network selection, Private DNS diagnostics, and UID owner attribution.
* Bumped the device-test build to `1.0.2+3`.

Changed:

* The earlier packet-worker containment prevents this class of exception from crash-looping the process, while the manifest permission restores actual upstream DNS relay operation.

Next:

* Reinstall on the connected physical device and verify inspected/blocked DNS counters plus crash-free background operation.

## 2026-08-09 - Unknown App Drill-Down

Completed:

* Made the aggregated Unknown App row navigable instead of discarding taps when package name is unavailable.
* Added native `package_name IS NULL` domain aggregation so Unknown App details do not accidentally include every application's domains.
* Added an Unknown App selector to Traffic/History for inspecting individual timestamps, domains, decisions, and any available UID metadata.
* Added an explicit explanation that Android attribution is unavailable while domain and filter-decision data remains real.
* Added bridge coverage for nullable-package Unknown App details and bumped the build to `1.0.3+4`.

## 2026-08-09 - Embedded Ruleset Browser

Completed:

* Added a lazy native bridge endpoint that exposes the currently loaded immutable ruleset without duplicating provider logic in Dart.
* Made Filter Information navigable to a searchable domain/provider ledger.
* Added All, Advertisements, and Trackers filters with provider and category metadata on every row.
* Kept the 96-rule payload out of normal startup; it loads only when the rules screen is opened.
* Added bridge parsing coverage and bumped the build to `1.0.4+5`.

## 2026-08-09 - Branded Launcher Icon

Completed:

* Adopted the repository-root `logo.png` as the launcher artwork without altering the supplied design.
* Added reproducible `flutter_launcher_icons` configuration for Android density-specific legacy icons.
* Added Android adaptive foreground/background icon resources with a dark background matched to the source artwork.
* Kept the VPN notification glyph separate because Android status-bar icons require a monochrome silhouette.
* Bumped the build to `1.0.5+6`.

Changed:

* Measured the supplied artwork bounds after launcher testing and found their visual center at `(537, 485)` rather than the canvas center `(512, 512)`.
* Added a deterministic launcher preparation step that shifts the mark 25 px left and 27 px down while edge-extending the existing background; the original `logo.png` remains untouched.
* Verified the prepared artwork bounds at center `(510, 513)` against the `(512, 512)` canvas center and bumped the corrected icon build to `1.0.6+7`.

## 2026-08-09 - Observed Endpoint Ruleset Expansion

Completed:

* Promoted the local ruleset to version 2 with 48 advertisement and 53 tracker rules.
* Added the observed exact endpoints `ms4.applvn.com`, `app.adjust.com`, `edge.safedk.com`, `api.gameanalytics.com`, and `config.uca.cloud.unity3d.com` with provider/category metadata.
* Kept `graph.facebook.com`, Firebase Remote Config, and the observed ByteDance content CDN allowed to avoid broad functional-service blocking.
* Added regression coverage for every new rule, the intentionally allowed endpoints, exact counts, and metadata consistency.
* Bumped the APK build to `1.0.7+8`.

## 2026-08-09 - User-Controlled Domain Blocking

Completed:

* Added a persistent local custom blocklist with a non-destructive database migration.
* Added a block action beside allowed domains in App Activity.
* Added a mandatory warning that custom DNS blocking applies device-wide and may break app functionality.
* Made a confirmed block remove any matching allowlist override first, with the affected rule named in the warning.
* Applied custom blocklist changes to the running VPN immediately without restarting protection.
* Added custom blocklist visibility and removal controls in Settings.
* Kept allowlist precedence above custom and bundled blocking rules.
* Bumped the APK build to `1.0.8+9`.

## 2026-08-09 - ArmaNet Product Rename

Completed:

* Renamed the user-facing application from ShieldBlock to ArmaNet.
* Updated the Android launcher label, Flutter title, home branding, settings copy, VPN session, foreground notification, and notification channel name.
* Preserved the existing Android application ID, method channels, local database, settings storage, and Kotlin/Dart type names so the new APK upgrades the existing installation without losing local data.
* Bumped the APK build to `1.0.9+10`.

## 2026-08-09 - GitHub Repository Readiness

Completed:

* Added a least-privilege GitHub Actions workflow for format, analysis, Flutter tests, Android tests, and debug APK artifacts.
* Pinned the repository toolchain to Flutter 3.41.7 / Dart 3.11.5 and Java 21 in CI.
* Added the official Gradle 8.14 distribution checksum for wrapper download verification.
* Expanded ignore rules for local SDK paths, generated output, signing material, environment files, and APK/AAB artifacts.
* Added Dependabot configuration, structured issue forms, a pull request template, contribution guidance, a security policy, editor settings, and line-ending attributes.
* Documented current MVP status, CI behavior, signing limits, contribution rules, and the absence of an open-source license.
