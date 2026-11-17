package com.example.data.repository

import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.firebase.unwrapFirestoreException
import com.example.core.rbac.AccountStatus
import com.example.core.rbac.UserRole
import com.example.core.subscription.SubscriptionPolicy
import com.example.domain.model.Company
import com.example.domain.model.PaymentMethodConfig
import com.example.domain.model.PaymentMethodProviderType
import com.example.domain.model.PaymentVerificationStatus
import com.example.domain.model.PlanFeature
import com.example.domain.model.PlatformAuditLog
import com.example.domain.model.SubscriptionPayment
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SubscriptionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")

    fun observePlans(): Flow<List<SubscriptionPlan>> = flow {
        val uid = requireUserId()
        db.collection("subscription_plans")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SubscriptionPlan::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareBy<SubscriptionPlan> { it.sortOrder }.thenBy { it.durationMonths })
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "subscription_plans")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observePaymentMethods(): Flow<List<PaymentMethodConfig>> = flow {
        val uid = requireUserId()
        db.collection("payment_methods")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PaymentMethodConfig::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.nameEn }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "payment_methods")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAllCompanies(): Flow<List<Company>> = flow {
        val uid = requireUserId()
        db.collection("companies")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Company::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "companies")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAllPayments(): Flow<List<SubscriptionPayment>> = flow {
        val uid = requireUserId()
        db.collection("subscription_payments")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SubscriptionPayment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "subscription_payments")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeCompanyPayments(ownerUid: String): Flow<List<SubscriptionPayment>> = flow {
        val uid = requireUserId()
        db.collection("subscription_payments")
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SubscriptionPayment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "subscription_payments")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observePlatformAuditLogs(): Flow<List<PlatformAuditLog>> = flow {
        val uid = requireUserId()
        db.collection("platform_audit_logs")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PlatformAuditLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "platform_audit_logs")
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun seedStarterPlansAndMethods(actorName: String) {
        val uid = requireUserId()
        val batch = db.batch()

        val starterPlans = listOf(
            Triple("plan_1m", 1 to 30, 35_000L to 0.0) to Triple("1 Month Growth", "၁ လ အစီအစဉ်", 2 to 10),
            Triple("plan_6m", 6 to 180, 195_000L to 10.0) to Triple("6 Months Pro", "၆ လ အထူးအစီအစဉ်", 10 to 50),
            Triple("plan_1y", 12 to 365, 360_000L to 15.0) to Triple("1 Year Enterprise", "၁ နှစ် လုပ်ငန်းကြီးအစီအစဉ်", 50 to 250),
        )

        starterPlans.forEachIndexed { idx, (left, right) ->
            val (planId, durPair, pricePair) = left
            val (months, days) = durPair
            val (price, discount) = pricePair
            val (nameEn, nameMm, quotaPair) = right
            val (maxBranches, maxEmployees) = quotaPair
            val promoPrice = SubscriptionPolicy.calculateDiscountedPriceMmk(price, discount)

            val ref = db.collection("subscription_plans").document(planId)
            batch.set(
                ref,
                buildMap<String, Any> {
                    put("nameEn", nameEn)
                    put("nameMm", nameMm)
                    put("descriptionEn", "Up to $maxBranches branches & $maxEmployees staff accounts")
                    put("descriptionMm", "ဆိုင်ခွဲ $maxBranches ခုနှင့် ဝန်ထမ်း $maxEmployees ဦးအထိ")
                    put("durationMonths", months)
                    put("durationDays", days)
                    put("priceMmk", price)
                    put("discountPercent", discount)
                    put("promoPriceMmk", promoPrice)
                    if (discount > 0.0) put("promoLabel", "Save ${discount.toInt()}%")
                    put("maxBranches", maxBranches)
                    put("maxEmployees", maxEmployees)
                    put("enabledFeatures", PlanFeature.entries.map { it.key })
                    put("isActive", true)
                    put("sortOrder", idx + 1)
                    put("createdBy", uid)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
        }

        val methods = listOf(
            listOf("kbzpay", "KBZPay Merchant", "KBZPay ဖြင့်ငွေလွှဲရန်", "MINI MART PRO CO., LTD", "09420009999", "Transfer exact MMK amount to KBZPay and upload slip screenshot.", "KBZPay သို့ ကျသင့်ငွေလွှဲပြီး ငွေလွှဲစလစ်ပုံတင်ပါ။"),
            listOf("wavepay", "WavePay Transfer", "WavePay ဖြင့်ငွေလွှဲရန်", "MINI MART PRO CO., LTD", "09790008888", "Send payment via WavePay and enter your Transaction Ref ID.", "WavePay ဖြင့်ငွေလွှဲပြီး Transaction ID ထည့်ပါ။"),
            listOf("ayabank", "AYA Bank Mobile", "AYA ဘဏ်လွှဲ", "MINI MART PRO ENTERPRISE", "2001-8892-0041", "Transfer via AYA mBanking and attach payment confirmation.", "AYA ဘဏ်မှ ငွေလွှဲပြီး ပြေစာပုံတင်ပါ။"),
        )
        methods.forEach { m ->
            val ref = db.collection("payment_methods").document(m[0])
            batch.set(
                ref,
                mapOf(
                    "nameEn" to m[1],
                    "nameMm" to m[2],
                    "providerType" to PaymentMethodProviderType.MANUAL_TRANSFER.key,
                    "accountTitle" to m[3],
                    "accountNumber" to m[4],
                    "instructionsEn" to m[5],
                    "instructionsMm" to m[6],
                    "isActive" to true,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        val logRef = db.collection("platform_audit_logs")
            .document("plog_${UUID.randomUUID().toString().replace("-", "").take(12)}")
        batch.set(
            logRef,
            mapOf(
                "companyId" to "PLATFORM",
                "ownerUid" to uid,
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to "PLATFORM_SUPER_ADMIN",
                "action" to "STARTER_PLANS_AND_METHODS_INITIALIZED",
                "targetType" to "PLAN",
                "targetId" to "starter_bundle",
                "details" to "Initialized 1M / 6M / 1Y configurable plans and payment methods",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subscription_plans")
            throw e
        }
    }

    suspend fun upsertPlan(
        existingPlan: SubscriptionPlan?,
        actorName: String,
        nameEn: String,
        nameMm: String,
        descriptionEn: String,
        descriptionMm: String,
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
    ) {
        val uid = requireUserId()
        val planId = existingPlan?.id?.takeIf { it.isNotBlank() }
            ?: "plan_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val promoPriceMmk = SubscriptionPolicy.calculateDiscountedPriceMmk(priceMmk, discountPercent)

        val planRef = db.collection("subscription_plans").document(planId)
        val logRef = db.collection("platform_audit_logs")
            .document("plog_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        if (existingPlan == null) {
            batch.set(
                planRef,
                buildMap<String, Any> {
                    put("nameEn", nameEn.trim())
                    put("nameMm", nameMm.trim())
                    put("descriptionEn", descriptionEn.trim())
                    put("descriptionMm", descriptionMm.trim())
                    put("durationMonths", durationMonths.coerceIn(1, 60))
                    put("durationDays", durationDays.coerceIn(1, 2000))
                    put("priceMmk", priceMmk.coerceAtLeast(0L))
                    put("discountPercent", discountPercent.coerceIn(0.0, 100.0))
                    put("promoPriceMmk", promoPriceMmk)
                    if (!promoLabel.isNullOrBlank()) put("promoLabel", promoLabel.trim())
                    put("maxBranches", maxBranches.coerceIn(1, 1000))
                    put("maxEmployees", maxEmployees.coerceIn(1, 10000))
                    put("enabledFeatures", enabledFeatures)
                    put("isActive", isActive)
                    put("sortOrder", sortOrder)
                    put("createdBy", uid)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
        } else {
            batch.update(
                planRef,
                buildMap<String, Any> {
                    put("nameEn", nameEn.trim())
                    put("nameMm", nameMm.trim())
                    put("descriptionEn", descriptionEn.trim())
                    put("descriptionMm", descriptionMm.trim())
                    put("durationMonths", durationMonths.coerceIn(1, 60))
                    put("durationDays", durationDays.coerceIn(1, 2000))
                    put("priceMmk", priceMmk.coerceAtLeast(0L))
                    put("discountPercent", discountPercent.coerceIn(0.0, 100.0))
                    put("promoPriceMmk", promoPriceMmk)
                    put("promoLabel", promoLabel?.trim() ?: "")
                    put("maxBranches", maxBranches.coerceIn(1, 1000))
                    put("maxEmployees", maxEmployees.coerceIn(1, 10000))
                    put("enabledFeatures", enabledFeatures)
                    put("isActive", isActive)
                    put("sortOrder", sortOrder)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
        }

        batch.set(
            logRef,
            mapOf(
                "companyId" to "PLATFORM",
                "ownerUid" to uid,
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to "PLATFORM_SUPER_ADMIN",
                "action" to if (existingPlan == null) "PLAN_CREATED" else "PLAN_UPDATED",
                "targetType" to "PLAN",
                "targetId" to planId,
                "details" to "${nameEn.trim()} • ${promoPriceMmk} MMK (${durationDays}d, maxB=$maxBranches, maxE=$maxEmployees, active=$isActive)",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, planRef.path)
            throw e
        }
    }

    suspend fun deletePlan(plan: SubscriptionPlan, actorName: String) {
        val uid = requireUserId()
        val planRef = db.collection("subscription_plans").document(plan.id)
        val logRef = db.collection("platform_audit_logs")
            .document("plog_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        batch.delete(planRef)
        batch.set(
            logRef,
            mapOf(
                "companyId" to "PLATFORM",
                "ownerUid" to uid,
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to "PLATFORM_SUPER_ADMIN",
                "action" to "PLAN_DELETED",
                "targetType" to "PLAN",
                "targetId" to plan.id,
                "details" to "Deleted plan ${plan.nameEn}",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, planRef.path)
            throw e
        }
    }

    suspend fun upsertPaymentMethod(
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
    ) {
        val uid = requireUserId()
        val methodId = existingMethod?.id?.takeIf { it.isNotBlank() }
            ?: "pm_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val methodRef = db.collection("payment_methods").document(methodId)
        val logRef = db.collection("platform_audit_logs")
            .document("plog_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        if (existingMethod == null) {
            batch.set(
                methodRef,
                buildMap<String, Any> {
                    put("nameEn", nameEn.trim())
                    put("nameMm", nameMm.trim())
                    put("providerType", providerType.key)
                    put("accountTitle", accountTitle.trim())
                    put("accountNumber", accountNumber.trim())
                    put("instructionsEn", instructionsEn.trim())
                    put("instructionsMm", instructionsMm.trim())
                    if (!gatewayEndpoint.isNullOrBlank()) put("gatewayEndpoint", gatewayEndpoint.trim())
                    put("isActive", isActive)
                    put("createdBy", uid)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
        } else {
            batch.update(
                methodRef,
                mapOf(
                    "nameEn" to nameEn.trim(),
                    "nameMm" to nameMm.trim(),
                    "providerType" to providerType.key,
                    "accountTitle" to accountTitle.trim(),
                    "accountNumber" to accountNumber.trim(),
                    "instructionsEn" to instructionsEn.trim(),
                    "instructionsMm" to instructionsMm.trim(),
                    "gatewayEndpoint" to (gatewayEndpoint?.trim() ?: ""),
                    "isActive" to isActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        batch.set(
            logRef,
            mapOf(
                "companyId" to "PLATFORM",
                "ownerUid" to uid,
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to "PLATFORM_SUPER_ADMIN",
                "action" to if (existingMethod == null) "PAYMENT_METHOD_CREATED" else "PAYMENT_METHOD_UPDATED",
                "targetType" to "PAYMENT_METHOD",
                "targetId" to methodId,
                "details" to "${nameEn.trim()} (${providerType.key}, active=$isActive)",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, methodRef.path)
            throw e
        }
    }

    /**
     * New subscribing customer business onboarding:
     * Creates company strictly locked in PENDING_APPROVAL (no active expiry bypass!)
     * and creates PENDING_VERIFICATION payment submission for Super Admin review.
     */
    suspend fun registerCustomerBusinessWithPayment(
        businessNameEn: String,
        businessNameMm: String,
        ownerName: String,
        phone: String,
        address: String,
        selectedPlan: SubscriptionPlan,
        selectedMethod: PaymentMethodConfig,
        transactionRef: String,
        paymentDateText: String,
        proofImageBase64: String?,
        defaultLanguage: String,
    ): String {
        val uid = requireUserId()
        val user = auth.currentUser!!
        val email = (user.email?.takeIf { it.isNotBlank() } ?: "$uid@minimart.local").lowercase()

        val companyId = "comp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val branchId = "branch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val paymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val logId = "plog_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val compRef = db.collection("companies").document(companyId)
        val memberRef = compRef.collection("members").document(uid)
        val dirRef = db.collection("account_directory").document(CompanyRepository.emailKey(email))
        val branchRef = compRef.collection("branches").document(branchId)
        val paymentRef = db.collection("subscription_payments").document(paymentId)
        val logRef = db.collection("platform_audit_logs").document(logId)
        val userRef = db.collection("users").document(uid)

        val batch = db.batch()
        batch.set(
            compRef,
            mapOf(
                "ownerUid" to uid,
                "ownerName" to ownerName.trim(),
                "ownerEmail" to email,
                "name" to businessNameEn.trim(),
                "nameMm" to businessNameMm.trim(),
                "phone" to phone.trim(),
                "address" to address.trim(),
                "currency" to "MMK",
                "defaultLanguage" to defaultLanguage,
                "taxRatePercent" to 5.0,
                "allowNegativeStock" to false,
                "offlineLimitMmk" to 500_000L,
                "subscriptionPlanId" to selectedPlan.id,
                "subscriptionPlanName" to selectedPlan.nameEn,
                "subscriptionStatus" to SubscriptionStatus.PENDING_APPROVAL.key,
                "maxBranchesAllowed" to 1,
                "maxEmployeesAllowed" to 5,
                "enabledFeatures" to emptyList<String>(),
                "activeBranchCount" to 1,
                "activeEmployeeCount" to 1,
                "expiryNotified7d" to false,
                "expiryNotified3d" to false,
                "expiryNotified1d" to false,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            memberRef,
            mapOf(
                "companyId" to companyId,
                "ownerUid" to uid,
                "userId" to uid,
                "employeeId" to "EMP-000",
                "email" to email,
                "displayName" to ownerName.trim(),
                "phone" to phone.trim(),
                "role" to UserRole.SUPER_ADMIN.key,
                "status" to AccountStatus.APPROVED.key,
                "allBranchesAccess" to true,
                "assignedBranchIds" to emptyList<String>(),
                "adminUids" to listOf(uid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            dirRef,
            mapOf(
                "companyId" to companyId,
                "ownerUid" to uid,
                "employeeId" to "EMP-000",
                "email" to email,
                "displayName" to ownerName.trim(),
                "phone" to phone.trim(),
                "role" to UserRole.SUPER_ADMIN.key,
                "status" to AccountStatus.APPROVED.key,
                "allBranchesAccess" to true,
                "assignedBranchIds" to emptyList<String>(),
                "linkedUid" to uid,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            branchRef,
            mapOf(
                "companyId" to companyId,
                "ownerUid" to uid,
                "code" to "HQ-001",
                "name" to "Head Office",
                "nameMm" to "ရုံးချုပ်ဆိုင်ခွဲ",
                "address" to address.trim(),
                "phone" to phone.trim(),
                "managerUid" to uid,
                "managerName" to ownerName.trim(),
                "isHeadOffice" to true,
                "isActive" to true,
                "memberUids" to listOf(uid),
                "todaySalesMmk" to 0L,
                "todayProfitMmk" to 0L,
                "todayExpensesMmk" to 0L,
                "todayTransactions" to 0L,
                "productCount" to 0L,
                "customerCount" to 0L,
                "outstandingDebtMmk" to 0L,
                "inventoryValueMmk" to 0L,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            paymentRef,
            buildMap<String, Any> {
                put("companyId", companyId)
                put("companyName", businessNameEn.trim())
                put("ownerUid", uid)
                put("ownerName", ownerName.trim())
                put("ownerEmail", email)
                put("planId", selectedPlan.id)
                put("planNameEn", selectedPlan.nameEn)
                put("planNameMm", selectedPlan.nameMm)
                put("durationMonths", selectedPlan.durationMonths)
                put("durationDays", selectedPlan.durationDays)
                put("amountMmk", selectedPlan.effectivePriceMmk())
                put("paymentMethodId", selectedMethod.id)
                put("paymentMethodName", selectedMethod.nameEn)
                put("transactionRef", transactionRef.trim())
                put("paymentDateText", paymentDateText.trim())
                if (!proofImageBase64.isNullOrBlank()) put("proofImageBase64", proofImageBase64)
                put("status", PaymentVerificationStatus.PENDING_VERIFICATION.key)
                put("receiptNumber", "")
                put("createdBy", uid)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        batch.set(
            logRef,
            mapOf(
                "companyId" to companyId,
                "ownerUid" to uid,
                "actorUid" to uid,
                "actorName" to ownerName.trim(),
                "actorRole" to "BUSINESS_OWNER",
                "action" to "BUSINESS_REGISTERED",
                "targetType" to "COMPANY",
                "targetId" to companyId,
                "details" to "Registered ${businessNameEn.trim()} with plan ${selectedPlan.nameEn} (${selectedPlan.effectivePriceMmk()} MMK)",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(
            userRef,
            mapOf(
                "activeCompanyId" to companyId,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
            return companyId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, compRef.path)
            throw e
        }
    }

    suspend fun submitRenewalPaymentProof(
        company: Company,
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
        transactionRef: String,
        paymentDateText: String,
        proofImageBase64: String?,
    ) {
        val uid = requireUserId()
        val paymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val logId = "plog_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val payRef = db.collection("subscription_payments").document(paymentId)
        val compRef = db.collection("companies").document(company.id)
        val logRef = db.collection("platform_audit_logs").document(logId)

        val batch = db.batch()
        batch.set(
            payRef,
            buildMap<String, Any> {
                put("companyId", company.id)
                put("companyName", company.name)
                put("ownerUid", company.ownerUid)
                put("ownerName", company.ownerName.ifBlank { company.ownerEmail })
                put("ownerEmail", company.ownerEmail)
                put("planId", plan.id)
                put("planNameEn", plan.nameEn)
                put("planNameMm", plan.nameMm)
                put("durationMonths", plan.durationMonths)
                put("durationDays", plan.durationDays)
                put("amountMmk", plan.effectivePriceMmk())
                put("paymentMethodId", method.id)
                put("paymentMethodName", method.nameEn)
                put("transactionRef", transactionRef.trim())
                put("paymentDateText", paymentDateText.trim())
                if (!proofImageBase64.isNullOrBlank()) put("proofImageBase64", proofImageBase64)
                put("status", PaymentVerificationStatus.PENDING_VERIFICATION.key)
                put("receiptNumber", "")
                put("createdBy", uid)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )

        val currentStatus = SubscriptionStatus.fromKey(company.subscriptionStatus)
        if (currentStatus == SubscriptionStatus.PENDING_PAYMENT || currentStatus == SubscriptionStatus.EXPIRED) {
            batch.update(
                compRef,
                mapOf(
                    "subscriptionStatus" to SubscriptionStatus.PENDING_APPROVAL.key,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        batch.set(
            logRef,
            mapOf(
                "companyId" to company.id,
                "ownerUid" to company.ownerUid,
                "actorUid" to uid,
                "actorName" to company.ownerName.ifBlank { company.ownerEmail },
                "actorRole" to "BUSINESS_OWNER",
                "action" to "PAYMENT_SUBMITTED",
                "targetType" to "PAYMENT",
                "targetId" to paymentId,
                "details" to "Submitted ${plan.effectivePriceMmk()} MMK via ${method.nameEn} (Ref: ${transactionRef.trim()})",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, payRef.path)
            throw e
        }
    }

    suspend fun approvePaymentAndActivateSubscription(
        payment: SubscriptionPayment,
        company: Company,
        plan: SubscriptionPlan?,
        actorName: String,
        trustedServerNowMillis: Long,
    ) {
        val uid = requireUserId()
        val effectivePlan = plan ?: SubscriptionPlan(
            id = payment.planId,
            nameEn = payment.planNameEn,
            nameMm = payment.planNameMm,
            durationMonths = payment.durationMonths,
            durationDays = payment.durationDays,
            priceMmk = payment.amountMmk,
            maxBranches = company.maxBranchesAllowed.coerceAtLeast(3),
            maxEmployees = company.maxEmployeesAllowed.coerceAtLeast(15),
            enabledFeatures = PlanFeature.entries.map { it.key },
        )

        val (startMs, expiresMs) = SubscriptionPolicy.calculatePlanStartAndExpiryMillis(
            company = company,
            plan = effectivePlan,
            trustedServerNowMillis = trustedServerNowMillis,
        )
        val startAt = Timestamp(Date(startMs))
        val expiresAt = Timestamp(Date(expiresMs))
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(trustedServerNowMillis))
        val receiptNo = "INV-$dateStamp-${payment.id.takeLast(4).uppercase()}"

        val payRef = db.collection("subscription_payments").document(payment.id)
        val compRef = db.collection("companies").document(company.id)
        val notifRef = compRef.collection("notifications")
            .document("notif_${UUID.randomUUID().toString().replace("-", "").take(12)}")
        val logRef = db.collection("platform_audit_logs")
            .document("plog_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        batch.update(
            payRef,
            mapOf(
                "status" to PaymentVerificationStatus.VERIFIED.key,
                "receiptNumber" to receiptNo,
                "verifiedByUid" to uid,
                "verifiedByName" to actorName,
                "verifiedAt" to FieldValue.serverTimestamp(),
                "grantedStartAt" to startAt,
                "grantedExpiresAt" to expiresAt,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(
            compRef,
            mapOf(
                "subscriptionPlanId" to effectivePlan.id,
                "subscriptionPlanName" to effectivePlan.nameEn,
                "subscriptionStatus" to SubscriptionStatus.ACTIVE.key,
                "subscriptionStartAt" to startAt,
                "subscriptionExpiresAt" to expiresAt,
                "maxBranchesAllowed" to effectivePlan.maxBranches,
                "maxEmployeesAllowed" to effectivePlan.maxEmployees,
                "enabledFeatures" to effectivePlan.enabledFeatures,
                "expiryNotified7d" to false,
                "expiryNotified3d" to false,
                "expiryNotified1d" to false,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            notifRef,
            mapOf(
                "companyId" to company.id,
                "branchId" to "",
                "title" to "Subscription Activated ($receiptNo)",
                "titleMm" to "စာရင်းသွင်းမှု ဖွင့်လှစ်ပြီးပါပြီ ($receiptNo)",
                "message" to "${effectivePlan.nameEn} is now active for ${effectivePlan.durationDays} days.",
                "messageMm" to "${effectivePlan.nameMm} ကို ${effectivePlan.durationDays} ရက်စာ ဖွင့်လှစ်ပေးပြီးပါပြီ။",
                "severity" to "INFO",
                "category" to "BILLING",
                "isRead" to false,
                "memberUids" to listOf(company.ownerUid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            logRef,
            mapOf(
                "companyId" to company.id,
                "ownerUid" to company.ownerUid,
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to "PLATFORM_SUPER_ADMIN",
                "action" to "PAYMENT_VERIFIED_SUBSCRIPTION_ACTIVATED",
                "targetType" to "PAYMENT",
                "targetId" to payment.id,
                "details" to "Verified $receiptNo (${payment.amountMmk} MMK) for ${company.name} -> ${effectivePlan.durationDays}d",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, payRef.path)
            throw e
        }
    }

    suspend fun rejectPayment(
        payment: SubscriptionPayment,
        company: Company?,
        reason: String,
        actorName: String,
        trustedServerNowMillis: Long,
    ) {
        val uid = requireUserId()
        val payRef = db.collection("subscription_payments").document(payment.id)
        val logRef = db.collection("platform_audit_logs")
            .document("plog_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        batch.update(
            payRef,
            mapOf(
                "status" to PaymentVerificationStatus.REJECTED.key,
                "rejectionReason" to reason.trim().ifBlank { "Payment could not be verified" },
                "verifiedByUid" to uid,
                "verifiedByName" to actorName,
                "verifiedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )

        if (company != null) {
            val compRef = db.collection("companies").document(company.id)
            val expiresMs = company.subscriptionExpiresAt?.toDate()?.time
            val fallbackStatus = if (expiresMs != null && expiresMs <= trustedServerNowMillis) {
                SubscriptionStatus.EXPIRED.key
            } else if (expiresMs == null) {
                SubscriptionStatus.PENDING_PAYMENT.key
            } else {
                company.subscriptionStatus
            }
            batch.update(
                compRef,
                mapOf(
                    "subscriptionStatus" to fallbackStatus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            val notifRef = compRef.collection("notifications")
                .document("notif_${UUID.randomUUID().toString().replace("-", "").take(12)}")
            batch.set(
                notifRef,
                mapOf(
                    "companyId" to company.id,
                    "branchId" to "",
                    "title" to "Payment Verification Rejected",
                    "titleMm" to "ငွေပေးချေမှု စစ်ဆေးမှု မအောင်မြင်ပါ",
                    "message" to "Ref ${payment.transactionRef}: ${reason.trim()}",
                    "messageMm" to "Ref ${payment.transactionRef}: ${reason.trim()}",
                    "severity" to "CRITICAL",
                    "category" to "BILLING",
                    "isRead" to false,
                    "memberUids" to listOf(company.ownerUid),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        batch.set(
            logRef,
            mapOf(
                "companyId" to payment.companyId,
                "ownerUid" to payment.ownerUid,
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to "PLATFORM_SUPER_ADMIN",
                "action" to "PAYMENT_REJECTED",
                "targetType" to "PAYMENT",
                "targetId" to payment.id,
                "details" to "Rejected payment ${payment.transactionRef}: ${reason.trim()}",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, payRef.path)
            throw e
        }
    }

    suspend fun updateBusinessSubscriptionStatus(
        company: Company,
        newStatus: SubscriptionStatus,
        actorName: String,
    ) {
        val uid = requireUserId()
        val compRef = db.collection("companies").document(company.id)
        val logRef = db.collection("platform_audit_logs")
            .document("plog_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        batch.update(
            compRef,
            mapOf(
                "subscriptionStatus" to newStatus.key,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            logRef,
            mapOf(
                "companyId" to company.id,
                "ownerUid" to company.ownerUid,
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to "PLATFORM_SUPER_ADMIN",
                "action" to "SUBSCRIPTION_${newStatus.key}",
                "targetType" to "COMPANY",
                "targetId" to company.id,
                "details" to "Set ${company.name} subscription status -> ${newStatus.key}",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, compRef.path)
            throw e
        }
    }

    /**
     * Synchronizes 7d/3d/1d expiry notifications for an active company when server time approaches expiry.
     */
    suspend fun syncOwnerExpiryNotificationsIfNeeded(
        company: Company,
        trustedServerNowMillis: Long,
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (company.ownerUid != uid) return
        val status = SubscriptionPolicy.effectiveStatus(company, trustedServerNowMillis)
        val daysLeft = SubscriptionPolicy.remainingDays(company, trustedServerNowMillis)

        if (status == SubscriptionStatus.EXPIRING_SOON) {
            val need7 = daysLeft <= 7 && !company.expiryNotified7d
            val need3 = daysLeft <= 3 && !company.expiryNotified3d
            val need1 = daysLeft <= 1 && !company.expiryNotified1d
            if (!need7 && !need3 && !need1) return

            val compRef = db.collection("companies").document(company.id)
            val notifRef = compRef.collection("notifications")
                .document("notif_exp_${daysLeft}d")
            val batch = db.batch()
            batch.update(
                compRef,
                buildMap<String, Any> {
                    if (need7) put("expiryNotified7d", true)
                    if (need3) put("expiryNotified3d", true)
                    if (need1) put("expiryNotified1d", true)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
            batch.set(
                notifRef,
                mapOf(
                    "companyId" to company.id,
                    "branchId" to "",
                    "title" to "Subscription Expiring in $daysLeft Day(s)",
                    "titleMm" to "စာရင်းသွင်းမှု $daysLeft ရက်အတွင်း ကုန်ဆုံးပါမည်",
                    "message" to "Renew your ${company.subscriptionPlanName} subscription to prevent interruption.",
                    "messageMm" to "POS အသုံးပြုမှုမပြတ်တောက်စေရန် $daysLeft ရက်အတွင်း သက်တမ်းတိုးပါ။",
                    "severity" to if (daysLeft <= 1) "CRITICAL" else "WARNING",
                    "category" to "EXPIRY",
                    "isRead" to false,
                    "memberUids" to listOf(company.ownerUid),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            runCatching { batch.commit().await() }
        }
    }
}
