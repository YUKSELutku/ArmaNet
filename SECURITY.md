# Security Policy

## Supported versions

Security fixes are applied to the latest code on `main` and the latest `1.0.x` build during MVP development. Older APKs should be treated as unsupported.

## Reporting a vulnerability

Prefer GitHub Private Vulnerability Reporting from the repository's **Security** tab. If private reporting is unavailable, open a minimal issue asking for a private contact channel without publishing exploit details, credentials, private traffic domains, package names, device identifiers, or IP addresses.

Include, when safe:

- affected ArmaNet version and Android version
- reproducible steps using synthetic domains or test applications
- expected and actual behavior
- security impact
- whether the issue requires VPN permission, physical access, or a malicious application

Do not attach production keystores, signing passwords, unredacted `logcat` output, database files, or real browsing history.

## Scope notes

ArmaNet is a DNS-only local VPN. Reports about bypass through application-embedded DNS-over-HTTPS, cached DNS, direct IP traffic, or same-domain advertising are useful product limitations but are not automatically security vulnerabilities. Claims involving data exfiltration, permission misuse, traffic interception, unsafe rule updates, or signing-key exposure are security-sensitive.
