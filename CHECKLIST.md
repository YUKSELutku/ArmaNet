# ArmaNet MVP Checklist

## Project Setup

* [x] Flutter project created
* [x] Android Kotlin integration ready
* [x] MethodChannel and EventChannel ready
* [x] Project compiles as an APK
* [x] Branded standard and adaptive Android launcher icons

## VPN

* [x] VpnService implemented
* [x] Official VPN permission flow implemented
* [x] Android 13+ notification permission handled
* [x] Foreground notification and stop action implemented
* [x] VPN start/stop state machine implemented
* [x] Bounded start/stop confirmation implemented
* [x] Service independent from Flutter UI lifecycle
* [x] Foreground startup and packet-worker failures contained
* [x] Live DNS/blocked/error health diagnostics exposed
* [ ] Physical-device VPN survival verified

## DNS

* [x] DNS-only `/32` TUN route
* [x] IPv4 UDP packet parsing
* [x] IPv6 UDP packet parsing without extension headers
* [x] DNS domain parser
* [x] A requests supported
* [x] AAAA requests supported
* [x] Protected upstream DNS relay
* [x] NXDOMAIN blocked response
* [x] SERVFAIL timeout response
* [x] VPN socket loop avoided
* [ ] TCP DNS supported

## Filtering

* [x] Versioned local advertisement rules
* [x] Versioned local tracker rules
* [x] Domain-to-network metadata
* [x] Filter source and loader abstraction
* [x] Domain normalization
* [x] Exact and parent-domain matching
* [x] Allowlist precedence and persistence
* [x] Persistent user custom blocklist with breakage warning
* [x] Advertisement/tracker category
* [x] Unknown domains default to allow
* [x] Future ruleset replacement seam

## App Attribution

* [x] Android 10+ connection-owner UID attempt
* [x] UID to package mapping
* [x] Package to app name and icon
* [x] Attribution confidence
* [x] Unknown App fallback without fake data
* [ ] Attribution reliability verified across physical Android versions

## Traffic and Data

* [x] Central TrafficEvent model
* [x] SQLite TrafficRepository abstraction
* [x] Recent history queries and filters
* [x] 5,000-event default retention
* [x] Batched old-event pruning
* [x] Clear history/statistics
* [x] Per-app SQL aggregation
* [x] Per-domain SQL aggregation
* [x] AndroidX DataStore settings

## UI

* [x] Material 3 shell and four-section navigation
* [x] Home protection and live statistics
* [x] Apps list with icons and counts
* [x] App Detail domain statistics and filters
* [x] Traffic/history filters
* [x] Settings and allowlist
* [x] Filter information
* [x] Searchable embedded ruleset browser
* [x] Debug metadata mode
* [x] Non-starving 400 ms live-event batching

## Tests

* [x] FilterEngine unit tests
* [x] Flutter/native bridge contract tests
* [x] DNS parser and response tests
* [x] IP/UDP packet tests
* [x] UI smoke tests updated

## Release

* [x] Debug build works
* [x] Release build works with the development signing configuration
* [x] GitHub CI, issue templates, contribution guide, and security policy
* [ ] Physical Android device tested
