package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.rbac.UserRole
import com.example.domain.model.Branch
import com.example.domain.model.Company
import com.example.ui.navigation.AppDestination
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning

@Composable
fun AdaptiveEnterpriseShell(
    company: Company,
    branches: List<Branch>,
    selectedBranch: Branch?,
    canSwitchBranches: Boolean,
    userRole: UserRole,
    userName: String,
    language: AppLanguage,
    strings: AppStrings,
    isOnline: Boolean,
    unreadNotificationsCount: Int,
    destinations: List<AppDestination>,
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    onSelectBranch: (Branch) -> Unit,
    onToggleLanguage: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = strings.appTitle,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(10.dp).size(24.dp),
                            )
                        }
                    },
                ) {
                    destinations.forEach { dest ->
                        val selected = dest == currentDestination
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onSelectDestination(dest) },
                            icon = {
                                DestinationIcon(
                                    dest = dest,
                                    selected = selected,
                                    unreadCount = unreadNotificationsCount,
                                    contentDescription = dest.labelProvider(strings),
                                )
                            },
                            label = {
                                Text(
                                    text = dest.labelProvider(strings),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.testTag("nav_rail_${dest.route}"),
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.navigationBars,
                    topBar = {
                        EnterpriseTopBar(
                            company = company,
                            branches = branches,
                            selectedBranch = selectedBranch,
                            canSwitchBranches = canSwitchBranches,
                            userRole = userRole,
                            userName = userName,
                            language = language,
                            strings = strings,
                            isOnline = isOnline,
                            unreadNotificationsCount = unreadNotificationsCount,
                            onSelectBranch = onSelectBranch,
                            onToggleLanguage = onToggleLanguage,
                            onOpenNotifications = { onSelectDestination(AppDestination.NOTIFICATIONS) },
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        content()
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    EnterpriseTopBar(
                        company = company,
                        branches = branches,
                        selectedBranch = selectedBranch,
                        canSwitchBranches = canSwitchBranches,
                        userRole = userRole,
                        userName = userName,
                        language = language,
                        strings = strings,
                        isOnline = isOnline,
                        unreadNotificationsCount = unreadNotificationsCount,
                        onSelectBranch = onSelectBranch,
                        onToggleLanguage = onToggleLanguage,
                        onOpenNotifications = { onSelectDestination(AppDestination.NOTIFICATIONS) },
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        destinations.forEach { dest ->
                            val selected = dest == currentDestination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onSelectDestination(dest) },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        selected = selected,
                                        unreadCount = unreadNotificationsCount,
                                        contentDescription = dest.labelProvider(strings),
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.labelProvider(strings),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag("nav_bar_${dest.route}"),
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    dest: AppDestination,
    selected: Boolean,
    unreadCount: Int,
    contentDescription: String,
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest == AppDestination.NOTIFICATIONS && unreadCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(unreadCount.coerceAtMost(99).toString()) }
            }
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun EnterpriseTopBar(
    company: Company,
    branches: List<Branch>,
    selectedBranch: Branch?,
    canSwitchBranches: Boolean,
    userRole: UserRole,
    userName: String,
    language: AppLanguage,
    strings: AppStrings,
    isOnline: Boolean,
    unreadNotificationsCount: Int,
    onSelectBranch: (Branch) -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    var branchMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = company.localizedName(language == AppLanguage.MYANMAR),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = userRole.label(language),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        color = (if (isOnline) SemanticSuccess else SemanticWarning).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = if (isOnline) strings.onlineLabel else strings.offlineLabel,
                                tint = if (isOnline) SemanticSuccess else SemanticWarning,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (isOnline) strings.onlineLabel else strings.offlineLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOnline) SemanticSuccess else SemanticWarning,
                            )
                        }
                    }

                    TextButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier.testTag("top_bar_language_toggle"),
                    ) {
                        Text(
                            text = if (language == AppLanguage.MYANMAR) "MY" else "EN",
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("top_bar_notifications_button"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge { Text(unreadNotificationsCount.coerceAtMost(99).toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = strings.navNotifications,
                            )
                        }
                    }
                }
            }

            if (branches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { if (canSwitchBranches && branches.size > 1) branchMenuExpanded = true },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("branch_selector_chip"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = selectedBranch?.let {
                                        "${it.code} • ${it.localizedName(language == AppLanguage.MYANMAR)}"
                                    } ?: strings.selectBranch,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (canSwitchBranches && branches.size > 1) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = strings.selectBranch,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = branchMenuExpanded,
                        onDismissRequest = { branchMenuExpanded = false },
                    ) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = {
                                    Text("${branch.code} • ${branch.localizedName(language == AppLanguage.MYANMAR)}")
                                },
                                onClick = {
                                    branchMenuExpanded = false
                                    onSelectBranch(branch)
                                },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}
