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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonuverma.deeploader.ui.components.ClipboardBanner
import com.sonuverma.deeploader.ui.components.DownloadCard
import com.sonuverma.deeploader.ui.components.FormatBottomSheet
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors
import com.sonuverma.deeploader.ui.viewmodel.ExtractionState
import com.sonuverma.deeploader.ui.viewmodel.MainViewModel

/**
 * Home Screen — Active downloads, quick paste bar, speed stats.
 * Now connected to MainViewModel for real extraction + download state.
 * Developer: Sonu Verma
 */
@Composable
fun HomeScreen(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    viewModel: MainViewModel
) {
    var urlInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    val extractionState by viewModel.extractionState.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()
    val clipboardDetection by viewModel.clipboardDetection.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    var showFormatSheet by remember { mutableStateOf(false) }

    // Handle shared URL from intent
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            urlInput = sharedUrl
            onSharedUrlConsumed()
            viewModel.extractUrl(sharedUrl)
        }
    }

    // Show format sheet when extraction succeeds
    LaunchedEffect(extractionState) {
        if (extractionState is ExtractionState.Success) {
            showFormatSheet = true
        }
    }

    // Format bottom sheet
    if (showFormatSheet && currentResult != null) {
        FormatBottomSheet(
            result = currentResult!!,
            onDismiss = {
                showFormatSheet = false
                viewModel.resetExtraction()
            },
            onDownload = { video, audio, mp3 ->
                showFormatSheet = false
                currentResult?.let { result ->
                    viewModel.startDownload(result, video, audio, mp3)
                }
                viewModel.resetExtraction()
            },
            onPlay = { format ->
                showFormatSheet = false
                currentResult?.let { result ->
                    viewModel.playStream(result, format)
                }
                viewModel.resetExtraction()
            }
        )
    }

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
                    text = "DeepLoader",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Download anything, from anywhere",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ─── Clipboard Banner ───
        item {
            ClipboardBanner(
                detection = clipboardDetection,
                onDownload = { url ->
                    urlInput = url
                    viewModel.extractUrl(url)
                    viewModel.dismissClipboard()
                },
                onDismiss = { viewModel.dismissClipboard() }
            )
        }

        // ─── URL Input Bar ───
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .animateContentSize()
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Paste video or audio URL...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()
                                    if (clip != null) {
                                        urlInput = clip.text
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (urlInput.isNotBlank()) {
                                    viewModel.extractUrl(urlInput.trim())
                                }
                            }
                        ),
                        singleLine = true,
                        enabled = extractionState !is ExtractionState.Loading
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                viewModel.extractUrl(urlInput.trim())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = urlInput.isNotBlank() && extractionState !is ExtractionState.Loading
                    ) {
                        when (extractionState) {
                            is ExtractionState.Loading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extracting...", fontWeight = FontWeight.SemiBold)
                            }
                            else -> {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetch & Download", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Error message
                    AnimatedVisibility(
                        visible = extractionState is ExtractionState.Error,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        val errorMsg = (extractionState as? ExtractionState.Error)?.message ?: ""
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = DeepLoaderColors.AccentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = DeepLoaderColors.AccentRed
                            )
                        }
                    }
                }
            }
        }

        // ─── Stats Cards ───
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    label = "Speed",
                    value = "0 B/s",
                    gradientColors = listOf(
                        DeepLoaderColors.GradientBlueStart,
                        DeepLoaderColors.GradientBlueEnd
                    )
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.TrendingUp,
                    label = "Active",
                    value = "${activeDownloads.size}",
                    gradientColors = listOf(
                        DeepLoaderColors.GradientGreenStart,
                        DeepLoaderColors.GradientGreenEnd
                    )
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Storage,
                    label = "Total",
                    value = "0 MB",
                    gradientColors = listOf(
                        DeepLoaderColors.GradientPurpleStart,
                        DeepLoaderColors.GradientPurpleEnd
                    )
                )
            }
        }

        // ─── Active Downloads Section ───
        item {
            Text(
                text = "Active Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // ─── Download Cards or Empty State ───
        if (activeDownloads.isEmpty()) {
            item {
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
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No active downloads",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Paste a URL above or share a link from any app to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = activeDownloads,
                key = { it.id }
            ) { download ->
                DownloadCard(
                    download = download,
                    onPause = { viewModel.pauseDownload(download.id) },
                    onResume = { viewModel.resumeDownload(download.id) },
                    onRetry = { viewModel.retryDownload(download.id) },
                    onCancel = { viewModel.cancelDownload(download.id) }
                )
            }
        }

        // ─── Supported Platforms ───
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Supported Platforms",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            PlatformGrid()
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    gradientColors: List<Color>
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.linearGradient(gradientColors))
                .padding(16.dp)
        ) {
            Column {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PlatformGrid() {
    val platforms = listOf(
        "YouTube" to DeepLoaderColors.YouTube,
        "Instagram" to DeepLoaderColors.Instagram,
        "Twitter/X" to DeepLoaderColors.Twitter,
        "TikTok" to DeepLoaderColors.TikTok,
        "Reddit" to DeepLoaderColors.Reddit,
        "Facebook" to DeepLoaderColors.Facebook,
        "SoundCloud" to DeepLoaderColors.SoundCloud,
        "Vimeo" to DeepLoaderColors.Vimeo,
        "Dailymotion" to DeepLoaderColors.Dailymotion,
        "Torrents" to DeepLoaderColors.Torrent,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        platforms.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (name, color) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = color.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
