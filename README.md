# ArmaNet

ArmaNet is an Android-first, device-wide DNS advertisement and tracker blocker built with Flutter and Kotlin. It shows which app attempted to resolve which domain, labels known advertising/tracking networks, stores recent metadata locally, and blocks known rules without decrypting HTTPS.

> **Project status:** Active MVP development. Debug and development-signed release APKs build successfully, but physical-device behavior can vary by Android version and OEM. This repository is not yet a production Play Store release.

## How it works

1. Flutter asks Android to display the official `VpnService.prepare()` permission screen.
2. `AdBlockVpnService` starts as a foreground service with a persistent stop action.
3. The VPN creates a narrow TUN route only for the synthetic DNS address `10.111.222.2/32`.
4. Normal application traffic stays on the underlying Wi-Fi/mobile network; only DNS packets addressed to the synthetic resolver enter the TUN interface.
5. Kotlin parses IPv4/IPv6 UDP packets and DNS A/AAAA questions, then evaluates the normalized domain with the local versioned ruleset.
6. Blocked questions receive an on-device NXDOMAIN response. Allowed and malformed questions are relayed through a protected socket to an underlying network DNS server.
7. Connection metadata is stored in local SQLite and emitted to Flutter in batches through an EventChannel.

The upstream resolver prefers DNS servers from the current non-VPN network and uses `1.1.1.1` only when Android exposes no usable DNS server. User traffic is never sent to an ArmaNet-operated server.

## Architecture

```text
Flutter UI
  Home / Apps / App Detail / Traffic / Settings / Allowlist
        |
  ShieldBlockNativeApi (MethodChannel + EventChannel)
        |
Kotlin native layer
  bridge/    FlutterBridge, native events
  vpn/       AdBlockVpnService, runtime status
  dns/       IP/UDP parser, DNS parser, response builder, upstream relay
  filter/    versioned loader, normalizer, FilterEngine
  traffic/   TrafficEvent, UID/package/app resolver
  database/  SQLiteOpenHelper, TrafficRepository, retention
  settings/  AndroidX DataStore preferences
  stats/     aggregate result models
```

Flutter contains UI, navigation, settings controls, and rendering. Packet processing, filtering, attribution attempts, persistence, and aggregation remain native so the VPN can continue when Flutter UI closes.

## Filtering model

The pipeline is entirely data-driven:

`FilterSource -> FilterLoader -> FilterRuleset -> FilterEngine -> FilterDecision`

There are no provider-name substring checks in Kotlin or Dart. Evaluation order is:

1. Allowlist exact/parent match → `ALLOW`
2. User custom blocklist exact/parent match → `BLOCK`
3. Advertisement/tracker exact/parent match → `BLOCK`
4. Unknown domain → `ALLOW`

Allowing `example.com` also allows `api.example.com` and other subdomains. A specific allowlist entry overrides a broader blocking rule.

Allowed domains shown in App Activity can be added to the persistent, device-wide custom blocklist after an explicit breakage warning. Changes apply to a running VPN immediately and can be reversed from App Activity or Settings. The warning names and removes any matching allowlist rule only after confirmation; allowlist precedence remains deterministic inside `FilterEngine`.

Version 2 contains 48 advertisement rules and 53 tracker rules with matching provider/category metadata:

- `assets/filters/ads.txt`
- `assets/filters/trackers.txt`
- `assets/filters/networks.json`

The same bundled data can be inspected inside the app from Settings → Filter Information. The ledger supports domain/provider search and advertisement/tracker category filtering; it does not maintain a separate UI-only rule copy.

