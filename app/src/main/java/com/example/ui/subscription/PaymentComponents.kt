package com.example.ui.subscription

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.i18n.MmkFormatter
import com.example.domain.model.PaymentMethodConfig
import com.example.domain.model.PaymentVerificationStatus
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning
import com.example.ui.theme.TabularMonoFontFamily
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun compressReceiptImageToBase64(context: Context, uri: Uri): String? {
    return runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val maxSide = 720
        val ratio = maxOf(decoded.width, decoded.height).toFloat() / maxSide.toFloat()
        val scaled = if (ratio > 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width / ratio).toInt().coerceAtLeast(1),
                (decoded.height / ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
        val bytes = out.toByteArray()
        if (bytes.size > 240_000) {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, 45, out)
        }
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

@Composable
fun SubscriptionStatusBadge(status: SubscriptionStatus, language: AppLanguage) {
    val color = when (status) {
        SubscriptionStatus.ACTIVE -> SemanticSuccess
        SubscriptionStatus.EXPIRING_SOON -> SemanticWarning
        SubscriptionStatus.PENDING_APPROVAL -> MaterialTheme.colorScheme.primary
        SubscriptionStatus.PENDING_PAYMENT -> Color(0xFF0284C7)
        SubscriptionStatus.SUSPENDED -> MaterialTheme.colorScheme.tertiary
        SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED -> SemanticError
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = status.label(language),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun PaymentVerificationBadge(status: PaymentVerificationStatus, language: AppLanguage) {
    val color = when (status) {
        PaymentVerificationStatus.VERIFIED -> SemanticSuccess
        PaymentVerificationStatus.PENDING_VERIFICATION -> SemanticWarning
        PaymentVerificationStatus.REJECTED -> SemanticError
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = status.label(language),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun ReceiptSlipThumbnail(
    base64: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(base64) {
        if (base64.isNullOrBlank()) null
        else runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        var expanded by remember { mutableStateOf(false) }
        Image(
            bitmap = bitmap,
            contentDescription = "Receipt slip",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true },
        )
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                confirmButton = {
                    TextButton(onClick = { expanded = false }) { Text("OK") }
                },
                text = {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Receipt slip full preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanOptionCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    language: AppLanguage,
    strings: AppStrings,
    onSelect: () -> Unit,
) {
    val isMm = language == AppLanguage.MYANMAR
    val effectivePrice = plan.effectivePriceMmk()
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = plan.localizedName(isMm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!plan.promoLabel.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = plan.promoLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = MmkFormatter.formatMmk(effectivePrice, language),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularMonoFontFamily),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (effectivePrice < plan.priceMmk) {
                    Text(
                        text = MmkFormatter.formatMmk(plan.priceMmk, language),
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "/ ${plan.durationDays}d",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${strings.maxBranchesLabel}: ${plan.maxBranches}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "• ${strings.maxEmployeesLabel}: ${plan.maxEmployees}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitPaymentProofDialog(
    plans: List<SubscriptionPlan>,
    paymentMethods: List<PaymentMethodConfig>,
    initialPlan: SubscriptionPlan?,
    language: AppLanguage,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSubmit: (
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
        transactionRef: String,
        paymentDateText: String,
        proofBase64: String?,
    ) -> Unit,
) {
    val context = LocalContext.current
    val activePlans = remember(plans) { plans.filter { it.isActive } }
    val activeMethods = remember(paymentMethods) { paymentMethods.filter { it.isActive } }

    var selectedPlan by remember(activePlans, initialPlan) {
        mutableStateOf(initialPlan ?: activePlans.firstOrNull())
    }
    var selectedMethod by remember(activeMethods) {
        mutableStateOf(activeMethods.firstOrNull())
    }
    var planExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var transactionRef by remember { mutableStateOf("") }
    var paymentDateText by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var proofBase64 by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            proofBase64 = compressReceiptImageToBase64(context, uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(strings.submitPaymentAction)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = planExpanded,
                    onExpandedChange = { planExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedPlan?.let {
                            "${it.localizedName(language == AppLanguage.MYANMAR)} • ${MmkFormatter.formatMmk(it.effectivePriceMmk(), language)}"
                        } ?: "-",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.selectPlanLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = planExpanded, onDismissRequest = { planExpanded = false }) {
                        activePlans.forEach { plan ->
                            DropdownMenuItem(
                                text = {
                                    Text("${plan.localizedName(language == AppLanguage.MYANMAR)} — ${MmkFormatter.formatMmk(plan.effectivePriceMmk(), language)}")
                                },
                                onClick = {
                                    selectedPlan = plan
                                    planExpanded = false
                                },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = methodExpanded,
                    onExpandedChange = { methodExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedMethod?.localizedName(language == AppLanguage.MYANMAR) ?: "-",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.selectPaymentMethodLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                        activeMethods.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.localizedName(language == AppLanguage.MYANMAR)) },
                                onClick = {
                                    selectedMethod = method
                                    methodExpanded = false
                                },
                            )
                        }
                    }
                }

                selectedMethod?.let { method ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "${method.accountTitle} • ${method.accountNumber}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularMonoFontFamily),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = method.localizedInstructions(language == AppLanguage.MYANMAR),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = transactionRef,
                    onValueChange = { transactionRef = it },
                    label = { Text(strings.transactionRefLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("payment_ref_input"),
                )

                OutlinedTextField(
                    value = paymentDateText,
                    onValueChange = { paymentDateText = it },
                    label = { Text(strings.paymentDateLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("attach_receipt_button"),
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (proofBase64 != null) strings.receiptSlipAttachedLabel else strings.attachReceiptSlipAction)
                }

                if (proofBase64 != null) {
                    ReceiptSlipThumbnail(
                        base64 = proofBase64,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }

                if (errorText != null) {
                    Text(errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = selectedPlan
                    val m = selectedMethod
                    if (p == null || m == null || transactionRef.isBlank() || paymentDateText.isBlank()) {
                        errorText = strings.validationRequiredError
                        return@Button
                    }
                    onSubmit(p, m, transactionRef, paymentDateText, proofBase64)
                },
                modifier = Modifier.testTag("submit_payment_proof_button"),
            ) {
                Text(strings.submitPaymentAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}
