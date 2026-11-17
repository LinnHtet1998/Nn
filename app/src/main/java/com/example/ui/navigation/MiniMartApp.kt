package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.connectivity.NetworkMonitor
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.BilingualStrings
import com.example.core.preferences.AppPreferencesStore
import com.example.core.preferences.ThemeMode
import com.example.core.rbac.AccountStatus
import com.example.core.rbac.Permission
import com.example.core.rbac.RolePermissionMatrix
import com.example.core.rbac.UserRole
import com.example.data.repository.AuthRepository
import com.example.data.repository.CompanyRepository
import com.example.data.repository.SubscriptionRepository
import com.example.ui.auth.CompanySetupScreen
import com.example.ui.auth.SignInScreen
import com.example.ui.components.AdaptiveEnterpriseShell
import com.example.ui.screens.BranchesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TeamRolesScreen
import com.example.ui.subscription.AccessGateStatusScreen
import com.example.ui.subscription.BusinessRegistrationScreen
import com.example.ui.subscription.BusinessSubscriptionScreen
import com.example.ui.subscription.PlatformAdminScreen
import com.example.ui.theme.MiniMartProTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun MiniMartApp(
    authRepository: AuthRepository = remember { AuthRepository() },
) {
    val context = LocalContext.current
    val preferencesStore = remember(context) { AppPreferencesStore(context.applicationContext) }
    val language by preferencesStore.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.MYANMAR)
    val themeMode by preferencesStore.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val strings = remember(language) { BilingualStrings.get(language) }
    val scope = rememberCoroutineScope()

    val currentUser by authRepository.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

    LaunchedEffect(Unit) {
        if (authRepository.currentUser == null) {
            authRepository.attemptSilentSignIn(context)
        }
    }

    MiniMartProTheme(themeMode = themeMode) {
        val user = currentUser
        if (user == null) {
            SignInScreen(
                language = language,
                strings = strings,
                authRepository = authRepository,
                onLanguageSelected = { newLang ->
                    scope.launch { preferencesStore.setLanguage(newLang) }
                },
                onSignInSuccess = {},
            )
        } else {
            val uid = user.uid
            val email = user.email.orEmpty()
            key(uid) {
                val viewModel: MiniMartViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val firestore = FirebaseFirestore.getInstance(databaseId)
                            MiniMartViewModel(
                                companyRepository = CompanyRepository(firestore),
                                subscriptionRepository = SubscriptionRepository(firestore),
                                preferencesStore = AppPreferencesStore(app),
                                networkMonitor = NetworkMonitor(app),
                                currentUserId = uid,
                                currentUserEmail = email,
                            )
                        }
                    },
                )

                AuthenticatedRoot(
                    viewModel = viewModel,
                    userEmail = email,
                    userDisplayName = user.displayName ?: (email.substringBefore("@").ifBlank { "User" }),
                    language = language,
                    themeMode = themeMode,
                    onSignOut = {
                        scope.launch { authRepository.signOut(context) }
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedRoot(
    viewModel: MiniMartViewModel,
    userEmail: String,
    userDisplayName: String,
    language: AppLanguage,
    themeMode: ThemeMode,
    onSignOut: () -> Unit,
) {
    val orgState by viewModel.orgState.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.actionSubmitting.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val strings = remember(language) { BilingualStrings.get(language) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback, actionError) {
        val msg = feedback ?: actionError
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { _ ->
        when (val state = orgState) {
            OrgUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is OrgUiState.PlatformAdminWorkspace -> {
                val actorName = state.userProfile.displayName.ifBlank { userDisplayName }
                PlatformAdminScreen(
                    companies = state.companies,
                    payments = state.payments,
                    plans = state.plans,
                    paymentMethods = state.paymentMethods,
                    platformAuditLogs = state.platformAuditLogs,
                    trustedServerNowMillis = state.trustedServerNowMillis,
                    language = language,
                    strings = strings,
                    showStandaloneHeader = true,
                    onSeedStarterPlans = {
                        viewModel.seedStarterPlans(actorName, strings.savedSuccessMessage)
                    },
                    onUpsertPlan = { existing, nEn, nMm, dEn, dMm, months, days, price, discount, promo, maxB, maxE, features, active, sort ->
                        viewModel.upsertPlan(
                            existingPlan = existing,
                            actorName = actorName,
                            nameEn = nEn,
                            nameMm = nMm,
                            descEn = dEn,
                            descMm = dMm,
                            durationMonths = months,
                            durationDays = days,
                            priceMmk = price,
                            discountPercent = discount,
                            promoLabel = promo,
                            maxBranches = maxB,
                            maxEmployees = maxE,
                            enabledFeatures = features,
                            isActive = active,
                            sortOrder = sort,
                            successMessage = strings.savedSuccessMessage,
                        )
                    },
                    onDeletePlan = { plan ->
                        viewModel.deletePlan(plan, actorName, strings.savedSuccessMessage)
                    },
                    onUpsertPaymentMethod = { existing, nEn, nMm, pType, accTitle, accNum, insEn, insMm, endpoint, active ->
                        viewModel.upsertPaymentMethod(
                            existingMethod = existing,
                            actorName = actorName,
                            nameEn = nEn,
                            nameMm = nMm,
                            providerType = pType,
                            accountTitle = accTitle,
                            accountNumber = accNum,
                            instructionsEn = insEn,
                            instructionsMm = insMm,
                            gatewayEndpoint = endpoint,
                            isActive = active,
                            successMessage = strings.savedSuccessMessage,
                        )
                    },
                    onApprovePayment = { payment, company, plan ->
                        viewModel.approvePayment(payment, company, plan, actorName, strings.savedSuccessMessage)
                    },
                    onRejectPayment = { payment, company, reason ->
                        viewModel.rejectPayment(payment, company, reason, actorName, strings.savedSuccessMessage)
                    },
                    onUpdateBusinessStatus = { company, newStatus ->
                        viewModel.updateBusinessStatus(company, newStatus, actorName, strings.savedSuccessMessage)
                    },
                    onSignOut = onSignOut,
                    onLaunchOwnCompanySetup = { viewModel.openOwnCompanySetup() },
                )
            }

            OrgUiState.NeedsCompanySetup -> {
                BackHandler { viewModel.closeOwnCompanySetup() }
                CompanySetupScreen(
                    strings = strings,
                    language = language,
                    userEmail = userEmail,
                    isSubmitting = isSubmitting,
                    errorMessage = actionError,
                    onCreateCompany = { compEn, compMm, brEn, brMm, code, phone, addr, taxId, rate ->
                        viewModel.createOwnCompany(
                            companyNameEn = compEn,
                            companyNameMm = compMm,
                            branchNameEn = brEn,
                            branchNameMm = brMm,
                            branchCode = code,
                            phone = phone,
                            address = addr,
                            taxId = taxId,
                            taxRatePercent = rate,
                            defaultLanguage = language.code,
                        )
                    },
                    onSignOut = onSignOut,
                )
            }

            is OrgUiState.NotAuthorizedOrNewBusiness -> {
                BusinessRegistrationScreen(
                    userEmail = userEmail,
                    userDisplayName = userDisplayName,
                    plans = state.plans,
                    paymentMethods = state.paymentMethods,
                    isSubmitting = isSubmitting,
                    errorMessage = actionError,
                    language = language,
                    strings = strings,
                    onRefreshCheck = { viewModel.refreshServerClockAndCheck() },
                    onRegisterBusiness = { bizEn, bizMm, ownerName, phone, addr, plan, method, ref, dateTxt, proof ->
                        viewModel.registerCustomerBusiness(
                            businessNameEn = bizEn,
                            businessNameMm = bizMm,
                            ownerName = ownerName,
                            phone = phone,
                            address = addr,
                            plan = plan,
                            method = method,
                            transactionRef = ref,
                            paymentDateText = dateTxt,
                            proofBase64 = proof,
                            defaultLanguage = language.code,
                            successMessage = strings.savedSuccessMessage,
                        )
                    },
                    onSignOut = onSignOut,
                )
            }

            is OrgUiState.PendingApproval -> {
                AccessGateStatusScreen(
                    icon = Icons.Filled.HourglassTop,
                    title = strings.pendingApprovalTitle,
                    body = strings.pendingApprovalBody,
                    badgeText = "${state.entry.employeeId} • ${AccountStatus.PENDING.label(language)}",
                    userEmail = state.entry.email.ifBlank { userEmail },
                    strings = strings,
                    testTagValue = "pending_approval_gate",
                    onRefresh = { viewModel.refreshServerClockAndCheck() },
                    onSignOut = onSignOut,
                )
            }

            is OrgUiState.AccountDisabled -> {
                val status = AccountStatus.fromKey(state.entry.status)
                AccessGateStatusScreen(
                    icon = Icons.Filled.Block,
                    title = strings.accountDisabledTitle,
                    body = strings.accountDisabledBody,
                    badgeText = "${state.entry.employeeId} • ${status.label(language)}",
                    userEmail = state.entry.email.ifBlank { userEmail },
                    strings = strings,
                    testTagValue = "account_disabled_gate",
                    onRefresh = { viewModel.refreshServerClockAndCheck() },
                    onSignOut = onSignOut,
                )
            }

            is OrgUiState.SubscriptionLockedOwner -> {
                BusinessSubscriptionScreen(
                    company = state.company,
                    plans = state.plans,
                    paymentMethods = state.paymentMethods,
                    payments = state.payments,
                    trustedServerNowMillis = state.trustedServerNowMillis,
                    isLockedGateMode = true,
                    language = language,
                    strings = strings,
                    onSubmitRenewalPayment = { plan, method, ref, dateTxt, proof ->
                        viewModel.submitRenewalPayment(
                            company = state.company,
                            plan = plan,
                            method = method,
                            transactionRef = ref,
                            paymentDateText = dateTxt,
                            proofBase64 = proof,
                            successMessage = strings.savedSuccessMessage,
                        )
                    },
                    onRefreshServerClock = { viewModel.refreshServerClockAndCheck() },
                    onSignOut = onSignOut,
                )
            }

            is OrgUiState.SubscriptionLockedEmployee -> {
                AccessGateStatusScreen(
                    icon = Icons.Filled.LockClock,
                    title = strings.subscriptionBlockedEmployeeTitle,
                    body = strings.subscriptionBlockedEmployeeBody,
                    badgeText = "${state.company.localizedName(language == AppLanguage.MYANMAR)} • ${state.effectiveStatus.label(language)}",
                    userEmail = userEmail,
                    strings = strings,
                    testTagValue = "subscription_locked_employee_gate",
                    onRefresh = { viewModel.refreshServerClockAndCheck() },
                    onSignOut = onSignOut,
                )
            }

            is OrgUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onSignOut) {
                            Text(strings.signOut)
                        }
                    }
                }
            }

            is OrgUiState.Ready -> {
                val role = UserRole.fromKey(
                    state.currentMember?.role
                        ?: if (state.company.ownerUid == state.userProfile.userId || state.isPlatformSuperAdmin) {
                            UserRole.SUPER_ADMIN.key
                        } else {
                            UserRole.CASHIER.key
                        }
                )
                val accessibleDestinations = remember(role, state.isPlatformSuperAdmin) {
                    AppDestination.accessibleFor(role, isPlatformSuperAdmin = state.isPlatformSuperAdmin)
                }
                var currentDestination by remember(role, state.isPlatformSuperAdmin) {
                    mutableStateOf(
                        if (state.isPlatformSuperAdmin && accessibleDestinations.contains(AppDestination.PLATFORM_ADMIN)) {
                            AppDestination.PLATFORM_ADMIN
                        } else {
                            AppDestination.DASHBOARD
                        }
                    )
                }
                if (!accessibleDestinations.contains(currentDestination)) {
                    currentDestination = accessibleDestinations.firstOrNull() ?: AppDestination.DASHBOARD
                }

                val rootDestination = if (state.isPlatformSuperAdmin) {
                    AppDestination.PLATFORM_ADMIN
                } else {
                    AppDestination.DASHBOARD
                }
                if (currentDestination != rootDestination) {
                    BackHandler { currentDestination = rootDestination }
                }

                val canSwitchBranches = state.currentMember?.allBranchesAccess == true ||
                    RolePermissionMatrix.hasPermission(role, Permission.VIEW_COMPANY_OVERVIEW) ||
                    state.branches.size > 1
                val unreadCount = state.notifications.count { !it.isRead }
                val actorName = state.userProfile.displayName.ifBlank { userDisplayName }

                AdaptiveEnterpriseShell(
                    company = state.company,
                    branches = state.branches,
                    selectedBranch = state.selectedBranch,
                    canSwitchBranches = canSwitchBranches,
                    userRole = role,
                    userName = actorName,
                    language = language,
                    strings = strings,
                    isOnline = isOnline,
                    unreadNotificationsCount = unreadCount,
                    destinations = accessibleDestinations,
                    currentDestination = currentDestination,
                    onSelectDestination = { currentDestination = it },
                    onSelectBranch = { viewModel.selectBranch(it) },
                    onToggleLanguage = {
                        val nextLang = if (language == AppLanguage.MYANMAR) AppLanguage.ENGLISH else AppLanguage.MYANMAR
                        viewModel.setLanguage(nextLang, themeMode)
                    },
                ) {
                    when (currentDestination) {
                        AppDestination.DASHBOARD -> DashboardScreen(
                            branches = state.branches,
                            selectedBranch = state.selectedBranch,
                            userRole = role,
                            language = language,
                            strings = strings,
                        )

                        AppDestination.BRANCHES -> BranchesScreen(
                            branches = state.branches,
                            accounts = state.accounts,
                            selectedBranch = state.selectedBranch,
                            userRole = role,
                            language = language,
                            strings = strings,
                            onSelectBranch = { viewModel.selectBranch(it) },
                            onCreateBranch = { code, nameEn, nameMm, addr, phone, mgrUid, mgrName, isHq ->
                                viewModel.createBranch(
                                    ready = state,
                                    code = code,
                                    nameEn = nameEn,
                                    nameMm = nameMm,
                                    address = addr,
                                    phone = phone,
                                    managerUid = mgrUid,
                                    managerName = mgrName,
                                    isHeadOffice = isHq,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                            onUpdateBranch = { branch, code, nameEn, nameMm, addr, phone, mgrUid, mgrName, isHq, isActive ->
                                val isAdmin = RolePermissionMatrix.hasPermission(role, Permission.MANAGE_BRANCHES)
                                viewModel.updateBranch(
                                    ready = state,
                                    branch = branch,
                                    code = code,
                                    nameEn = nameEn,
                                    nameMm = nameMm,
                                    address = addr,
                                    phone = phone,
                                    managerUid = mgrUid,
                                    managerName = mgrName,
                                    isHeadOffice = isHq,
                                    isActive = isActive,
                                    isAdmin = isAdmin,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                        )

                        AppDestination.TEAM_ROLES -> TeamRolesScreen(
                            accounts = state.accounts,
                            branches = state.branches,
                            auditLogs = state.auditLogs,
                            companyOwnerUid = state.company.ownerUid,
                            userRole = role,
                            language = language,
                            strings = strings,
                            onUpsertAccount = { existing, empId, name, email, phone, targetRole, status, allBranches, branchIds ->
                                viewModel.upsertEmployeeAccount(
                                    ready = state,
                                    existingEntry = existing,
                                    employeeId = empId,
                                    displayName = name,
                                    email = email,
                                    phone = phone,
                                    role = targetRole,
                                    status = status,
                                    allBranchesAccess = allBranches,
                                    assignedBranchIds = branchIds,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                            onChangeAccountStatus = { entry, newStatus ->
                                viewModel.changeEmployeeAccountStatus(
                                    ready = state,
                                    entry = entry,
                                    newStatus = newStatus,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                        )

                        AppDestination.SUBSCRIPTION -> BusinessSubscriptionScreen(
                            company = state.company,
                            plans = state.plans,
                            paymentMethods = state.paymentMethods,
                            payments = state.companyPayments,
                            trustedServerNowMillis = state.trustedServerNowMillis,
                            isLockedGateMode = false,
                            language = language,
                            strings = strings,
                            onSubmitRenewalPayment = { plan, method, ref, dateTxt, proof ->
                                viewModel.submitRenewalPayment(
                                    company = state.company,
                                    plan = plan,
                                    method = method,
                                    transactionRef = ref,
                                    paymentDateText = dateTxt,
                                    proofBase64 = proof,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                            onRefreshServerClock = { viewModel.refreshServerClockAndCheck() },
                            onSignOut = null,
                        )

                        AppDestination.PLATFORM_ADMIN -> PlatformAdminScreen(
                            companies = state.allCompanies,
                            payments = state.allPayments,
                            plans = state.plans,
                            paymentMethods = state.paymentMethods,
                            platformAuditLogs = state.platformAuditLogs,
                            trustedServerNowMillis = state.trustedServerNowMillis,
                            language = language,
                            strings = strings,
                            showStandaloneHeader = false,
                            onSeedStarterPlans = {
                                viewModel.seedStarterPlans(actorName, strings.savedSuccessMessage)
                            },
                            onUpsertPlan = { existing, nEn, nMm, dEn, dMm, months, days, price, discount, promo, maxB, maxE, features, active, sort ->
                                viewModel.upsertPlan(
                                    existingPlan = existing,
                                    actorName = actorName,
                                    nameEn = nEn,
                                    nameMm = nMm,
                                    descEn = dEn,
                                    descMm = dMm,
                                    durationMonths = months,
                                    durationDays = days,
                                    priceMmk = price,
                                    discountPercent = discount,
                                    promoLabel = promo,
                                    maxBranches = maxB,
                                    maxEmployees = maxE,
                                    enabledFeatures = features,
                                    isActive = active,
                                    sortOrder = sort,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                            onDeletePlan = { plan ->
                                viewModel.deletePlan(plan, actorName, strings.savedSuccessMessage)
                            },
                            onUpsertPaymentMethod = { existing, nEn, nMm, pType, accTitle, accNum, insEn, insMm, endpoint, active ->
                                viewModel.upsertPaymentMethod(
                                    existingMethod = existing,
                                    actorName = actorName,
                                    nameEn = nEn,
                                    nameMm = nMm,
                                    providerType = pType,
                                    accountTitle = accTitle,
                                    accountNumber = accNum,
                                    instructionsEn = insEn,
                                    instructionsMm = insMm,
                                    gatewayEndpoint = endpoint,
                                    isActive = active,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                            onApprovePayment = { payment, company, plan ->
                                viewModel.approvePayment(payment, company, plan, actorName, strings.savedSuccessMessage)
                            },
                            onRejectPayment = { payment, company, reason ->
                                viewModel.rejectPayment(payment, company, reason, actorName, strings.savedSuccessMessage)
                            },
                            onUpdateBusinessStatus = { company, newStatus ->
                                viewModel.updateBusinessStatus(company, newStatus, actorName, strings.savedSuccessMessage)
                            },
                        )

                        AppDestination.NOTIFICATIONS -> NotificationsScreen(
                            notifications = state.notifications,
                            language = language,
                            strings = strings,
                            onMarkRead = { notifId ->
                                viewModel.markNotificationRead(state.company.id, notifId)
                            },
                        )

                        AppDestination.SETTINGS -> SettingsScreen(
                            company = state.company,
                            userEmail = state.userProfile.email.ifBlank { userEmail },
                            userName = actorName,
                            userRole = role,
                            language = language,
                            themeMode = themeMode,
                            strings = strings,
                            onLanguageSelected = { newLang -> viewModel.setLanguage(newLang, themeMode) },
                            onThemeSelected = { newTheme -> viewModel.setThemeMode(newTheme, language) },
                            onSaveCompanySettings = { nameEn, nameMm, phone, addr, taxId, rate, defLang, negStock, offLimit ->
                                viewModel.updateCompanySettings(
                                    ready = state,
                                    nameEn = nameEn,
                                    nameMm = nameMm,
                                    phone = phone,
                                    address = addr,
                                    taxId = taxId,
                                    taxRatePercent = rate,
                                    defaultLanguage = defLang,
                                    allowNegativeStock = negStock,
                                    offlineLimitMmk = offLimit,
                                    successMessage = strings.savedSuccessMessage,
                                )
                            },
                            onSignOut = onSignOut,
                        )
                    }
                }
            }
        }
    }
}
