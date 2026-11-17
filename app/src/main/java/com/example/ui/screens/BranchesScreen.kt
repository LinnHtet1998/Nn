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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.core.rbac.AccountStatus
import com.example.core.rbac.Permission
import com.example.core.rbac.RolePermissionMatrix
import com.example.core.rbac.UserRole
import com.example.domain.model.AccountDirectoryEntry
import com.example.domain.model.Branch
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.TabularMonoFontFamily

@Composable
fun BranchesScreen(
    branches: List<Branch>,
    accounts: List<AccountDirectoryEntry>,
    selectedBranch: Branch?,
    userRole: UserRole,
    language: AppLanguage,
    strings: AppStrings,
    onSelectBranch: (Branch) -> Unit,
    onCreateBranch: (
        code: String,
        nameEn: String,
        nameMm: String,
        address: String,
        phone: String,
        managerUid: String?,
        managerName: String?,
        isHeadOffice: Boolean,
    ) -> Unit,
    onUpdateBranch: (
        branch: Branch,
        code: String,
        nameEn: String,
        nameMm: String,
        address: String,
        phone: String,
        managerUid: String?,
        managerName: String?,
        isHeadOffice: Boolean,
        isActive: Boolean,
    ) -> Unit,
) {
    val canManageBranches = RolePermissionMatrix.hasPermission(userRole, Permission.MANAGE_BRANCHES)
    val canEditAssigned = RolePermissionMatrix.hasPermission(userRole, Permission.EDIT_ASSIGNED_BRANCH)

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingBranch by remember { mutableStateOf<Branch?>(null) }

    val approvedAccounts = remember(accounts) {
        accounts.filter { it.status == AccountStatus.APPROVED.key }
    }

    val filteredBranches = remember(branches, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) branches
        else branches.filter {
            it.name.lowercase().contains(q) ||
                it.nameMm.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (canManageBranches) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("create_branch_fab"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = strings.createBranch)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("branches_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = strings.branchManagementTitle,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchBranchesHint) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("search_branches_input"),
                )
            }

            items(filteredBranches, key = { it.id }) { branch ->
                val isSelected = selectedBranch?.id == branch.id
                BranchCard(
                    branch = branch,
                    isSelected = isSelected,
                    canEdit = canManageBranches || canEditAssigned,
                    canToggleStatus = canManageBranches || canEditAssigned,
                    language = language,
                    strings = strings,
                    onSelectBranch = { onSelectBranch(branch) },
                    onEdit = { editingBranch = branch },
                    onToggleActive = {
                        onUpdateBranch(
                            branch,
                            branch.code,
                            branch.name,
                            branch.nameMm,
                            branch.address,
                            branch.phone,
                            branch.managerUid,
                            branch.managerName,
                            branch.isHeadOffice,
                            !branch.isActive,
                        )
                    },
                )
            }

            if (filteredBranches.isEmpty()) {
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

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCreateDialog) {
        BranchEditorDialog(
            title = strings.createBranch,
            initialBranch = null,
            accounts = approvedAccounts,
            canEditAdminFields = canManageBranches,
            strings = strings,
            onDismiss = { showCreateDialog = false },
            onConfirm = { code, nameEn, nameMm, address, phone, mgrUid, mgrName, isHq, _ ->
                onCreateBranch(code, nameEn, nameMm, address, phone, mgrUid, mgrName, isHq)
                showCreateDialog = false
            },
        )
    }

    editingBranch?.let { target ->
        BranchEditorDialog(
            title = strings.editBranch,
            initialBranch = target,
            accounts = approvedAccounts,
            canEditAdminFields = canManageBranches,
            strings = strings,
            onDismiss = { editingBranch = null },
            onConfirm = { code, nameEn, nameMm, address, phone, mgrUid, mgrName, isHq, isActive ->
                onUpdateBranch(target, code, nameEn, nameMm, address, phone, mgrUid, mgrName, isHq, isActive)
                editingBranch = null
            },
        )
    }
}

