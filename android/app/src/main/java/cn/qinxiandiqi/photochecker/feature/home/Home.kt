package cn.qinxiandiqi.photochecker.feature.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil.compose.AsyncImage
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
        val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()
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
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )

                    // Privacy clean FAB (always show when EXIF data exists)
                    FloatingActionButton(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 32.dp),
                        containerColor = when (result.overallRisk) {
                            PrivacyRisk.HIGH -> Color(0xFFE53935)
                            PrivacyRisk.MEDIUM -> Color(0xFFFF9800)
                            PrivacyRisk.LOW -> MaterialTheme.colorScheme.tertiaryContainer
                            PrivacyRisk.NONE -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = Color.White,
                        onClick = { showRemovalSheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.action_privacy_clean)
                        )
                    }
                }

                is HomeUIState.Error -> {
                    PhotoExifDetailError(
                        windowSizeClass = windowSizeClass,
                        uri = (uiState as HomeUIState.Error).uri,
                        message = (uiState as HomeUIState.Error).message
                    )
                }
            }

            // Main photo picker FAB
            if (removalState !is RemovalState.Removing) {
                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(32.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        launcher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(id = R.string.select_photo),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Loading overlay during removal
            if (removalState is RemovalState.Removing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.msg_removing),
                            color = Color.White
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
        } // close Scaffold content
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
        Text(
            modifier = Modifier.padding(5.dp),
            style = MaterialTheme.typography.titleLarge,
            text = stringResource(id = R.string.empty),
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.padding(5.dp),
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
                modifier = Modifier.padding(5.dp),
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.error),
                text = stringResource(id = R.string.error),
                textAlign = TextAlign.Center
            )
            Text(
                modifier = Modifier.padding(5.dp),
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
    onShowSnackbar: (String) -> Unit = {}
) {
    PhotoExifDetail(
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        uri = result.uri
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Privacy summary card
            if (result.overallRisk != PrivacyRisk.NONE) {
                PrivacySummaryCard(result = result, onShowSnackbar = onShowSnackbar)
            }

            // Consistency warnings
            if (result.consistencyWarnings.isNotEmpty()) {
                ConsistencyWarningSection(warnings = result.consistencyWarnings)
            }

            // Grouped EXIF list
            ExifGroupedList(
                groups = result.categoryGroups,
                result = result,
                onShowSnackbar = onShowSnackbar,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ============================================================
// Privacy Summary Card
// ============================================================

@Composable
fun PrivacySummaryCard(
    result: ExifAnalysisResult,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {}
) {
    val containerColor = when (result.overallRisk) {
        PrivacyRisk.HIGH -> Color(0xFFFFEBEE) // Light red
        PrivacyRisk.MEDIUM -> Color(0xFFFFF3E0) // Light orange
        PrivacyRisk.LOW -> Color(0xFFE3F2FD) // Light blue
        PrivacyRisk.NONE -> Color.Transparent
    }
    val contentColor = when (result.overallRisk) {
        PrivacyRisk.HIGH -> Color(0xFFC62828)
        PrivacyRisk.MEDIUM -> Color(0xFFE65100)
        PrivacyRisk.LOW -> Color(0xFF1565C0)
        PrivacyRisk.NONE -> Color.Transparent
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
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
            // GPS map button
            if (result.hasGpsCoordinates && result.gpsLatitude != null && result.gpsLongitude != null) {
                GpsMapButton(
                    latitude = result.gpsLatitude,
                    longitude = result.gpsLongitude,
                    onShowSnackbar = onShowSnackbar
                )
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
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        warnings.forEach { warning ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8E1) // Light amber
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFF57F17)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = warning.messageResId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            text = warning.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF795548)
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
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
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            modifier = Modifier.padding(vertical = 4.dp)
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
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
            .padding(top = 12.dp, bottom = 4.dp),
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
            Spacer(modifier = Modifier.width(8.dp))
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
            .padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                modifier = Modifier.padding(start = 12.dp)
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
    val color by animateColorAsState(
        targetValue = when (risk) {
            PrivacyRisk.HIGH -> Color(0xFFE53935)
            PrivacyRisk.MEDIUM -> Color(0xFFFF9800)
            PrivacyRisk.LOW -> Color(0xFF2196F3)
            PrivacyRisk.NONE -> Color(0xFF9E9E9E)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(id = R.string.action_privacy_clean),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (removalState) {
                is RemovalState.Idle -> {
                    if (result.hasGpsCoordinates) {
                        RemovalOptionButton(
                            text = stringResource(id = R.string.action_remove_gps),
                            subtitle = stringResource(id = R.string.msg_privacy_summary_gps),
                            onClick = { onRemove(ExifRemovalMode.GPS_ONLY) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (result.overallRisk == PrivacyRisk.MEDIUM || result.overallRisk == PrivacyRisk.HIGH) {
                        RemovalOptionButton(
                            text = stringResource(id = R.string.action_remove_personal),
                            subtitle = stringResource(id = R.string.msg_privacy_summary_personal),
                            onClick = { onRemove(ExifRemovalMode.PERSONAL_ONLY) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(id = R.string.msg_removing))
                    }
                }

                is RemovalState.Done -> {
                    Text(
                        text = stringResource(id = R.string.msg_remove_success),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onShare(removalState.file) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.action_share))
                    }
                }

                is RemovalState.Error -> {
                    Text(
                        text = stringResource(id = R.string.msg_remove_error),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
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
                .padding(horizontal = 16.dp),
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
