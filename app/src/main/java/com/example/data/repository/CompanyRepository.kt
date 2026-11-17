package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.firebase.unwrapFirestoreException
import com.example.core.rbac.AccountStatus
import com.example.core.rbac.UserRole
import com.example.core.subscription.SubscriptionPolicy
import com.example.domain.model.AccountDirectoryEntry
import com.example.domain.model.AppNotification
import com.example.domain.model.AuditLog
import com.example.domain.model.Branch
import com.example.domain.model.Company
import com.example.domain.model.CompanyMember
import com.example.domain.model.PlanFeature
import com.example.domain.model.SubscriptionStatus
import com.example.domain.model.UserProfile
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
import java.util.Date
import java.util.UUID

class CompanyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
    )

    companion object {
        const val BOOTSTRAPPED_OWNER_EMAIL = "storelinnhtet@gmail.com"

        fun emailKey(email: String): String =
            email.trim().lowercase().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

        fun isPlatformSuperAdminEmail(email: String?): Boolean =
            email?.trim().equals(BOOTSTRAPPED_OWNER_EMAIL, ignoreCase = true)
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    suspend fun ensureUserProfile(
        preferredLanguage: String,
        preferredTheme: String,
    ): UserProfile {
        val user = auth.currentUser
            ?: throw IllegalStateException("User must be signed in with Google.")
        val uid = user.uid
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            val email = (user.email?.takeIf { it.isNotBlank() } ?: "$uid@minimart.local").lowercase()
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@")
            val photoUrl = user.photoUrl?.toString()
            if (!snap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("email", email)
                    put("displayName", displayName)
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl)
                    put("preferredLanguage", preferredLanguage)
                    put("preferredTheme", preferredTheme)
                    put("lastServerTimeAt", FieldValue.serverTimestamp())
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
                return docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(
                    id = uid,
                    userId = uid,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    preferredLanguage = preferredLanguage,
                    preferredTheme = preferredTheme,
                )
            } else {
                val updates = buildMap<String, Any> {
                    put("email", email)
                    put("displayName", displayName)
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl)
                    put("lastServerTimeAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
                return docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = uid, userId = uid, email = email, displayName = displayName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    /**
     * Fetches trusted server timestamp from Firestore backend to prevent device clock tampering.
     */
    suspend fun fetchTrustedServerTimeMillis(uid: String): Long {
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "lastServerTimeAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            val fresh = docRef.get().await()
            fresh.getTimestamp("lastServerTimeAt")?.toDate()?.time
                ?: fresh.getTimestamp("updatedAt")?.toDate()?.time
                ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    suspend fun updateUserPreferences(
        preferredLanguage: String,
        preferredTheme: String,
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "preferredLanguage" to preferredLanguage,
                    "preferredTheme" to preferredTheme,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(uid)
        docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAccountDirectoryForEmail(email: String): Flow<AccountDirectoryEntry?> = flow {
        val uid = requireUserId()
        val key = emailKey(email)
        val docRef = db.collection("account_directory").document(key)
        docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    AccountDirectoryEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(null)
                    return@catch
                }
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeOwnerAccountDirectory(ownerUid: String): Flow<List<AccountDirectoryEntry>> = flow {
        val uid = requireUserId()
        val path = "account_directory"
        db.collection("account_directory")
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AccountDirectoryEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.employeeId }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun claimApprovedEmployeeAccount(entry: AccountDirectoryEntry) {
        val uid = requireUserId()
        val compRef = db.collection("companies").document(entry.companyId)
        val memberRef = compRef.collection("members").document(uid)
        val dirRef = db.collection("account_directory").document(emailKey(entry.email))
        val userRef = db.collection("users").document(uid)
        val auditRef = compRef.collection("audit_logs")
            .document("log_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        try {
            val memberSnap = memberRef.get().await()
            if (!memberSnap.exists()) {
                memberRef.set(
                    buildMap<String, Any> {
                        put("companyId", entry.companyId)
                        put("ownerUid", entry.ownerUid)
                        put("userId", uid)
                        put("employeeId", entry.employeeId)
                        put("email", entry.email.lowercase())
                        put("displayName", entry.displayName)
                        if (entry.phone.isNotBlank()) put("phone", entry.phone)
                        put("role", entry.role)
                        put("status", AccountStatus.APPROVED.key)
                        put("allBranchesAccess", entry.allBranchesAccess)
                        put("assignedBranchIds", entry.assignedBranchIds)
                        put("adminUids", listOf(entry.ownerUid))
                        put("createdBy", uid)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                ).await()
            }

            val batch = db.batch()
            if (entry.linkedUid != uid) {
                batch.update(
                    dirRef,
                    mapOf(
                        "linkedUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.update(
                userRef,
                mapOf(
                    "activeCompanyId" to entry.companyId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "companyId" to entry.companyId,
                    "branchId" to (entry.assignedBranchIds.firstOrNull() ?: ""),
                    "actorUid" to uid,
                    "actorName" to entry.displayName,
                    "action" to "LOGIN_ACTIVITY",
                    "targetType" to "ACCOUNT",
                    "targetId" to uid,
                    "details" to "Approved employee ${entry.displayName} (${entry.employeeId}) signed in",
                    "adminUids" to listOf(entry.ownerUid),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, memberRef.path)
            throw e
        }
    }

    fun observeCompany(companyId: String): Flow<Company?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("companies").document(companyId)
        docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    Company::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(null)
                    return@catch
                }
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeMember(companyId: String, uid: String): Flow<CompanyMember?> = flow {
        val docRef = db.collection("companies").document(companyId)
            .collection("members").document(uid)
        docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    CompanyMember::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(null)
                    return@catch
                }
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeBranches(companyId: String, uid: String): Flow<List<Branch>> = flow {
        val path = "companies/$companyId/branches"
        db.collection("companies").document(companyId)
            .collection("branches")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Branch::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareByDescending<Branch> { it.isHeadOffice }.thenBy { it.code })
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(emptyList())
                    return@catch
                }
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAuditLogs(companyId: String, uid: String): Flow<List<AuditLog>> = flow {
        val path = "companies/$companyId/audit_logs"
        db.collection("companies").document(companyId)
            .collection("audit_logs")
            .whereArrayContains("adminUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AuditLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeNotifications(companyId: String, uid: String): Flow<List<AppNotification>> = flow {
        val path = "companies/$companyId/notifications"
        db.collection("companies").document(companyId)
            .collection("notifications")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AppNotification::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    /**
     * Platform Super Admin creates their own flagship enterprise store.
     */
    suspend fun createCompanyWithDefaultBranch(
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
    ): Pair<String, String> {
        val uid = requireUserId()
        val user = auth.currentUser!!
        val email = (user.email?.takeIf { it.isNotBlank() } ?: "$uid@minimart.local").lowercase()
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@")

        val companyId = "comp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val branchId = "branch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val auditId = "log_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val companyRef = db.collection("companies").document(companyId)
        val memberRef = companyRef.collection("members").document(uid)
        val dirRef = db.collection("account_directory").document(emailKey(email))
        val branchRef = companyRef.collection("branches").document(branchId)
        val auditRef = companyRef.collection("audit_logs").document(auditId)
        val notifRef = companyRef.collection("notifications").document(notifId)
        val userRef = db.collection("users").document(uid)

        val ownerExpiry = Timestamp(Date(System.currentTimeMillis() + 3650L * SubscriptionPolicy.DAY_MILLIS))

        val batch = db.batch()
        batch.set(
            companyRef,
            buildMap<String, Any> {
                put("ownerUid", uid)
                put("ownerName", displayName)
                put("ownerEmail", email)
                put("name", companyNameEn.trim())
                put("nameMm", companyNameMm.trim())
                put("phone", phone.trim())
                put("address", address.trim())
                if (!taxId.isNullOrBlank()) put("taxId", taxId.trim())
                put("currency", "MMK")
                put("defaultLanguage", defaultLanguage)
                put("taxRatePercent", taxRatePercent.coerceIn(0.0, 50.0))
                put("allowNegativeStock", false)
                put("offlineLimitMmk", 500_000L)
                put("subscriptionPlanId", "platform_owner")
                put("subscriptionPlanName", "Platform Owner Enterprise")
                put("subscriptionStatus", SubscriptionStatus.ACTIVE.key)
                put("subscriptionStartAt", FieldValue.serverTimestamp())
                put("subscriptionExpiresAt", ownerExpiry)
                put("maxBranchesAllowed", 200)
                put("maxEmployeesAllowed", 1000)
                put("enabledFeatures", PlanFeature.entries.map { it.key })
                put("activeBranchCount", 1)
                put("activeEmployeeCount", 1)
                put("expiryNotified7d", false)
                put("expiryNotified3d", false)
                put("expiryNotified1d", false)
                put("createdBy", uid)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        batch.set(
            memberRef,
            mapOf(
                "companyId" to companyId,
                "ownerUid" to uid,
                "userId" to uid,
                "employeeId" to "EMP-000",
                "email" to email,
                "displayName" to displayName,
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
                "displayName" to displayName,
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
                "code" to branchCode.trim().uppercase(),
                "name" to branchNameEn.trim(),
                "nameMm" to branchNameMm.trim(),
                "address" to address.trim(),
                "phone" to phone.trim(),
                "managerUid" to uid,
                "managerName" to displayName,
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
            auditRef,
            mapOf(
                "companyId" to companyId,
                "branchId" to branchId,
                "actorUid" to uid,
                "actorName" to displayName,
                "action" to "OWNER_PROVISIONED",
                "targetType" to "COMPANY",
                "targetId" to companyId,
                "details" to "Owner provisioned ${companyNameEn.trim()} and Head Office ${branchCode.trim().uppercase()}",
                "adminUids" to listOf(uid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            notifRef,
            mapOf(
                "companyId" to companyId,
                "branchId" to branchId,
                "title" to "Organization Initialized",
                "titleMm" to "လုပ်ငန်းစနစ် စတင်ဖွင့်လှစ်ပြီးပါပြီ",
                "message" to "${companyNameEn.trim()} is active with SaaS & Owner-controlled security.",
                "messageMm" to "${companyNameMm.trim()} ကို စတင်ဖွင့်လှစ်ပြီးပါပြီ။",
                "severity" to "INFO",
                "category" to "SYSTEM",
                "isRead" to false,
                "memberUids" to listOf(uid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
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
            return companyId to branchId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, companyRef.path)
            throw e
        }
    }

    suspend fun createBranch(
        company: Company,
        currentBranchCount: Int,
        actorName: String,
        code: String,
        nameEn: String,
        nameMm: String,
        address: String,
        phone: String,
        managerUid: String?,
        managerName: String?,
        isHeadOffice: Boolean,
        adminUids: List<String>,
    ): String {
        val uid = requireUserId()
        if (!SubscriptionPolicy.canCreateAnotherBranch(company, currentBranchCount)) {
            throw IllegalStateException("Branch limit reached (${company.maxBranchesAllowed}). Upgrade subscription plan.")
        }

        val branchId = "branch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val auditId = "log_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val compRef = db.collection("companies").document(company.id)
        val branchRef = compRef.collection("branches").document(branchId)
        val auditRef = compRef.collection("audit_logs").document(auditId)

        val members = buildSet {
            add(company.ownerUid)
            add(uid)
            addAll(adminUids)
            if (!managerUid.isNullOrBlank()) add(managerUid)
        }.toList().take(150)

        val batch = db.batch()
        batch.set(
            branchRef,
            buildMap<String, Any> {
                put("companyId", company.id)
                put("ownerUid", company.ownerUid)
                put("code", code.trim().uppercase())
                put("name", nameEn.trim())
                put("nameMm", nameMm.trim())
                put("address", address.trim())
                put("phone", phone.trim())
                if (!managerUid.isNullOrBlank()) put("managerUid", managerUid)
                if (!managerName.isNullOrBlank()) put("managerName", managerName)
                put("isHeadOffice", isHeadOffice)
                put("isActive", true)
                put("memberUids", members)
                put("todaySalesMmk", 0L)
                put("todayProfitMmk", 0L)
                put("todayExpensesMmk", 0L)
                put("todayTransactions", 0L)
                put("productCount", 0L)
                put("customerCount", 0L)
                put("outstandingDebtMmk", 0L)
                put("inventoryValueMmk", 0L)
                put("createdBy", uid)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        batch.update(
            compRef,
            mapOf(
                "activeBranchCount" to (currentBranchCount + 1),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            auditRef,
            mapOf(
                "companyId" to company.id,
                "branchId" to branchId,
                "actorUid" to uid,
                "actorName" to actorName,
                "action" to "BRANCH_CREATED",
                "targetType" to "BRANCH",
                "targetId" to branchId,
                "details" to "Created branch ${code.trim().uppercase()} (${nameEn.trim()})",
                "adminUids" to (adminUids + company.ownerUid).distinct().take(20),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        try {
            batch.commit().await()
            return branchId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, branchRef.path)
            throw e
        }
    }

    suspend fun updateBranch(
        company: Company,
        branch: Branch,
        actorName: String,
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
        adminUids: List<String>,
    ) {
        val uid = requireUserId()
        val branchRef = db.collection("companies").document(company.id)
            .collection("branches").document(branch.id)
        val auditRef = db.collection("companies").document(company.id)
            .collection("audit_logs").document("log_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val updatedMembers = buildSet {
            addAll(branch.memberUids)
            add(company.ownerUid)
            if (!managerUid.isNullOrBlank()) add(managerUid)
        }.toList().take(150)

        val updates = if (isAdmin) {
            buildMap<String, Any> {
                put("code", code.trim().uppercase())
                put("name", nameEn.trim())
                put("nameMm", nameMm.trim())
                put("address", address.trim())
                put("phone", phone.trim())
                put("managerUid", managerUid ?: "")
                put("managerName", managerName ?: "")
                put("isHeadOffice", isHeadOffice)
                put("isActive", isActive)
                put("memberUids", updatedMembers)
                put("updatedAt", FieldValue.serverTimestamp())
            }
        } else {
            mapOf(
                "phone" to phone.trim(),
                "address" to address.trim(),
                "isActive" to isActive,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        }

        val batch = db.batch()
        batch.update(branchRef, updates)
        batch.set(
            auditRef,
            mapOf(
                "companyId" to company.id,
                "branchId" to branch.id,
                "actorUid" to uid,
                "actorName" to actorName,
                "action" to "BRANCH_UPDATED",
                "targetType" to "BRANCH",
                "targetId" to branch.id,
                "details" to "Updated branch ${code.trim().uppercase()} (active=$isActive)",
                "adminUids" to (adminUids + company.ownerUid).distinct().take(20),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, branchRef.path)
            throw e
        }
    }

    suspend fun upsertOwnerEmployeeAccount(
        company: Company,
        currentAccountCount: Int,
        actorName: String,
        existingEntry: AccountDirectoryEntry?,
        employeeId: String,
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        status: AccountStatus,
        allBranchesAccess: Boolean,
        assignedBranchIds: List<String>,
        allBranches: List<Branch>,
    ) {
        val uid = requireUserId()
        if (existingEntry == null && !SubscriptionPolicy.canAddAnotherEmployee(company, currentAccountCount)) {
            throw IllegalStateException("Employee limit reached (${company.maxEmployeesAllowed}). Upgrade subscription plan.")
        }

        val normalizedEmail = email.trim().lowercase()
        val key = emailKey(normalizedEmail)
        val dirRef = db.collection("account_directory").document(key)
        val compRef = db.collection("companies").document(company.id)
        val auditRef = compRef.collection("audit_logs")
            .document("log_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val effectiveBranches = if (allBranchesAccess) emptyList() else assignedBranchIds.take(50)
        val batch = db.batch()
        if (existingEntry == null) {
            batch.set(
                dirRef,
                mapOf(
                    "companyId" to company.id,
                    "ownerUid" to company.ownerUid,
                    "employeeId" to employeeId.trim().uppercase(),
                    "email" to normalizedEmail,
                    "displayName" to displayName.trim(),
                    "phone" to phone.trim(),
                    "role" to role.key,
                    "status" to status.key,
                    "allBranchesAccess" to allBranchesAccess,
                    "assignedBranchIds" to effectiveBranches,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.update(
                compRef,
                mapOf(
                    "activeEmployeeCount" to (currentAccountCount + 1),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        } else {
            batch.update(
                dirRef,
                mapOf(
                    "employeeId" to employeeId.trim().uppercase(),
                    "displayName" to displayName.trim(),
                    "phone" to phone.trim(),
                    "role" to role.key,
                    "status" to status.key,
                    "allBranchesAccess" to allBranchesAccess,
                    "assignedBranchIds" to effectiveBranches,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            existingEntry.linkedUid?.takeIf { it.isNotBlank() }?.let { linkedUid ->
                val memberRef = compRef.collection("members").document(linkedUid)
                batch.update(
                    memberRef,
                    mapOf(
                        "employeeId" to employeeId.trim().uppercase(),
                        "displayName" to displayName.trim(),
                        "phone" to phone.trim(),
                        "role" to role.key,
                        "status" to status.key,
                        "allBranchesAccess" to allBranchesAccess,
                        "assignedBranchIds" to effectiveBranches,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                syncBranchMembersInBatch(
                    batch = batch,
                    company = company,
                    targetUid = linkedUid,
                    isApproved = status == AccountStatus.APPROVED,
                    allBranchesAccess = allBranchesAccess,
                    assignedBranchIds = effectiveBranches,
                    allBranches = allBranches,
                )
            }
        }

        val actionName = when {
            existingEntry == null -> "ACCOUNT_CREATED"
            existingEntry.role != role.key -> "ROLE_CHANGED"
            existingEntry.assignedBranchIds != effectiveBranches -> "BRANCH_ASSIGNMENT_CHANGED"
            else -> "ACCOUNT_UPDATED"
        }
        batch.set(
            auditRef,
            mapOf(
                "companyId" to company.id,
                "branchId" to (effectiveBranches.firstOrNull() ?: ""),
                "actorUid" to uid,
                "actorName" to actorName,
                "action" to actionName,
                "targetType" to "ACCOUNT",
                "targetId" to normalizedEmail,
                "details" to "${employeeId.trim().uppercase()} • ${displayName.trim()} (${role.key}, ${status.key})",
                "adminUids" to listOf(company.ownerUid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, dirRef.path)
            throw e
        }
    }

    suspend fun changeEmployeeAccountStatus(
        company: Company,
        actorName: String,
        entry: AccountDirectoryEntry,
        newStatus: AccountStatus,
        allBranches: List<Branch>,
    ) {
        val uid = requireUserId()
        val key = emailKey(entry.email)
        val dirRef = db.collection("account_directory").document(key)
        val auditRef = db.collection("companies").document(company.id)
            .collection("audit_logs").document("log_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        batch.update(
            dirRef,
            mapOf(
                "status" to newStatus.key,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        entry.linkedUid?.takeIf { it.isNotBlank() }?.let { linkedUid ->
            val memberRef = db.collection("companies").document(company.id)
                .collection("members").document(linkedUid)
            batch.update(
                memberRef,
                mapOf(
                    "status" to newStatus.key,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            syncBranchMembersInBatch(
                batch = batch,
                company = company,
                targetUid = linkedUid,
                isApproved = newStatus == AccountStatus.APPROVED,
                allBranchesAccess = entry.allBranchesAccess,
                assignedBranchIds = entry.assignedBranchIds,
                allBranches = allBranches,
            )
        }

        val actionName = when (newStatus) {
            AccountStatus.APPROVED -> if (entry.status == AccountStatus.SUSPENDED.key || entry.status == AccountStatus.REVOKED.key) {
                "ACCOUNT_REACTIVATED"
            } else {
                "ACCOUNT_APPROVED"
            }
            AccountStatus.SUSPENDED -> "ACCOUNT_SUSPENDED"
            AccountStatus.REVOKED -> if (entry.status == AccountStatus.PENDING.key) "ACCOUNT_REJECTED" else "ACCOUNT_REVOKED"
            AccountStatus.PENDING -> "ACCOUNT_PENDING"
        }

        batch.set(
            auditRef,
            mapOf(
                "companyId" to company.id,
                "branchId" to (entry.assignedBranchIds.firstOrNull() ?: ""),
                "actorUid" to uid,
                "actorName" to actorName,
                "action" to actionName,
                "targetType" to "ACCOUNT",
                "targetId" to entry.email,
                "details" to "${entry.employeeId} • ${entry.displayName} -> ${newStatus.key}",
                "adminUids" to listOf(company.ownerUid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, dirRef.path)
            throw e
        }
    }

    private fun syncBranchMembersInBatch(
        batch: com.google.firebase.firestore.WriteBatch,
        company: Company,
        targetUid: String,
        isApproved: Boolean,
        allBranchesAccess: Boolean,
        assignedBranchIds: List<String>,
        allBranches: List<Branch>,
    ) {
        allBranches.forEach { branch ->
            val shouldHaveAccess = isApproved &&
                (allBranchesAccess || assignedBranchIds.contains(branch.id) || targetUid == company.ownerUid)
            val newMembers = if (shouldHaveAccess) {
                (branch.memberUids + targetUid).distinct().take(150)
            } else {
                if (targetUid == company.ownerUid) branch.memberUids else branch.memberUids.filterNot { it == targetUid }
            }
            if (newMembers != branch.memberUids) {
                val branchRef = db.collection("companies").document(company.id)
                    .collection("branches").document(branch.id)
                batch.update(
                    branchRef,
                    mapOf(
                        "memberUids" to newMembers,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
        }
    }

    suspend fun updateCompanySettings(
        company: Company,
        actorName: String,
        nameEn: String,
        nameMm: String,
        phone: String,
        address: String,
        taxId: String?,
        taxRatePercent: Double,
        defaultLanguage: String,
        allowNegativeStock: Boolean,
        offlineLimitMmk: Long,
    ) {
        val uid = requireUserId()
        val compRef = db.collection("companies").document(company.id)
        val auditRef = compRef.collection("audit_logs")
            .document("log_${UUID.randomUUID().toString().replace("-", "").take(12)}")

        val batch = db.batch()
        batch.update(
            compRef,
            buildMap<String, Any> {
                put("name", nameEn.trim())
                put("nameMm", nameMm.trim())
                put("phone", phone.trim())
                put("address", address.trim())
                put("taxId", taxId?.trim() ?: "")
                put("defaultLanguage", defaultLanguage)
                put("taxRatePercent", taxRatePercent.coerceIn(0.0, 50.0))
                put("allowNegativeStock", allowNegativeStock)
                put("offlineLimitMmk", offlineLimitMmk.coerceIn(0L, 100_000_000L))
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        batch.set(
            auditRef,
            mapOf(
                "companyId" to company.id,
                "actorUid" to uid,
                "actorName" to actorName,
                "action" to "SETTINGS_UPDATED",
                "targetType" to "COMPANY",
                "targetId" to company.id,
                "details" to "Updated company profile & POS policies",
                "adminUids" to listOf(company.ownerUid),
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

    suspend fun markNotificationRead(companyId: String, notificationId: String) {
        requireUserId()
        val notifRef = db.collection("companies").document(companyId)
            .collection("notifications").document(notificationId)
        try {
            notifRef.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, notifRef.path)
            throw e
        }
    }
}
