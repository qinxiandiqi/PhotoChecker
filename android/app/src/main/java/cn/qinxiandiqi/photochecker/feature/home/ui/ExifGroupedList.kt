package cn.qinxiandiqi.photochecker.feature.home.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.qinxiandiqi.photochecker.App
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult
import cn.qinxiandiqi.photochecker.feature.home.model.ExifCategory
import cn.qinxiandiqi.photochecker.feature.home.model.ExifCategoryGroup
import cn.qinxiandiqi.photochecker.feature.home.model.ExifTagEntry
import cn.qinxiandiqi.photochecker.feature.home.model.PrivacyRisk
import cn.qinxiandiqi.photochecker.feature.home.model.displayName
import cn.qinxiandiqi.photochecker.ui.theme.LocalAppColors
import cn.qinxiandiqi.photochecker.ui.theme.SpacingLg
import cn.qinxiandiqi.photochecker.ui.theme.SpacingMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingSm
import cn.qinxiandiqi.photochecker.ui.theme.SpacingXs

@Composable
internal fun ExifGroupedList(
    groups: List<ExifCategoryGroup>,
    result: ExifAnalysisResult,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {}
) {
    // When there are no EXIF entries at all (e.g. a photo cleaned with "remove all
    // metadata"), show a friendly empty-state instead of a blank panel.
    if (groups.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(SpacingLg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(SpacingSm))
            Text(
                text = stringResource(id = R.string.msg_no_exif),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val expandedStates = remember {
        groups.associate { it.category to mutableStateOf(true) }
    }

    // Bottom content padding must clear both the floating FAB (88dp) AND the system
    // navigation bar, now that the screen draws edge-to-edge into that area.
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues(LocalDensity.current).calculateBottomPadding()

    LazyColumn(
        modifier = modifier.padding(horizontal = SpacingLg),
        contentPadding = PaddingValues(bottom = 88.dp + navBarBottom),
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
internal fun ExifCategoryHeader(
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
internal fun ExifTagEntryItem(
    entry: ExifTagEntry,
    modifier: Modifier = Modifier
) {
    val displayName = entry.displayName(LocalContext.current)

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

@Composable
internal fun RiskDot(
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
