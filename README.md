# Janet Mobile

Android app for [Janet](https://github.com/QuittyMR/janet).

I made this to avoid yelling instructions at a server-connected mic from across the room.

## Features

*   Record/stop audio and upload it to Janet.
*   "Private" toggle for uploads.
*   Customizable device name.
*   Home screen widget for quick access.
*   Abort button to cancel a recording or upload.

## Building

Clone the repo, open it in Android Studio, and build. It targets `minSdk` 24.

## Configuration

The Janet server URL is hardcoded in `app/src/main/java/tech/quitty/janet/RecordingService.kt`. If you need to change it, you'll have to do it there and rebuild.

```kotlin
private val uploadUrl = "https://domicile.home.mr/janet"
```

The app is set up to trust self-signed SSL certificates, so it works with local HTTPS instances.

## Dev Notes

*   A foreground service (`RecordingService`) handles recording so the OS doesn't kill it.
*   `LiveData` syncs state between the service, main activity, and widget.
*   *WARNING*: This project is 100% vibe-coded and is by no means a testament to quality Kotlin code. I know just enough Kotlin to be dangerous.
