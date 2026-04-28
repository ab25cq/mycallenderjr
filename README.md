# MyCallenderJr

## Build prerequisites

Required packages and tools:

- `bash`
- `OpenJDK 21`
- `Android SDK Command-line Tools`
- `Android SDK Platform 35`
- `Android SDK Build-Tools 35.0.0`
- `Android SDK Platform-Tools`

This project uses the Gradle wrapper and downloads `Gradle 8.7` automatically.

## Build APK

Make the script executable once:

```bash
chmod +x fast_build.sh
```

Build a debug APK:

```bash
./fast_build.sh
```

The APK is copied to the current directory as:

```text
./mycallenderjr.apk
```

## Build signed release APK

Set the signing environment variables before running the script:

```bash
export RELEASE_STORE_FILE=/path/to/my-release-key.jks
export RELEASE_STORE_PASSWORD=your-store-password
export RELEASE_KEY_ALIAS=your-key-alias
export RELEASE_KEY_PASSWORD=your-key-password
./fast_build.sh
```

When all four variables are set, the script builds a signed release APK and copies it to:

```text
./mycallenderjr.apk
```

or 

use codex for build instead.

# join google play tester

join the mycallender group
https://groups.google.com/g/mycallender

become the tester and intall app
https://play.google.com/apps/testing/com.mycallenderjr


