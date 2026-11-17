package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.domain.model.AuditLog
import com.example.domain.model.Branch
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning
import com.example.ui.theme.TabularMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamRolesScreen(
    accounts: List<AccountDirectoryEntry>,
    branches: List<Branch>,
    auditLogs: List<AuditLog>,
    companyOwnerUid: String,
    userRole: UserRole,
    language: AppLanguage,
    strings: AppStrings,
    onUpsertAccount: (
        existingEntry: AccountDirectoryEntry?,
        employeeId: String,
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        status: AccountStatus,
        allBranchesAccess: Boolean,
        assignedBranchIds: List<String>,
    ) -> Unit,
    onChangeAccountStatus: (
        entry: AccountDirectoryEntry,
        newStatus: AccountStatus,
    ) -> Unit,
) {
    val canManageUsers = RolePermissionMatrix.hasPermission(userRole, Permission.MANAGE_USERS)
    val canViewAuditLogs = RolePermissionMatrix.hasPermission(userRole, Permission.VIEW_AUDIT_LOGS)

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedStatusFilter by remember { mutableStateOf<AccountStatus?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountDirectoryEntry?>(null) }

    val tabs = buildList {
        add(strings.teamMembersTab)
        add(strings.permissionMatrixTab)
        if (canViewAuditLogs) add(strings.auditLogsTab)
    }

    val filteredAccounts = remember(accounts, selectedStatusFilter) {
        if (selectedStatusFilter == null) accounts
        else accounts.filter { AccountStatus.fromKey(it.status) == selectedStatusFilter }
    }

    val branchLookup = remember(branches) {
        branches.associateBy { it.id }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTab == 0 && canManageUsers) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_member_fab"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = strings.addTeamMember)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("team_roles_screen"),
        ) {
            TabRow(selectedTabIndex = selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selectedStatusFilter == null,
                                    onClick = { selectedStatusFilter = null },
                                    label = { Text("${strings.filterAllAccounts} (${accounts.size})") },
                                    modifier = Modifier.testTag("filter_accounts_all"),
                                )
                                AccountStatus.entries.forEach { st ->
                                    val count = accounts.count { AccountStatus.fromKey(it.status) == st }
                                    FilterChip(
                                        selected = selectedStatusFilter == st,
                                        onClick = { selectedStatusFilter = st },
                                        label = { Text("${st.label(language)} ($count)") },
                                        modifier = Modifier.testTag("filter_accounts_${st.key.lowercase()}"),
                                    )
                                }
                            }
                        }

                        items(filteredAccounts, key = { it.id.ifBlank { it.email } }) { entry ->
                            val entryRole = UserRole.fromKey(entry.role)
                            val entryStatus = AccountStatus.fromKey(entry.status)
                            val isProtectedOwner = entry.linkedUid == companyOwnerUid || entryRole == UserRole.SUPER_ADMIN
                            val statusColor = when (entryStatus) {
                                AccountStatus.APPROVED -> SemanticSuccess
                                AccountStatus.PENDING -> SemanticWarning
                                AccountStatus.SUSPENDED -> MaterialTheme.colorScheme.tertiary
                                AccountStatus.REVOKED -> SemanticError
                            }
                            val branchText = if (entry.allBranchesAccess) {
                                strings.headOfficeAllBranches
                            } else {
                                entry.assignedBranchIds
                                    .mapNotNull { branchLookup[it]?.code }
                                    .joinToString(", ")
                                    .ifBlank { "-" }
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.testTag("account_card_${entry.employeeId}"),
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(10.dp).size(20.dp),
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        text = entry.displayName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    Text(
                                                        text = entry.employeeId,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontFamily = TabularMonoFontFamily,
                                                        ),
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Text(
                                                    text = buildString {
                                                        append(entry.email)
                                                        if (entry.phone.isNotBlank()) append(" • ${entry.phone}")
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    text = branchText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }

                                        if (canManageUsers && !isProtectedOwner) {
                                            IconButton(
                                                onClick = { editingAccount = entry },
                                                modifier = Modifier.testTag("edit_account_${entry.employeeId}"),
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = strings.editTeamMember)
                                            }
                                        }
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = entryRole.label(language),
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                        Surface(
                                            color = statusColor.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = entryStatus.label(language),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = statusColor,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }

                                    if (canManageUsers && !isProtectedOwner) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            when (entryStatus) {
                                                AccountStatus.PENDING -> {
                                                    FilledTonalButton(
                                                        onClick = { onChangeAccountStatus(entry, AccountStatus.APPROVED) },
                                                        modifier = Modifier.weight(1f).testTag("approve_account_${entry.employeeId}"),
                                                    ) {
                                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(strings.approveAction)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { onChangeAccountStatus(entry, AccountStatus.REVOKED) },
                                                        modifier = Modifier.weight(1f).testTag("reject_account_${entry.employeeId}"),
                                                    ) {
                                                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(strings.rejectAction)
                                                    }
                                                }
                                                AccountStatus.APPROVED -> {
                                                    OutlinedButton(
                                                        onClick = { onChangeAccountStatus(entry, AccountStatus.SUSPENDED) },
                                                        modifier = Modifier.weight(1f).testTag("suspend_account_${entry.employeeId}"),
                                                    ) {
                                                        Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(strings.suspendAction)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { onChangeAccountStatus(entry, AccountStatus.REVOKED) },
                                                        modifier = Modifier.weight(1f).testTag("revoke_account_${entry.employeeId}"),
                                                    ) {
                                                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(strings.revokeAction)
                                                    }
                                                }
                                                AccountStatus.SUSPENDED, AccountStatus.REVOKED -> {
                                                    Button(
                                                        onClick = { onChangeAccountStatus(entry, AccountStatus.APPROVED) },
                                                        modifier = Modifier.weight(1f).testTag("reactivate_account_${entry.employeeId}"),
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

                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }

                1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(UserRole.entries) { role ->
                            val perms = RolePermissionMatrix.permissionsFor(role)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "${role.label(language)} (${role.key})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        perms.forEach { perm ->
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(perm.name, style = MaterialTheme.typography.labelMedium) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (auditLogs.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                ) {
                                    Text(
                                        text = strings.noAuditLogsYet,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    )
                                }
                            }
                        } else {
                            items(auditLogs, key = { it.id }) { log ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Icon(
                                            Icons.Filled.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp).size(18.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "${log.action} • ${log.actorName}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = log.details,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = MmkFormatter.formatDateTime(log.createdAt?.toDate()?.time),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val nextIndex = accounts.size + 1
        AccountEditorDialog(
            title = strings.addTeamMember,
            initialEntry = null,
            suggestedEmployeeId = "EMP-${nextIndex.toString().padStart(3, '0')}",
            branches = branches,
            language = language,
            strings = strings,
            onDismiss = { showAddDialog = false },
            onConfirm = { empId, name, email, phone, role, status, allBranches, branchIds ->
                onUpsertAccount(null, empId, name, email, phone, role, status, allBranches, branchIds)
                showAddDialog = false
            },
        )
    }

    editingAccount?.let { entry ->
        AccountEditorDialog(
            title = strings.editTeamMember,
            initialEntry = entry,
            suggestedEmployeeId = entry.employeeId,
            branches = branches,
            language = language,
            strings = strings,
            onDismiss = { editingAccount = null },
            onConfirm = { empId, name, email, phone, role, status, allBranches, branchIds ->
                onUpsertAccount(entry, empId, name, email, phone, role, status, allBranches, branchIds)
                editingAccount = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorDialog(
    title: String,
    initialEntry: AccountDirectoryEntry?,
    suggestedEmployeeId: String,
    branches: List<Branch>,
    language: AppLanguage,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (
        employeeId: String,
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        status: AccountStatus,
        allBranchesAccess: Boolean,
        assignedBranchIds: List<String>,
    ) -> Unit,
) {
    var employeeId by remember { mutableStateOf(initialEntry?.employeeId?.ifBlank { suggestedEmployeeId } ?: suggestedEmployeeId) }
    var displayName by remember { mutableStateOf(initialEntry?.displayName ?: "") }
    var email by remember { mutableStateOf(initialEntry?.email ?: "") }
    var phone by remember { mutableStateOf(initialEntry?.phone ?: "") }
    var selectedRole by remember { mutableStateOf(UserRole.fromKey(initialEntry?.role ?: UserRole.CASHIER.key)) }
    var selectedStatus by remember { mutableStateOf(AccountStatus.fromKey(initialEntry?.status ?: AccountStatus.PENDING.key)) }
    var allBranchesAccess by remember { mutableStateOf(initialEntry?.allBranchesAccess ?: false) }
    val selectedBranchIds = remember {
        mutableStateListOf<String>().apply {
            addAll(initialEntry?.assignedBranchIds ?: branches.firstOrNull()?.let { listOf(it.id) }.orEmpty())
        }
    }
    var roleExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val assignableRoles = remember {
        UserRole.entries.filterNot { it == UserRole.SUPER_ADMIN }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = employeeId,
                    onValueChange = { employeeId = it },
                    label = { Text(strings.employeeIdLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_member_emp_id"),
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(strings.employeeNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_member_name"),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(strings.employeeEmailLabel) },
                    enabled = initialEntry == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_member_email"),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(strings.phoneLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_member_phone"),
                )

                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedRole.label(language),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.roleLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false },
                    ) {
                        assignableRoles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text("${role.label(language)} (${role.key})") },
                                onClick = {
                                    selectedRole = role
                                    if (role == UserRole.GENERAL_MANAGER) {
                                        allBranchesAccess = true
                                    }
                                    roleExpanded = false
                                },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedStatus.label(language),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.statusLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                    ) {
                        AccountStatus.entries.forEach { st ->
                            DropdownMenuItem(
                                text = { Text("${st.label(language)} (${st.key})") },
                                onClick = {
                                    selectedStatus = st
                                    statusExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.allBranchesAccessLabel, modifier = Modifier.weight(1f))
                    Switch(checked = allBranchesAccess, onCheckedChange = { allBranchesAccess = it })
                }

                if (!allBranchesAccess && branches.isNotEmpty()) {
                    Text(strings.assignedBranchesLabel, style = MaterialTheme.typography.labelLarge)
                    branches.forEach { branch ->
                        val checked = selectedBranchIds.contains(branch.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!selectedBranchIds.contains(branch.id)) selectedBranchIds.add(branch.id)
                                    } else {
                                        selectedBranchIds.remove(branch.id)
                                    }
                                },
                            )
                            Text("${branch.code} • ${branch.localizedName(language == AppLanguage.MYANMAR)}")
                        }
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
                    if (employeeId.isBlank() || displayName.isBlank() || email.isBlank() || !email.contains("@")) {
                        errorText = strings.validationRequiredError
                        return@Button
                    }
                    onConfirm(
                        employeeId.trim().uppercase(),
                        displayName.trim(),
                        email.trim().lowercase(),
                        phone.trim(),
                        selectedRole,
                        selectedStatus,
                        allBranchesAccess,
                        if (allBranchesAccess) emptyList() else selectedBranchIds.toList(),
                    )
                },
                modifier = Modifier.testTag("dialog_member_save"),
            ) {
                Text(strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}