@Composable
private fun BranchCard(
    branch: Branch,
    isSelected: Boolean,
    canEdit: Boolean,
    canToggleStatus: Boolean,
    language: AppLanguage,
    strings: AppStrings,
    onSelectBranch: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Store,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = branch.localizedName(language == AppLanguage.MYANMAR),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (branch.isHeadOffice) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = strings.headOfficeBadge,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = branch.code,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    color = (if (branch.isActive) SemanticSuccess else SemanticError).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = if (branch.isActive) strings.activeStatus else strings.inactiveStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (branch.isActive) SemanticSuccess else SemanticError,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(branch.address.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(branch.phone.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = branch.managerName?.takeIf { it.isNotBlank() } ?: strings.unassignedManager,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(strings.kpiTodaySales, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            MmkFormatter.formatMmk(branch.todaySalesMmk, language),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(strings.kpiTodayProfit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            MmkFormatter.formatMmk(branch.todayProfitMmk, language),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = SemanticSuccess,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.currentActiveBadge)
                    }
                } else {
                    Button(
                        onClick = onSelectBranch,
                        modifier = Modifier.weight(1f).testTag("switch_branch_${branch.code}"),
                    ) {
                        Text(strings.switchToBranch)
                    }
                }

                if (canToggleStatus) {
                    OutlinedButton(onClick = onToggleActive) {
                        Text(if (branch.isActive) strings.deactivateBranch else strings.activateBranch)
                    }
                }

                if (canEdit) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_branch_${branch.code}"),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = strings.editBranch)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchEditorDialog(
    title: String,
    initialBranch: Branch?,
    accounts: List<AccountDirectoryEntry>,
    canEditAdminFields: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (
        code: String,
        nameEn: String,
        nameMm: String,
        address: String,
        phone: String,
        managerUid: String?,
        managerName: String?,
        isHeadOffice: Boolean,
        isActive: Boolean,
    ) -> Unit,
) {
    var code by remember { mutableStateOf(initialBranch?.code ?: "") }
    var nameEn by remember { mutableStateOf(initialBranch?.name ?: "") }
    var nameMm by remember { mutableStateOf(initialBranch?.nameMm ?: "") }
    var address by remember { mutableStateOf(initialBranch?.address ?: "") }
    var phone by remember { mutableStateOf(initialBranch?.phone ?: "") }
    var selectedManagerUid by remember { mutableStateOf(initialBranch?.managerUid) }
    var selectedManagerName by remember { mutableStateOf(initialBranch?.managerName) }
    var isHeadOffice by remember { mutableStateOf(initialBranch?.isHeadOffice ?: false) }
    var isActive by remember { mutableStateOf(initialBranch?.isActive ?: true) }
    var managerExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(strings.branchCodeLabel) },
                    enabled = canEditAdminFields,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_branch_code"),
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(strings.branchNameEn) },
                    enabled = canEditAdminFields,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_branch_name_en"),
                )
                OutlinedTextField(
                    value = nameMm,
                    onValueChange = { nameMm = it },
                    label = { Text(strings.branchNameMm) },
                    enabled = canEditAdminFields,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_branch_name_mm"),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(strings.phoneLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_branch_phone"),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(strings.addressLabel) },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_branch_address"),
                )

                if (canEditAdminFields && accounts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = managerExpanded,
                        onExpandedChange = { managerExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedManagerName?.takeIf { it.isNotBlank() } ?: strings.unassignedManager,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(strings.assignManager) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = managerExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = managerExpanded,
                            onDismissRequest = { managerExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.unassignedManager) },
                                onClick = {
                                    selectedManagerUid = null
                                    selectedManagerName = null
                                    managerExpanded = false
                                },
                            )
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text("${account.displayName} (${account.role})") },
                                    onClick = {
                                        selectedManagerUid = account.linkedUid?.takeIf { it.isNotBlank() }
                                        selectedManagerName = account.displayName
                                        managerExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isHeadOffice, onCheckedChange = { isHeadOffice = it })
                        Text(strings.headOfficeBadge)
                    }
                }

                if (initialBranch != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.activeStatus)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }

                if (errorText != null) {
                    Text(errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isBlank() || nameEn.isBlank()) {
                        errorText = strings.validationRequiredError
                        return@Button
                    }
                    onConfirm(
                        code,
                        nameEn,
                        nameMm.ifBlank { nameEn },
                        address.ifBlank { "-" },
                        phone.ifBlank { "-" },
                        selectedManagerUid,
                        selectedManagerName,
                        isHeadOffice,
                        isActive,
                    )
                },
                modifier = Modifier.testTag("dialog_branch_save"),
            ) {
                Text(strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}
