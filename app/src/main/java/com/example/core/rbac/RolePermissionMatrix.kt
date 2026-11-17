package com.example.core.rbac

import com.example.core.i18n.AppLanguage

enum class UserRole(
    val key: String,
    val labelEn: String,
    val labelMm: String,
) {
    SUPER_ADMIN("SUPER_ADMIN", "Super Admin / Owner", "စူပါအက်ဒမင် / ပိုင်ရှင်"),
    GENERAL_MANAGER("GENERAL_MANAGER", "General Manager", "အထွေထွေမန်နေဂျာ"),
    BRANCH_MANAGER("BRANCH_MANAGER", "Branch Manager", "ဆိုင်ခွဲမန်နေဂျာ"),
    CASHIER("CASHIER", "Cashier", "ငွေကိုင်"),
    INVENTORY_STAFF("INVENTORY_STAFF", "Inventory Staff", "ကုန်လှောင်ရုံဝန်ထမ်း"),
    ACCOUNTANT("ACCOUNTANT", "Accountant", "စာရင်းကိုင်"),
    HR_PAYROLL_STAFF("HR_PAYROLL_STAFF", "HR / Payroll Staff", "HR နှင့် လစာစာရင်းဝန်ထမ်း");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.MYANMAR -> labelMm
        AppLanguage.ENGLISH -> labelEn
    }

    companion object {
        fun fromKey(key: String?): UserRole = when (key) {
            "COMPANY_ADMIN" -> GENERAL_MANAGER
            "HR_MANAGER" -> HR_PAYROLL_STAFF
            else -> entries.firstOrNull { it.key == key } ?: CASHIER
        }
    }
}

enum class AccountStatus(
    val key: String,
    val labelEn: String,
    val labelMm: String,
) {
    PENDING("PENDING", "Pending Approval", "အတည်ပြုရန်စောင့်ဆိုင်း"),
    APPROVED("APPROVED", "Approved", "ခွင့်ပြုပြီး"),
    SUSPENDED("SUSPENDED", "Suspended", "ယာယီရပ်ဆိုင်း"),
    REVOKED("REVOKED", "Revoked", "ပယ်ဖျက်ပြီး");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.MYANMAR -> labelMm
        AppLanguage.ENGLISH -> labelEn
    }

    companion object {
        fun fromKey(key: String?): AccountStatus =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PENDING
    }
}

enum class Permission {
    VIEW_DASHBOARD,
    VIEW_COMPANY_OVERVIEW,
    MANAGE_BRANCHES,
    EDIT_ASSIGNED_BRANCH,
    MANAGE_USERS,
    VIEW_FINANCIALS,
    VIEW_PAYROLL,
    OPERATE_POS,
    MANAGE_INVENTORY,
    APPROVE_ACTIONS,
    EXPORT_REPORTS,
    VIEW_AUDIT_LOGS,
    MANAGE_SETTINGS,
}

object RolePermissionMatrix {
    private val rolePermissions: Map<UserRole, Set<Permission>> = mapOf(
        UserRole.SUPER_ADMIN to Permission.entries.toSet(),
        UserRole.GENERAL_MANAGER to (Permission.entries.toSet() - Permission.MANAGE_USERS),
        UserRole.BRANCH_MANAGER to setOf(
            Permission.VIEW_DASHBOARD,
            Permission.EDIT_ASSIGNED_BRANCH,
            Permission.VIEW_FINANCIALS,
            Permission.OPERATE_POS,
            Permission.MANAGE_INVENTORY,
            Permission.APPROVE_ACTIONS,
            Permission.EXPORT_REPORTS,
        ),
        UserRole.ACCOUNTANT to setOf(
            Permission.VIEW_DASHBOARD,
            Permission.VIEW_COMPANY_OVERVIEW,
            Permission.VIEW_FINANCIALS,
            Permission.VIEW_PAYROLL,
            Permission.APPROVE_ACTIONS,
            Permission.EXPORT_REPORTS,
        ),
        UserRole.CASHIER to setOf(
            Permission.VIEW_DASHBOARD,
            Permission.OPERATE_POS,
        ),
        UserRole.INVENTORY_STAFF to setOf(
            Permission.VIEW_DASHBOARD,
            Permission.MANAGE_INVENTORY,
        ),
        UserRole.HR_PAYROLL_STAFF to setOf(
            Permission.VIEW_DASHBOARD,
            Permission.VIEW_COMPANY_OVERVIEW,
            Permission.VIEW_PAYROLL,
            Permission.EXPORT_REPORTS,
        ),
    )

    fun hasPermission(role: UserRole, permission: Permission): Boolean =
        rolePermissions[role]?.contains(permission) == true

    fun permissionsFor(role: UserRole): Set<Permission> =
        rolePermissions[role].orEmpty()
}
