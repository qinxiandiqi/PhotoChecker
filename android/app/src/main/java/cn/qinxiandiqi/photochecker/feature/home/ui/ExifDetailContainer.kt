package cn.qinxiandiqi.photochecker.feature.home.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult
import cn.qinxiandiqi.photochecker.feature.home.model.PrivacyRisk
import cn.qinxiandiqi.photochecker.ui.theme.SpacingMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingSm
import coil.compose.AsyncImage

// Layout-private constants for the collapsible compact header (not design tokens).
private val DefaultExpandedImageHeight = 240.dp
private val CollapsedImageHeight = 56.dp

@Composable
internal fun EmptyExifDetail(modifier: Modifier = Modifier) {
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
internal fun PhotoExifDetail(
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
internal fun PhotoExifDetailLoading(
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
internal fun PhotoExifDetailError(
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

@Composable
internal fun PhotoExifDetailSuccess(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    result: ExifAnalysisResult,
    collapseOffset: MutableState<Float> = mutableStateOf(0f),
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
                // Top spacer that shrinks during Phase 1 as the image slides up. It is the
                // gap between the (fading) TopAppBar and the image; filling it with the
                // theme primary color (same as the bar) keeps the area visually continuous
                // during the fade instead of showing the jarring white window background.
                val topSpacerDp = with(density) {
                    (topBarHeightPx - offset).coerceAtLeast(0f).toDp()
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topSpacerDp)
                        .background(MaterialTheme.colorScheme.primary)
                )

                // Image height: stays at expandedHeightDp during Phase 1, collapses during
                // Phase 2.
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
