package cn.qinxiandiqi.photochecker.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.qinxiandiqi.photochecker.App
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult
import cn.qinxiandiqi.photochecker.feature.home.model.ConsistencyWarning
import cn.qinxiandiqi.photochecker.feature.home.model.ExifCategory
import cn.qinxiandiqi.photochecker.feature.home.model.ExifCategoryGroup
import cn.qinxiandiqi.photochecker.feature.home.model.ExifRemovalMode
import cn.qinxiandiqi.photochecker.feature.home.model.ExifTagEntry
import cn.qinxiandiqi.photochecker.feature.home.model.PrivacyRisk
import cn.qinxiandiqi.photochecker.ui.theme.LocalAppColors
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ============================================================
// Spacing & shape tokens (Phase D)
// ============================================================

private val SpacingXs = 4.dp
private val SpacingSm = 8.dp
private val SpacingMd = 12.dp
private val SpacingLg = 16.dp
private val SpacingXl = 24.dp

private val ShapeSm = 8.dp
private val ShapeMd = 12.dp

private val DefaultExpandedImageHeight = 240.dp
private val CollapsedImageHeight = 56.dp

// ============================================================
// HomeScreen
// ============================================================

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

    Scaffold(
        modifier = modifier,
        topBar = {
            val currentUiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()
            val isCompactSuccess = isCompact && currentUiState is HomeUIState.Success
            if (isCompactSuccess) {
                val barHeight = measuredTopBarHeightPx.value
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
                                            context.getString(R.string.msg_exif_copied)
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
                    viewModel.resetRemovalState()
                    showRemovalSheet = false
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

// ============================================================
// Speed Dial sub-item
// ============================================================

/**
 * Builds a plain-text, category-grouped representation of all EXIF entries,
 * matching what is shown in the UI (translated display names + formatted values).
 */
private fun buildExifText(result: ExifAnalysisResult, context: Context): String {
    val sb = StringBuilder()
    for (group in result.categoryGroups) {
        sb.append(context.getString(group.displayNameResId)).append('\n')
        for (entry in group.entries) {
            val name = if (entry.displayNameResId != 0) {
                context.getString(entry.displayNameResId)
            } else {
                entry.rawTagName
            }
            sb.append(name).append(": ").append(entry.formattedValue).append('\n')
        }
        sb.append('\n')
    }
    return sb.toString().trimEnd()
}

@Composable
private fun SubFabItem(
    visible: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
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

// ============================================================
// Empty / Loading / Error states
// ============================================================

@Composable
fun EmptyExifDetail(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(SpacingMd))
        Text(
            modifier = Modifier.padding(SpacingSm),
            style = MaterialTheme.typography.titleLarge,
            text = stringResource(id = R.string.empty),
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.padding(SpacingSm),
            style = MaterialTheme.typography.bodyMedium,
            text = stringResource(id = R.string.empty_tips),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PhotoExifDetail(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    uri: Uri,
    content: @Composable () -> Unit
) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            Column(modifier = modifier.fillMaxSize()) {
                Box {
                    AsyncImage(
                        model = uri,
                        modifier = Modifier
                            .fillMaxWidth(1f)
                            .aspectRatio(ratio = 1.6f),
                        contentScale = ContentScale.Crop,
                        contentDescription = ""
                    )
                }
                content()
            }
        }

        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> {
            Row(modifier = modifier.fillMaxSize()) {
                Box {
                    AsyncImage(
                        model = uri,
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f),
                        contentScale = ContentScale.Crop,
                        contentDescription = ""
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun PhotoExifDetailLoading(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    uri: Uri,
) {
    PhotoExifDetail(
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        uri = uri
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun PhotoExifDetailError(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    uri: Uri? = null,
    message: String = ""
) {
    PhotoExifDetail(
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        uri = uri ?: Uri.EMPTY
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier.padding(SpacingSm),
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.error),
                text = stringResource(id = R.string.error),
                textAlign = TextAlign.Center
            )
            Text(
                modifier = Modifier.padding(SpacingSm),
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onError),
                text = stringResource(id = R.string.error_tips),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// Success state: privacy summary + grouped EXIF list
// ============================================================

@Composable
fun PhotoExifDetailSuccess(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    result: ExifAnalysisResult,
    collapseOffset: androidx.compose.runtime.MutableState<Float> = mutableStateOf(0f),
    topBarHeightDp: Dp = 0.dp,
    onShowSnackbar: (String) -> Unit = {}
) {
    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
        // Compact: two-phase collapsible header (TopAppBar fade → image collapse)
        val density = LocalDensity.current
        val topBarHeightPx = with(density) { topBarHeightDp.toPx() }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            // Aspect-ratio-aware expanded height: preserve the original width/height
            // ratio, but cap so width:height >= 3:4 (height <= width * 4/3).
            val expandedHeightDp: Dp = if (result.imageWidth > 0 && result.imageHeight > 0) {
                val ratioWH = result.imageWidth.toFloat() / result.imageHeight
                val cappedRatioWH = ratioWH.coerceAtLeast(0.75f) // 3:4
                (maxWidth.value / cappedRatioWH).dp
            } else {
                DefaultExpandedImageHeight
            }
            val imageRangePx = with(density) { (expandedHeightDp - CollapsedImageHeight).toPx() }
            val totalRangePx = topBarHeightPx + imageRangePx

            // Reset offset when result changes
            LaunchedEffect(result.uri) { collapseOffset.value = 0f }

            // Key on totalRangePx so the connection is rebuilt when the image's
            // expanded height changes (different aspect ratio), avoiding a stale range.
            val nestedScrollConnection = remember(totalRangePx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val dy = available.y
                        if (dy < 0f && collapseOffset.value < totalRangePx) {
                            val consumed = dy.coerceAtLeast(-(totalRangePx - collapseOffset.value))
                            collapseOffset.value -= consumed
                            return Offset(0f, consumed)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset, available: Offset, source: NestedScrollSource
                    ): Offset {
                        val dy = available.y
                        if (dy > 0f && collapseOffset.value > 0f) {
                            val consumed = dy.coerceAtMost(collapseOffset.value)
                            collapseOffset.value -= consumed
                            return Offset(0f, consumed)
                        }
                        return Offset.Zero
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
            ) {
                val offset = collapseOffset.value
                // Top spacer: shrinks during Phase 1 as TopAppBar fades.
                // Initially = topBarHeight (image below TopAppBar),
                // Phase 1 end = 0 (image behind transparent TopAppBar)
                val topSpacerDp = with(density) {
                    (topBarHeightPx - offset).coerceAtLeast(0f).toDp()
                }
                Spacer(modifier = Modifier.height(topSpacerDp))

                // Image height: stays at expandedHeightDp during Phase 1,
                // collapses during Phase 2
                val currentHeightDp = with(density) {
                    val imageOffsetPx = (offset - topBarHeightPx).coerceAtLeast(0f)
                    (expandedHeightDp - imageOffsetPx.toDp()).coerceAtLeast(CollapsedImageHeight)
                }
                // Collapsible image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentHeightDp)
                ) {
                    AsyncImage(
                        model = result.uri,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        contentDescription = ""
                    )
                }

                // Content below image
                Column(modifier = Modifier.weight(1f)) {
                    if (result.overallRisk != PrivacyRisk.NONE) {
                        PrivacySummaryCard(result = result)
                    }

                    if (result.consistencyWarnings.isNotEmpty()) {
                        ConsistencyWarningSection(warnings = result.consistencyWarnings)
                    }

                    ExifGroupedList(
                        groups = result.categoryGroups,
                        result = result,
                        onShowSnackbar = onShowSnackbar,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    } else {
        // Medium/Expanded: side-by-side layout (unchanged)
        PhotoExifDetail(
            windowSizeClass = windowSizeClass,
            modifier = modifier,
            uri = result.uri
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (result.overallRisk != PrivacyRisk.NONE) {
                    PrivacySummaryCard(result = result)
                }

                if (result.consistencyWarnings.isNotEmpty()) {
                    ConsistencyWarningSection(warnings = result.consistencyWarnings)
                }

                ExifGroupedList(
                    groups = result.categoryGroups,
                    result = result,
                    onShowSnackbar = onShowSnackbar,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ============================================================
// Privacy Summary Card
// ============================================================

@Composable
fun PrivacySummaryCard(
    result: ExifAnalysisResult,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val containerColor = when (result.overallRisk) {
        PrivacyRisk.HIGH -> appColors.riskHighContainer
        PrivacyRisk.MEDIUM -> appColors.riskMediumContainer
        PrivacyRisk.LOW -> appColors.riskLowContainer
        PrivacyRisk.NONE -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (result.overallRisk) {
        PrivacyRisk.HIGH -> appColors.riskOnHighContainer
        PrivacyRisk.MEDIUM -> appColors.riskOnMediumContainer
        PrivacyRisk.LOW -> appColors.riskOnLowContainer
        PrivacyRisk.NONE -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpacingLg, vertical = SpacingSm),
        shape = RoundedCornerShape(ShapeMd),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(SpacingSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = riskLabelResId(result.overallRisk)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                if (result.hasGpsCoordinates) {
                    Text(
                        text = stringResource(id = R.string.msg_privacy_summary_gps),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                } else if (result.overallRisk == PrivacyRisk.MEDIUM) {
                    Text(
                        text = stringResource(id = R.string.msg_privacy_summary_personal),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

// ============================================================
// Consistency Warnings
// ============================================================

@Composable
fun ConsistencyWarningSection(
    warnings: List<ConsistencyWarning>,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier.padding(horizontal = SpacingLg, vertical = SpacingSm),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        warnings.forEach { warning ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ShapeSm),
                colors = CardDefaults.cardColors(
                    containerColor = appColors.warningContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpacingMd, vertical = SpacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = appColors.warningIcon
                    )
                    Spacer(modifier = Modifier.width(SpacingSm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = warning.messageResId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = appColors.warningTitle
                        )
                        Text(
                            text = warning.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors.warningDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GpsMapButton(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {}
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .clickable {
                val geoUri = Uri.parse("geo:$latitude,$longitude")
                val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    onShowSnackbar(context.getString(R.string.msg_no_map_app))
                }
            },
        shape = RoundedCornerShape(ShapeSm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SpacingMd, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(SpacingXs))
            Text(
                text = stringResource(id = R.string.action_view_on_map),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// ============================================================
// Grouped EXIF list
// ============================================================

@Composable
fun ExifGroupedList(
    groups: List<ExifCategoryGroup>,
    result: ExifAnalysisResult,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {}
) {
    val expandedStates = remember {
        groups.associate { it.category to mutableStateOf(true) }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = SpacingLg),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(SpacingXs)
    ) {
        groups.forEach { group ->
            val isExpanded = expandedStates[group.category]?.value ?: true

            item(key = "header_${group.category}") {
                ExifCategoryHeader(
                    group = group,
                    expanded = isExpanded,
                    onToggle = {
                        expandedStates[group.category]?.value = !isExpanded
                    }
                )
            }
            if (isExpanded) {
                // GPS group: show map button as first item if GPS data exists
                if (group.category == ExifCategory.GPS
                    && result.hasGpsCoordinates
                    && result.gpsLatitude != null
                    && result.gpsLongitude != null
                ) {
                    item(key = "gps_map_button") {
                        GpsMapInlineButton(
                            latitude = result.gpsLatitude,
                            longitude = result.gpsLongitude,
                            onShowSnackbar = onShowSnackbar,
                            modifier = Modifier.padding(vertical = SpacingXs)
                        )
                    }
                }
                items(
                    items = group.entries,
                    key = { entry -> "tag_${entry.rawTagName}" }
                ) { entry ->
                    ExifTagEntryItem(entry = entry)
                }
            }
        }
    }
}

@Composable
fun GpsMapInlineButton(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {}
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val geoUri = Uri.parse("geo:$latitude,$longitude")
                val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    onShowSnackbar(context.getString(R.string.msg_no_map_app))
                }
            },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingLg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(SpacingSm))
            Text(
                text = stringResource(id = R.string.action_view_on_map),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================
// Category header + tag entry items
// ============================================================

@Composable
fun ExifCategoryHeader(
    group: ExifCategoryGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = stringResource(id = group.displayNameResId)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SpacingMd, bottom = SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (group.maxRisk != PrivacyRisk.NONE) {
                RiskDot(risk = group.maxRisk, size = 8.dp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(SpacingSm))
            Text(
                text = "(${group.entries.size})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.action_collapse)
                else stringResource(R.string.action_expand),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExifTagEntryItem(
    entry: ExifTagEntry,
    modifier: Modifier = Modifier
) {
    val displayName = if (entry.displayNameResId != 0) {
        stringResource(id = entry.displayNameResId)
    } else {
        entry.rawTagName
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpacingXs),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = SpacingLg, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.risk != PrivacyRisk.NONE) {
                    RiskDot(risk = entry.risk, size = 6.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.formattedValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = SpacingMd)
            )
        }
    }
}

// ============================================================
// Shared components
// ============================================================

@Composable
fun RiskDot(
    risk: PrivacyRisk,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val appColors = LocalAppColors.current
    val color by animateColorAsState(
        targetValue = when (risk) {
            PrivacyRisk.HIGH -> appColors.riskHigh
            PrivacyRisk.MEDIUM -> appColors.riskMedium
            PrivacyRisk.LOW -> appColors.riskLow
            PrivacyRisk.NONE -> appColors.neutralRisk
        },
        label = "risk_color"
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

private fun riskLabelResId(risk: PrivacyRisk): Int = when (risk) {
    PrivacyRisk.HIGH -> R.string.risk_high
    PrivacyRisk.MEDIUM -> R.string.risk_medium
    PrivacyRisk.LOW -> R.string.risk_low
    PrivacyRisk.NONE -> R.string.risk_none
}

// ============================================================
// Removal Bottom Sheet
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifRemovalSheet(
    result: ExifAnalysisResult,
    removalState: RemovalState,
    onRemove: (ExifRemovalMode) -> Unit,
    onShare: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = LocalAppColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingXl)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(id = R.string.action_privacy_clean),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(SpacingLg))

            when (removalState) {
                is RemovalState.Idle -> {
                    if (result.hasGpsCoordinates) {
                        RemovalOptionButton(
                            text = stringResource(id = R.string.action_remove_gps),
                            subtitle = stringResource(id = R.string.msg_privacy_summary_gps),
                            onClick = { onRemove(ExifRemovalMode.GPS_ONLY) }
                        )
                        Spacer(modifier = Modifier.height(SpacingSm))
                    }
                    if (result.overallRisk == PrivacyRisk.MEDIUM || result.overallRisk == PrivacyRisk.HIGH) {
                        RemovalOptionButton(
                            text = stringResource(id = R.string.action_remove_personal),
                            subtitle = stringResource(id = R.string.msg_privacy_summary_personal),
                            onClick = { onRemove(ExifRemovalMode.PERSONAL_ONLY) }
                        )
                        Spacer(modifier = Modifier.height(SpacingSm))
                    }
                    RemovalOptionButton(
                        text = stringResource(id = R.string.action_remove_all),
                        onClick = { onRemove(ExifRemovalMode.ALL) }
                    )
                }

                is RemovalState.Removing -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(SpacingMd))
                        Text(text = stringResource(id = R.string.msg_removing))
                    }
                }

                is RemovalState.Done -> {
                    Text(
                        text = stringResource(id = R.string.msg_remove_success),
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors.success,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(SpacingLg))
                    Button(
                        onClick = { onShare(removalState.file) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(SpacingSm))
                        Text(text = stringResource(id = R.string.action_share))
                    }
                }

                is RemovalState.Error -> {
                    Text(
                        text = stringResource(id = R.string.msg_remove_error),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(SpacingSm))
                    Text(
                        text = removalState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun RemovalOptionButton(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(ShapeMd),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(SpacingLg)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(SpacingXs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================
// Previews
// ============================================================

@Preview(showBackground = true)
@Composable
fun ExifGroupedListPreview() {
    App {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(horizontal = SpacingLg),
        ) {
            items(5) { index ->
                ExifTagEntryItem(
                    entry = ExifTagEntry(
                        rawTagName = "Tag$index",
                        displayNameResId = R.string.tag_f_number,
                        rawValue = "70/10",
                        formattedValue = "f/7.0",
                        category = ExifCategory.EXPOSURE,
                        risk = PrivacyRisk.NONE
                    )
                )
            }
        }
    }
}
