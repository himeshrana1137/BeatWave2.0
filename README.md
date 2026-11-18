# BeatWave 2

Your local music, elevated.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-minSdk%2024-3DDC84?style=for-the-badge&logo=android&logoColor=black)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%26%20Firestore-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Material 3](https://img.shields.io/badge/Material%203-Dark%20Theme-1ED760?style=for-the-badge)
![License: MIT](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

## 🎧 About

BeatWave 2 is a Spotify-inspired local Android music player built with Kotlin and Jetpack Compose for playing audio files already stored on your device. It auto-discovers local tracks via Android `MediaStore`, runs background playback through a Media3 ExoPlayer foreground service, and persists custom-ordered playlists and listening stats in a local Room database. Users can sign in with Google to back up and restore playlists and listening statistics via Cloud Firestore, or skip sign-in and use the player completely offline.

## ✨ Features

### Music Playback
- Automatic device audio discovery via `MediaStore` across **Songs**, **Albums**, **Artists**, and **Playlists** tabs.
- Instant search filtering by track title, artist, or playlist name.
- Background and lock-screen playback powered by Media3 `ExoPlayer` and `MediaSessionService` with media notification controls.
- Full-screen player with interactive seek bar, play/pause, next/previous, shuffle toggle, and repeat modes (`OFF`, `ALL`, `ONE`).
- Album artwork loading with Coil and custom fallback cover art when embedded art is unavailable.

### Playlists
- Create, rename, and delete playlists with no pre-made default playlists.
- Long-press any song to enter multi-select mode and batch-add tracks to a new or existing playlist.
- Drag the reorder handle up or down to arrange tracks in a custom playback order that syncs live with the player queue.
- Persistent Room storage with deletion tombstones so deleted playlists remain deleted across restarts and cloud syncs.

### Stats
- Tracks play counts and active listening duration per song and overall.
- Displays total listening hours, total plays, unique tracks played, and a ranked Most Played Songs list with tap-to-play.

### Login & Cloud Sync
- Google Sign-In powered by Firebase Authentication and Android Jetpack Credential Manager (`GetSignInWithGoogleOption`).
- Automatic silent session restoration (`GetGoogleIdOption`) for returning users and a **Skip for now** offline mode saved via DataStore.
- Profile screen showing the user's Google avatar, display name, email, manual sync trigger, and logout.
- Background bidirectional sync of playlists, ordered tracks, and listening stats using Room as the local source of truth.

### UI
- Spotify-inspired obsidian dark Material 3 theme with custom `Space Grotesk` and `Plus Jakarta Sans` typography.
- Bottom navigation bar (`Library`, `Stats`, `Profile`) with a persistent mini-player docked above it and smooth animated transitions.
- Edge-to-edge layout handling system status and navigation bar insets.

## 🛠️ Tech Stack

| Layer | Technology |
| :--- | :--- |
| Language | Kotlin (`2.4.21`) |
| UI | Jetpack Compose, Material 3, Coil, Navigation Compose |
| Architecture | MVVM with `ViewModel`, `StateFlow`, Repository pattern, manual DI (`AppContainer`) |
| Audio | Jetpack Media3 (`media3-exoplayer`, `media3-session`, `media3-common` `1.5.1`) |
| Local DB | Room (`2.7.0` with KSP) & Jetpack DataStore Preferences |
| Auth | Firebase Authentication + Jetpack Credential Manager (Google Sign-In) |
| Cloud | Cloud Firestore (offline-first sync under `users/{uid}/...`) |
| Min SDK | Android 7.0 (API 24), Target/Compile SDK 36 |
| ABI Support | 32-bit (`armeabi-v7a`) and 64-bit (`arm64-v8a`) compatible universal build |

## 🏗️ Architecture

```text
app/src/main/java/com/example/
├── BeatWaveApplication.kt      # Application entry point & AppContainer manual DI graph
├── MainActivity.kt             # Edge-to-edge host activity & MediaController connection
├── auth/                       # Credential Manager Google Sign-In, auto-login & sign-out helper
├── data/
│   ├── PlaylistRepository.kt   # Coordinates local Room operations with background cloud sync
│   ├── model/                  # Domain models (Song, AlbumGroup, ArtistGroup) & SHA-256 songKey utils
│   ├── local/                  # Room database, entities, DAOs, MediaStore scanner & DataStore prefs
│   └── remote/                 # Cloud Firestore bidirectional sync & structured error logging
├── player/                     # Media3 MediaSessionService foreground service & MediaController bridge
└── ui/
    ├── BeatWaveApp.kt          # Auth gate, NavHost, bottom navigation, mini-player & player overlay
    ├── theme/                  # Dark Material 3 color palette, shapes & bundled Google Fonts
    ├── components/             # Shared AlbumArtwork, MiniPlayerBar, PlaylistPickerSheet & dialogs
    ├── viewmodel/              # MainViewModel, LibraryViewModel, PlaylistDetailViewModel, StatsViewModel
    └── screens/                # AuthScreen, LibraryScreen, PlaylistDetailScreen, PlayerScreen, StatsScreen, ProfileScreen
```

## 🚀 Getting Started

### Prerequisites
- Android Studio (Ladybug or newer) with Android SDK 36 installed
- JDK 11 or higher (configured in `compileOptions`)
- An Android device or emulator running Android 7.0 (API 24) or higher with local audio files

### Setup & Run

1. **Clone the repository**
   ```bash
   git clone https://github.com/himeshrana1137/beatwave-2.git
   cd beatwave-2
   ```

2. **Configure Firebase**
   1. Create a project in the [Firebase Console](https://console.firebase.google.com/) and add an Android app using package name `com.aistudio.beatwave.mzkqrx` (or your updated `applicationId` in `app/build.gradle.kts`).
   2. Enable **Authentication → Sign-in method → Google** and **Cloud Firestore**.
   3. Generate your debug SHA-1 fingerprint and register it in Firebase Project Settings:
      ```bash
      keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
      ```
   4. Download `google-services.json` from Firebase and place it in `app/google-services.json` (ensure the Web client ID with `"client_type": 3` is included so `R.string.default_web_client_id` is generated automatically).
   5. Verify `app/src/main/res/values/firebase_applet_config.xml` contains your `firestore_database_id` (or `"(default)"` for a standard Firebase Console database) and deploy `firestore.rules`.

3. **Build and run**
   ```bash
   gradle :app:assembleDebug
   ```
   Or open the project in Android Studio, wait for Gradle sync, and click **Run**.

## 🔐 Permissions

| Permission | Purpose |
| :--- | :--- |
| `READ_MEDIA_AUDIO` | Scans local audio files stored on devices running Android 13 (API 33) and above. |
| `READ_EXTERNAL_STORAGE` | Scans local audio files on Android 12 (API 32) and below (`maxSdkVersion="32"`). |
| `FOREGROUND_SERVICE` | Allows the Media3 playback service to run continuously in the background. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Declares media playback foreground service type required on Android 14+. |
| `POST_NOTIFICATIONS` | Displays active playback media controls in the system notification shade on Android 13+. |
| `WAKE_LOCK` | Keeps audio playback uninterrupted when the device screen turns off. |
| `INTERNET` | Connects to Firebase Authentication, Cloud Firestore sync, and Google profile avatars. |

## ☁️ How Cloud Sync Works

- **Metadata Only:** Only lightweight text metadata—playlist names, ordered track references, play counts, and listening durations—is synced to Cloud Firestore. Your actual audio files never leave your phone.
- **Offline-First Source of Truth:** Room remains the primary data store at all times so browsing, playlists, and playback work seamlessly offline; deleted playlists record local tombstones so they stay deleted when syncing later.
- **Strict User Isolation:** Deployed `firestore.rules` lock all documents under `/users/{userId}/...` with `request.auth != null && request.auth.uid == userId`, type/size validation, and immutable `createdAt` timestamps so each user can only read and write their own data.

## 🗺️ Roadmap

- [x] Google Sign-In via Credential Manager with persistent session & offline skip mode
- [x] `MediaStore` local audio scanner with Songs, Albums, Artists, and Playlists tabs
- [x] Media3 ExoPlayer foreground service with notification & lock-screen controls
- [x] Custom-ordered Room playlists with multi-select add and drag-to-reorder
- [x] Listening stats tracking play counts and total listening hours
- [x] Authenticated Cloud Firestore sync for playlists and stats with zero-trust rules
- [ ] Built-in audio equalizer and playback speed controls
- [ ] Sleep timer and home-screen playback widget
- [ ] LRC synced lyrics viewer for local audio files

## 🤝 Contributing

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/your-feature`).
3. Commit your changes (`git commit -m "Add your feature"`).
4. Push to your branch (`git push origin feature/your-feature`) and open a Pull Request.

## 📄 License

Distributed under the MIT License. See `LICENSE` for details.

## 👤 Author

**Rana Himesh** — GitHub: [@himeshrana1137](https://github.com/himeshrana1137)
