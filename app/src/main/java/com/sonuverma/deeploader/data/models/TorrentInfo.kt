package com.sonuverma.deeploader.data.models

/**
 * Represents a torrent with search result metadata.
 */
data class TorrentInfo(
    val name: String,
    val magnetUri: String,
    val infoHash: String = "",
    val size: Long = 0L,          // bytes
    val seeders: Int = 0,
    val leechers: Int = 0,
    val uploadDate: String = "",
    val uploader: String = "",
    val category: TorrentCategory = TorrentCategory.OTHER,
    val source: TorrentSource = TorrentSource.UNKNOWN,
    val files: List<TorrentFile> = emptyList(),
    val trackers: List<String> = emptyList(),
    val isStreaming: Boolean = false, // True if sequential download active
    val downloadProgress: Float = 0f,
    val downloadSpeed: Long = 0L,  // bytes/sec
    val uploadSpeed: Long = 0L,    // bytes/sec
    val connectedPeers: Int = 0,
    val status: TorrentStatus = TorrentStatus.IDLE
) {
    val displaySize: String
        get() = when {
            size <= 0 -> "Unknown"
            size < 1024 * 1024 -> "${"%.0f".format(size.toDouble() / 1024)} KB"
            size < 1024L * 1024 * 1024 -> "${"%.1f".format(size.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(size.toDouble() / (1024L * 1024 * 1024))} GB"
        }
}

data class TorrentFile(
    val path: String,
    val size: Long,
    val priority: Int = 4, // 0=skip, 1=low, 4=normal, 7=high
    val progress: Float = 0f
) {
    val fileName: String
        get() = path.substringAfterLast("/")

    val displaySize: String
        get() = when {
            size < 1024 * 1024 -> "${"%.0f".format(size.toDouble() / 1024)} KB"
            size < 1024L * 1024 * 1024 -> "${"%.1f".format(size.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(size.toDouble() / (1024L * 1024 * 1024))} GB"
        }
}

enum class TorrentCategory(val displayName: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    GAMES("Games"),
    SOFTWARE("Software"),
    BOOKS("Books"),
    ANIME("Anime"),
    OTHER("Other")
}

enum class TorrentSource(val displayName: String) {
    PIRATEBAY("The Pirate Bay"),
    NYAA("Nyaa.si"),
    LEETX("1337x"),
    UNKNOWN("Unknown")
}

enum class TorrentStatus(val displayName: String) {
    IDLE("Idle"),
    CHECKING("Checking Files"),
    DOWNLOADING("Downloading"),
    SEEDING("Seeding"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    ERROR("Error")
}
