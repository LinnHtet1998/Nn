package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.i18n.MmkFormatter
import com.example.domain.model.AppNotification
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticInfo
import com.example.ui.theme.SemanticWarning

@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    language: AppLanguage,
    strings: AppStrings,
    onMarkRead: (String) -> Unit,
) {
    val isMyanmar = language == AppLanguage.MYANMAR

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("notifications_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val unreadCount = notifications.count { !it.isRead }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.notificationsTitle, style = MaterialTheme.typography.headlineMedium)
                if (unreadCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = strings.unreadCountFormat.format(unreadCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(strings.noNotificationsYet, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            items(notifications, key = { it.id }) { notif ->
                val accent = when (notif.severity) {
                    "CRITICAL" -> SemanticError
                    "WARNING" -> SemanticWarning
                    else -> SemanticInfo
                }
                val icon = when (notif.severity) {
                    "CRITICAL", "WARNING" -> Icons.Filled.Warning
                    else -> Icons.Filled.Info
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (notif.isRead) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        }
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = notif.severity,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = notif.localizedTitle(isMyanmar),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (!notif.isRead) {
                                TextButton(onClick = { onMarkRead(notif.id) }) {
                                    Text(strings.markAsRead)
                                }
                            }
                        }
                        Text(
                            text = notif.localizedMessage(isMyanmar),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${notif.category} • ${MmkFormatter.formatDateTime(notif.createdAt?.toDate()?.time)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
