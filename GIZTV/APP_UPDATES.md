# Automatic GIZTV app updates

After the one-time setup below, publishing an update does not require anyone to build, upload, or download an APK manually. Pushing a version tag makes GitHub Actions build and sign the app, publish the APK and `update.json`, and the installed app discovers it automatically.

Android still shows its own installation confirmation. A normal sideloaded app cannot silently replace itself; only a managed Device Owner, system app, rooted device, or an app store can do that.

## One-time setup

1. Create a **public** GitHub repository for this project and push the project to it. Public releases are required because the TV app does not contain a GitHub access token.
2. Install the first release-signed APK once. A debug-signed build cannot be upgraded by a release-signed APK, so remove the debug build before this first installation if necessary.
3. On the TV, allow **Install unknown apps** for GIZTV when Android asks. This permission is per-app and normally only needs to be enabled once.

## The signing key

Releases are signed with `app/giztv.keystore`, which is committed alongside the source. That is a
deliberate trade: the key is not a secret from anyone who can read this repository, and in exchange
every checkout can build an APK that installs over an existing GIZTV without a signature mismatch.

The workflow used to restore a keystore from an `ANDROID_KEYSTORE_BASE64` repository secret. That
secret holds a *different* certificate (`7e02fc…`) from the committed key (`3eeb52…`), and releases
up to v1.51.0 were signed with it. Anything signed with the secret can no longer install over
v1.52.0 or newer, so the workflow no longer passes `ANDROID_KEYSTORE_*` at all — with those unset,
the build falls back to the committed keystore. The secrets are unused; do not wire them back up.

If the signing key ever has to change again, every installed copy has to be uninstalled and
reinstalled by hand. Android refuses to update an app whose signature does not match.

## The application ID

`applicationId` is `com.giztv.tv`. It was `com.example.auroratv` up to and including v1.52.1.

That identifier is how Android decides whether two APKs are the same app, so the change is a clean
break rather than an upgrade:

- An installed v1.52.1 or older cannot be updated to a build carrying the new ID. The in-app updater
  will download it, but Android installs it as a **second, separate app** rather than replacing the
  first — both icons end up on the launcher.
- Nothing carries over. Watch history, My List, resume positions, and subtitle preferences all live
  in per-application private storage keyed to the old ID.
- Users have to uninstall the old GIZTV themselves. Say so plainly in the release notes for the
  first build published under the new ID.

The change was made because `com.example.*` is a placeholder prefix that Google Play rejects
outright, so shipping there would have forced the same break later, against a larger install base.

## Publish each future update

1. Increase both `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Rewrite `RELEASE_NOTES` in `../.github/workflows/release.yml` to say what changed in this
   build, and nothing else. The two standing blocks beside it are added automatically — see
   [The release notes](#the-release-notes) below.
3. Commit and push the change.
4. Push a matching tag. For `versionName = "1.7.1"`:

   ```powershell
   git tag v1.7.1
   git push origin v1.7.1
   ```

The repository workflow in `../.github/workflows/release.yml` does everything else. On its next normal launch, GIZTV checks:

`https://github.com/OWNER/REPOSITORY/releases/latest/download/update.json`

The check is quiet if the network is unavailable or the installed version is current. A newer version displays a D-pad-friendly update screen. The app downloads into persistent private storage, verifies the SHA-256 checksum, package name, version, and signing certificate, then opens Android's installer. If the installer is dismissed, the verified APK remains available through **Install now** and is re-verified instead of downloaded again.

## The release notes

Two audiences read them, and they are not the same people, so the workflow assembles a different
set for each from three pieces:

| Piece | Where it lives | Release page | In-app update screen |
| --- | --- | --- | --- |
| What changed | `RELEASE_NOTES` in the workflow | yes | yes |
| First-install and Play Protect | the `NOTES` heredoc in the workflow | yes | no |
| Upgrade path off 1.52.x | `UPGRADE_NOTES` in the workflow | yes | no — it installs itself |

Only the first needs editing per release.

The in-app screen leaves the install block out deliberately: everyone reading it already has GIZTV
installed and is about to be updated in place. The release page carries it because that is where
somebody installing for the first time lands, and Play Protect's scan prompt is what makes them
close the tab.

### Why the install block is there at all

GIZTV is sideloaded, so Android asks twice — once for **Install unknown apps**, once for Play
Protect's **App scan recommended**. Play Protect decides an app is known by how widely it has seen
that app's package name and signing certificate together, and this one is published from GitHub
rather than the Play Store, so it stays unrecognised however it is built. No manifest flag,
permission, or signing scheme turns the prompt off. Saying so before a viewer meets it is the whole
mitigation.

The APK's SHA-256 is interpolated into the block from `$APK_SHA256`, the same value that goes into
`update.json`, so the notes cannot drift from what was actually published.

Three things not to write into them:

- **Never tell anyone to turn Play Protect off.** It reads exactly like what a malicious app would
  ask for, and it costs a viewer real protection on a device that is about to grant an
  install-packages permission.
- **Never claim the app is "verified" or "Play Protect approved".** It is neither, and a viewer who
  checks will trust the rest less.
- **Never put the changelog above the install block on the release page.** A first-time viewer meets
  the prompt before they care what changed.

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
  "versionCode": 20,
  "versionName": "1.7.2",
  "apkUrl": "https://example.com/GIZTV-v1.7.2.apk",
  "sha256": "64-lowercase-hex-characters",
  "releaseNotes": "What changed in this version."
}
```
