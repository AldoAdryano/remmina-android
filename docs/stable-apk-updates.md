# Stable APK Updates

RemoteX debug APKs built by GitHub Actions must use a persistent signing key.
Ephemeral Android debug keys from disposable CI runners cannot provide in-place updates.

Required GitHub Actions repository secrets:

- `REMOTEX_KEYSTORE_BASE64`
- `REMOTEX_KEYSTORE_PASSWORD`
- `REMOTEX_KEY_ALIAS`
- `REMOTEX_KEY_PASSWORD`

The debug workflow uses the same persistent signing material on every run and sets
`versionCode` from `github.run_number`.

The first APK produced after enabling persistent signing cannot update an older APK
that was signed by an ephemeral key. Uninstall the old APK once, install the first
persistently signed APK, then keep the same GitHub secrets permanently. Future
debug builds can update in place.

Do not commit the keystore or its Base64 representation to this public repository.
