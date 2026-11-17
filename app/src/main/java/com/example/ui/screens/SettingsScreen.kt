package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.preferences.ThemeMode
import com.example.core.rbac.Permission
import com.example.core.rbac.RolePermissionMatrix
import com.example.core.rbac.UserRole
import com.example.domain.model.Company

@Composable
fun SettingsScreen(
    company: Company,
    userEmail: String,
    userName: String,
    userRole: UserRole,
    language: AppLanguage,
    themeMode: ThemeMode,
    strings: AppStrings,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onSaveCompanySettings: (
        nameEn: String,
        nameMm: String,
        phone: String,
        address: String,
        taxId: String?,
        taxRatePercent: Double,
        defaultLanguage: String,
        allowNegativeStock: Boolean,
        offlineLimitMmk: Long,
    ) -> Unit,
    onSignOut: () -> Unit,
) {
    val canManageSettings = RolePermissionMatrix.hasPermission(userRole, Permission.MANAGE_SETTINGS)

    var nameEn by remember(company.id, company.name) { mutableStateOf(company.name) }
    var nameMm by remember(company.id, company.nameMm) { mutableStateOf(company.nameMm) }
    var phone by remember(company.id, company.phone) { mutableStateOf(company.phone) }
    var address by remember(company.id, company.address) { mutableStateOf(company.address) }
    var taxId by remember(company.id, company.taxId) { mutableStateOf(company.taxId ?: "") }
    var taxRateText by remember(company.id, company.taxRatePercent) { mutableStateOf(company.taxRatePercent.toString()) }
    var allowNegativeStock by remember(company.id, company.allowNegativeStock) { mutableStateOf(company.allowNegativeStock) }
    var offlineLimitText by remember(company.id, company.offlineLimitMmk) { mutableStateOf(company.offlineLimitMmk.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(strings.settingsTitle, style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(strings.localizationThemeSection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Text(strings.languageSettingLabel, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { onLanguageSelected(lang) },
                                label = { Text(lang.nativeLabel) },
                                modifier = Modifier.testTag("settings_lang_${lang.code}"),
                            )
                        }
                    }

                    Text(strings.themeSettingLabel, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to strings.themeSystem,
                            ThemeMode.LIGHT to strings.themeLight,
                            ThemeMode.DARK to strings.themeDark,
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeSelected(mode) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("settings_theme_${mode.key}"),
                            )
                        }
                    }
                }
            }
        }

        if (canManageSettings) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(strings.companyProfileSection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = nameEn,
                            onValueChange = { nameEn = it },
                            label = { Text(strings.companyNameEn) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = nameMm,
                            onValueChange = { nameMm = it },
                            label = { Text(strings.companyNameMm) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(strings.phoneLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(strings.addressLabel) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = taxId,
                                onValueChange = { taxId = it },
                                label = { Text(strings.taxIdLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = taxRateText,
                                onValueChange = { taxRateText = it },
                                label = { Text(strings.taxRateLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(0.6f),
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(strings.posPolicySection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.allowNegativeStockLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = allowNegativeStock,
                                onCheckedChange = { allowNegativeStock = it },
                            )
                        }

                        OutlinedTextField(
                            value = offlineLimitText,
                            onValueChange = { offlineLimitText = it },
                            label = { Text(strings.offlineCashLimitLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            onClick = {
                                if (nameEn.isNotBlank()) {
                                    onSaveCompanySettings(
                                        nameEn,
                                        nameMm.ifBlank { nameEn },
                                        phone.ifBlank { "-" },
                                        address.ifBlank { "-" },
                                        taxId.takeIf { it.isNotBlank() },
                                        taxRateText.toDoubleOrNull() ?: company.taxRatePercent,
                                        language.code,
                                        allowNegativeStock,
                                        offlineLimitText.toLongOrNull() ?: company.offlineLimitMmk,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_company_settings_button"),
                        ) {
                            Text(strings.saveAction)
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(strings.accountSection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(userName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${strings.roleBadgePrefix}: ${userRole.label(language)}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOut)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.signOut)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
