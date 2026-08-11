# Privacy

RemoteX Android is a direct remote-access client. The current source tree does not include advertising, analytics, telemetry, Firebase Analytics, Crashlytics, or third-party tracking SDKs.

Connection profiles and application settings are stored locally on the Android device. Credentials saved with **Simpan terenkripsi** are encrypted using an Android Keystore-backed key. Profile export intentionally excludes passwords and private-key secrets.

RemoteX sends network traffic only as required for the remote features selected by the user, including VNC, SSH, SFTP, remote audio over SSH, and Watch Mode over SSH.

Diagnostic logs are intended for troubleshooting and use redaction safeguards. Users should still review logs before posting them publicly and must not publish passwords, private keys, tokens, or other secrets.
