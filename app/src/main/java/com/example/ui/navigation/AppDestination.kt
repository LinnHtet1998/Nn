package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.core.i18n.AppStrings
import com.example.core.rbac.Permission
import com.example.core.rbac.RolePermissionMatrix
import com.example.core.rbac.UserRole

enum class AppDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelProvider: (AppStrings) -> String,
    val requiredPermission: Permission? = null,
    val platformSuperAdminOnly: Boolean = false,
) {
    DASHBOARD(
        route = "dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        labelProvider = { it.navDashboard },
        requiredPermission = Permission.VIEW_DASHBOARD,
    ),
    BRANCHES(
        route = "branches",
        selectedIcon = Icons.Filled.Store,
        unselectedIcon = Icons.Outlined.Store,
        labelProvider = { it.navBranches },
        requiredPermission = null,
    ),
    TEAM_ROLES(
        route = "team_roles",
        selectedIcon = Icons.Filled.Group,
        unselectedIcon = Icons.Outlined.Group,
        labelProvider = { it.navTeamRoles },
        requiredPermission = Permission.MANAGE_USERS,
    ),
    SUBSCRIPTION(
        route = "subscription",
        selectedIcon = Icons.Filled.CreditCard,
        unselectedIcon = Icons.Outlined.CreditCard,
        labelProvider = { it.navSubscription },
        requiredPermission = Permission.MANAGE_SETTINGS,
    ),
    PLATFORM_ADMIN(
        route = "platform_admin",
        selectedIcon = Icons.Filled.AdminPanelSettings,
        unselectedIcon = Icons.Outlined.AdminPanelSettings,
        labelProvider = { it.navPlatformAdmin },
        requiredPermission = null,
        platformSuperAdminOnly = true,
    ),
    NOTIFICATIONS(
        route = "notifications",
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications,
        labelProvider = { it.navNotifications },
        requiredPermission = null,
    ),
    SETTINGS(
        route = "settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        labelProvider = { it.navSettings },
        requiredPermission = null,
    );

    companion object {
        fun accessibleFor(role: UserRole, isPlatformSuperAdmin: Boolean = false): List<AppDestination> =
            entries.filter { dest ->
                if (dest.platformSuperAdminOnly) {
                    isPlatformSuperAdmin
                } else {
                    dest.requiredPermission == null || RolePermissionMatrix.hasPermission(role, dest.requiredPermission)
                }
            }
    }
}