Broad service roots such as `google.com`, `facebook.com`, and `unity3d.com` are intentionally not blocked. Version 2 adds only observed, provider-verified Adjust, AppLovin/SafeDK, GameAnalytics, and Unity UCA endpoints; functional endpoints such as `graph.facebook.com`, `firebaseremoteconfig.googleapis.com`, and ByteDance content CDNs remain allowed. The ruleset was conservatively cross-checked against the [AdGuard DNS filter](https://github.com/AdguardTeam/AdGuardSDNSFilter), [DuckDuckGo Tracker Radar](https://github.com/duckduckgo/tracker-radar), and provider documentation. Review upstream licensing before redistributing a substantially expanded automatically derived list.

## Local data and privacy

ArmaNet stores only connection metadata needed for the product:

- detected UID/package/app name when Android can provide it
- queried domain
- virtual DNS destination and port
- protocol, network/category decision, blocked state, timestamp
- attribution confidence

The default retention limit is the latest 5,000 events and is configurable up to 50,000. Old events are pruned in periodic batches. Statistics are SQL aggregates and are not recalculated in Flutter.

Home reports the number of DNS requests actually inspected and blocked during the current VPN session. Debug Mode additionally shows packet errors, the last observed DNS timestamp, and whether Android reported active Private DNS, so a running VPN can be distinguished from a working filtering path.

ArmaNet does **not**:

- decrypt HTTPS or TLS
- perform MITM interception
- install user certificates
- inspect packet content beyond DNS questions
- collect passwords, forms, messages, or page content
- log traffic remotely
- require an account or backend

## Build and run

Requirements:

- Flutter 3.41.7 (Dart 3.11.5)
- Android SDK
- Java 17 or Java 21 (the verified local build uses Java 21)

```text
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
flutter build apk --release
```

The repository's current `release` build uses Android's debug signing key so that the complete release pipeline can be validated locally. Replace it with a private production keystore before publishing or distributing the app.

Native unit tests:

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
```

Install the APK on a physical Android device, open ArmaNet, tap the protection aperture, grant notification and VPN permissions, then use other apps normally. The foreground notification can stop protection without reopening Flutter.

## Known limitations

- Physical-device end-to-end behavior still requires manual validation; build success cannot prove OEM networking behavior.
- Reliable DNS connection-owner attribution requires Android 10/API 29+. Android may attribute resolver traffic to a system UID or return no owner; ArmaNet records `Unknown App` instead of inventing an app.
- The Unknown App row remains inspectable: its detail page aggregates only unattributed records, and Traffic can filter those individual events. Domains and decisions are real even when Android does not expose a trustworthy originating package.
- Android Private DNS, application-embedded DNS-over-HTTPS, cached DNS answers, direct IP connections, and encrypted DNS that bypasses the system resolver are not visible to this DNS-only MVP.
- The parser supports IPv4/IPv6 UDP and DNS A/AAAA. TCP DNS, full TCP/UDP forwarding, and arbitrary connection inspection are future work.
- Meta Audience Network ad delivery shares broad Meta infrastructure. Only narrow known measurement endpoints are blocked to avoid breaking Facebook/Instagram connectivity.
- The VPN uses a DNS-only `/32` route. This preserves normal internet access but limits visibility compared with a full traffic VPN engine.
- Installed-app visibility follows Android package visibility rules and intentionally lists launchable apps rather than system package clutter.

## Remote ruleset readiness

`FilterSource`, immutable versioned rules, validation, and atomic `replaceRuleset` support a future signed remote updater without provider logic changes or an application release. Network download, signature verification, rollback storage, and update scheduling are not part of this MVP.

## GitHub workflow

Pull requests and pushes to `main` run formatting verification, static analysis, Flutter tests, Android/Kotlin unit tests, and a debug APK build. Successful workflow runs expose the debug APK as a short-lived Actions artifact. Production signing credentials must never be committed; `key.properties`, keystores, certificates, local SDK paths, APKs, and build outputs are ignored.

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting code or filter rules. Security reports should follow [SECURITY.md](SECURITY.md), especially when logs or traffic metadata are involved.

## License

No open-source license has been selected yet. Until a license is added, copyright law reserves reuse and redistribution rights by default. Choose and add a license before presenting this repository as open source.
