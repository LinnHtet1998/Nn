package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.i18n.MmkFormatter
import com.example.core.rbac.Permission
import com.example.core.rbac.RolePermissionMatrix
import com.example.core.rbac.UserRole
import com.example.domain.model.Branch
import com.example.domain.model.DashboardCalculator
import com.example.ui.components.BranchComparisonChart
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticInfo
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning
import com.example.ui.theme.TabularMonoFontFamily

@Composable
fun DashboardScreen(
    branches: List<Branch>,
    selectedBranch: Branch?,
    userRole: UserRole,
    language: AppLanguage,
    strings: AppStrings,
) {
    val canViewCompanyOverview = RolePermissionMatrix.hasPermission(userRole, Permission.VIEW_COMPANY_OVERVIEW)
    val canViewFinancials = RolePermissionMatrix.hasPermission(userRole, Permission.VIEW_FINANCIALS)
    var headOfficeMode by remember(canViewCompanyOverview) { mutableStateOf(canViewCompanyOverview) }

    val metrics = remember(headOfficeMode, branches, selectedBranch) {
        if (headOfficeMode && canViewCompanyOverview) {
            DashboardCalculator.computeHeadOfficeMetrics(branches)
        } else {
            DashboardCalculator.computeBranchMetrics(selectedBranch ?: branches.firstOrNull())
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (headOfficeMode && canViewCompanyOverview) {
                                    strings.dashboardHeadOfficeTitle
                                } else {
                                    strings.dashboardBranchTitle
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = strings.phase1ScopeBanner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (canViewCompanyOverview) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = headOfficeMode,
                                onClick = { headOfficeMode = true },
                                label = { Text("${strings.viewHeadOffice} (${branches.size})") },
                                modifier = Modifier.testTag("dashboard_mode_head_office"),
                            )
                            FilterChip(
                                selected = !headOfficeMode,
                                onClick = { headOfficeMode = false },
                                label = { Text(selectedBranch?.code ?: strings.viewBranchOnly) },
                                modifier = Modifier.testTag("dashboard_mode_branch"),
                            )
                        }
                    }
                }
            }
        }

        item {
            val kpiItems = buildList {
                add(
                    Triple(
                        strings.kpiTodaySales,
                        MmkFormatter.formatMmk(metrics.todaySalesMmk, language),
                        Icons.Filled.PointOfSale to MaterialTheme.colorScheme.primary,
                    )
                )
                if (canViewFinancials) {
                    add(
                        Triple(
                            strings.kpiTodayProfit,
                            MmkFormatter.formatMmk(metrics.todayProfitMmk, language),
                            Icons.Filled.TrendingUp to SemanticSuccess,
                        )
                    )
                    add(
                        Triple(
                            strings.kpiTotalExpenses,
                            MmkFormatter.formatMmk(metrics.totalExpensesMmk, language),
                            Icons.Filled.MoneyOff to SemanticWarning,
                        )
                    )
                    add(
                        Triple(
                            strings.kpiNetProfit,
                            MmkFormatter.formatMmk(metrics.netProfitMmk, language),
                            Icons.Filled.AccountBalance to if (metrics.netProfitMmk >= 0) SemanticSuccess else SemanticError,
                        )
                    )
                }
                add(
                    Triple(
                        strings.kpiTransactions,
                        MmkFormatter.formatCount(metrics.totalTransactions),
                        Icons.Filled.ReceiptLong to SemanticInfo,
                    )
                )
                add(
                    Triple(
                        strings.kpiTotalProducts,
                        MmkFormatter.formatCount(metrics.totalProducts),
                        Icons.Filled.Inventory2 to MaterialTheme.colorScheme.primary,
                    )
                )
                add(
                    Triple(
                        strings.kpiTotalCustomers,
                        MmkFormatter.formatCount(metrics.totalCustomers),
                        Icons.Filled.Groups to MaterialTheme.colorScheme.secondary,
                    )
                )
                add(
                    Triple(
                        strings.kpiOutstandingDebt,
                        MmkFormatter.formatMmk(metrics.totalOutstandingDebtMmk, language),
                        Icons.Filled.AttachMoney to SemanticError,
                    )
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 720.dp) 4 else 2
                val rows = kpiItems.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { (title, value, iconPair) ->
                                KpiMetricCard(
                                    title = title,
                                    value = value,
                                    icon = iconPair.first,
                                    accentColor = iconPair.second,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warehouse,
                                contentDescription = strings.inventoryValuationLabel,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(10.dp).size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.inventoryValuationLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = MmkFormatter.formatMmk(metrics.totalInventoryValueMmk, language),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularMonoFontFamily),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "${metrics.activeBranchCount}/${metrics.totalBranchCount} ${strings.activeStatus}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        if (headOfficeMode && canViewCompanyOverview && branches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    metrics.bestBranch?.let { best ->
                        KpiMetricCard(
                            title = strings.bestPerformingBranch,
                            value = best.localizedName(language == AppLanguage.MYANMAR),
                            subtitle = MmkFormatter.formatMmk(best.todaySalesMmk, language),
                            icon = Icons.Filled.EmojiEvents,
                            accentColor = SemanticSuccess,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    metrics.lowestBranch?.let { low ->
                        KpiMetricCard(
                            title = strings.lowestPerformingBranch,
                            value = low.localizedName(language == AppLanguage.MYANMAR),
                            subtitle = MmkFormatter.formatMmk(low.todaySalesMmk, language),
                            icon = Icons.Filled.TrendingDown,
                            accentColor = SemanticWarning,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                BranchComparisonChart(
                    branches = metrics.rankedBranches,
                    language = language,
                    title = strings.branchRankingTitle,
                )
            }
        } else if (branches.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        text = strings.noBranchesMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}
