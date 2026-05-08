package com.sonuverma.deeploader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonuverma.deeploader.data.models.TorrentCategory
import com.sonuverma.deeploader.data.models.TorrentSource
import com.sonuverma.deeploader.ui.components.TorrentCard
import com.sonuverma.deeploader.ui.components.TorrentSearchResultCard
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors
import com.sonuverma.deeploader.ui.viewmodel.TorrentSearchState
import com.sonuverma.deeploader.ui.viewmodel.TorrentViewModel

/**
 * Search Screen — Torrent search with category filters.
 * Searches across PirateBay, Nyaa.si, 1337x APIs.
 * Now fully connected to TorrentViewModel for real search & download.
 * Developer: Sonu Verma
 */
@Composable
fun SearchScreen(viewModel: TorrentViewModel) {
    var searchQuery by remember { mutableStateOf("") }

    val searchState by viewModel.searchState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val activeTorrents by viewModel.activeTorrents.collectAsState()
    val sessionStats by viewModel.sessionStats.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()

    val categories = TorrentCategory.entries.toList()
    val sources = listOf(
        null to "All",
        TorrentSource.PIRATEBAY to "PirateBay",
        TorrentSource.NYAA to "Nyaa.si",
        TorrentSource.LEETX to "1337x"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ─── Header ───
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Torrent Search",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Search across multiple torrent sources",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ─── Search Bar ───
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search torrents...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.search(searchQuery.trim())
                        }
                    }
                ),
                singleLine = true,
                enabled = searchState !is TorrentSearchState.Loading
            )
        }

        // ─── Source Filter ───
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                sources.forEach { (source, label) ->
                    FilterChip(
                        selected = selectedSource == source,
                        onClick = { viewModel.setSource(source) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // ─── Category Chips ───
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.take(5).forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            viewModel.setCategory(
                                if (selectedCategory == category) null else category
                            )
                        },
                        label = { Text(category.displayName, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (selectedCategory == category) {
                            {
                                Icon(
                                    Icons.Filled.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepLoaderColors.AccentPurple.copy(alpha = 0.15f),
                            selectedLabelColor = DeepLoaderColors.AccentPurple
                        )
                    )
                }
            }
        }

        // ─── Loading Indicator ───
        if (searchState is TorrentSearchState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = DeepLoaderColors.AccentPurple,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching across sources...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── Active Torrents ───
        if (activeTorrents.isNotEmpty()) {
            item {
                Text(
                    text = "Active Torrents (${activeTorrents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(activeTorrents.entries.toList(), key = { it.key }) { (infoHash, torrent) ->
                TorrentCard(
                    torrent = torrent,
                    onPause = { viewModel.pauseTorrent(infoHash) },
                    onResume = { viewModel.resumeTorrent(infoHash) },
                    onStream = { /* Navigate to player with streaming path */ },
                    onDelete = { viewModel.removeTorrent(infoHash, deleteFiles = false) }
                )
            }
        }

        // ─── Search Results ───
        if (searchResults.isNotEmpty()) {
            item {
                Text(
                    text = "Results (${searchResults.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(searchResults, key = { it.infoHash.ifEmpty { it.name } }) { torrent ->
                TorrentSearchResultCard(
                    torrent = torrent,
                    onDownload = { viewModel.startTorrent(torrent, sequential = false) },
                    onStream = { viewModel.startTorrent(torrent, sequential = true) }
                )
            }
        }

        // ─── Empty / Error / Idle State ───
        if (searchState is TorrentSearchState.Idle || searchState is TorrentSearchState.Empty || searchState is TorrentSearchState.Error) {
            item {
                AnimatedVisibility(
                    visible = searchResults.isEmpty() && activeTorrents.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.TravelExplore,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = DeepLoaderColors.AccentPurple.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            val title = when (searchState) {
                                is TorrentSearchState.Empty -> "No Results Found"
                                is TorrentSearchState.Error -> "Search Error"
                                else -> "Search for Torrents"
                            }
                            val subtitle = when (searchState) {
                                is TorrentSearchState.Empty -> "Try different keywords or change filters"
                                is TorrentSearchState.Error -> (searchState as TorrentSearchState.Error).message
                                else -> "Find movies, music, software, and more across PirateBay, Nyaa.si, and 1337x"
                            }

                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )

                            if (searchState is TorrentSearchState.Idle) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    SearchInfoItem(
                                        icon = Icons.Filled.Group,
                                        label = "P2P",
                                        value = if (sessionStats.dhtNodes > 0) "${sessionStats.dhtNodes} nodes" else "DHT+PEX"
                                    )
                                    SearchInfoItem(
                                        icon = Icons.Filled.ArrowUpward,
                                        label = "Stream",
                                        value = "Sequential"
                                    )
                                    SearchInfoItem(
                                        icon = Icons.Filled.Storage,
                                        label = "Sources",
                                        value = "3+"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = DeepLoaderColors.AccentPurple.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
