package com.example.ui.auth

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings

@Composable
fun CompanySetupScreen(
    strings: AppStrings,
    language: AppLanguage,
    userEmail: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCreateCompany: (
        companyNameEn: String,
        companyNameMm: String,
        branchNameEn: String,
        branchNameMm: String,
        branchCode: String,
        phone: String,
        address: String,
        taxId: String?,
        taxRatePercent: Double,
    ) -> Unit,
    onSignOut: () -> Unit,
) {
    var companyNameEn by remember { mutableStateOf("") }
    var companyNameMm by remember { mutableStateOf("") }
    var branchNameEn by remember { mutableStateOf("Head Office Store") }
    var branchNameMm by remember { mutableStateOf("ရုံးချုပ် ပင်မဆိုင်ခွဲ") }
    var branchCode by remember { mutableStateOf("HQ-001") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }
    var taxRateText by remember { mutableStateOf("5") }
    var localError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp).size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(strings.appTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("setup_sign_out_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = strings.signOut,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(strings.setupOrgTitle, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        strings.setupOrgSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = companyNameEn,
                        onValueChange = { companyNameEn = it },
                        label = { Text(strings.companyNameEn) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_company_name_en"),
                    )
                    OutlinedTextField(
                        value = companyNameMm,
                        onValueChange = { companyNameMm = it },
                        label = { Text(strings.companyNameMm) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_company_name_mm"),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = branchCode,
                            onValueChange = { branchCode = it },
                            label = { Text(strings.branchCodeLabel) },
                            singleLine = true,
                            modifier = Modifier.weight(0.42f).testTag("setup_branch_code"),
                        )
                        OutlinedTextField(
                            value = taxRateText,
                            onValueChange = { taxRateText = it },
                            label = { Text(strings.taxRateLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(0.58f),
                        )
                    }

                    OutlinedTextField(
                        value = branchNameEn,
                        onValueChange = { branchNameEn = it },
                        label = { Text(strings.branchNameEn) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_branch_name_en"),
                    )
                    OutlinedTextField(
                        value = branchNameMm,
                        onValueChange = { branchNameMm = it },
                        label = { Text(strings.branchNameMm) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_branch_name_mm"),
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(strings.phoneLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_phone"),
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(strings.addressLabel) },
                        modifier = Modifier.fillMaxWidth().testTag("setup_address"),
                    )
                    OutlinedTextField(
                        value = taxId,
                        onValueChange = { taxId = it },
                        label = { Text(strings.taxIdLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val shownError = localError ?: errorMessage
                    if (shownError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = shownError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (companyNameEn.isBlank() || branchNameEn.isBlank() || branchCode.isBlank()) {
                                localError = strings.validationRequiredError
                                return@Button
                            }
                            localError = null
                            val effectiveCompanyMm = companyNameMm.ifBlank { companyNameEn }
                            val effectiveBranchMm = branchNameMm.ifBlank { branchNameEn }
                            val rate = taxRateText.toDoubleOrNull() ?: 5.0
                            onCreateCompany(
                                companyNameEn,
                                effectiveCompanyMm,
                                branchNameEn,
                                effectiveBranchMm,
                                branchCode,
                                phone.ifBlank { "-" },
                                address.ifBlank { "-" },
                                taxId.takeIf { it.isNotBlank() },
                                rate,
                            )
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("create_company_submit_button"),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(strings.createCompanyAction, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
