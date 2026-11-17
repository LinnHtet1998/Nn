package com.example.core.subscription

import com.example.domain.model.Company
import com.example.domain.model.PaymentVerificationStatus
import com.example.domain.model.PlatformDashboardMetrics
import com.example.domain.model.SubscriptionPayment
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import kotlin.math.ceil
import kotlin.math.roundToLong

object SubscriptionPolicy {
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    const val EXPIRING_SOON_DAYS = 7L

    /**
     * Evaluates effective subscription status against TRUSTED server time (never device clock alone).
     */
    fun effectiveStatus(company: Company, trustedServerNowMillis: Long): SubscriptionStatus {
        val stored = SubscriptionStatus.fromKey(company.subscriptionStatus)
        if (stored == SubscriptionStatus.SUSPENDED ||
            stored == SubscriptionStatus.CANCELLED ||
            stored == SubscriptionStatus.PENDING_PAYMENT ||
            stored == SubscriptionStatus.PENDING_APPROVAL
        ) {
            return stored
        }

        val expiresAtMillis = company.subscriptionExpiresAt?.toDate()?.time
            ?: return SubscriptionStatus.EXPIRED

        if (expiresAtMillis <= trustedServerNowMillis) {
            return SubscriptionStatus.EXPIRED
        }

        val daysLeft = remainingDays(company, trustedServerNowMillis)
        return if (daysLeft <= EXPIRING_SOON_DAYS) {
            SubscriptionStatus.EXPIRING_SOON
        } else {
            SubscriptionStatus.ACTIVE
        }
    }

    fun remainingDays(company: Company, trustedServerNowMillis: Long): Long {
        val expiresAtMillis = company.subscriptionExpiresAt?.toDate()?.time ?: return 0L
        val diff = expiresAtMillis - trustedServerNowMillis
        if (diff <= 0L) return 0L
        return ceil(diff.toDouble() / DAY_MILLIS.toDouble()).toLong().coerceAtLeast(1L)
    }

    fun isOperationalAccessAllowed(company: Company, trustedServerNowMillis: Long): Boolean {
        val effective = effectiveStatus(company, trustedServerNowMillis)
        return effective.isOperational()
    }

    fun calculateDiscountedPriceMmk(priceMmk: Long, discountPercent: Double): Long {
        val safePrice = priceMmk.coerceAtLeast(0L)
        val clampedDiscount = discountPercent.coerceIn(0.0, 100.0)
        return (safePrice * (1.0 - clampedDiscount / 100.0)).roundToLong().coerceIn(0L, safePrice)
    }

    fun canCreateAnotherBranch(company: Company, currentBranchCount: Int): Boolean =
        currentBranchCount < company.maxBranchesAllowed.coerceAtLeast(1)

    fun canAddAnotherEmployee(company: Company, currentEmployeeCount: Int): Boolean =
        currentEmployeeCount < company.maxEmployeesAllowed.coerceAtLeast(1)

    fun computePlatformMetrics(
        companies: List<Company>,
        payments: List<SubscriptionPayment>,
        trustedServerNowMillis: Long,
    ): PlatformDashboardMetrics {
        var active = 0
        var expiringSoon = 0
        var expired = 0
        var suspendedOrCancelled = 0
        var pendingApprovals = 0
        val upcoming = mutableListOf<Company>()

        companies.forEach { company ->
            when (effectiveStatus(company, trustedServerNowMillis)) {
                SubscriptionStatus.ACTIVE -> active++
                SubscriptionStatus.EXPIRING_SOON -> {
                    active++
                    expiringSoon++
                    upcoming.add(company)
                }
                SubscriptionStatus.EXPIRED -> expired++
                SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELLED -> suspendedOrCancelled++
                SubscriptionStatus.PENDING_APPROVAL, SubscriptionStatus.PENDING_PAYMENT -> pendingApprovals++
            }
        }

        val verifiedPayments = payments.filter {
            PaymentVerificationStatus.fromKey(it.status) == PaymentVerificationStatus.VERIFIED
        }
        val pendingPayments = payments.count {
            PaymentVerificationStatus.fromKey(it.status) == PaymentVerificationStatus.PENDING_VERIFICATION
        }

        val monthlyRev = verifiedPayments.filter { it.durationMonths == 1 }.sumOf { it.amountMmk }
        val sixMonthRev = verifiedPayments.filter { it.durationMonths in 2..6 }.sumOf { it.amountMmk }
        val annualRev = verifiedPayments.filter { it.durationMonths >= 12 }.sumOf { it.amountMmk }
        val totalRev = verifiedPayments.sumOf { it.amountMmk }

        return PlatformDashboardMetrics(
            totalBusinesses = companies.size,
            activeSubscriptions = active,
            expiringSoonCount = expiringSoon,
            expiredSubscriptions = expired,
            suspendedOrCancelledCount = suspendedOrCancelled,
            pendingApprovalsCount = pendingApprovals,
            pendingPaymentsCount = pendingPayments,
            monthlyRevenueMmk = monthlyRev,
            sixMonthRevenueMmk = sixMonthRev,
            annualRevenueMmk = annualRev,
            totalCollectedRevenueMmk = totalRev,
            upcomingExpirations = upcoming.sortedBy { it.subscriptionExpiresAt?.toDate()?.time ?: Long.MAX_VALUE },
        )
    }

    fun calculatePlanStartAndExpiryMillis(
        company: Company,
        plan: SubscriptionPlan,
        trustedServerNowMillis: Long,
    ): Pair<Long, Long> {
        val currentExpiry = company.subscriptionExpiresAt?.toDate()?.time ?: 0L
        val isStillActive = isOperationalAccessAllowed(company, trustedServerNowMillis) && currentExpiry > trustedServerNowMillis
        val startMillis = if (isStillActive) currentExpiry else trustedServerNowMillis
        val durationMs = plan.durationDays.coerceAtLeast(1).toLong() * DAY_MILLIS
        return startMillis to (startMillis + durationMs)
    }
}
