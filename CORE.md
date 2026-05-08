# CORE — DeepLoader Project State File
**Last Updated:** 2026-05-08T11:21:00+05:30
**Updated By:** Claude Opus 4.6
**Session Summary:** Phase 5 COMPLETE — Stream Player with ExoPlayer/Media3, PiP, background audio, MiniPlayer

## PROJECT IDENTITY
- Name: DeepLoader
- Type: Android APK (Native Kotlin)
- Developer: Sonu Verma
- Target: Android 8.0+ (API 26+)
- Distribution: GitHub Releases + Direct APK (no Play Store)
- Cost: Zero (all free-tier tools only)
- Build Environment: 100% Cloud — GitHub Codespaces + GitHub Actions
- Local Requirement: NONE (no Android Studio needed)

## CLOUD ENVIRONMENT STATUS
- GitHub Repo: https://github.com/SONUVERMA11/DeepLoader
- Codespaces URL: Not yet launched
- Actions Workflow: CONFIGURED
- Last Successful APK Build: NONE
- APK Download Link: NONE

## CURRENT PHASE
Phase 5 — Stream Player
Status: COMPLETE ✅
Blocker (if any): None

## ARCHITECTURE SNAPSHOT
- Module: ExtractionEngine → Status: DONE ✅
- Module: DownloadManager  → Status: DONE ✅
- Module: TorrentEngine    → Status: DONE ✅
- Module: StreamPlayer     → Status: DONE ✅
- Module: UILayer          → Status: DONE ✅
- Module: AutoUpdater      → Status: DONE ✅

