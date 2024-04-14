package com.lmqr.ha9_comp_service

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.util.AttributeSet
import android.view.View
import kotlin.math.min


class NotificationIconView(context: Context, attrs: AttributeSet) :
    View(context, attrs) {
    private val icons = HashMap<String, Drawable>()
    private var activeIcons = emptyList<String>()
    private val maxIcons = 5

    fun updateNotifications(notifications: List<StatusBarNotification>) {
        for(notification in notifications){
            if(!icons.containsKey(notification.packageName)) {
                icons[notification.packageName] =
                    context.packageManager.getApplicationIcon(notification.packageName)
            }
        }
        activeIcons = notifications.map(StatusBarNotification::getPackageName).distinct()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);
        val iconSpace = min(width / maxIcons, height)
        val iconWidth = iconSpace * 9 / 10
        val iconDivider = (iconSpace - iconWidth) / 2
        var i = 0
        for (iconKey in activeIcons) {
            icons[iconKey]?.let{ icon ->
                icon.setBounds(i * iconSpace + iconDivider, iconDivider, i * iconSpace + iconWidth, iconDivider + iconWidth)
                icon.draw(canvas)
                i++
            }
        }
    }
}