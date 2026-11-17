package com.example.ui.subscription

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.domain.model.PaymentMethodConfig
import com.example.domain.model.SubscriptionPlan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessRegistrationScreen(
    userEmail: String,
    userDisplayName: String,
    plans: List<SubscriptionPlan>,
    paymentMethods: List<PaymentMethodConfig>,
    isSubmitting: Boolean,
    errorMessage: String?,
    language: AppLanguage,
    strings: AppStrings,
    onRefreshCheck: () -> Unit,
    onRegisterBusiness: (
        businessNameEn: String,
        businessNameMm: String,
        ownerName: String,
        phone: String,
        address: String,
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
        transactionRef: String,
        paymentDateText: String,
        proofBase64: String?,
    ) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val activePlans = remember(plans) { plans.filter { it.isActive } }
    val activeMethods = remember(paymentMethods) { paymentMethods.filter { it.isActive } }

    var businessNameEn by remember { mutableStateOf("") }
    var businessNameMm by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf(userDisplayName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var selectedPlan by remember(activePlans) { mutableStateOf(activePlans.firstOrNull()) }
    var selectedMethod by remember(activeMethods) { mutableStateOf(activeMethods.firstOrNull()) }
    var methodExpanded by remember { mutableStateOf(false) }
    var transactionRef by remember { mutableStateOf("") }
    var paymentDateText by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var proofBase64 by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            proofBase64 = compressReceiptImageToBase64(context, uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("business_registration_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(strings.appTitle, fontWeight = FontWeight.Bold)
                        Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row {
                    IconButton(onClick = onRefreshCheck) {
                        Icon(Icons.Filled.Refresh, contentDescription = strings.retryAction)
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOut)
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.accessDeniedNotProvisionedTitle, fontWeight = FontWeight.Bold)
                    Text(strings.accessDeniedNotProvisionedBody, style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.registerBusinessTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(strings.registerBusinessSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(value = businessNameEn, onValueChange = { businessNameEn = it }, label = { Text(strings.companyNameEn) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = businessNameMm, onValueChange = { businessNameMm = it }, label = { Text(strings.companyNameMm) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text(strings.ownerNameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(strings.addressLabel) }, modifier = Modifier.fillMaxWidth())

                    if (activePlans.isEmpty()) {
                        Text(
                            text = "No active subscription plans configured yet. Ask Platform Super Admin to initialize plans.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(strings.selectPlanLabel, style = MaterialTheme.typography.labelLarge)
                        activePlans.forEach { plan ->
                            PlanOptionCard(
                                plan = plan,
                                isSelected = selectedPlan?.id == plan.id,
                                language = language,
                                strings = strings,
                                onSelect = { selectedPlan = plan },
                            )
                        }
                    }

                    if (activeMethods.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = methodExpanded, onExpandedChange = { methodExpanded = it }) {
                            OutlinedTextField(
                                value = selectedMethod?.localizedName(language == AppLanguage.MYANMAR) ?: "-",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(strings.selectPaymentMethodLabel) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                                activeMethods.forEach { m ->
                                    DropdownMenuItem(text = { Text(m.localizedName(language == AppLanguage.MYANMAR)) }, onClick = { selectedMethod = m; methodExpanded = false })
                                }
                            }
                        }

                        selectedMethod?.let { m ->
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(10.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text("${m.accountTitle} • ${m.accountNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text(m.localizedInstructions(language == AppLanguage.MYANMAR), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    OutlinedTextField(value = transactionRef, onValueChange = { transactionRef = it }, label = { Text(strings.transactionRefLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = paymentDateText, onValueChange = { paymentDateText = it }, label = { Text(strings.paymentDateLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (proofBase64 != null) strings.receiptSlipAttachedLabel else strings.attachReceiptSlipAction)
                    }

                    if (proofBase64 != null) {
                        ReceiptSlipThumbnail(base64 = proofBase64, modifier = Modifier.fillMaxWidth().height(110.dp))
                    }

                    val shownError = localError ?: errorMessage
                    if (shownError != null) {
                        Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val plan = selectedPlan
                            val method = selectedMethod
                            if (businessNameEn.isBlank() || ownerName.isBlank() || phone.isBlank() || plan == null || method == null || transactionRef.isBlank()) {
                                localError = strings.validationRequiredError
                                return@Button
                            }
                            localError = null
                            onRegisterBusiness(
                                businessNameEn,
                                businessNameMm.ifBlank { businessNameEn },
                                ownerName,
                                phone,
                                address.ifBlank { "-" },
                                plan,
                                method,
                                transactionRef,
                                paymentDateText,
                                proofBase64,
                            )
                        },
                        enabled = !isSubmitting && activePlans.isNotEmpty() && activeMethods.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            val priceTag = selectedPlan?.let { MmkFormatter.formatMmk(it.effectivePriceMmk(), language) } ?: ""
                            Text("${strings.submitPaymentAction} ($priceTag)")
                        }
                    }
                }
            }
        }
    }
}
