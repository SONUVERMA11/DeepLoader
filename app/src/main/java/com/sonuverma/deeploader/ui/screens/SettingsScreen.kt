package com.sonuverma.deeploader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors
import com.sonuverma.deeploader.ui.viewmodel.MainViewModel

/**
 * Settings Screen — Theme toggle, download prefs, security, about.
 * iOS-style grouped settings with toggle switches.
 * "Sonu Verma" developer credit at bottom.
 * Developer: Sonu Verma
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel? = null) {
    var wifiOnly by remember { mutableStateOf(false) }
    var clipboardWatcher by remember { mutableStateOf(true) }
    var autoUpdateYtDlp by remember { mutableStateOf(true) }
    var biometricLock by remember { mutableStateOf(false) }
    var notificationSound by remember { mutableStateOf(true) }
    var autoRetry by remember { mutableStateOf(true) }
    var saveToGallery by remember { mutableStateOf(true) }
    var showSpeedGraph by remember { mutableStateOf(true) }
    var selectedThemeIndex by remember { mutableIntStateOf(0) } // 0=System, 1=Light, 2=Dark, 3=AMOLED

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
                    text = "Settings",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Customize your experience",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ─── Appearance Section ───
        item {
            SettingsSection(title = "Appearance") {
                // Theme selector
                val themes = listOf(
                    Triple("System", Icons.Filled.Settings, null),
                    Triple("Light", Icons.Filled.BrightnessHigh, null),
                    Triple("Dark", Icons.Filled.DarkMode, null),
                    Triple("AMOLED", Icons.Filled.BrightnessLow, null)
                )

                Column {
                    SettingsItem(
                        icon = Icons.Filled.Palette,
                        iconTint = DeepLoaderColors.AccentPurple,
                        title = "Theme",
                        subtitle = themes[selectedThemeIndex].first,
                        onClick = {
                            selectedThemeIndex = (selectedThemeIndex + 1) % themes.size
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    SettingsToggle(
                        icon = Icons.Filled.Speed,
                        iconTint = DeepLoaderColors.PrimaryBlue,
                        title = "Speed Graph",
                        subtitle = "Show real-time speed graph on home",
                        checked = showSpeedGraph,
                        onToggle = { showSpeedGraph = it }
                    )
                }
            }
        }

        // ─── Downloads Section ───
        item {
            SettingsSection(title = "Downloads") {
                Column {
                    SettingsToggle(
                        icon = Icons.Filled.Wifi,
                        iconTint = DeepLoaderColors.AccentGreen,
                        title = "WiFi Only",
                        subtitle = "Pause downloads on mobile data",
                        checked = wifiOnly,
                        onToggle = { wifiOnly = it }
                    )
                    SettingsDivider()
                    SettingsToggle(
                        icon = Icons.Filled.ContentCopy,
                        iconTint = DeepLoaderColors.AccentCyan,
                        title = "Clipboard Watcher",
                        subtitle = "Auto-detect URLs from clipboard",
                        checked = clipboardWatcher,
                        onToggle = { clipboardWatcher = it }
                    )
                    SettingsDivider()
                    SettingsToggle(
                        icon = Icons.Filled.Storage,
                        iconTint = DeepLoaderColors.AccentOrange,
                        title = "Save to Gallery",
                        subtitle = "Auto-save to device gallery",
                        checked = saveToGallery,
                        onToggle = { saveToGallery = it }
                    )
                    SettingsDivider()
                    SettingsToggle(
                        icon = Icons.Filled.SystemUpdate,
                        iconTint = DeepLoaderColors.PrimaryBlueDark,
                        title = "Auto-Retry",
                        subtitle = "Retry failed downloads automatically",
                        checked = autoRetry,
                        onToggle = { autoRetry = it }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Filled.Download,
                        iconTint = DeepLoaderColors.PrimaryBlue,
                        title = "Max Concurrent Downloads",
                        subtitle = "3 downloads",
                        onClick = { /* TODO: Show picker */ }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Filled.Folder,
                        iconTint = DeepLoaderColors.AccentOrange,
                        title = "Download Directory",
                        subtitle = "Movies/DeepLoader",
                        onClick = { /* TODO: Directory picker */ }
                    )
                }
            }
        }

        // ─── Security Section ───
        item {
            SettingsSection(title = "Security") {
                Column {
                    SettingsToggle(
                        icon = Icons.Filled.Fingerprint,
                        iconTint = DeepLoaderColors.AccentRed,
                        title = "Biometric Lock",
                        subtitle = "Require fingerprint or PIN to open",
                        checked = biometricLock,
                        onToggle = { biometricLock = it }
                    )
                }
            }
        }

        // ─── Notifications Section ───
        item {
            SettingsSection(title = "Notifications") {
                Column {
                    SettingsToggle(
                        icon = Icons.Filled.Notifications,
                        iconTint = DeepLoaderColors.AccentPink,
                        title = "Notification Sound",
                        subtitle = "Play sound when download completes",
                        checked = notificationSound,
                        onToggle = { notificationSound = it }
                    )
                }
            }
        }

        // ─── Advanced Section ───
        item {
            SettingsSection(title = "Advanced") {
                Column {
                    SettingsToggle(
                        icon = Icons.Filled.SystemUpdate,
                        iconTint = DeepLoaderColors.AccentGreen,
                        title = "Auto-Update yt-dlp",
                        subtitle = "Keep extraction engine up to date",
                        checked = autoUpdateYtDlp,
                        onToggle = { autoUpdateYtDlp = it }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Filled.NetworkCheck,
                        iconTint = DeepLoaderColors.PrimaryBlue,
                        title = "Speed Test",
                        subtitle = "Measure network speed",
                        onClick = { /* TODO: Speed test screen */ }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Filled.Security,
                        iconTint = DeepLoaderColors.AccentOrange,
                        title = "yt-dlp Version",
                        subtitle = "Not installed",
                        onClick = { /* TODO: Update check */ }
                    )
                }
            }
        }

        // ─── About Section ───
        item {
            SettingsSection(title = "About") {
                Column {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        iconTint = DeepLoaderColors.PrimaryBlue,
                        title = "DeepLoader",
                        subtitle = "Version 1.0.0",
                        onClick = { }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Filled.Code,
                        iconTint = DeepLoaderColors.AccentPurple,
                        title = "Developer",
                        subtitle = "Sonu Verma",
                        onClick = { }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Filled.FavoriteBorder,
                        iconTint = DeepLoaderColors.AccentRed,
                        title = "Source Code",
                        subtitle = "github.com/SONUVERMA11/DeepLoader",
                        onClick = { /* TODO: Open browser */ }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        iconTint = DeepLoaderColors.AccentGreen,
                        title = "Help & Feedback",
                        subtitle = "Report bugs and request features",
                        onClick = { }
                    )
                }
            }
        }

        // ─── Footer ───
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Made with ❤️ by Sonu Verma",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DeepLoader v1.0.0 • Zero Cost • Open Source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            letterSpacing = 1.2.sp
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onToggle(!checked) })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    )
}
