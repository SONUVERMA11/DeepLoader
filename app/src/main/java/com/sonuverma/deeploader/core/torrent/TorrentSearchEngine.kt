package com.sonuverma.deeploader.core.torrent

import android.util.Log
import com.sonuverma.deeploader.data.models.TorrentCategory
import com.sonuverma.deeploader.data.models.TorrentInfo
import com.sonuverma.deeploader.data.models.TorrentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorrentSearchEngine — Scrapes torrent sites for search results.
 *
 * Searches across multiple sources in parallel:
 *   - The Pirate Bay (via apibay.org API)
 *   - 1337x (via HTML scraping)
 *   - Nyaa.si (via HTML scraping, anime/Asian content)
 *
 * Results are normalized into the unified TorrentInfo model.
 * Each source has independent error handling — one failure
 * doesn't break the others.
 *
 * Developer: Sonu Verma
 */
@Singleton
class TorrentSearchEngine @Inject constructor() {

    companion object {
        private const val TAG = "TorrentSearch"
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"

        // Well-known public tracker list for building magnet links
        private val DEFAULT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://open.demonii.com:1337/announce"
        )
    }

    /**
     * Search all sources in parallel and merge results.
     *
     * @param query Search query string
     * @param category Optional category filter
     * @param source Optional source filter (null = search all)
     * @return Combined list sorted by seeders (descending)
     */
    suspend fun search(
        query: String,
        category: TorrentCategory? = null,
        source: TorrentSource? = null
    ): List<TorrentInfo> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        val results = mutableListOf<TorrentInfo>()

        // Search sources in parallel
        val jobs = buildList {
            if (source == null || source == TorrentSource.PIRATEBAY) {
                add(async(Dispatchers.IO) { searchPirateBay(query, category) })
            }
            if (source == null || source == TorrentSource.LEETX) {
                add(async(Dispatchers.IO) { searchLeetx(query, category) })
            }
            if (source == null || source == TorrentSource.NYAA) {
                add(async(Dispatchers.IO) { searchNyaa(query, category) })
            }
        }

        jobs.awaitAll().forEach { sourceResults ->
            results.addAll(sourceResults)
        }

        // Sort by seeders (most seeded first) and deduplicate by info hash
        results
            .distinctBy { it.infoHash.ifEmpty { it.magnetUri } }
            .sortedByDescending { it.seeders }
    }

    // ─── Pirate Bay (via apibay.org JSON API) ───

    private fun searchPirateBay(query: String, category: TorrentCategory?): List<TorrentInfo> {
        return try {
            val categoryParam = when (category) {
                TorrentCategory.VIDEO -> "&cat=200"
                TorrentCategory.AUDIO -> "&cat=100"
                TorrentCategory.GAMES -> "&cat=400"
                TorrentCategory.SOFTWARE -> "&cat=300"
                TorrentCategory.BOOKS -> "&cat=600"
                else -> ""
            }

            val url = "https://apibay.org/q.php?q=${java.net.URLEncoder.encode(query, "UTF-8")}$categoryParam"

            val response = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .execute()
                .body()

            // Parse JSON array from apibay
            val jsonArray = com.google.gson.JsonParser.parseString(response).asJsonArray
            val results = mutableListOf<TorrentInfo>()

            for (element in jsonArray) {
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: continue
                if (id == "0") continue // apibay returns {"id":"0"} for no results

                val name = obj.get("name")?.asString ?: continue
                val infoHash = obj.get("info_hash")?.asString ?: continue
                val size = obj.get("size")?.asLong ?: 0L
                val seeders = obj.get("seeders")?.asInt ?: 0
                val leechers = obj.get("leechers")?.asInt ?: 0
                val uploader = obj.get("username")?.asString ?: ""
                val added = obj.get("added")?.asString ?: ""

                val magnetUri = buildMagnetUri(infoHash, name)

                results.add(
                    TorrentInfo(
                        name = name,
                        magnetUri = magnetUri,
                        infoHash = infoHash.lowercase(),
                        size = size,
                        seeders = seeders,
                        leechers = leechers,
                        uploadDate = added,
                        uploader = uploader,
                        category = category ?: guessCategory(name),
                        source = TorrentSource.PIRATEBAY
                    )
                )
            }

            Log.i(TAG, "PirateBay: ${results.size} results for \"$query\"")
            results

        } catch (e: Exception) {
            Log.w(TAG, "PirateBay search failed: ${e.message}")
            emptyList()
        }
    }

    // ─── 1337x (HTML scraping) ───

    private fun searchLeetx(query: String, category: TorrentCategory?): List<TorrentInfo> {
        return try {
            val categoryPath = when (category) {
                TorrentCategory.VIDEO -> "Movies"
                TorrentCategory.AUDIO -> "Music"
                TorrentCategory.GAMES -> "Games"
                TorrentCategory.SOFTWARE -> "Apps"
                TorrentCategory.ANIME -> "Anime"
                else -> null
            }

            val searchUrl = if (categoryPath != null) {
                "https://1337x.to/category-search/${java.net.URLEncoder.encode(query, "UTF-8")}/$categoryPath/1/"
            } else {
                "https://1337x.to/search/${java.net.URLEncoder.encode(query, "UTF-8")}/1/"
            }

            val doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            val rows = doc.select("table.table-list tbody tr")
            val results = mutableListOf<TorrentInfo>()

            for (row in rows) {
                try {
                    val nameElement = row.selectFirst("td.coll-1 a:nth-child(2)") ?: continue
                    val name = nameElement.text()
                    val detailUrl = "https://1337x.to${nameElement.attr("href")}"

                    val seeders = row.selectFirst("td.coll-2")?.text()?.toIntOrNull() ?: 0
                    val leechers = row.selectFirst("td.coll-3")?.text()?.toIntOrNull() ?: 0
                    val uploadDate = row.selectFirst("td.coll-date")?.text() ?: ""
                    val sizeText = row.selectFirst("td.coll-4")?.text() ?: ""
                    val uploader = row.selectFirst("td.coll-5")?.text() ?: ""

                    // Parse size text (e.g. "1.5 GB") to bytes
                    val size = parseSizeToBytes(sizeText)

                    results.add(
                        TorrentInfo(
                            name = name,
                            magnetUri = "", // Will need detail page scrape for magnet
                            infoHash = "",
                            size = size,
                            seeders = seeders,
                            leechers = leechers,
                            uploadDate = uploadDate,
                            uploader = uploader,
                            category = category ?: guessCategory(name),
                            source = TorrentSource.LEETX
                        )
                    )
                } catch (e: Exception) {
                    // Skip malformed rows
                }
            }

            Log.i(TAG, "1337x: ${results.size} results for \"$query\"")
            results

        } catch (e: Exception) {
            Log.w(TAG, "1337x search failed: ${e.message}")
            emptyList()
        }
    }

    // ─── Nyaa.si (HTML scraping, anime/Asian) ───

    private fun searchNyaa(query: String, category: TorrentCategory?): List<TorrentInfo> {
        return try {
            val categoryParam = when (category) {
                TorrentCategory.ANIME -> "&c=1_0"       // Anime
                TorrentCategory.AUDIO -> "&c=2_0"       // Audio
                TorrentCategory.BOOKS -> "&c=3_0"       // Literature
                TorrentCategory.SOFTWARE -> "&c=6_0"    // Software
                else -> ""
            }

            val url = "https://nyaa.si/?f=0&q=${java.net.URLEncoder.encode(query, "UTF-8")}$categoryParam"

            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            val rows = doc.select("table.torrent-list tbody tr")
            val results = mutableListOf<TorrentInfo>()

            for (row in rows) {
                try {
                    val nameElement = row.selectFirst("td:nth-child(2) a:last-child") ?: continue
                    val name = nameElement.text()
                    val magnetLink = row.selectFirst("td:nth-child(3) a[href^=magnet]")?.attr("href") ?: ""
                    val sizeText = row.selectFirst("td:nth-child(4)")?.text() ?: ""
                    val uploadDate = row.selectFirst("td:nth-child(5)")?.text() ?: ""
                    val seeders = row.selectFirst("td:nth-child(6)")?.text()?.toIntOrNull() ?: 0
                    val leechers = row.selectFirst("td:nth-child(7)")?.text()?.toIntOrNull() ?: 0

                    val size = parseSizeToBytes(sizeText)
                    val infoHash = extractInfoHash(magnetLink)

                    results.add(
                        TorrentInfo(
                            name = name,
                            magnetUri = magnetLink,
                            infoHash = infoHash,
                            size = size,
                            seeders = seeders,
                            leechers = leechers,
                            uploadDate = uploadDate,
                            category = category ?: TorrentCategory.ANIME,
                            source = TorrentSource.NYAA
                        )
                    )
                } catch (e: Exception) {
                    // Skip malformed rows
                }
            }

            Log.i(TAG, "Nyaa: ${results.size} results for \"$query\"")
            results

        } catch (e: Exception) {
            Log.w(TAG, "Nyaa search failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Scrape the magnet link from a 1337x detail page.
     * Called lazily when user taps a 1337x result.
     */
    suspend fun fetchMagnetLink(detailUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(detailUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            doc.selectFirst("a[href^=magnet]")?.attr("href")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch magnet from $detailUrl: ${e.message}")
            null
        }
    }

    // ─── Helpers ───

    private fun buildMagnetUri(infoHash: String, name: String): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val trackers = DEFAULT_TRACKERS.joinToString("") { "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        return "magnet:?xt=urn:btih:$infoHash&dn=$encodedName$trackers"
    }

    private fun extractInfoHash(magnetUri: String): String {
        val regex = Regex("btih:([a-fA-F0-9]{40})")
        return regex.find(magnetUri)?.groupValues?.get(1)?.lowercase() ?: ""
    }

    private fun parseSizeToBytes(sizeText: String): Long {
        return try {
            val parts = sizeText.trim().split("\\s+".toRegex())
            if (parts.size < 2) return 0L
            val value = parts[0].replace(",", "").toDouble()
            val unit = parts[1].uppercase()
            when {
                unit.startsWith("G") -> (value * 1024 * 1024 * 1024).toLong()
                unit.startsWith("M") -> (value * 1024 * 1024).toLong()
                unit.startsWith("K") -> (value * 1024).toLong()
                unit.startsWith("T") -> (value * 1024L * 1024 * 1024 * 1024).toLong()
                else -> value.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun guessCategory(name: String): TorrentCategory {
        val lower = name.lowercase()
        return when {
            lower.contains("720p") || lower.contains("1080p") || lower.contains("2160p") ||
            lower.contains("bluray") || lower.contains("webrip") || lower.contains("hdtv") ||
            lower.contains("brrip") || lower.contains("dvdrip") -> TorrentCategory.VIDEO

            lower.contains("flac") || lower.contains("mp3") || lower.contains("album") ||
            lower.contains("discography") || lower.contains("aac") -> TorrentCategory.AUDIO

            lower.contains("anime") || lower.contains("sub") && lower.contains("ep") ->
                TorrentCategory.ANIME

            lower.contains(".exe") || lower.contains("setup") || lower.contains("portable") ||
            lower.contains("crack") || lower.contains("keygen") -> TorrentCategory.SOFTWARE

            lower.contains("game") || lower.contains("fitgirl") || lower.contains("dodi") ||
            lower.contains("ps4") || lower.contains("switch") -> TorrentCategory.GAMES

            lower.contains("epub") || lower.contains("pdf") || lower.contains("mobi") ||
            lower.contains("book") -> TorrentCategory.BOOKS

            else -> TorrentCategory.OTHER
        }
    }
}
