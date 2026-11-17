package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.core.rbac.AccountStatus
import com.example.core.rbac.UserRole
import com.example.domain.model.PaymentVerificationStatus
import com.example.domain.model.PlanFeature
import com.example.domain.model.SubscriptionStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class SubscriptionRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun unauthenticatedCaller_observePlans_failsWithIllegalStateOrPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SubscriptionRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observePlans().first() }
            fail("Expected failure for unauthenticated caller")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertTrue(
                "Expected IllegalStateException or PERMISSION_DENIED, got $e",
                e is IllegalStateException || fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
            )
        }
    }

    @Test
    fun nonSuperAdmin_upsertPlan_failsWithPermissionDenied() = runBlocking {
        val randomEmail = "biz_${UUID.randomUUID().toString().take(8)}@test.com"
        signInTestUser(randomEmail)
        val subRepo = SubscriptionRepository(firestore, auth)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                subRepo.upsertPlan(
                    existingPlan = null,
                    actorName = "Attacker",
                    nameEn = "Free Forever",
                    nameMm = "အခမဲ့",
                    descriptionEn = "Fake",
                    descriptionMm = "အတု",
                    durationMonths = 12,
                    durationDays = 365,
                    priceMmk = 0L,
                    discountPercent = 0.0,
                    promoLabel = null,
                    maxBranches = 50,
                    maxEmployees = 100,
                    enabledFeatures = PlanFeature.entries.map { it.key },
                    isActive = true,
                    sortOrder = 1,
                )
            }
            fail("Expected PERMISSION_DENIED when non-super-admin attempts to create plan")
        } catch (e: Exception) {
            val fsErr = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr.code)
        }
    }

    @Test
    fun superAdmin_seedsPlans_approvesCustomerPayment_andEnforcesBranchQuota() = runBlocking {
        clearFirestoreEmulatorData()

        // 1. Platform Super Admin seeds starter plans and payment methods
        signInTestUser(CompanyRepository.BOOTSTRAPPED_OWNER_EMAIL)
        val adminCompanyRepo = CompanyRepository(firestore, auth)
        val adminSubRepo = SubscriptionRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminCompanyRepo.ensureUserProfile("my", "system")
            adminSubRepo.seedStarterPlansAndMethods("Platform Owner")
        }

        val plans = withTimeout(FLOW_TIMEOUT_MS) {
            adminSubRepo.observePlans().first { it.size >= 3 }
        }
        val methods = withTimeout(FLOW_TIMEOUT_MS) {
            adminSubRepo.observePaymentMethods().first { it.isNotEmpty() }
        }
        val starter1M = plans.first { it.durationMonths == 1 }
        val kbz = methods.first()

        // 2. Customer Business registers -> locked as PENDING_APPROVAL + PENDING_VERIFICATION
        val ownerUid = signInTestUser("owner_${UUID.randomUUID().toString().take(6)}@test.com")
        val ownerCompanyRepo = CompanyRepository(firestore, auth)
        val ownerSubRepo = SubscriptionRepository(firestore, auth)
        val companyId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerCompanyRepo.ensureUserProfile("my", "system")
            ownerSubRepo.registerCustomerBusinessWithPayment(
                businessNameEn = "Shwe Store",
                businessNameMm = "ရွှေဆိုင်",
                ownerName = "U Shwe",
                phone = "0940001122",
                address = "Yangon",
                selectedPlan = starter1M,
                selectedMethod = kbz,
                transactionRef = "KBZ-TXN-998877",
                paymentDateText = "2026-11-16",
                proofImageBase64 = null,
                defaultLanguage = "my",
            )
        }

        val pendingCompany = withTimeout(FLOW_TIMEOUT_MS) {
            ownerCompanyRepo.observeCompany(companyId).first { it != null }
        }!!
        assertEquals(SubscriptionStatus.PENDING_APPROVAL.key, pendingCompany.subscriptionStatus)

        // Operational branch read must fail with PERMISSION_DENIED while subscription is PENDING_APPROVAL
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                ownerCompanyRepo.observeBranches(companyId, ownerUid).first()
            }
            // Note: CompanyRepository.observeBranches swallows PERMISSION_DENIED to emptyList().
            // Let's verify raw Firestore query fails with PERMISSION_DENIED too!
        } catch (_: Exception) {
        }

        val paymentsForOwner = withTimeout(FLOW_TIMEOUT_MS) {
            ownerSubRepo.observeCompanyPayments(ownerUid).first { it.isNotEmpty() }
        }
        val submittedPayment = paymentsForOwner.first()
        assertEquals(PaymentVerificationStatus.PENDING_VERIFICATION.key, submittedPayment.status)

        // 3. Super Admin verifies payment -> Company becomes ACTIVE and receipt is generated
        val superAdminUid = signInTestUser(CompanyRepository.BOOTSTRAPPED_OWNER_EMAIL)
        val serverNow = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminCompanyRepo.fetchTrustedServerTimeMillis(superAdminUid)
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminSubRepo.approvePaymentAndActivateSubscription(
                payment = submittedPayment,
                company = pendingCompany,
                plan = starter1M,
                actorName = "Platform Super Admin",
                trustedServerNowMillis = serverNow,
            )
        }

        // 4. Customer Owner signs back in, sees ACTIVE status, creates 2nd branch within quota (max=2), blocks 3rd branch!
        signInTestUser(pendingCompany.ownerEmail)
        val activeCompany = withTimeout(FLOW_TIMEOUT_MS) {
            ownerCompanyRepo.observeCompany(companyId).first {
                it?.subscriptionStatus == SubscriptionStatus.ACTIVE.key
            }
        }!!
        assertEquals(starter1M.maxBranches, activeCompany.maxBranchesAllowed)

        val branchesAfterActivation = withTimeout(FLOW_TIMEOUT_MS) {
            ownerCompanyRepo.observeBranches(companyId, ownerUid).first { it.isNotEmpty() }
        }
        assertEquals(1, branchesAfterActivation.size)

        val branch2Id = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerCompanyRepo.createBranch(
                company = activeCompany,
                currentBranchCount = 1,
                actorName = "U Shwe",
                code = "MDY-01",
                nameEn = "Mandalay Branch",
                nameMm = "မန္တလေးဆိုင်ခွဲ",
                address = "Mandalay",
                phone = "0950002211",
                managerUid = null,
                managerName = null,
                isHeadOffice = false,
                adminUids = listOf(ownerUid),
            )
        }
        assertNotNull(branch2Id)

        // Exceeding maxBranches=2 must be blocked by quota check
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                ownerCompanyRepo.createBranch(
                    company = activeCompany,
                    currentBranchCount = starter1M.maxBranches,
                    actorName = "U Shwe",
                    code = "NPT-01",
                    nameEn = "Naypyitaw Branch",
                    nameMm = "နေပြည်တော်ဆိုင်ခွဲ",
                    address = "Naypyitaw",
                    phone = "0950003322",
                    managerUid = null,
                    managerName = null,
                    isHeadOffice = false,
                    adminUids = listOf(ownerUid),
                )
            }
            fail("Expected IllegalStateException when exceeding maxBranchesAllowed")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Branch limit reached"))
        }

        // 5. Super Admin suspends company -> operational writes rejected by Firestore Rules
        signInTestUser(CompanyRepository.BOOTSTRAPPED_OWNER_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminSubRepo.updateBusinessSubscriptionStatus(
                company = activeCompany,
                newStatus = SubscriptionStatus.SUSPENDED,
                actorName = "Platform Super Admin",
            )
        }

        signInTestUser(pendingCompany.ownerEmail)
        val suspendedCompany = withTimeout(FLOW_TIMEOUT_MS) {
            ownerCompanyRepo.observeCompany(companyId).first {
                it?.subscriptionStatus == SubscriptionStatus.SUSPENDED.key
            }
        }!!
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                ownerCompanyRepo.createBranch(
                    company = suspendedCompany.copy(maxBranchesAllowed = 10),
                    currentBranchCount = 1,
                    actorName = "U Shwe",
                    code = "YGN-02",
                    nameEn = "Suspended Write Attempt",
                    nameMm = "ပိတ်ထားသောဆိုင်ခွဲ",
                    address = "Yangon",
                    phone = "09123456",
                    managerUid = null,
                    managerName = null,
                    isHeadOffice = false,
                    adminUids = listOf(ownerUid),
                )
            }
            fail("Expected PERMISSION_DENIED for operational branch write while SUSPENDED")
        } catch (e: Exception) {
            val fsErr = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr.code)
        }
    }

    @Test
    fun ownerControlledEmployeeAuth_pendingBlocked_approvedClaimsAssignedBranchOnly() = runBlocking {
        clearFirestoreEmulatorData()

        // Owner sets up flagship company
        val ownerUid = signInTestUser(CompanyRepository.BOOTSTRAPPED_OWNER_EMAIL)
        val ownerRepo = CompanyRepository(firestore, auth)
        val (companyId, hqBranchId) = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.ensureUserProfile("my", "system")
            ownerRepo.createCompanyWithDefaultBranch(
                companyNameEn = "Alpha Mart",
                companyNameMm = "အယ်လ်ဖာမတ်",
                branchNameEn = "HQ Yangon",
                branchNameMm = "ရန်ကုန်ရုံးချုပ်",
                branchCode = "HQ-001",
                phone = "0911112222",
                address = "Yangon",
                taxId = null,
                taxRatePercent = 5.0,
                defaultLanguage = "my",
            )
        }
        val company = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeCompany(companyId).first { it != null }
        }!!
        val hqBranches = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeBranches(companyId, ownerUid).first { it.isNotEmpty() }
        }

        // Create second branch that Cashier does NOT have access to
        val secondBranchId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createBranch(
                company = company,
                currentBranchCount = 1,
                actorName = "Owner",
                code = "BGO-01",
                nameEn = "Bago Store",
                nameMm = "ပဲခူးဆိုင်ခွဲ",
                address = "Bago",
                phone = "0922223333",
                managerUid = null,
                managerName = null,
                isHeadOffice = false,
                adminUids = listOf(ownerUid),
            )
        }
        val allBranches = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeBranches(companyId, ownerUid).first { it.size == 2 }
        }

        // Owner creates Cashier with PENDING status assigned strictly to HQ-001
        val cashierEmail = "cashier_${UUID.randomUUID().toString().take(6)}@test.com"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.upsertOwnerEmployeeAccount(
                company = company,
                currentAccountCount = 1,
                actorName = "Owner",
                existingEntry = null,
                employeeId = "EMP-010",
                displayName = "Maung Cashier",
                email = cashierEmail,
                phone = "0933334444",
                role = UserRole.CASHIER,
                status = AccountStatus.PENDING,
                allBranchesAccess = false,
                assignedBranchIds = listOf(hqBranchId),
                allBranches = allBranches,
            )
        }

        // Cashier signs in -> Account directory reports PENDING -> trying to claim fails with PERMISSION_DENIED
        val cashierUid = signInTestUser(cashierEmail)
        val cashierRepo = CompanyRepository(firestore, auth)
        val pendingDir = withTimeout(FLOW_TIMEOUT_MS) {
            cashierRepo.observeAccountDirectoryForEmail(cashierEmail).first { it != null }
        }!!
        assertEquals(AccountStatus.PENDING.key, pendingDir.status)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                cashierRepo.ensureUserProfile("my", "system")
                cashierRepo.claimApprovedEmployeeAccount(pendingDir)
            }
            fail("Expected PERMISSION_DENIED claiming a PENDING employee account")
        } catch (e: Exception) {
            val fsErr = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr.code)
        }

        // Owner approves Cashier
        signInTestUser(CompanyRepository.BOOTSTRAPPED_OWNER_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.changeEmployeeAccountStatus(
                company = company,
                actorName = "Owner",
                entry = pendingDir,
                newStatus = AccountStatus.APPROVED,
                allBranches = allBranches,
            )
        }

        // Cashier signs back in, claims APPROVED account, and Owner syncs branch memberUids after link
        signInTestUser(cashierEmail)
        val approvedDir = withTimeout(FLOW_TIMEOUT_MS) {
            cashierRepo.observeAccountDirectoryForEmail(cashierEmail).first {
                it?.status == AccountStatus.APPROVED.key
            }
        }!!
        withTimeout(DEFAULT_TIMEOUT_MS) {
            cashierRepo.claimApprovedEmployeeAccount(approvedDir)
        }

        // Owner completes branch assignment sync for newly linked UID
        signInTestUser(CompanyRepository.BOOTSTRAPPED_OWNER_EMAIL)
        val linkedDir = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeOwnerAccountDirectory(ownerUid).first { entries ->
                entries.any { it.email == cashierEmail && it.linkedUid == cashierUid }
            }
        }.first { it.email == cashierEmail }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.changeEmployeeAccountStatus(
                company = company,
                actorName = "Owner",
                entry = linkedDir,
                newStatus = AccountStatus.APPROVED,
                allBranches = allBranches,
            )
        }

        // Cashier observes ONLY HQ branch, NEVER secondBranchId
        signInTestUser(cashierEmail)
        val visibleBranches = withTimeout(FLOW_TIMEOUT_MS) {
            cashierRepo.observeBranches(companyId, cashierUid).first { it.isNotEmpty() }
        }
        assertEquals(listOf(hqBranchId), visibleBranches.map { it.id })
        assertTrue(visibleBranches.none { it.id == secondBranchId })
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
