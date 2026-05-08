package com.sonuverma.deeploader.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sonuverma.deeploader.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-based preferences for DeepLoader.
 * Replaces SharedPreferences with type-safe, coroutine-first approach.
 * All preference reads return Flow for reactive UI updates.
 */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deeploader_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // ─── Preference Keys ───
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val DEFAULT_CHUNK_COUNT = intPreferencesKey("default_chunk_count")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_UPDATE_YTDLP = booleanPreferencesKey("auto_update_ytdlp")
        val CLIPBOARD_WATCHER = booleanPreferencesKey("clipboard_watcher")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory")
        val MP3_BITRATE = intPreferencesKey("mp3_bitrate")
        val SHOW_SPEED_GRAPH = booleanPreferencesKey("show_speed_graph")
        val YTDLP_VERSION = stringPreferencesKey("ytdlp_version")
        val YTDLP_LAST_CHECK = longPreferencesKey("ytdlp_last_check")
        val SAVE_TO_GALLERY = booleanPreferencesKey("save_to_gallery")
        val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val DARK_NAV_BAR = booleanPreferencesKey("dark_nav_bar")
    }

    // ─── Theme ───
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        try {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    // ─── Download Settings ───
    val maxConcurrentDownloads: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 3
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5) }
    }

    val defaultChunkCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_CHUNK_COUNT] ?: 16
    }

    suspend fun setDefaultChunkCount(count: Int) {
        dataStore.edit { it[Keys.DEFAULT_CHUNK_COUNT] = count.coerceIn(1, 32) }
    }

    val wifiOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.WIFI_ONLY] ?: false
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    val defaultQuality: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_QUALITY] ?: "1080p"
    }

    suspend fun setDefaultQuality(quality: String) {
        dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality }
    }

    val mp3Bitrate: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.MP3_BITRATE] ?: 320
    }

    suspend fun setMp3Bitrate(bitrate: Int) {
        dataStore.edit { it[Keys.MP3_BITRATE] = bitrate }
    }

    val saveToGallery: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SAVE_TO_GALLERY] ?: true
    }

    suspend fun setSaveToGallery(enabled: Boolean) {
        dataStore.edit { it[Keys.SAVE_TO_GALLERY] = enabled }
    }

    val autoRetry: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_RETRY] ?: true
    }

    suspend fun setAutoRetry(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RETRY] = enabled }
    }

    // ─── Feature Toggles ───
    val autoUpdateYtDlp: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_UPDATE_YTDLP] ?: true
    }

    suspend fun setAutoUpdateYtDlp(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_UPDATE_YTDLP] = enabled }
    }

    val clipboardWatcher: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CLIPBOARD_WATCHER] ?: true
    }

    suspend fun setClipboardWatcher(enabled: Boolean) {
        dataStore.edit { it[Keys.CLIPBOARD_WATCHER] = enabled }
    }

    val biometricLock: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC_LOCK] ?: false
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    val showSpeedGraph: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_SPEED_GRAPH] ?: true
    }

    suspend fun setShowSpeedGraph(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_SPEED_GRAPH] = enabled }
    }

    val notificationSound: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATION_SOUND] ?: true
    }

    suspend fun setNotificationSound(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATION_SOUND] = enabled }
    }

    // ─── yt-dlp Version Tracking ───
    val ytDlpVersion: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.YTDLP_VERSION] ?: "none"
    }

    suspend fun setYtDlpVersion(version: String) {
        dataStore.edit { it[Keys.YTDLP_VERSION] = version }
    }

    val ytDlpLastCheck: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.YTDLP_LAST_CHECK] ?: 0L
    }

    suspend fun setYtDlpLastCheck(timestamp: Long) {
        dataStore.edit { it[Keys.YTDLP_LAST_CHECK] = timestamp }
    }
}
