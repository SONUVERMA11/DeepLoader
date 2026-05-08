package com.sonuverma.deeploader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sonuverma.deeploader.core.player.PlayerState
import com.sonuverma.deeploader.ui.components.MiniPlayer
import com.sonuverma.deeploader.ui.screens.HomeScreen
import com.sonuverma.deeploader.ui.screens.LibraryScreen
import com.sonuverma.deeploader.ui.screens.SearchScreen
import com.sonuverma.deeploader.ui.screens.SettingsScreen
import com.sonuverma.deeploader.ui.screens.VideoPlayerScreen
import com.sonuverma.deeploader.ui.viewmodel.MainViewModel
import com.sonuverma.deeploader.ui.viewmodel.TorrentViewModel

/**
 * Main screen with bottom navigation — connected to MainViewModel.
 * 4 tabs: Home (Downloads), Search (Torrents), Library, Settings.
 * iOS-quality bottom tab bar with spring animations.
 * Developer: Sonu Verma
 */

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    BottomNavItem("Home", Icons.Filled.Download, Icons.Outlined.Download),
    BottomNavItem("Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem("Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    BottomNavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun DeepLoaderMainScreen(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
    torrentViewModel: TorrentViewModel = hiltViewModel()
) {
    val playerManager = viewModel.playerManager
    val pipController = viewModel.pipController

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    val playbackState by playerManager.playbackState.collectAsState()

    // Start/stop clipboard watcher based on lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                    viewModel.startClipboardWatcher()
                    viewModel.checkClipboardNow()
                }
                override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                    viewModel.stopClipboardWatcher()
                }
            }
        )
    }

    // Full-screen player overlay
    if (showFullPlayer) {
        VideoPlayerScreen(
            playerManager = playerManager,
            pipController = pipController,
            onBack = { showFullPlayer = false }
        )
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                // MiniPlayer above bottom nav
                MiniPlayer(
                    playbackState = playbackState,
                    isVisible = playbackState.state != PlayerState.IDLE,
                    onTogglePlayPause = { playerManager.togglePlayPause() },
                    onClose = { playerManager.stop() },
                    onExpand = { showFullPlayer = true }
                )

                DeepLoaderBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = onSharedUrlConsumed,
                    viewModel = viewModel
                )
                1 -> SearchScreen(viewModel = torrentViewModel)
                2 -> LibraryScreen(viewModel = viewModel)
                3 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun DeepLoaderBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        navItems.forEachIndexed { index, item ->
            val isSelected = selectedTab == index

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.1f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "tab_scale"
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.scale(scale)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
