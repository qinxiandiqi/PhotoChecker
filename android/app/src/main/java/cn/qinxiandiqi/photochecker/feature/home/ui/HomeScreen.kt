package cn.qinxiandiqi.photochecker.feature.home.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.HomeUIState
import cn.qinxiandiqi.photochecker.feature.home.HomeViewModel
import cn.qinxiandiqi.photochecker.feature.home.RemovalState
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult
import cn.qinxiandiqi.photochecker.feature.home.model.PrivacyRisk
import cn.qinxiandiqi.photochecker.feature.home.model.displayName
import cn.qinxiandiqi.photochecker.ui.theme.LocalAppColors
import cn.qinxiandiqi.photochecker.ui.theme.SpacingLg
import cn.qinxiandiqi.photochecker.ui.theme.SpacingMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingSm
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onAboutClick: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.updatePhoto(uri)
        }
    }
    var showRemovalSheet by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    // Scroll offset for collapsible header (hoisted so TopAppBar can read it)
    val collapseOffset = remember { mutableStateOf(0f) }
    // Measured top bar height (statusBar + TopAppBar), set from content lambda
    val measuredTopBarHeightPx = remember { mutableStateOf(0f) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val context = LocalContext.current
    val msgExifCopied = stringResource(id = R.string.msg_exif_copied)

    Scaffold(
        modifier = modifier,
        topBar = {
            val currentUiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()
            val isCompactSuccess = isCompact && currentUiState is HomeUIState.Success
            if (isCompactSuccess) {
                val barHeight = measuredTopBarHeightPx.value
                // TopAppBar alpha fades 1 → 0 across Phase 1 (collapseOffset: 0 → barHeight).
                // Because the image is always drawn from the screen top (y = 0), the area
                // behind the bar is image content at every alpha — so the fade reveals the
                // image rather than the window background (no white gap). At alpha = 1 the
                // opaque bar fully covers the image's top portion, which is visually
                // identical to "image sits below the bar" — so the image is never obscured
                // by the status/title bar in the initial state.
                val appBarAlpha = if (barHeight > 0f)
                    1f - (collapseOffset.value / barHeight).coerceIn(0f, 1f) else 1f
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = appBarAlpha),
                        titleContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = appBarAlpha),
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = appBarAlpha)
                    ),
                    actions = {
                        IconButton(onClick = onAboutClick) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(id = R.string.about),
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        IconButton(onClick = onAboutClick) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(id = R.string.about),
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // Measure and store the exact top bar height (statusBar + TopAppBar)
        val density = LocalDensity.current
        LaunchedEffect(innerPadding) {
            measuredTopBarHeightPx.value = with(density) { innerPadding.calculateTopPadding().toPx() }
        }

        val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()
        val isCompactSuccess = isCompact && uiState is HomeUIState.Success

        // topBarHeight = innerPadding top (always includes statusBar + TopAppBar exactly)
        val topBarHeightDp = innerPadding.calculateTopPadding()
        val topPadding = if (isCompactSuccess) 0.dp else topBarHeightDp

        Column(modifier = Modifier.padding(top = topPadding, bottom = innerPadding.calculateBottomPadding())) {
            val removalState by viewModel.removalState.collectAsStateWithLifecycle()

            Box(modifier = Modifier.fillMaxSize()) {

                when (uiState) {
                    HomeUIState.Empty -> {
                        EmptyExifDetail()
                    }

                    is HomeUIState.Loading -> {
                        PhotoExifDetailLoading(
                            windowSizeClass = windowSizeClass,
                            uri = (uiState as HomeUIState.Loading).uri
                        )
                    }

                    is HomeUIState.Success -> {
                        val result = (uiState as HomeUIState.Success).result

                        PhotoExifDetailSuccess(
                            windowSizeClass = windowSizeClass,
                            result = result,
                            collapseOffset = collapseOffset,
                            topBarHeightDp = if (isCompactSuccess) topBarHeightDp else 0.dp,
                            onShowSnackbar = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    is HomeUIState.Error -> {
                        PhotoExifDetailError(
                            windowSizeClass = windowSizeClass,
                            uri = (uiState as HomeUIState.Error).uri,
                            message = (uiState as HomeUIState.Error).message
                        )
                    }
                }

                // Speed Dial FAB
                if (removalState !is RemovalState.Removing) {
                    val appColors = LocalAppColors.current
                    val isSuccess = uiState is HomeUIState.Success
                    val successResult = (uiState as? HomeUIState.Success)?.result

                    // Scrim overlay when expanded
                    androidx.compose.animation.AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(appColors.scrimOverlay)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { fabExpanded = false }
                        )
                    }

                    // Speed Dial column
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = SpacingLg, bottom = SpacingLg),
                        horizontalAlignment = Alignment.End
                    ) {
                        // Sub-items (only when Success + expanded)
                        if (isSuccess) {
                            // Copy EXIF sub-item
                            SubFabItem(
                                visible = fabExpanded,
                                label = stringResource(id = R.string.action_copy_exif),
                                icon = Icons.Default.ContentCopy,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = {
                                    fabExpanded = false
                                    successResult?.let { res ->
                                        val text = buildExifText(res, context)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("EXIF", text))
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                msgExifCopied
                                            )
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(SpacingMd))

                            // Privacy clean sub-item
                            SubFabItem(
                                visible = fabExpanded,
                                label = stringResource(id = R.string.action_privacy_clean),
                                icon = Icons.Default.Delete,
                                containerColor = when (successResult?.overallRisk) {
                                    PrivacyRisk.HIGH -> appColors.riskHigh
                                    PrivacyRisk.MEDIUM -> appColors.riskMedium
                                    else -> MaterialTheme.colorScheme.tertiaryContainer
                                },
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                onClick = {
                                    fabExpanded = false
                                    showRemovalSheet = true
                                }
                            )
                            Spacer(modifier = Modifier.height(SpacingMd))

                            // Photo picker sub-item
                            SubFabItem(
                                visible = fabExpanded,
                                label = stringResource(id = R.string.select_photo),
                                icon = Icons.Default.Search,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    fabExpanded = false
                                    launcher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(SpacingMd))
                        }

                        // Main FAB
                        FloatingActionButton(
                            onClick = {
                                if (isSuccess) {
                                    fabExpanded = !fabExpanded
                                } else {
                                    launcher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = when {
                                    fabExpanded -> Icons.Default.Close
                                    isSuccess -> Icons.Default.Add
                                    else -> Icons.Default.Search
                                },
                                contentDescription = if (isSuccess) null
                                    else stringResource(id = R.string.select_photo)
                            )
                        }
                    }
                }

                // Loading overlay during removal
                if (removalState is RemovalState.Removing) {
                    val appColors = LocalAppColors.current
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(appColors.scrimOverlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.height(SpacingSm))
                            Text(
                                text = stringResource(id = R.string.msg_removing),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Removal bottom sheet
            val currentResult = (uiState as? HomeUIState.Success)?.result
            if (showRemovalSheet && currentResult != null) {
                ExifRemovalSheet(
                    result = currentResult,
                    removalState = removalState,
                    onRemove = { mode ->
                        viewModel.removeExif(currentResult.uri, mode)
                    },
                    onShare = { file ->
                        viewModel.shareCleanedFile(file)
                        viewModel.clearRemovalStateKeepingFile()
                        showRemovalSheet = false
                    },
                    onSave = { file, onResult ->
                        viewModel.saveToGallery(file, onResult)
                    },
                    onDismiss = {
                        viewModel.resetRemovalState()
                        showRemovalSheet = false
                    }
                )
            }
        } // close Scaffold content Column
    }
}

/**
 * Builds a plain-text, category-grouped representation of all EXIF entries,
 * matching what is shown in the UI (translated display names + formatted values).
 */
private fun buildExifText(result: ExifAnalysisResult, context: Context): String {
    val sb = StringBuilder()
    for (group in result.categoryGroups) {
        sb.append(context.getString(group.displayNameResId)).append('\n')
        for (entry in group.entries) {
            sb.append(entry.displayName(context)).append(": ").append(entry.formattedValue).append('\n')
        }
        sb.append('\n')
    }
    return sb.toString().trimEnd()
}

@Composable
private fun SubFabItem(
    visible: Boolean,
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = SpacingMd, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.width(SpacingSm))
            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = containerColor,
                contentColor = contentColor
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
