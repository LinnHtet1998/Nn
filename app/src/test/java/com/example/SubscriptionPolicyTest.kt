package com.example

import com.example.core.i18n.AppLanguage
import com.example.core.i18n.MmkFormatter
import com.example.core.rbac.Permission
import com.example.core.rbac.RolePermissionMatrix
import com.example.core.rbac.UserRole
import com.example.core.subscription.SubscriptionPolicy
import com.example.domain.model.Branch
import com.example.domain.model.Company
import com.example.domain.model.DashboardCalculator
import com.example.domain.model.PaymentVerificationStatus
import com.example.domain.model.SubscriptionPayment
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SubscriptionPolicyTest {

    @Test
    fun calculateDiscountedPriceMmk_handlesDiscountsAndClamping() {
        assertEquals(35_000L, SubscriptionPolicy.calculateDiscountedPriceMmk(35_000L, 0.0))
        assertEquals(175_500L, SubscriptionPolicy.calculateDiscountedPriceMmk(195_000L, 10.0))
        assertEquals(306_000L, SubscriptionPolicy.calculateDiscountedPriceMmk(360_000L, 15.0))
        assertEquals(0L, SubscriptionPolicy.calculateDiscountedPriceMmk(50_000L, 120.0))
        assertEquals(0L, SubscriptionPolicy.calculateDiscountedPriceMmk(-100L, 10.0))
    }

    @Test
    fun effectiveStatus_enforcesTrustedServerTimeOverStaleActiveStatus() {
        val now = 1_760_000_000_000L
        val companyActive = Company(
            subscriptionStatus = SubscriptionStatus.ACTIVE.key,
            subscriptionExpiresAt = Timestamp(Date(now + 20L * SubscriptionPolicy.DAY_MILLIS)),
        )
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionPolicy.effectiveStatus(companyActive, now))
        assertTrue(SubscriptionPolicy.isOperationalAccessAllowed(companyActive, now))

        val companyExpiringSoon = Company(
            subscriptionStatus = SubscriptionStatus.ACTIVE.key,
            subscriptionExpiresAt = Timestamp(Date(now + 3L * SubscriptionPolicy.DAY_MILLIS)),
        )
        assertEquals(SubscriptionStatus.EXPIRING_SOON, SubscriptionPolicy.effectiveStatus(companyExpiringSoon, now))
        assertEquals(3L, SubscriptionPolicy.remainingDays(companyExpiringSoon, now))
        assertTrue(SubscriptionPolicy.isOperationalAccessAllowed(companyExpiringSoon, now))

        val companyExpired = Company(
            subscriptionStatus = SubscriptionStatus.ACTIVE.key,
            subscriptionExpiresAt = Timestamp(Date(now - 60_000L)),
        )
        assertEquals(SubscriptionStatus.EXPIRED, SubscriptionPolicy.effectiveStatus(companyExpired, now))
        assertFalse(SubscriptionPolicy.isOperationalAccessAllowed(companyExpired, now))

        val companySuspended = Company(
            subscriptionStatus = SubscriptionStatus.SUSPENDED.key,
            subscriptionExpiresAt = Timestamp(Date(now + 30L * SubscriptionPolicy.DAY_MILLIS)),
        )
        assertEquals(SubscriptionStatus.SUSPENDED, SubscriptionPolicy.effectiveStatus(companySuspended, now))
        assertFalse(SubscriptionPolicy.isOperationalAccessAllowed(companySuspended, now))
    }

    @Test
    fun calculatePlanStartAndExpiryMillis_stacksWhenRenewingBeforeExpiry() {
        val now = 1_760_000_000_000L
        val existingExpiry = now + 5L * SubscriptionPolicy.DAY_MILLIS
        val activeCompany = Company(
            subscriptionStatus = SubscriptionStatus.ACTIVE.key,
            subscriptionExpiresAt = Timestamp(Date(existingExpiry)),
        )
        val plan30d = SubscriptionPlan(durationDays = 30)

        val (startStacked, endStacked) = SubscriptionPolicy.calculatePlanStartAndExpiryMillis(activeCompany, plan30d, now)
        assertEquals(existingExpiry, startStacked)
        assertEquals(existingExpiry + 30L * SubscriptionPolicy.DAY_MILLIS, endStacked)

        val expiredCompany = Company(subscriptionStatus = SubscriptionStatus.EXPIRED.key)
        val (startFresh, endFresh) = SubscriptionPolicy.calculatePlanStartAndExpiryMillis(expiredCompany, plan30d, now)
        assertEquals(now, startFresh)
        assertEquals(now + 30L * SubscriptionPolicy.DAY_MILLIS, endFresh)
    }

    @Test
    fun computePlatformMetrics_aggregatesTiersAndStatusesAccurately() {
        val now = 1_760_000_000_000L
        val companies = listOf(
            Company(id = "c1", subscriptionStatus = SubscriptionStatus.ACTIVE.key, subscriptionExpiresAt = Timestamp(Date(now + 20L * SubscriptionPolicy.DAY_MILLIS))),
            Company(id = "c2", subscriptionStatus = SubscriptionStatus.ACTIVE.key, subscriptionExpiresAt = Timestamp(Date(now + 4L * SubscriptionPolicy.DAY_MILLIS))),
            Company(id = "c3", subscriptionStatus = SubscriptionStatus.ACTIVE.key, subscriptionExpiresAt = Timestamp(Date(now - 1000L))),
            Company(id = "c4", subscriptionStatus = SubscriptionStatus.PENDING_APPROVAL.key),
        )
        val payments = listOf(
            SubscriptionPayment(id = "p1", durationMonths = 1, amountMmk = 35_000L, status = PaymentVerificationStatus.VERIFIED.key),
            SubscriptionPayment(id = "p2", durationMonths = 6, amountMmk = 175_500L, status = PaymentVerificationStatus.VERIFIED.key),
            SubscriptionPayment(id = "p3", durationMonths = 12, amountMmk = 306_000L, status = PaymentVerificationStatus.VERIFIED.key),
            SubscriptionPayment(id = "p4", durationMonths = 1, amountMmk = 35_000L, status = PaymentVerificationStatus.PENDING_VERIFICATION.key),
        )

        val metrics = SubscriptionPolicy.computePlatformMetrics(companies, payments, now)
        assertEquals(4, metrics.totalBusinesses)
        assertEquals(2, metrics.activeSubscriptions)
        assertEquals(1, metrics.expiringSoonCount)
        assertEquals(1, metrics.expiredSubscriptions)
        assertEquals(1, metrics.pendingApprovalsCount)
        assertEquals(1, metrics.pendingPaymentsCount)
        assertEquals(35_000L, metrics.monthlyRevenueMmk)
        assertEquals(175_500L, metrics.sixMonthRevenueMmk)
        assertEquals(306_000L, metrics.annualRevenueMmk)
        assertEquals(516_500L, metrics.totalCollectedRevenueMmk)
    }

    @Test
    fun rbacAndQuotaAndFormatting_behaveAsSpecified() {
        val company = Company(maxBranchesAllowed = 3, maxEmployeesAllowed = 10)
        assertTrue(SubscriptionPolicy.canCreateAnotherBranch(company, 2))
        assertFalse(SubscriptionPolicy.canCreateAnotherBranch(company, 3))
        assertTrue(SubscriptionPolicy.canAddAnotherEmployee(company, 9))
        assertFalse(SubscriptionPolicy.canAddAnotherEmployee(company, 10))

        assertTrue(RolePermissionMatrix.hasPermission(UserRole.SUPER_ADMIN, Permission.MANAGE_USERS))
        assertFalse(RolePermissionMatrix.hasPermission(UserRole.CASHIER, Permission.MANAGE_BRANCHES))
        assertTrue(RolePermissionMatrix.hasPermission(UserRole.CASHIER, Permission.OPERATE_POS))

        assertEquals("195,000 MMK", MmkFormatter.formatMmk(195_000L, AppLanguage.ENGLISH))
        assertEquals("195,000 ကျပ်", MmkFormatter.formatMmk(195_000L, AppLanguage.MYANMAR))

        val b1 = Branch(id = "b1", isActive = true, todaySalesMmk = 500_000L, todayProfitMmk = 120_000L, todayExpensesMmk = 20_000L)
        val b2 = Branch(id = "b2", isActive = true, todaySalesMmk = 250_000L, todayProfitMmk = 60_000L, todayExpensesMmk = 10_000L)
        val calc = DashboardCalculator.computeHeadOfficeMetrics(listOf(b1, b2))
        assertEquals(750_000L, calc.todaySalesMmk)
        assertEquals(150_000L, calc.netProfitMmk)
        assertEquals("b1", calc.bestBranch?.id)
        assertEquals("b2", calc.lowestBranch?.id)
    }
}
