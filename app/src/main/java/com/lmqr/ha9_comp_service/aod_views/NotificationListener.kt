package com.lmqr.ha9_comp_service.aod_views

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val intent = Intent(ACTION_NOTIFICATION_POSTED)
        intent.putExtra(EXTRA_STATUS_BAR_NOTIFICATION, sbn)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val intent = Intent(ACTION_NOTIFICATION_REMOVED)
        intent.putExtra(EXTRA_STATUS_BAR_NOTIFICATION, sbn)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications.forEach {
            onNotificationPosted(it)
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_POSTED = "notification_posted"
        const val ACTION_NOTIFICATION_REMOVED = "notification_removed"
        const val EXTRA_STATUS_BAR_NOTIFICATION = "status_bar_notification"
    }
}

