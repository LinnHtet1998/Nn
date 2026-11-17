package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.MmkFormatter
import com.example.domain.model.Branch
import com.example.ui.theme.TabularMonoFontFamily

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.padding(7.dp).size(18.dp),
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularMonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun BranchComparisonChart(
    branches: List<Branch>,
    language: AppLanguage,
    title: String,
    modifier: Modifier = Modifier,
) {
    val maxSales = (branches.maxOfOrNull { it.todaySalesMmk } ?: 0L).coerceAtLeast(1L)
    val primaryBarColor = MaterialTheme.colorScheme.primary
    val profitBarColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            branches.take(8).forEachIndexed { index, branch ->
                val salesRatio = (branch.todaySalesMmk.toFloat() / maxSales.toFloat()).coerceIn(0f, 1f)
                val profitRatio = (branch.todayProfitMmk.coerceAtLeast(0L).toFloat() / maxSales.toFloat()).coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}. ${branch.localizedName(language == AppLanguage.MYANMAR)} (${branch.code})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = MmkFormatter.formatMmk(branch.todaySalesMmk, language),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                    ) {
                        val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            cornerRadius = radius,
                        )
                        if (salesRatio > 0f) {
                            drawRoundRect(
                                color = primaryBarColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width * salesRatio, size.height),
                                cornerRadius = radius,
                            )
                        }
                        if (profitRatio > 0f) {
                            drawRoundRect(
                                color = profitBarColor,
                                topLeft = Offset(0f, size.height * 0.55f),
                                size = Size(size.width * profitRatio, size.height * 0.45f),
                                cornerRadius = radius,
                            )
                        }
                    }
                }
            }
        }
    }
}
