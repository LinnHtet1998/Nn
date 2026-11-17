package com.example.ui.subscription

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.i18n.MmkFormatter
import com.example.core.pdf.ReceiptPdfGenerator
import com.example.core.subscription.SubscriptionPolicy
import com.example.domain.model.Company
import com.example.domain.model.PaymentMethodConfig
import com.example.domain.model.PaymentMethodProviderType
import com.example.domain.model.PaymentVerificationStatus
import com.example.domain.model.PlanFeature
import com.example.domain.model.PlatformAuditLog
import com.example.domain.model.SubscriptionPayment
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.JadePrimary
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning
import com.example.ui.theme.TabularMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformAdminScreen(
    companies: List<Company>,
    payments: List<SubscriptionPayment>,
    plans: List<SubscriptionPlan>,
    paymentMethods: List<PaymentMethodConfig>,
    platformAuditLogs: List<PlatformAuditLog>,
    trustedServerNowMillis: Long,
    language: AppLanguage,
    strings: AppStrings,
    showStandaloneHeader: Boolean,
    onSeedStarterPlans: () -> Unit,
    onUpsertPlan: (
        existingPlan: SubscriptionPlan?,
        nameEn: String,
        nameMm: String,
        descEn: String,
        descMm: String,
        durationMonths: Int,
        durationDays: Int,
        priceMmk: Long,
        discountPercent: Double,
        promoLabel: String?,
        maxBranches: Int,
        maxEmployees: Int,
        enabledFeatures: List<String>,
        isActive: Boolean,
        sortOrder: Int,
    ) -> Unit,
    onDeletePlan: (SubscriptionPlan) -> Unit,
    onUpsertPaymentMethod: (
        existingMethod: PaymentMethodConfig?,
        nameEn: String,
        nameMm: String,
        providerType: PaymentMethodProviderType,
        accountTitle: String,
        accountNumber: String,
        instructionsEn: String,
        instructionsMm: String,
        gatewayEndpoint: String?,
        isActive: Boolean,
    ) -> Unit,
    onApprovePayment: (SubscriptionPayment, Company, SubscriptionPlan?) -> Unit,
    onRejectPayment: (SubscriptionPayment, Company?, String) -> Unit,
    onUpdateBusinessStatus: (Company, SubscriptionStatus) -> Unit,
    onSignOut: (() -> Unit)? = null,
    onLaunchOwnCompanySetup: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isMm = language == AppLanguage.MYANMAR

    var selectedTab by remember { mutableIntStateOf(0) }
    var businessSearch by remember { mutableStateOf("") }
    var businessFilter by remember { mutableStateOf<SubscriptionStatus?>(null) }
    var pendingPaymentsOnly by remember { mutableStateOf(true) }

    var showPlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var editingMethod by remember { mutableStateOf<PaymentMethodConfig?>(null) }
    var rejectingPayment by remember { mutableStateOf<SubscriptionPayment?>(null) }
    var pendingPdfPayment by remember { mutableStateOf<SubscriptionPayment?>(null) }

    val pdfSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val p = pendingPdfPayment
        if (uri != null && p != null) {
            val res = ReceiptPdfGenerator.writeSubscriptionReceiptPdf(context, uri, p)
            Toast.makeText(
                context,
                if (res.isSuccess) strings.savedSuccessMessage else (res.exceptionOrNull()?.localizedMessage ?: "PDF Error"),
                Toast.LENGTH_SHORT,
            ).show()
        }
        pendingPdfPayment = null
    }

    val metrics = remember(companies, payments, trustedServerNowMillis) {
        SubscriptionPolicy.computePlatformMetrics(companies, payments, trustedServerNowMillis)
    }

    val companiesById = remember(companies) { companies.associateBy { it.id } }
    val plansById = remember(plans) { plans.associateBy { it.id } }

    val tabs = listOf(
        strings.platformOverviewTab,
        strings.platformBusinessesTab,
        strings.platformPaymentsTab,
        strings.platformPlansTab,
        strings.auditLogsTab,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTab == 3) {
                FloatingActionButton(
                    onClick = {
                        editingPlan = null
                        showPlanDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("create_plan_fab"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = strings.createPlanAction)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("platform_admin_screen"),
        ) {
            if (showStandaloneHeader) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.platformAdminTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(strings.appSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (onLaunchOwnCompanySetup != null) {
                            OutlinedButton(onClick = onLaunchOwnCompanySetup) {
                                Text("POS Setup")
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        if (onSignOut != null) {
                            IconButton(onClick = onSignOut) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOut)
                            }
                        }
                    }
                }
            }

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1) },
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (plans.isEmpty() || paymentMethods.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(10.dp))
                                        Button(onClick = onSeedStarterPlans, modifier = Modifier.weight(1f).testTag("seed_starter_plans_button")) {
                                            Text(strings.seedStarterPlansAction)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            BoxWithConstraints {
                                val columns = if (maxWidth >= 600.dp) 4 else 2
                                val cards = listOf(
                                    Triple(strings.totalBusinessesKpi, metrics.totalBusinesses.toString(), Icons.Filled.Business to MaterialTheme.colorScheme.primary),
                                    Triple(strings.activeSubscriptionsKpi, metrics.activeSubscriptions.toString(), Icons.Filled.CheckCircle to SemanticSuccess),
                                    Triple(strings.expiredSubscriptionsKpi, metrics.expiredSubscriptions.toString(), Icons.Filled.Warning to SemanticError),
                                    Triple(strings.pendingPaymentsKpi, metrics.pendingPaymentsCount.toString(), Icons.Filled.HourglassTop to SemanticWarning),
                                    Triple(strings.monthlyRevenueKpi, MmkFormatter.formatMmk(metrics.monthlyRevenueMmk, language), Icons.Filled.Payments to JadePrimary),
                                    Triple(strings.sixMonthRevenueKpi, MmkFormatter.formatMmk(metrics.sixMonthRevenueMmk, language), Icons.Filled.CreditCard to MaterialTheme.colorScheme.secondary),
                                    Triple(strings.annualRevenueKpi, MmkFormatter.formatMmk(metrics.annualRevenueMmk, language), Icons.Filled.AccountBalanceWallet to GoldAccent),
                                    Triple(strings.totalRevenueKpi, MmkFormatter.formatMmk(metrics.totalCollectedRevenueMmk, language), Icons.Filled.Payments to SemanticSuccess),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    cards.chunked(columns).forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            row.forEach { (title, value, style) ->
                                                KpiMetricCard(
                                                    title = title,
                                                    value = value,
                                                    icon = style.first,
                                                    accentColor = style.second,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }

                        // Revenue Breakdown Canvas Chart
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(strings.revenueBreakdownTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val tiers = listOf(
                                        "1M" to metrics.monthlyRevenueMmk,
                                        "6M" to metrics.sixMonthRevenueMmk,
                                        "1Y" to metrics.annualRevenueMmk,
                                    )
                                    val maxVal = tiers.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                                    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                        val barWidth = size.width / 5f
                                        val colors = listOf(JadePrimary, GoldAccent, SemanticSuccess)
                                        tiers.forEachIndexed { idx, (_, amount) ->
                                            val hRatio = amount.toFloat() / maxVal.toFloat()
                                            val barH = (size.height - 16f) * hRatio.coerceAtLeast(0.06f)
                                            val x = (idx + 0.5f) * (size.width / 3f) - barWidth / 2f
                                            drawRoundRect(
                                                color = colors[idx],
                                                topLeft = Offset(x, size.height - barH),
                                                size = Size(barWidth, barH),
                                                cornerRadius = CornerRadius(12f, 12f),
                                            )
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                        tiers.forEach { (label, amount) ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                                Text(
                                                    MmkFormatter.formatMmk(amount, language),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularMonoFontFamily),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (metrics.upcomingExpirations.isNotEmpty()) {
                            item {
                                Text(strings.upcomingExpirationsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            items(metrics.upcomingExpirations, key = { it.id }) { comp ->
                                val rem = SubscriptionPolicy.remainingDays(comp, trustedServerNowMillis)
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(comp.localizedName(isMm), fontWeight = FontWeight.Bold)
                                            Text("${comp.ownerName} • ${comp.phone}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Surface(color = SemanticWarning.copy(alpha = 0.16f), shape = RoundedCornerShape(12.dp)) {
                                            Text(
                                                text = strings.remainingDaysFormat.format(rem),
                                                color = SemanticWarning,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val filteredCompanies = remember(companies, businessSearch, businessFilter, trustedServerNowMillis) {
                        val q = businessSearch.trim().lowercase()
                        companies.filter { comp ->
                            val matchesQ = q.isBlank() ||
                                comp.name.lowercase().contains(q) ||
                                comp.nameMm.lowercase().contains(q) ||
                                comp.ownerName.lowercase().contains(q) ||
                                comp.ownerEmail.lowercase().contains(q)
                            val eff = SubscriptionPolicy.effectiveStatus(comp, trustedServerNowMillis)
                            matchesQ && (businessFilter == null || eff == businessFilter)
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            OutlinedTextField(
                                value = businessSearch,
                                onValueChange = { businessSearch = it },
                                placeholder = { Text(strings.searchBusinessesHint) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = businessFilter == null,
                                    onClick = { businessFilter = null },
                                    label = { Text("${strings.filterAllAccounts} (${companies.size})") },
                                )
                                SubscriptionStatus.entries.forEach { st ->
                                    FilterChip(
                                        selected = businessFilter == st,
                                        onClick = { businessFilter = st },
                                        label = { Text(st.label(language)) },
                                    )
                                }
                            }
                        }

                        items(filteredCompanies, key = { it.id }) { comp ->
                            val effStatus = SubscriptionPolicy.effectiveStatus(comp, trustedServerNowMillis)
                            val remDays = SubscriptionPolicy.remainingDays(comp, trustedServerNowMillis)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(comp.localizedName(isMm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("${comp.ownerName} • ${comp.ownerEmail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${comp.subscriptionPlanName} • ${strings.remainingDaysFormat.format(remDays)}", style = MaterialTheme.typography.labelMedium)
                                        }
                                        SubscriptionStatusBadge(effStatus, language)
                                    }

                                    Text(
                                        text = "${strings.planQuotaBranchesFormat.format(comp.activeBranchCount, comp.maxBranchesAllowed)}  |  ${strings.planQuotaEmployeesFormat.format(comp.activeEmployeeCount, comp.maxEmployeesAllowed)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (effStatus != SubscriptionStatus.SUSPENDED) {
                                            OutlinedButton(
                                                onClick = { onUpdateBusinessStatus(comp, SubscriptionStatus.SUSPENDED) },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.suspendAction)
                                            }
                                        } else {
                                            Button(
                                                onClick = { onUpdateBusinessStatus(comp, SubscriptionStatus.ACTIVE) },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.reactivateAction)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    val shownPayments = remember(payments, pendingPaymentsOnly) {
                        if (pendingPaymentsOnly) {
                            payments.filter { PaymentVerificationStatus.fromKey(it.status) == PaymentVerificationStatus.PENDING_VERIFICATION }
                        } else {
                            payments
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = pendingPaymentsOnly,
                                    onClick = { pendingPaymentsOnly = true },
                                    label = { Text("${strings.pendingPaymentsKpi} (${metrics.pendingPaymentsCount})") },
                                )
                                FilterChip(
                                    selected = !pendingPaymentsOnly,
                                    onClick = { pendingPaymentsOnly = false },
                                    label = { Text("${strings.filterAllAccounts} (${payments.size})") },
                                )
                            }
                        }

                        items(shownPayments, key = { it.id }) { pay ->
                            val payStatus = PaymentVerificationStatus.fromKey(pay.status)
                            val targetCompany = companiesById[pay.companyId]
                            val targetPlan = plansById[pay.planId]
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pay.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("${pay.ownerName} • ${pay.ownerEmail}", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                "${pay.localizedPlanName(isMm)} — ${MmkFormatter.formatMmk(pay.amountMmk, language)}",
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularMonoFontFamily),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                "${pay.paymentMethodName} • Ref: ${pay.transactionRef} • ${pay.paymentDateText}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        PaymentVerificationBadge(payStatus, language)
                                    }

                                    if (!pay.proofImageBase64.isNullOrBlank()) {
                                        ReceiptSlipThumbnail(
                                            base64 = pay.proofImageBase64,
                                            modifier = Modifier.fillMaxWidth().height(140.dp),
                                        )
                                    }

                                    if (payStatus == PaymentVerificationStatus.PENDING_VERIFICATION && targetCompany != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = { onApprovePayment(pay, targetCompany, targetPlan) },
                                                modifier = Modifier.weight(1f).testTag("approve_payment_${pay.id}"),
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.verifyAndApprovePayment)
                                            }
                                            OutlinedButton(
                                                onClick = { rejectingPayment = pay },
                                                modifier = Modifier.weight(1f).testTag("reject_payment_${pay.id}"),
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.rejectPaymentAction)
                                            }
                                        }
                                    } else if (payStatus == PaymentVerificationStatus.VERIFIED) {
                                        OutlinedButton(
                                            onClick = {
                                                pendingPdfPayment = pay
                                                pdfSaverLauncher.launch("${pay.receiptNumber.ifBlank { pay.id }}.pdf")
                                            },
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("${strings.downloadReceiptPdf} (${pay.receiptNumber})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(strings.subscriptionPlansTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (plans.isEmpty()) {
                                    TextButton(onClick = onSeedStarterPlans) {
                                        Text(strings.seedStarterPlansAction)
                                    }
                                }
                            }
                        }
                        items(plans, key = { it.id }) { plan ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(plan.localizedName(isMm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${MmkFormatter.formatMmk(plan.effectivePriceMmk(), language)} / ${plan.durationMonths}m (${plan.durationDays}d) • Discount ${plan.discountPercent.toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                "${strings.maxBranchesLabel}: ${plan.maxBranches} • ${strings.maxEmployeesLabel}: ${plan.maxEmployees} • ${if (plan.isActive) strings.activeStatus else strings.inactiveStatus}",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                editingPlan = plan
                                                showPlanDialog = true
                                            }) {
                                                Icon(Icons.Filled.Edit, contentDescription = strings.editPlanAction)
                                            }
                                            IconButton(onClick = { onDeletePlan(plan) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete Plan", tint = SemanticError)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(strings.paymentMethodsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = {
                                    editingMethod = null
                                    showMethodDialog = true
                                }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.addPaymentMethodAction)
                                }
                            }
                        }

                        items(paymentMethods, key = { it.id }) { method ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(method.localizedName(isMm), fontWeight = FontWeight.Bold)
                                        Text("${method.accountTitle} • ${method.accountNumber}", style = MaterialTheme.typography.bodySmall)
                                        Text("${method.providerType} • ${if (method.isActive) strings.activeStatus else strings.inactiveStatus}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = {
                                        editingMethod = method
                                        showMethodDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Method")
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(platformAuditLogs, key = { it.id }) { log ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${log.action} • ${log.actorName}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                                    Text(MmkFormatter.formatDateTime(log.createdAt?.toDate()?.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlanDialog) {
        PlanEditorDialog(
            initialPlan = editingPlan,
            language = language,
            strings = strings,
            onDismiss = { showPlanDialog = false },
            onSave = { nEn, nMm, dEn, dMm, durM, durD, price, disc, promo, maxB, maxE, feats, active, sort ->
                onUpsertPlan(editingPlan, nEn, nMm, dEn, dMm, durM, durD, price, disc, promo, maxB, maxE, feats, active, sort)
                showPlanDialog = false
            },
        )
    }

    if (showMethodDialog) {
        PaymentMethodEditorDialog(
            initialMethod = editingMethod,
            strings = strings,
            onDismiss = { showMethodDialog = false },
            onSave = { nEn, nMm, type, accTitle, accNum, instEn, instMm, gw, active ->
                onUpsertPaymentMethod(editingMethod, nEn, nMm, type, accTitle, accNum, instEn, instMm, gw, active)
                showMethodDialog = false
            },
        )
    }

    rejectingPayment?.let { targetPay ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectingPayment = null },
            title = { Text(strings.rejectPaymentAction) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(strings.rejectionReasonHint) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRejectPayment(targetPay, companiesById[targetPay.companyId], reason)
                    rejectingPayment = null
                }) {
                    Text(strings.rejectPaymentAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingPayment = null }) { Text(strings.cancelAction) }
            },
        )
    }
}

@Composable
private fun PlanEditorDialog(
    initialPlan: SubscriptionPlan?,
    language: AppLanguage,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Int, Long, Double, String?, Int, Int, List<String>, Boolean, Int) -> Unit,
) {
    var nameEn by remember { mutableStateOf(initialPlan?.nameEn ?: "") }
    var nameMm by remember { mutableStateOf(initialPlan?.nameMm ?: "") }
    var durationMonths by remember { mutableStateOf((initialPlan?.durationMonths ?: 1).toString()) }
    var durationDays by remember { mutableStateOf((initialPlan?.durationDays ?: 30).toString()) }
    var priceMmk by remember { mutableStateOf((initialPlan?.priceMmk ?: 0L).toString()) }
    var discountPercent by remember { mutableStateOf((initialPlan?.discountPercent ?: 0.0).toInt().toString()) }
    var promoLabel by remember { mutableStateOf(initialPlan?.promoLabel ?: "") }
    var maxBranches by remember { mutableStateOf((initialPlan?.maxBranches ?: 1).toString()) }
    var maxEmployees by remember { mutableStateOf((initialPlan?.maxEmployees ?: 5).toString()) }
    var isActive by remember { mutableStateOf(initialPlan?.isActive ?: true) }
    val selectedFeatures = remember {
        mutableStateListOf<String>().apply {
            addAll(initialPlan?.enabledFeatures ?: PlanFeature.entries.map { it.key })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPlan == null) strings.createPlanAction else strings.editPlanAction) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text(strings.planNameEnLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nameMm, onValueChange = { nameMm = it }, label = { Text(strings.planNameMmLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = durationMonths, onValueChange = { durationMonths = it }, label = { Text(strings.durationMonthsLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = durationDays, onValueChange = { durationDays = it }, label = { Text(strings.durationDaysLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = priceMmk, onValueChange = { priceMmk = it }, label = { Text(strings.priceMmkLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.4f))
                    OutlinedTextField(value = discountPercent, onValueChange = { discountPercent = it }, label = { Text(strings.discountPercentLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = promoLabel, onValueChange = { promoLabel = it }, label = { Text(strings.promoLabelHint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = maxBranches, onValueChange = { maxBranches = it }, label = { Text(strings.maxBranchesLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxEmployees, onValueChange = { maxEmployees = it }, label = { Text(strings.maxEmployeesLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.activeStatus)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                PlanFeature.entries.forEach { feat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedFeatures.contains(feat.key),
                            onCheckedChange = { checked ->
                                if (checked) selectedFeatures.add(feat.key) else selectedFeatures.remove(feat.key)
                            },
                        )
                        Text(feat.label(language), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nameEn.isNotBlank()) {
                    val m = (durationMonths.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val d = (durationDays.toIntOrNull() ?: (m * 30)).coerceAtLeast(1)
                    val p = (priceMmk.toLongOrNull() ?: 0L).coerceAtLeast(0L)
                    val disc = (discountPercent.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                    val b = (maxBranches.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val e = (maxEmployees.toIntOrNull() ?: 5).coerceAtLeast(1)
                    onSave(
                        nameEn,
                        nameMm.ifBlank { nameEn },
                        "Up to $b branches & $e staff accounts",
                        "ဆိုင်ခွဲ $b ခုနှင့် ဝန်ထမ်း $e ဦး",
                        m,
                        d,
                        p,
                        disc,
                        promoLabel.takeIf { it.isNotBlank() },
                        b,
                        e,
                        selectedFeatures.toList(),
                        isActive,
                        initialPlan?.sortOrder ?: m,
                    )
                }
            }) {
                Text(strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodEditorDialog(
    initialMethod: PaymentMethodConfig?,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (String, String, PaymentMethodProviderType, String, String, String, String, String?, Boolean) -> Unit,
) {
    var nameEn by remember { mutableStateOf(initialMethod?.nameEn ?: "") }
    var nameMm by remember { mutableStateOf(initialMethod?.nameMm ?: "") }
    var providerType by remember { mutableStateOf(PaymentMethodProviderType.fromKey(initialMethod?.providerType)) }
    var providerExpanded by remember { mutableStateOf(false) }
    var accountTitle by remember { mutableStateOf(initialMethod?.accountTitle ?: "") }
    var accountNumber by remember { mutableStateOf(initialMethod?.accountNumber ?: "") }
    var instructionsEn by remember { mutableStateOf(initialMethod?.instructionsEn ?: "") }
    var instructionsMm by remember { mutableStateOf(initialMethod?.instructionsMm ?: "") }
    var gatewayEndpoint by remember { mutableStateOf(initialMethod?.gatewayEndpoint ?: "") }
    var isActive by remember { mutableStateOf(initialMethod?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addPaymentMethodAction) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text("Method Name (EN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nameMm, onValueChange = { nameMm = it }, label = { Text("Method Name (MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = it }) {
                    OutlinedTextField(
                        value = providerType.key,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                        PaymentMethodProviderType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.key) }, onClick = { providerType = t; providerExpanded = false })
                        }
                    }
                }
                OutlinedTextField(value = accountTitle, onValueChange = { accountTitle = it }, label = { Text(strings.accountTitleLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = accountNumber, onValueChange = { accountNumber = it }, label = { Text(strings.accountNumberLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instructionsEn, onValueChange = { instructionsEn = it }, label = { Text("${strings.instructionsLabel} (EN)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instructionsMm, onValueChange = { instructionsMm = it }, label = { Text("${strings.instructionsLabel} (MM)") }, modifier = Modifier.fillMaxWidth())
                if (providerType == PaymentMethodProviderType.GATEWAY_WEBHOOK) {
                    OutlinedTextField(value = gatewayEndpoint, onValueChange = { gatewayEndpoint = it }, label = { Text("Gateway Webhook Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.activeStatus)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nameEn.isNotBlank()) {
                    onSave(
                        nameEn,
                        nameMm.ifBlank { nameEn },
                        providerType,
                        accountTitle,
                        accountNumber,
                        instructionsEn.ifBlank { "Transfer and upload payment slip." },
                        instructionsMm.ifBlank { instructionsEn.ifBlank { "ငွေလွှဲပြီး စလစ်တင်ပါ။" } },
                        gatewayEndpoint.takeIf { it.isNotBlank() },
                        isActive,
                    )
                }
            }) {
                Text(strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}
