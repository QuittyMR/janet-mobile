# Project: janet

This is an Android application named "janet".

## Core Functionality:

The application is a simple audio recorder. Its primary features are:

1.  **Audio Recording:** The app can record audio from the device's microphone.
2.  **File Upload:** After recording, the audio file is automatically uploaded to a hardcoded server endpoint (`https://domicile.home.mr/janet`).
3.  **Device Identification:** The app allows users to set a custom "device name" which is sent as a parameter during the upload.
4.  **Private Upload:** A toggle allows the user to mark an upload as "private" by adding a `private=1` parameter to the request.
5.  **Abort Functionality:** A button allows the user to cancel a recording in progress or an ongoing upload.
6.  **UI Feedback:** The app displays a spinner during the upload and shows the server's response message on the screen after the upload is complete.

## Key Components:

*   **`MainActivity.kt`**: The main user interface of the application. It contains the main record/stop button, a "Private" toggle switch, an "Abort" button, and a settings menu to define the custom device name. It also displays UI feedback like the upload spinner and server messages.
*   **`RecordingService.kt`**: A background service that manages the `MediaRecorder` for audio capture. It handles the lifecycle of recording, initiates the upload process using `OkHttp`, and can be aborted via an intent. It is configured to trust self-signed SSL certificates for use with LAN-only endpoints.
*   **`JanetWidgetProvider.kt`**: Provides a home screen widget that allows the user to start and stop audio recording without opening the main application.
*   **`WidgetClickReceiver.kt`**: A dedicated `BroadcastReceiver` that handles clicks from the home screen widget to start the service and the main activity.
*   **`RecordingState.kt`**: A singleton object that holds the global application state using `LiveData` (e.g., `isRecording`, `isUploading`, `uploadResponse`, `activeCall`), allowing different components to stay in sync.

## Architecture:

The app uses a foreground service (`RecordingService`) to ensure audio recording can continue even when the app is in the background. State is shared between the service, main activity, and widget using a `LiveData` object (`RecordingState`). The upload functionality is performed asynchronously using `OkHttp`. Widget clicks are handled by a dedicated `BroadcastReceiver` to ensure reliability.
