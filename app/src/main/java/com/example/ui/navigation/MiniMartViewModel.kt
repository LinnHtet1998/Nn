package com.example.ui.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.connectivity.NetworkMonitor
import com.example.core.firebase.unwrapFirestoreException
import com.example.core.i18n.AppLanguage
import com.example.core.preferences.AppPreferencesStore
import com.example.core.preferences.ThemeMode
import com.example.core.rbac.AccountStatus
import com.example.core.rbac.Permission
import com.example.core.rbac.RolePermissionMatrix
import com.example.core.rbac.UserRole
import com.example.core.subscription.SubscriptionPolicy
import com.example.data.repository.CompanyRepository
import com.example.data.repository.SubscriptionRepository
import com.example.domain.model.AccountDirectoryEntry
import com.example.domain.model.AppNotification
import com.example.domain.model.AuditLog
import com.example.domain.model.Branch
import com.example.domain.model.Company
import com.example.domain.model.CompanyMember
import com.example.domain.model.PaymentMethodConfig
import com.example.domain.model.PaymentMethodProviderType
import com.example.domain.model.PlatformAuditLog
import com.example.domain.model.SubscriptionPayment
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.domain.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OrgUiState {
    data object Loading : OrgUiState

    data class PlatformAdminWorkspace(
        val userProfile: UserProfile,
        val companies: List<Company>,
        val payments: List<SubscriptionPayment>,
        val plans: List<SubscriptionPlan>,
        val paymentMethods: List<PaymentMethodConfig>,
        val platformAuditLogs: List<PlatformAuditLog>,
        val trustedServerNowMillis: Long,
    ) : OrgUiState

    data object NeedsCompanySetup : OrgUiState

    data class NotAuthorizedOrNewBusiness(
        val plans: List<SubscriptionPlan>,
        val paymentMethods: List<PaymentMethodConfig>,
    ) : OrgUiState

    data class PendingApproval(
        val entry: AccountDirectoryEntry,
    ) : OrgUiState

    data class AccountDisabled(
        val entry: AccountDirectoryEntry,
    ) : OrgUiState

    data class SubscriptionLockedOwner(
        val company: Company,
        val plans: List<SubscriptionPlan>,
        val paymentMethods: List<PaymentMethodConfig>,
        val payments: List<SubscriptionPayment>,
        val trustedServerNowMillis: Long,
    ) : OrgUiState

    data class SubscriptionLockedEmployee(
        val company: Company,
        val effectiveStatus: SubscriptionStatus,
    ) : OrgUiState

    data class Ready(
        val isPlatformSuperAdmin: Boolean,
        val userProfile: UserProfile,
        val company: Company,
        val currentMember: CompanyMember?,
        val branches: List<Branch>,
        val selectedBranch: Branch?,
        val accounts: List<AccountDirectoryEntry>,
        val auditLogs: List<AuditLog>,
        val notifications: List<AppNotification>,
        val plans: List<SubscriptionPlan>,
        val paymentMethods: List<PaymentMethodConfig>,
        val companyPayments: List<SubscriptionPayment>,
        val allCompanies: List<Company>,
        val allPayments: List<SubscriptionPayment>,
        val platformAuditLogs: List<PlatformAuditLog>,
        val trustedServerNowMillis: Long,
    ) : OrgUiState

    data class Error(val message: String) : OrgUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MiniMartViewModel(
    private val companyRepository: CompanyRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val preferencesStore: AppPreferencesStore,
    private val networkMonitor: NetworkMonitor,
    private val currentUserId: String,
    private val currentUserEmail: String,
) : ViewModel() {

    private val isPlatformSuperAdmin = CompanyRepository.isPlatformSuperAdminEmail(currentUserEmail)

    private val _actionSubmitting = MutableStateFlow(false)
    val actionSubmitting: StateFlow<Boolean> = _actionSubmitting.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _trustedServerNowMillis = MutableStateFlow(System.currentTimeMillis())
    val trustedServerNowMillis: StateFlow<Long> = _trustedServerNowMillis.asStateFlow()

    private val _wantsOwnCompanySetup = MutableStateFlow(false)
    private var claimingSignature: String? = null
    private var expiryNotifCheckedCompanyKey: String? = null

    val isOnline: StateFlow<Boolean> = networkMonitor.observeOnlineState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = networkMonitor.isCurrentlyOnline(),
        )

    private val activeBranchIdFlow = preferencesStore.activeBranchIdFlow

    init {
        viewModelScope.launch {
            try {
                companyRepository.ensureUserProfile(
                    preferredLanguage = AppLanguage.MYANMAR.code,
                    preferredTheme = ThemeMode.SYSTEM.key,
                )
                _trustedServerNowMillis.value = companyRepository.fetchTrustedServerTimeMillis(currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize profile / server clock on startup", e)
            }
        }
    }

    fun refreshServerClockAndCheck() {
        viewModelScope.launch {
            try {
                _trustedServerNowMillis.value = companyRepository.fetchTrustedServerTimeMillis(currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh server clock", e)
            }
        }
    }

    fun openOwnCompanySetup() {
        _wantsOwnCompanySetup.value = true
    }

    fun closeOwnCompanySetup() {
        _wantsOwnCompanySetup.value = false
    }

    private fun triggerClaimIfNeeded(entry: AccountDirectoryEntry) {
        val signature = "${entry.email}_${entry.companyId}_${entry.linkedUid}_$currentUserId"
        if (claimingSignature == signature) return
        claimingSignature = signature
        viewModelScope.launch {
            try {
                companyRepository.claimApprovedEmployeeAccount(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to claim approved employee account", e)
                claimingSignature = null
                _actionError.value = e.localizedMessage
            }
        }
    }

    private fun triggerExpiryNotificationSync(company: Company, serverNowMs: Long) {
        if (company.ownerUid != currentUserId) return
        val effective = SubscriptionPolicy.effectiveStatus(company, serverNowMs)
        val syncKey = "${company.id}_${effective.key}_${company.expiryNotified7d}_${company.expiryNotified3d}_${company.expiryNotified1d}"
        if (expiryNotifCheckedCompanyKey == syncKey) return
        expiryNotifCheckedCompanyKey = syncKey
        viewModelScope.launch {
            try {
                subscriptionRepository.syncOwnerExpiryNotificationsIfNeeded(company, serverNowMs)
            } catch (_: Exception) {
            }
        }
    }

    val orgState: StateFlow<OrgUiState> = combine(
        companyRepository.observeUserProfile(currentUserId),
        if (currentUserEmail.isNotBlank() && !isPlatformSuperAdmin) {
            companyRepository.observeAccountDirectoryForEmail(currentUserEmail)
        } else {
            flowOf(null)
        },
        _wantsOwnCompanySetup,
        _trustedServerNowMillis,
    ) { profile, dirEntry, wantsSetup, serverNowMs ->
        BootContext(profile, dirEntry, wantsSetup, serverNowMs)
    }.flatMapLatest { (profile, dirEntry, wantsSetup, serverNowMs) ->
        if (profile == null) {
            return@flatMapLatest flowOf(OrgUiState.Loading)
        }

        if (isPlatformSuperAdmin) {
            val activeCompId = profile.activeCompanyId
            if (activeCompId.isNullOrBlank()) {
                if (wantsSetup) {
                    return@flatMapLatest flowOf(OrgUiState.NeedsCompanySetup)
                }
                return@flatMapLatest combine(
                    subscriptionRepository.observeAllCompanies().catch { emit(emptyList()) },
                    subscriptionRepository.observeAllPayments().catch { emit(emptyList()) },
                    subscriptionRepository.observePlans().catch { emit(emptyList()) },
                    subscriptionRepository.observePaymentMethods().catch { emit(emptyList()) },
                    subscriptionRepository.observePlatformAuditLogs().catch { emit(emptyList()) },
                ) { companies, payments, plans, methods, pLogs ->
                    OrgUiState.PlatformAdminWorkspace(
                        userProfile = profile,
                        companies = companies,
                        payments = payments,
                        plans = plans,
                        paymentMethods = methods,
                        platformAuditLogs = pLogs,
                        trustedServerNowMillis = serverNowMs,
                    ) as OrgUiState
                }
            }
        }

        // Strict Owner-Controlled Employee Authorization Check
        if (!isPlatformSuperAdmin && dirEntry != null) {
            when (AccountStatus.fromKey(dirEntry.status)) {
                AccountStatus.PENDING -> return@flatMapLatest flowOf(OrgUiState.PendingApproval(dirEntry))
                AccountStatus.SUSPENDED, AccountStatus.REVOKED -> return@flatMapLatest flowOf(OrgUiState.AccountDisabled(dirEntry))
                AccountStatus.APPROVED -> {
                    if (dirEntry.linkedUid != currentUserId || profile.activeCompanyId != dirEntry.companyId) {
                        triggerClaimIfNeeded(dirEntry)
                        return@flatMapLatest flowOf(OrgUiState.Loading)
                    }
                }
            }
        }

        val companyId = profile.activeCompanyId
        if (companyId.isNullOrBlank()) {
            return@flatMapLatest combine(
                subscriptionRepository.observePlans().catch { emit(emptyList()) },
                subscriptionRepository.observePaymentMethods().catch { emit(emptyList()) },
            ) { plans, methods ->
                OrgUiState.NotAuthorizedOrNewBusiness(
                    plans = plans,
                    paymentMethods = methods,
                ) as OrgUiState
            }
        }

        combine(
            companyRepository.observeCompany(companyId),
            companyRepository.observeMember(companyId, currentUserId),
            activeBranchIdFlow,
        ) { company, member, preferredBranchId ->
            Triple(company, member, preferredBranchId)
        }.flatMapLatest { (company, member, preferredBranchId) ->
            if (company == null) {
                return@flatMapLatest combine(
                    subscriptionRepository.observePlans().catch { emit(emptyList()) },
                    subscriptionRepository.observePaymentMethods().catch { emit(emptyList()) },
                ) { plans, methods ->
                    OrgUiState.NotAuthorizedOrNewBusiness(plans = plans, paymentMethods = methods) as OrgUiState
                }
            }

            if (!isPlatformSuperAdmin && company.ownerUid != currentUserId) {
                if (member != null) {
                    when (AccountStatus.fromKey(member.status)) {
                        AccountStatus.PENDING -> {
                            return@flatMapLatest flowOf(
                                OrgUiState.PendingApproval(
                                    AccountDirectoryEntry(
                                        id = CompanyRepository.emailKey(member.email),
                                        companyId = member.companyId,
                                        ownerUid = member.ownerUid,
                                        employeeId = member.employeeId,
                                        email = member.email,
                                        displayName = member.displayName,
                                        phone = member.phone.orEmpty(),
                                        role = member.role,
                                        status = member.status,
                                        allBranchesAccess = member.allBranchesAccess,
                                        assignedBranchIds = member.assignedBranchIds,
                                        linkedUid = member.userId,
                                    )
                                )
                            )
                        }
                        AccountStatus.SUSPENDED, AccountStatus.REVOKED -> {
                            return@flatMapLatest flowOf(
                                OrgUiState.AccountDisabled(
                                    AccountDirectoryEntry(
                                        id = CompanyRepository.emailKey(member.email),
                                        companyId = member.companyId,
                                        ownerUid = member.ownerUid,
                                        employeeId = member.employeeId,
                                        email = member.email,
                                        displayName = member.displayName,
                                        phone = member.phone.orEmpty(),
                                        role = member.role,
                                        status = member.status,
                                        allBranchesAccess = member.allBranchesAccess,
                                        assignedBranchIds = member.assignedBranchIds,
                                        linkedUid = member.userId,
                                    )
                                )
                            )
                        }
                        AccountStatus.APPROVED -> Unit
                    }
                }
            }

            val effectiveSubStatus = SubscriptionPolicy.effectiveStatus(company, serverNowMs)
            val isOwner = company.ownerUid == currentUserId

            // Lock operational POS / Branch access when subscription is not ACTIVE or EXPIRING_SOON
            if (!isPlatformSuperAdmin && !effectiveSubStatus.isOperational()) {
                if (isOwner) {
                    return@flatMapLatest combine(
                        subscriptionRepository.observePlans().catch { emit(emptyList()) },
                        subscriptionRepository.observePaymentMethods().catch { emit(emptyList()) },
                        subscriptionRepository.observeCompanyPayments(company.ownerUid).catch { emit(emptyList()) },
                    ) { plans, methods, payments ->
                        OrgUiState.SubscriptionLockedOwner(
                            company = company,
                            plans = plans,
                            paymentMethods = methods,
                            payments = payments,
                            trustedServerNowMillis = serverNowMs,
                        ) as OrgUiState
                    }
                } else {
                    return@flatMapLatest flowOf(
                        OrgUiState.SubscriptionLockedEmployee(
                            company = company,
                            effectiveStatus = effectiveSubStatus,
                        )
                    )
                }
            }

            if (isOwner && effectiveSubStatus == SubscriptionStatus.EXPIRING_SOON) {
                triggerExpiryNotificationSync(company, serverNowMs)
            }

            val role = UserRole.fromKey(
                member?.role ?: if (isOwner || isPlatformSuperAdmin) UserRole.SUPER_ADMIN.key else UserRole.CASHIER.key
            )
            val canManageUsers = RolePermissionMatrix.hasPermission(role, Permission.MANAGE_USERS)
            val canViewLogs = RolePermissionMatrix.hasPermission(role, Permission.VIEW_AUDIT_LOGS)
            val canManageBilling = RolePermissionMatrix.hasPermission(role, Permission.MANAGE_SETTINGS) || isOwner

            val branchesFlow = companyRepository.observeBranches(companyId, currentUserId).catch { emit(emptyList()) }
            val accountsFlow = if (canManageUsers) {
                companyRepository.observeOwnerAccountDirectory(company.ownerUid).catch { emit(emptyList()) }
            } else {
                flowOf(listOfNotNull(dirEntry))
            }
            val logsFlow = if (canViewLogs) {
                companyRepository.observeAuditLogs(companyId, currentUserId).catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
            val notifsFlow = companyRepository.observeNotifications(companyId, currentUserId).catch { emit(emptyList()) }

            val orgDataFlow = combine(
                branchesFlow,
                accountsFlow,
                logsFlow,
                notifsFlow,
            ) { branches, accounts, logs, notifs ->
                OrgDataBundle(branches, accounts, logs, notifs)
            }

            val billingDataFlow = if (isPlatformSuperAdmin) {
                combine(
                    subscriptionRepository.observePlans().catch { emit(emptyList()) },
                    subscriptionRepository.observePaymentMethods().catch { emit(emptyList()) },
                    subscriptionRepository.observeCompanyPayments(company.ownerUid).catch { emit(emptyList()) },
                    subscriptionRepository.observeAllCompanies().catch { emit(emptyList()) },
                    subscriptionRepository.observeAllPayments().catch { emit(emptyList()) },
                    subscriptionRepository.observePlatformAuditLogs().catch { emit(emptyList()) },
                ) { arr ->
                    @Suppress("UNCHECKED_CAST")
                    BillingDataBundle(
                        plans = arr[0] as List<SubscriptionPlan>,
                        paymentMethods = arr[1] as List<PaymentMethodConfig>,
                        companyPayments = arr[2] as List<SubscriptionPayment>,
                        allCompanies = arr[3] as List<Company>,
                        allPayments = arr[4] as List<SubscriptionPayment>,
                        platformAuditLogs = arr[5] as List<PlatformAuditLog>,
                    )
                }
            } else if (canManageBilling) {
                combine(
                    subscriptionRepository.observePlans().catch { emit(emptyList()) },
                    subscriptionRepository.observePaymentMethods().catch { emit(emptyList()) },
                    subscriptionRepository.observeCompanyPayments(company.ownerUid).catch { emit(emptyList()) },
                ) { plans, methods, companyPayments ->
                    BillingDataBundle(
                        plans = plans,
                        paymentMethods = methods,
                        companyPayments = companyPayments,
                    )
                }
            } else {
                flowOf(BillingDataBundle())
            }

            combine(orgDataFlow, billingDataFlow) { orgData, billing ->
                val selectedBranch = orgData.branches.firstOrNull { it.id == preferredBranchId }
                    ?: orgData.branches.firstOrNull { it.isActive }
                    ?: orgData.branches.firstOrNull()

                OrgUiState.Ready(
                    isPlatformSuperAdmin = isPlatformSuperAdmin,
                    userProfile = profile,
                    company = company,
                    currentMember = member,
                    branches = orgData.branches,
                    selectedBranch = selectedBranch,
                    accounts = orgData.accounts,
                    auditLogs = orgData.auditLogs,
                    notifications = orgData.notifications,
                    plans = billing.plans,
                    paymentMethods = billing.paymentMethods,
                    companyPayments = billing.companyPayments,
                    allCompanies = billing.allCompanies,
                    allPayments = billing.allPayments,
                    platformAuditLogs = billing.platformAuditLogs,
                    trustedServerNowMillis = serverNowMs,
                ) as OrgUiState
            }
        }
    }
        .catch { error ->
            val firestoreError = error.unwrapFirestoreException()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.e(TAG, "Error observing organization state", root)
            emit(OrgUiState.Error(root.localizedMessage ?: "Failed to load organization"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = OrgUiState.Loading,
        )

    fun selectBranch(branch: Branch) {
        viewModelScope.launch {
            preferencesStore.setActiveBranchId(branch.id)
        }
    }

    fun setLanguage(language: AppLanguage, currentTheme: ThemeMode) {
        viewModelScope.launch {
            preferencesStore.setLanguage(language)
            try {
                companyRepository.updateUserPreferences(language.code, currentTheme.key)
            } catch (_: Exception) {
            }
        }
    }

    fun setThemeMode(themeMode: ThemeMode, currentLanguage: AppLanguage) {
        viewModelScope.launch {
            preferencesStore.setThemeMode(themeMode)
            try {
                companyRepository.updateUserPreferences(currentLanguage.code, themeMode.key)
            } catch (_: Exception) {
            }
        }
    }

    fun createOwnCompany(
        companyNameEn: String,
        companyNameMm: String,
        branchNameEn: String,
        branchNameMm: String,
        branchCode: String,
        phone: String,
        address: String,
        taxId: String?,
        taxRatePercent: Double,
        defaultLanguage: String,
    ) {
        viewModelScope.launch {
            _actionSubmitting.value = true
            _actionError.value = null
            try {
                val (_, branchId) = companyRepository.createCompanyWithDefaultBranch(
                    companyNameEn = companyNameEn,
                    companyNameMm = companyNameMm,
                    branchNameEn = branchNameEn,
                    branchNameMm = branchNameMm,
                    branchCode = branchCode,
                    phone = phone,
                    address = address,
                    taxId = taxId,
                    taxRatePercent = taxRatePercent,
                    defaultLanguage = defaultLanguage,
                )
                preferencesStore.setActiveBranchId(branchId)
                _wantsOwnCompanySetup.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to create company"
            } finally {
                _actionSubmitting.value = false
            }
        }
    }

    fun registerCustomerBusiness(
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
        defaultLanguage: String,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionSubmitting.value = true
            _actionError.value = null
            try {
                subscriptionRepository.registerCustomerBusinessWithPayment(
                    businessNameEn = businessNameEn,
                    businessNameMm = businessNameMm,
                    ownerName = ownerName,
                    phone = phone,
                    address = address,
                    selectedPlan = plan,
                    selectedMethod = method,
                    transactionRef = transactionRef,
                    paymentDateText = paymentDateText,
                    proofImageBase64 = proofBase64,
                    defaultLanguage = defaultLanguage,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to submit subscription"
            } finally {
                _actionSubmitting.value = false
            }
        }
    }

    fun submitRenewalPayment(
        company: Company,
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
        transactionRef: String,
        paymentDateText: String,
        proofBase64: String?,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionSubmitting.value = true
            _actionError.value = null
            try {
                subscriptionRepository.submitRenewalPaymentProof(
                    company = company,
                    plan = plan,
                    method = method,
                    transactionRef = transactionRef,
                    paymentDateText = paymentDateText,
                    proofImageBase64 = proofBase64,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to submit payment"
            } finally {
                _actionSubmitting.value = false
            }
        }
    }

    fun seedStarterPlans(actorName: String, successMessage: String) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                subscriptionRepository.seedStarterPlansAndMethods(actorName)
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to seed plans"
            }
        }
    }

    fun upsertPlan(
        existingPlan: SubscriptionPlan?,
        actorName: String,
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
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                subscriptionRepository.upsertPlan(
                    existingPlan = existingPlan,
                    actorName = actorName,
                    nameEn = nameEn,
                    nameMm = nameMm,
                    descriptionEn = descEn,
                    descriptionMm = descMm,
                    durationMonths = durationMonths,
                    durationDays = durationDays,
                    priceMmk = priceMmk,
                    discountPercent = discountPercent,
                    promoLabel = promoLabel,
                    maxBranches = maxBranches,
                    maxEmployees = maxEmployees,
                    enabledFeatures = enabledFeatures,
                    isActive = isActive,
                    sortOrder = sortOrder,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to save plan"
            }
        }
    }

    fun deletePlan(plan: SubscriptionPlan, actorName: String, successMessage: String) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                subscriptionRepository.deletePlan(plan, actorName)
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to delete plan"
            }
        }
    }

    fun upsertPaymentMethod(
        existingMethod: PaymentMethodConfig?,
        actorName: String,
        nameEn: String,
        nameMm: String,
        providerType: PaymentMethodProviderType,
        accountTitle: String,
        accountNumber: String,
        instructionsEn: String,
        instructionsMm: String,
        gatewayEndpoint: String?,
        isActive: Boolean,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                subscriptionRepository.upsertPaymentMethod(
                    existingMethod = existingMethod,
                    actorName = actorName,
                    nameEn = nameEn,
                    nameMm = nameMm,
                    providerType = providerType,
                    accountTitle = accountTitle,
                    accountNumber = accountNumber,
                    instructionsEn = instructionsEn,
                    instructionsMm = instructionsMm,
                    gatewayEndpoint = gatewayEndpoint,
                    isActive = isActive,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to save payment method"
            }
        }
    }

    fun approvePayment(
        payment: SubscriptionPayment,
        company: Company,
        plan: SubscriptionPlan?,
        actorName: String,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                val trustedNow = companyRepository.fetchTrustedServerTimeMillis(currentUserId)
                _trustedServerNowMillis.value = trustedNow
                subscriptionRepository.approvePaymentAndActivateSubscription(
                    payment = payment,
                    company = company,
                    plan = plan,
                    actorName = actorName,
                    trustedServerNowMillis = trustedNow,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to verify payment"
            }
        }
    }

    fun rejectPayment(
        payment: SubscriptionPayment,
        company: Company?,
        reason: String,
        actorName: String,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                val trustedNow = companyRepository.fetchTrustedServerTimeMillis(currentUserId)
                _trustedServerNowMillis.value = trustedNow
                subscriptionRepository.rejectPayment(
                    payment = payment,
                    company = company,
                    reason = reason,
                    actorName = actorName,
                    trustedServerNowMillis = trustedNow,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to reject payment"
            }
        }
    }

    fun updateBusinessStatus(
        company: Company,
        newStatus: SubscriptionStatus,
        actorName: String,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                subscriptionRepository.updateBusinessSubscriptionStatus(
                    company = company,
                    newStatus = newStatus,
                    actorName = actorName,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to update status"
            }
        }
    }

    fun createBranch(
        ready: OrgUiState.Ready,
        code: String,
        nameEn: String,
        nameMm: String,
        address: String,
        phone: String,
        managerUid: String?,
        managerName: String?,
        isHeadOffice: Boolean,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                val adminUids = ready.accounts
                    .filter {
                        it.status == AccountStatus.APPROVED.key &&
                            it.role in listOf(UserRole.SUPER_ADMIN.key, UserRole.GENERAL_MANAGER.key) &&
                            !it.linkedUid.isNullOrBlank()
                    }
                    .mapNotNull { it.linkedUid }
                val branchId = companyRepository.createBranch(
                    company = ready.company,
                    currentBranchCount = ready.branches.size,
                    actorName = ready.userProfile.displayName,
                    code = code,
                    nameEn = nameEn,
                    nameMm = nameMm,
                    address = address,
                    phone = phone,
                    managerUid = managerUid,
                    managerName = managerName,
                    isHeadOffice = isHeadOffice,
                    adminUids = adminUids,
                )
                preferencesStore.setActiveBranchId(branchId)
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to create branch"
            }
        }
    }

    fun updateBranch(
        ready: OrgUiState.Ready,
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
        isAdmin: Boolean,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                val adminUids = ready.accounts
                    .filter {
                        it.status == AccountStatus.APPROVED.key &&
                            it.role in listOf(UserRole.SUPER_ADMIN.key, UserRole.GENERAL_MANAGER.key) &&
                            !it.linkedUid.isNullOrBlank()
                    }
                    .mapNotNull { it.linkedUid }
                companyRepository.updateBranch(
                    company = ready.company,
                    branch = branch,
                    actorName = ready.userProfile.displayName,
                    code = code,
                    nameEn = nameEn,
                    nameMm = nameMm,
                    address = address,
                    phone = phone,
                    managerUid = managerUid,
                    managerName = managerName,
                    isHeadOffice = isHeadOffice,
                    isActive = isActive,
                    isAdmin = isAdmin,
                    adminUids = adminUids,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to update branch"
            }
        }
    }

    fun upsertEmployeeAccount(
        ready: OrgUiState.Ready,
        existingEntry: AccountDirectoryEntry?,
        employeeId: String,
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        status: AccountStatus,
        allBranchesAccess: Boolean,
        assignedBranchIds: List<String>,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                companyRepository.upsertOwnerEmployeeAccount(
                    company = ready.company,
                    currentAccountCount = ready.accounts.size,
                    actorName = ready.userProfile.displayName,
                    existingEntry = existingEntry,
                    employeeId = employeeId,
                    displayName = displayName,
                    email = email,
                    phone = phone,
                    role = role,
                    status = status,
                    allBranchesAccess = allBranchesAccess,
                    assignedBranchIds = assignedBranchIds,
                    allBranches = ready.branches,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to save account"
            }
        }
    }

    fun changeEmployeeAccountStatus(
        ready: OrgUiState.Ready,
        entry: AccountDirectoryEntry,
        newStatus: AccountStatus,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                companyRepository.changeEmployeeAccountStatus(
                    company = ready.company,
                    actorName = ready.userProfile.displayName,
                    entry = entry,
                    newStatus = newStatus,
                    allBranches = ready.branches,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to update account status"
            }
        }
    }

    fun updateCompanySettings(
        ready: OrgUiState.Ready,
        nameEn: String,
        nameMm: String,
        phone: String,
        address: String,
        taxId: String?,
        taxRatePercent: Double,
        defaultLanguage: String,
        allowNegativeStock: Boolean,
        offlineLimitMmk: Long,
        successMessage: String,
    ) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                companyRepository.updateCompanySettings(
                    company = ready.company,
                    actorName = ready.userProfile.displayName,
                    nameEn = nameEn,
                    nameMm = nameMm,
                    phone = phone,
                    address = address,
                    taxId = taxId,
                    taxRatePercent = taxRatePercent,
                    defaultLanguage = defaultLanguage,
                    allowNegativeStock = allowNegativeStock,
                    offlineLimitMmk = offlineLimitMmk,
                )
                _feedbackMessage.value = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to save settings"
            }
        }
    }

    fun markNotificationRead(companyId: String, notificationId: String) {
        viewModelScope.launch {
            try {
                companyRepository.markNotificationRead(companyId, notificationId)
            } catch (_: Exception) {
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
        _actionError.value = null
    }

    private data class BootContext(
        val profile: UserProfile?,
        val dirEntry: AccountDirectoryEntry?,
        val wantsSetup: Boolean,
        val serverNowMs: Long,
    )

    private data class OrgDataBundle(
        val branches: List<Branch>,
        val accounts: List<AccountDirectoryEntry>,
        val auditLogs: List<AuditLog>,
        val notifications: List<AppNotification>,
    )

    private data class BillingDataBundle(
        val plans: List<SubscriptionPlan> = emptyList(),
        val paymentMethods: List<PaymentMethodConfig> = emptyList(),
        val companyPayments: List<SubscriptionPayment> = emptyList(),
        val allCompanies: List<Company> = emptyList(),
        val allPayments: List<SubscriptionPayment> = emptyList(),
        val platformAuditLogs: List<PlatformAuditLog> = emptyList(),
    )

    private companion object {
        const val TAG = "MiniMartVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
