package com.example.ui.subscription

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.i18n.MmkFormatter
import com.example.core.pdf.ReceiptPdfGenerator
import com.example.core.subscription.SubscriptionPolicy
import com.example.domain.model.Company
import com.example.domain.model.PaymentMethodConfig
import com.example.domain.model.PaymentVerificationStatus
import com.example.domain.model.SubscriptionPayment
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticWarning
import com.example.ui.theme.TabularMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessSubscriptionScreen(
    company: Company,
    plans: List<SubscriptionPlan>,
    paymentMethods: List<PaymentMethodConfig>,
    payments: List<SubscriptionPayment>,
    trustedServerNowMillis: Long,
    isLockedGateMode: Boolean,
    language: AppLanguage,
    strings: AppStrings,
    onSubmitRenewalPayment: (
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
        transactionRef: String,
        paymentDateText: String,
        proofBase64: String?,
    ) -> Unit,
    onRefreshServerClock: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isMm = language == AppLanguage.MYANMAR

    val effectiveStatus = SubscriptionPolicy.effectiveStatus(company, trustedServerNowMillis)
    val remainingDays = SubscriptionPolicy.remainingDays(company, trustedServerNowMillis)
    val activePlans = remember(plans) { plans.filter { it.isActive } }

    var showPaymentModal by remember { mutableStateOf(false) }
    var chosenPlanForModal by remember { mutableStateOf<SubscriptionPlan?>(null) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("business_subscription_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLockedGateMode) {
                item {
                    Surface(
                        color = SemanticError.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = SemanticError)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(strings.subscriptionBlockedOwnerTitle, fontWeight = FontWeight.Bold, color = SemanticError)
                                    Text(
                                        "${company.localizedName(isMm)} • ${effectiveStatus.label(language)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Row {
                                if (onRefreshServerClock != null) {
                                    IconButton(onClick = onRefreshServerClock) {
                                        Icon(Icons.Filled.Refresh, contentDescription = strings.retryAction)
                                    }
                                }
                                if (onSignOut != null) {
                                    IconButton(onClick = onSignOut) {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOut)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(strings.mySubscriptionTitle, style = MaterialTheme.typography.headlineMedium)
                }
            }

            if (effectiveStatus == SubscriptionStatus.EXPIRING_SOON) {
                item {
                    Surface(
                        color = SemanticWarning.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = SemanticWarning)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.expiringSoonBannerFormat.format(remainingDays),
                                color = SemanticWarning,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Current Plan & Quotas Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(strings.currentPlanLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(company.subscriptionPlanName.ifBlank { "-" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            SubscriptionStatusBadge(effectiveStatus, language)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(strings.startDateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(MmkFormatter.formatDateTime(company.subscriptionStartAt?.toDate()?.time), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(strings.expiryDateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(MmkFormatter.formatDateTime(company.subscriptionExpiresAt?.toDate()?.time), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = strings.remainingDaysFormat.format(remainingDays),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularMonoFontFamily),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val bMax = company.maxBranchesAllowed.coerceAtLeast(1)
                            val eMax = company.maxEmployeesAllowed.coerceAtLeast(1)
                            Text(strings.planQuotaBranchesFormat.format(company.activeBranchCount, bMax), style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(
                                progress = { (company.activeBranchCount.toFloat() / bMax.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(strings.planQuotaEmployeesFormat.format(company.activeEmployeeCount, eMax), style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(
                                progress = { (company.activeEmployeeCount.toFloat() / eMax.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (company.enabledFeatures.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                company.enabledFeatures.forEach { feat ->
                                    AssistChip(onClick = {}, label = { Text(feat, style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        }

                        if (effectiveStatus != SubscriptionStatus.SUSPENDED && effectiveStatus != SubscriptionStatus.CANCELLED) {
                            Button(
                                onClick = {
                                    chosenPlanForModal = activePlans.firstOrNull { it.id == company.subscriptionPlanId }
                                        ?: activePlans.firstOrNull()
                                    showPaymentModal = true
                                },
                                modifier = Modifier.fillMaxWidth().testTag("open_renewal_payment_button"),
                            ) {
                                Icon(Icons.Filled.CreditCard, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.renewSubscriptionAction)
                            }
                        }
                    }
                }
            }

            if (activePlans.isNotEmpty() && effectiveStatus != SubscriptionStatus.SUSPENDED) {
                item {
                    Text(strings.selectPlanLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(activePlans, key = { it.id }) { plan ->
                    PlanOptionCard(
                        plan = plan,
                        isSelected = plan.id == company.subscriptionPlanId,
                        language = language,
                        strings = strings,
                        onSelect = {
                            chosenPlanForModal = plan
                            showPaymentModal = true
                        },
                    )
                }
            }

            item {
                Text(strings.paymentHistoryTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(payments, key = { it.id }) { pay ->
                val payStatus = PaymentVerificationStatus.fromKey(pay.status)
                Card(
                    shape = RoundedCornerShape(14.dp),
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
                                Text(pay.localizedPlanName(isMm), fontWeight = FontWeight.Bold)
                                Text(
                                    "${MmkFormatter.formatMmk(pay.amountMmk, language)} • ${pay.paymentMethodName}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("Ref: ${pay.transactionRef} • ${pay.paymentDateText}", style = MaterialTheme.typography.labelSmall)
                            }
                            PaymentVerificationBadge(payStatus, language)
                        }
                        if (!pay.rejectionReason.isNullOrBlank() && payStatus == PaymentVerificationStatus.REJECTED) {
                            Text(pay.rejectionReason, color = SemanticError, style = MaterialTheme.typography.bodySmall)
                        }
                        if (payStatus == PaymentVerificationStatus.VERIFIED) {
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

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showPaymentModal) {
        SubmitPaymentProofDialog(
            plans = plans,
            paymentMethods = paymentMethods,
            initialPlan = chosenPlanForModal,
            language = language,
            strings = strings,
            onDismiss = { showPaymentModal = false },
            onSubmit = { plan, method, ref, dateTxt, base64 ->
                onSubmitRenewalPayment(plan, method, ref, dateTxt, base64)
                showPaymentModal = false
            },
        )
    }
}
