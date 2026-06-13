package cn.qinxiandiqi.photochecker.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult
import cn.qinxiandiqi.photochecker.feature.home.model.PrivacyRisk
import cn.qinxiandiqi.photochecker.ui.theme.LocalAppColors
import cn.qinxiandiqi.photochecker.ui.theme.ShapeMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingLg
import cn.qinxiandiqi.photochecker.ui.theme.SpacingMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingSm

@Composable
internal fun PrivacySummaryCard(
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

internal fun riskLabelResId(risk: PrivacyRisk): Int = when (risk) {
    PrivacyRisk.HIGH -> R.string.risk_high
    PrivacyRisk.MEDIUM -> R.string.risk_medium
    PrivacyRisk.LOW -> R.string.risk_low
    PrivacyRisk.NONE -> R.string.risk_none
}
