# Releasing to Google Play (M2-27)

Target API is 36, which meets Play's requirement for new apps and updates from 2026-08-31. `applicationId` is `com.greenzean.animekmp`; `versionCode` and `versionName` are in `androidApp/build.gradle.kts` and `versionCode` must go up with every upload.

## Upload keystore
Created once with `keytool`, kept outside the repo in `~/.animekmp-signing/` (`upload.jks` plus a `signing.env` file with the `ANIMEKMP_*` variables). Back it up (a password manager or an encrypted drive). Play App Signing holds the real app signing key; this one only proves uploads are yours.

```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Local build with it:

```
set -a; . ~/.animekmp-signing/signing.env; set +a
./gradlew :androidApp:bundleRelease
```

## GitHub secrets
| Secret | Value |
|---|---|
| `ANIMEKMP_KEYSTORE_BASE64` | `base64 -i upload.jks` |
| `ANIMEKMP_KEYSTORE_PASSWORD`, `ANIMEKMP_KEY_ALIAS`, `ANIMEKMP_KEY_PASSWORD` | From `keytool` |
| `ANIMEKMP_SERVER_URL_RELEASE` | The public HTTPS GraphQL URL |
| `ANIMEKMP_GOOGLE_SERVER_CLIENT_ID`, `ANIMEKMP_MAL_CLIENT_ID`, `ANIMEKMP_MAL_REDIRECT_URI` | As in `local.properties` |

## Build
- CI: run the `Release` workflow (manual, or push a `v*` tag). It uploads the signed AAB as the `release-aab` artifact.
- Locally: set the `ANIMEKMP_*` environment variables for the keystore and the `animekmp.*` Gradle properties, then `./gradlew :androidApp:bundleRelease`.
- The release build fails when the release server URL is not HTTPS or when a client ID or the MAL redirect is empty.
- Pull requests only check that the unsigned release build passes R8.

## Play Console checklist
1. Create the app and enrol in Play App Signing.
2. Internal testing, create a release, upload the AAB, add the testers' emails.
3. Privacy policy URL: https://greenzean.github.io/privacy/
4. Account deletion URL: the web page from [data-safety.md](data-safety.md).
5. Data safety form: answers in [data-safety.md](data-safety.md).
6. Content rating questionnaire, store listing text and screenshots.
7. App integrity, copy the app signing SHA-256 into `/.well-known/assetlinks.json` in the Pages repo, then check on an installed build: `adb shell pm get-app-links com.greenzean.animekmp` shows the MAL host as `verified`.
