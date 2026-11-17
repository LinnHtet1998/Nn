package com.example.domain.model

import com.example.core.i18n.AppLanguage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SubscriptionStatus(
    val key: String,
    val labelEn: String,
    val labelMm: String,
) {
    PENDING_PAYMENT("PENDING_PAYMENT", "Pending Payment", "ငွေပေးချေရန်စောင့်ဆိုင်း"),
    PENDING_APPROVAL("PENDING_APPROVAL", "Pending Approval", "အတည်ပြုရန်စောင့်ဆိုင်း"),
    ACTIVE("ACTIVE", "Active", "သက်တမ်းရှိ"),
    EXPIRING_SOON("EXPIRING_SOON", "Expiring Soon", "သက်တမ်းကုန်ခါနီး"),
    EXPIRED("EXPIRED", "Expired", "သက်တမ်းကုန်ဆုံးပြီး"),
    SUSPENDED("SUSPENDED", "Suspended", "ယာယီရပ်ဆိုင်း"),
    CANCELLED("CANCELLED", "Cancelled", "ပယ်ဖျက်ပြီး");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.MYANMAR -> labelMm
        AppLanguage.ENGLISH -> labelEn
    }

    fun isOperational(): Boolean = this == ACTIVE || this == EXPIRING_SOON

    companion object {
        fun fromKey(key: String?): SubscriptionStatus =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PENDING_PAYMENT
    }
}

enum class PaymentVerificationStatus(
    val key: String,
    val labelEn: String,
    val labelMm: String,
) {
    PENDING_VERIFICATION("PENDING_VERIFICATION", "Pending Verification", "စစ်ဆေးနေဆဲ"),
    VERIFIED("VERIFIED", "Verified", "အတည်ပြုပြီး"),
    REJECTED("REJECTED", "Rejected", "ငြင်းပယ်ပြီး");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.MYANMAR -> labelMm
        AppLanguage.ENGLISH -> labelEn
    }

    companion object {
        fun fromKey(key: String?): PaymentVerificationStatus =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PENDING_VERIFICATION
    }
}

enum class PaymentMethodProviderType(
    val key: String,
    val labelEn: String,
    val labelMm: String,
) {
    MANUAL_TRANSFER("MANUAL_TRANSFER", "Manual Slip / Bank Transfer", "ဘဏ်/Wallet လွှဲပြီးစလစ်တင်ခြင်း"),
    GATEWAY_WEBHOOK("GATEWAY_WEBHOOK", "Automated Payment Gateway", "အလိုအလျောက် ငွေပေးချေမှုစနစ်");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.MYANMAR -> labelMm
        AppLanguage.ENGLISH -> labelEn
    }

    companion object {
        fun fromKey(key: String?): PaymentMethodProviderType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: MANUAL_TRANSFER
    }
}

enum class PlanFeature(
    val key: String,
    val labelEn: String,
    val labelMm: String,
) {
    MULTI_BRANCH("MULTI_BRANCH", "Multi-Branch Sync", "ဆိုင်ခွဲစုံ ချိတ်ဆက်မှု"),
    OFFLINE_POS("OFFLINE_POS", "Offline POS Queue", "အော့ဖ်လိုင်း POS"),
    ANALYTICS("ANALYTICS", "Enterprise Analytics", "စီးပွားရေး ခွဲခြမ်းစိတ်ဖြာမှု"),
    PAYROLL("PAYROLL", "Employee Payroll", "ဝန်ထမ်းလစာစနစ်"),
    CREDIT_DEBT("CREDIT_DEBT", "Customer Credit / Debt", "ဖောက်သည်အကြွေးစာရင်း"),
    EXPORT_REPORTS("EXPORT_REPORTS", "PDF / Excel Export", "PDF / Excel ထုတ်ယူမှု");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.MYANMAR -> labelMm
        AppLanguage.ENGLISH -> labelEn
    }
}

@IgnoreExtraProperties
data class SubscriptionPlan(
    @DocumentId val id: String = "",
    val nameEn: String = "",
    val nameMm: String = "",
    val descriptionEn: String = "",
    val descriptionMm: String = "",
    val durationMonths: Int = 1,
    val durationDays: Int = 30,
    val priceMmk: Long = 0L,
    val discountPercent: Double = 0.0,
    val promoPriceMmk: Long = 0L,
    val promoLabel: String? = null,
    val maxBranches: Int = 1,
    val maxEmployees: Int = 5,
    val enabledFeatures: List<String> = emptyList(),
    val isActive: Boolean = true,
    val sortOrder: Int = 1,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun localizedName(isMyanmar: Boolean): String =
        if (isMyanmar && nameMm.isNotBlank()) nameMm else nameEn.ifBlank { nameMm }

    fun effectivePriceMmk(): Long =
        if (discountPercent > 0.0 && promoPriceMmk in 0..priceMmk) promoPriceMmk else priceMmk
}

@IgnoreExtraProperties
data class PaymentMethodConfig(
    @DocumentId val id: String = "",
    val nameEn: String = "",
    val nameMm: String = "",
    val providerType: String = PaymentMethodProviderType.MANUAL_TRANSFER.key,
    val accountTitle: String = "",
    val accountNumber: String = "",
    val instructionsEn: String = "",
    val instructionsMm: String = "",
    val gatewayEndpoint: String? = null,
    val isActive: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun localizedName(isMyanmar: Boolean): String =
        if (isMyanmar && nameMm.isNotBlank()) nameMm else nameEn.ifBlank { nameMm }

    fun localizedInstructions(isMyanmar: Boolean): String =
        if (isMyanmar && instructionsMm.isNotBlank()) instructionsMm else instructionsEn
}

@IgnoreExtraProperties
data class SubscriptionPayment(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val planId: String = "",
    val planNameEn: String = "",
    val planNameMm: String = "",
    val durationMonths: Int = 1,
    val durationDays: Int = 30,
    val amountMmk: Long = 0L,
    val paymentMethodId: String = "",
    val paymentMethodName: String = "",
    val transactionRef: String = "",
    val paymentDateText: String = "",
    val proofImageBase64: String? = null,
    val proofStorageUrl: String? = null,
    val status: String = PaymentVerificationStatus.PENDING_VERIFICATION.key,
    val receiptNumber: String = "",
    val rejectionReason: String? = null,
    val grantedStartAt: Timestamp? = null,
    val grantedExpiresAt: Timestamp? = null,
    val verifiedByUid: String? = null,
    val verifiedByName: String? = null,
    val verifiedAt: Timestamp? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun localizedPlanName(isMyanmar: Boolean): String =
        if (isMyanmar && planNameMm.isNotBlank()) planNameMm else planNameEn
}

@IgnoreExtraProperties
data class PlatformAuditLog(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val ownerUid: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class PlatformDashboardMetrics(
    val totalBusinesses: Int = 0,
    val activeSubscriptions: Int = 0,
    val expiringSoonCount: Int = 0,
    val expiredSubscriptions: Int = 0,
    val suspendedOrCancelledCount: Int = 0,
    val pendingApprovalsCount: Int = 0,
    val pendingPaymentsCount: Int = 0,
    val monthlyRevenueMmk: Long = 0L,
    val sixMonthRevenueMmk: Long = 0L,
    val annualRevenueMmk: Long = 0L,
    val totalCollectedRevenueMmk: Long = 0L,
    val upcomingExpirations: List<Company> = emptyList(),
)
