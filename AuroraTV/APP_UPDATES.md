# Automatic GIZTV app updates

After the one-time setup below, publishing an update does not require anyone to build, upload, or download an APK manually. Pushing a version tag makes GitHub Actions build and sign the app, publish the APK and `update.json`, and the installed app discovers it automatically.

Android still shows its own installation confirmation. A normal sideloaded app cannot silently replace itself; only a managed Device Owner, system app, rooted device, or an app store can do that.

## One-time setup

1. Create a **public** GitHub repository for this project and push the project to it. Public releases are required because the TV app does not contain a GitHub access token.
2. Create and permanently back up one release keystore. Every future update must use this exact key:

   ```powershell
   keytool -genkeypair -v -keystore giztv-release.jks -alias giztv -keyalg RSA -keysize 4096 -validity 10000
   ```

3. In the repository, open **Settings → Secrets and variables → Actions** and add these repository secrets:

   - `ANDROID_KEYSTORE_BASE64`: Base64 text of the entire `.jks` file. In PowerShell, generate it with:

     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\giztv-release.jks))) | Set-Clipboard
     ```

   - `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
   - `ANDROID_KEY_ALIAS`: `giztv` (or the alias chosen above).
   - `ANDROID_KEY_PASSWORD`: the key password.

4. Install the first release-signed APK once. A debug-signed build cannot be upgraded by a release-signed APK, so remove the debug build before this first installation if necessary.
5. On the TV, allow **Install unknown apps** for GIZTV when Android asks. This permission is per-app and normally only needs to be enabled once.

Never commit the `.jks` file or its passwords. Losing the keystore means existing installations can no longer accept updates.

## Publish each future update

1. Increase both `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit and push the change.
3. Push a matching tag. For `versionName = "1.7.0"`:

   ```powershell
   git tag v1.7.0
   git push origin v1.7.0
   ```

The repository workflow in `../.github/workflows/release.yml` does everything else. On its next normal launch, GIZTV checks:

`https://github.com/OWNER/REPOSITORY/releases/latest/download/update.json`

The check is quiet if the network is unavailable or the installed version is current. A newer version displays a D-pad-friendly update screen. The app downloads into its private cache, verifies the SHA-256 checksum, package name, version, and signing certificate, then opens Android's installer.

## Local testing with another update feed

For a debug-only test, add one of these entries to the untracked `local.properties` file and rebuild:

```properties
update.github.repository=OWNER/REPOSITORY
# Or provide a complete feed URL:
# update.manifest.url=https://example.com/update.json
```

The feed format is:

```json
{
  "versionCode": 19,
  "versionName": "1.7.1",
  "apkUrl": "https://example.com/GIZTV-v1.7.1.apk",
  "sha256": "64-lowercase-hex-characters",
  "releaseNotes": "What changed in this version."
}
```
