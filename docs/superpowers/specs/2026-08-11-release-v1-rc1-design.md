# RemoteX Android v1.0.0-rc1 Release Design

## Goal
Prepare the current RemoteX codebase for a public GitHub release without changing runtime behavior.

## Release strategy
Publish `v1.0.0-rc1` first. The tag-triggered GitHub Actions release workflow builds a signed release APK, verifies the signature, generates SHA-256, and publishes both assets. After one real-device validation cycle, the same code may be promoted to `v1.0.0`.

## Documentation
The repository must include an Indonesian user guide covering host prerequisites, profiles, Desktop/VNC, Terminal/SSH, File/SFTP, audio, Watch Mode, quality/input controls, profile backup, migration from debug to release, troubleshooting, and security. README remains concise and links to the full guide.

## Migration
Debug and release use different application IDs. The first release installs alongside the debug build. Users should export profiles from debug and import them into release. Export intentionally excludes credentials, so passwords/private-key secrets must be entered again.

## Security
Release signing secrets remain GitHub Actions secrets and the keystore must never be committed. VNC traffic is documented as suitable only for a trusted LAN/VPN because classic VNC does not encrypt session traffic.
