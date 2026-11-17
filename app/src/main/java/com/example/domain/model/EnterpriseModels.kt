package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val activeCompanyId: String? = null,
    val preferredLanguage: String = "my",
    val preferredTheme: String = "system",
    val lastServerTimeAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AccountDirectoryEntry(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val ownerUid: String = "",
    val employeeId: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val role: String = "CASHIER",
    val status: String = "PENDING",
    val allBranchesAccess: Boolean = false,
    val assignedBranchIds: List<String> = emptyList(),
    val linkedUid: String? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Company(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val name: String = "",
    val nameMm: String = "",
    val phone: String = "",
    val address: String = "",
    val taxId: String? = null,
    val currency: String = "MMK",
    val defaultLanguage: String = "my",
    val taxRatePercent: Double = 5.0,
    val allowNegativeStock: Boolean = false,
    val offlineLimitMmk: Long = 500_000L,
    val subscriptionPlanId: String = "",
    val subscriptionPlanName: String = "",
    val subscriptionStatus: String = SubscriptionStatus.PENDING_PAYMENT.key,
    val subscriptionStartAt: Timestamp? = null,
    val subscriptionExpiresAt: Timestamp? = null,
    val maxBranchesAllowed: Int = 1,
    val maxEmployeesAllowed: Int = 5,
    val enabledFeatures: List<String> = emptyList(),
    val activeBranchCount: Int = 1,
    val activeEmployeeCount: Int = 1,
    val expiryNotified7d: Boolean = false,
    val expiryNotified3d: Boolean = false,
    val expiryNotified1d: Boolean = false,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun localizedName(isMyanmar: Boolean): String =
        if (isMyanmar && nameMm.isNotBlank()) nameMm else name.ifBlank { nameMm }
}

@IgnoreExtraProperties
data class CompanyMember(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val ownerUid: String = "",
    val userId: String = "",
    val employeeId: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val role: String = "CASHIER",
    val status: String = "PENDING",
    val allBranchesAccess: Boolean = false,
    val assignedBranchIds: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Branch(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val ownerUid: String = "",
    val code: String = "",
    val name: String = "",
    val nameMm: String = "",
    val address: String = "",
    val phone: String = "",
    val managerUid: String? = null,
    val managerName: String? = null,
    val isHeadOffice: Boolean = false,
    val isActive: Boolean = true,
    val memberUids: List<String> = emptyList(),
    val todaySalesMmk: Long = 0L,
    val todayProfitMmk: Long = 0L,
    val todayExpensesMmk: Long = 0L,
    val todayTransactions: Long = 0L,
    val productCount: Long = 0L,
    val customerCount: Long = 0L,
    val outstandingDebtMmk: Long = 0L,
    val inventoryValueMmk: Long = 0L,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val netProfitMmk: Long get() = todayProfitMmk - todayExpensesMmk

    fun localizedName(isMyanmar: Boolean): String =
        if (isMyanmar && nameMm.isNotBlank()) nameMm else name.ifBlank { nameMm }
}

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val branchId: String? = null,
    val actorUid: String = "",
    val actorName: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    val adminUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val branchId: String = "",
    val title: String = "",
    val titleMm: String = "",
    val message: String = "",
    val messageMm: String = "",
    val severity: String = "INFO",
    val category: String = "SYSTEM",
    val isRead: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun localizedTitle(isMyanmar: Boolean): String =
        if (isMyanmar && titleMm.isNotBlank()) titleMm else title

    fun localizedMessage(isMyanmar: Boolean): String =
        if (isMyanmar && messageMm.isNotBlank()) messageMm else message
}