## FILES CREATED
- /CORE.md → Project state file (single source of truth)
- /.devcontainer/devcontainer.json → Codespace config
- /.devcontainer/setup-android.sh → Android SDK auto-installer
- /.github/workflows/build.yml → GitHub Actions APK builder
- /gradle.properties → Build performance flags
- /.gitignore → Git ignore rules
- /build.gradle.kts → Root Gradle build file
- /settings.gradle.kts → Gradle settings
- /gradlew → Gradle wrapper script
- /gradle/wrapper/gradle-wrapper.properties → Wrapper config
- /app/build.gradle.kts → App module dependencies
- /app/proguard-rules.pro → ProGuard config
- /app/src/main/AndroidManifest.xml → App manifest
- /app/src/main/java/com/sonuverma/deeploader/MainActivity.kt → Entry point
- /app/src/main/java/com/sonuverma/deeploader/DeepLoaderApp.kt → Application class
- /app/src/main/java/com/sonuverma/deeploader/ui/theme/Color.kt → Color system
- /app/src/main/java/com/sonuverma/deeploader/ui/theme/Type.kt → Typography
- /app/src/main/java/com/sonuverma/deeploader/ui/theme/Theme.kt → Theme system
- /app/src/main/java/com/sonuverma/deeploader/ui/navigation/DeepLoaderNavigation.kt → Nav
- /app/src/main/java/com/sonuverma/deeploader/ui/screens/HomeScreen.kt → Home tab
- /app/src/main/java/com/sonuverma/deeploader/ui/screens/SearchScreen.kt → Search tab
- /app/src/main/java/com/sonuverma/deeploader/ui/screens/LibraryScreen.kt → Library tab
- /app/src/main/java/com/sonuverma/deeploader/ui/screens/SettingsScreen.kt → Settings tab
- /app/src/main/java/com/sonuverma/deeploader/data/db/AppDatabase.kt → Room DB
- /app/src/main/java/com/sonuverma/deeploader/data/db/DownloadDao.kt → DAO
- /app/src/main/java/com/sonuverma/deeploader/data/db/DownloadEntity.kt → Entity
- /app/src/main/java/com/sonuverma/deeploader/data/prefs/AppPreferences.kt → Prefs
- /app/src/main/java/com/sonuverma/deeploader/data/models/ExtractionResult.kt → Models
- /app/src/main/java/com/sonuverma/deeploader/data/models/StreamFormat.kt → Stream model
- /app/src/main/java/com/sonuverma/deeploader/data/models/DownloadJob.kt → Download model
- /app/src/main/java/com/sonuverma/deeploader/di/AppModule.kt → Hilt DI
- /app/src/main/java/com/sonuverma/deeploader/data/models/TorrentInfo.kt → Torrent model
- /app/src/main/java/com/sonuverma/deeploader/core/extraction/UrlPlatformDetector.kt → Platform detection
- /app/src/main/java/com/sonuverma/deeploader/core/download/DownloadService.kt → Foreground service
- /app/src/main/java/com/sonuverma/deeploader/core/download/BootReceiver.kt → Boot receiver
- /app/src/main/java/com/sonuverma/deeploader/ui/DeepLoaderMainScreen.kt → Main screen + nav
- /app/src/main/res/* → Launcher icons, strings, themes, network config
- /README.md → Project documentation
- /gradlew.bat → Gradle wrapper (Windows)
- /gradle/wrapper/gradle-wrapper.jar → Wrapper JAR
- /app/src/main/java/.../core/extraction/ExtractionEngine.kt → 3-layer orchestrator
- /app/src/main/java/.../core/extraction/NewPipeExtractorWrapper.kt → Layer 1 (NewPipe)
- /app/src/main/java/.../core/extraction/NewPipeDownloaderImpl.kt → Custom HTTP downloader
- /app/src/main/java/.../core/extraction/YtDlpExtractor.kt → Layer 2 (yt-dlp binary)
- /app/src/main/java/.../core/extraction/InnertubeExtractor.kt → Layer 3 (Raw Innertube)
- /app/src/main/java/.../core/updater/YtDlpUpdater.kt → Auto-updater from GitHub
- /app/src/main/java/.../core/clipboard/ClipboardWatcher.kt → Auto-detect URLs
- /app/src/main/java/.../ui/viewmodel/MainViewModel.kt → Central ViewModel
- /app/src/main/java/.../ui/components/FormatBottomSheet.kt → Format selection UI
- /app/src/main/java/.../ui/components/ClipboardBanner.kt → Clipboard URL banner
- /app/src/main/java/.../ui/components/DownloadCard.kt → Download progress card
- /app/src/main/java/.../ui/components/SpeedGraph.kt → Real-time speed graph
- /app/src/main/java/.../core/download/ChunkedDownloader.kt → 16-thread byte-range engine
- /app/src/main/java/.../core/download/DownloadWorker.kt → WorkManager foreground worker
- /app/src/main/java/.../core/download/DownloadManager.kt → Central download coordinator
- /app/src/main/java/.../core/download/AudioVideoMerger.kt → DASH muxing via MediaMuxer
- /app/src/main/java/.../core/download/MediaStoreSaver.kt → Scoped storage / gallery save
- /app/src/main/java/.../core/download/NetworkMonitor.kt → Reactive connectivity observer
- /app/src/main/java/.../core/download/NotificationHelper.kt → Complete/error notifications
- /app/src/main/java/.../core/torrent/TorrentEngine.kt → libtorrent4j BitTorrent client
- /app/src/main/java/.../core/torrent/TorrentSearchEngine.kt → Multi-source torrent scraper
- /app/src/main/java/.../ui/viewmodel/TorrentViewModel.kt → Torrent search+download VM
- /app/src/main/java/.../ui/components/TorrentCard.kt → Torrent progress + search result cards
- /app/src/main/java/.../core/player/PlayerManager.kt → Singleton ExoPlayer/Media3 manager
- /app/src/main/java/.../core/player/PlaybackService.kt → Media3 MediaSessionService
- /app/src/main/java/.../core/player/PipController.kt → Picture-in-Picture controller
- /app/src/main/java/.../ui/screens/VideoPlayerScreen.kt → Full-screen video player
- /app/src/main/java/.../ui/components/MiniPlayer.kt → Compact playback bar

## DECISIONS LOG
- [2026-05-08] Using GitHub Codespaces as primary IDE — Reason: free 60hr/month, full VS Code in browser
- [2026-05-08] Using GitHub Actions for all APK builds — Reason: free 2000 min/month public repo
- [2026-05-08] Chose NewPipe Extractor as primary YouTube engine — Reason: native Java, no ARM binary needed
- [2026-05-08] 3-layer extraction fallback: NewPipe → yt-dlp → Innertube — Reason: maximum reliability
- [2026-05-08] libtorrent4j for torrent engine — Reason: mature Java bindings, DHT+PEX support
- [2026-05-08] Jetpack Compose + Material3 for UI — Reason: modern, declarative, iOS-quality animations
- [2026-05-08] Three themes: Light/Dark/AMOLED — Reason: user preference + battery savings on OLED

## WHAT IS DONE ✅
- [x] CORE.md created
- [x] .devcontainer configured
- [x] GitHub Actions workflow added
- [x] Project structure initialized
- [x] Gradle build files created
- [x] Theme system (Light/Dark/AMOLED)
- [x] 4-tab navigation with Compose
- [x] Room database schema
- [x] Hilt DI module
- [x] All screen skeletons
- [x] ExtractionEngine (3-layer fallback)
- [x] NewPipe Extractor wrapper + custom HTTP downloader
- [x] yt-dlp binary extractor + JSON parser
- [x] Innertube direct API extractor
- [x] yt-dlp auto-updater (GitHub releases)
- [x] Clipboard watcher (auto-detect URLs)
- [x] MainViewModel (central state management)
- [x] FormatBottomSheet (quality/format selection)
- [x] ClipboardBanner (animated URL detection)
- [x] DownloadCard (progress + controls)
- [x] SpeedGraph (real-time bezier curve)
- [x] HomeScreen connected to ViewModel
- [x] ChunkedDownloader (16-thread byte-range parallel)
- [x] DownloadWorker (WorkManager foreground worker)
- [x] DownloadManager (central coordinator)
- [x] AudioVideoMerger (DASH mux + MP3 conversion)
- [x] MediaStoreSaver (scoped storage + gallery)
- [x] NetworkMonitor (reactive WiFi/cellular state)
- [x] NotificationHelper (complete/error notifications)
- [x] DownloadService updated with real controls
- [x] HomeScreen wired to real download actions
- [x] TorrentEngine (libtorrent4j with DHT)
- [x] TorrentSearchEngine (PirateBay + 1337x + Nyaa.si)
- [x] TorrentViewModel (search state + active torrents)
- [x] TorrentCard + TorrentSearchResultCard UI
- [x] SearchScreen fully connected to TorrentViewModel
- [x] libtorrent4j dependency + ABI splits enabled
- [x] ProGuard rules for libtorrent4j JNI
- [x] PlayerManager (ExoPlayer singleton with state tracking)
- [x] PlaybackService (Media3 session + lock screen controls)
- [x] PipController (PiP with auto-enter on Android 12+)
- [x] VideoPlayerScreen (immersive player with overlay controls)
- [x] MiniPlayer (compact bar above bottom nav)
- [x] FormatBottomSheet Play button (stream without download)
- [x] MainViewModel playStream() integration
- [x] AndroidManifest PiP + media service declarations

## WHAT IS IN PROGRESS 🔄
- Push Phase 5 to GitHub
- Verify build passes

## WHAT IS NEXT 📋
1. Push Phase 5 to GitHub and verify Actions build
2. Phase 6 — Power Features (share intent processing, biometric auth, scheduled downloads)
3. Phase 7 — Polish & Release (ProGuard tuning, signing, Play Store listing)

## KNOWN ISSUES & BUGS 🐛
- None yet

## ENVIRONMENT & TOOLS
- Language: Kotlin
- Min SDK: 26 (Android 8.0)
- Target SDK: 35
- Build Tool: Gradle 8.x + AGP 8.x
- UI: Jetpack Compose + Material3
- Cloud IDE: GitHub Codespaces (VS Code in browser)
- Cloud Build: GitHub Actions (ubuntu-latest runner)
- Android SDK: Installed in Codespace via sdkmanager CLI
- Key libs: NewPipeExtractor, libtorrent4j, ExoPlayer Media3, OkHttp, Room, WorkManager, Coil, Gson
