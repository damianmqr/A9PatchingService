package com.lmqr.ha9_comp_service.aod_views

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.math.min


class NotificationIconView(context: Context, attrs: AttributeSet) :
    View(context, attrs) {
    private val notificationIcons = HashMap<String, StatusBarNotification>()
    private val icons = HashMap<String, Drawable>()
    private var activeIcons = emptyList<String>()
    private val maxIcons = 5

    private val notificationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val sbn =
                    intent.getParcelableExtra<StatusBarNotification>(NotificationListener.EXTRA_STATUS_BAR_NOTIFICATION)
                if (sbn != null) {
                    if (NotificationListener.ACTION_NOTIFICATION_POSTED == intent.action) {
                        notificationIcons[sbn.packageName+":"+sbn.id] = sbn
                        updateNotifications(notificationIcons.values.toList())

                    } else if (NotificationListener.ACTION_NOTIFICATION_REMOVED == intent.action) {
                        notificationIcons.remove(sbn.packageName+":"+sbn.id)
                        updateNotifications(notificationIcons.values.toList())
                    }
                }
            }catch (ex: Exception){
                ex.printStackTrace()
            }
        }
    }

    fun updateNotifications(notifications: List<StatusBarNotification>) {
        for(notification in notifications){
            if(!icons.containsKey(notification.packageName)) {
                val iconResource = context.packageManager.getResourcesForApplication(notification.packageName)
                icons[notification.packageName] =
                    ResourcesCompat.getDrawable(iconResource, notification.notification.smallIcon.resId, context.theme) as Drawable
                    //context.packageManager.getApplicationIcon(notification.packageName)
            }
        }
        activeIcons = notifications.map(StatusBarNotification::getPackageName).distinct()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
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

    private val notificationListenerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val filterNotifications = IntentFilter()
            filterNotifications.addAction(NotificationListener.ACTION_NOTIFICATION_POSTED)
            filterNotifications.addAction(NotificationListener.ACTION_NOTIFICATION_REMOVED)
            LocalBroadcastManager.getInstance(context).registerReceiver(notificationReceiver, filterNotifications)
            updateNotifications(notificationIcons.values.toList())
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d("NotificationIconView", "NotificationListener service disconnected")
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.notificationIcons = ArrayList(notificationIcons.values)
        savedState.activeIcons = ArrayList(activeIcons)
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        state.notificationIcons?.let {
            for (sbn in it) {
                notificationIcons[sbn.packageName + ":" + sbn.id] = sbn
            }
            updateNotifications(it)
        }
        activeIcons = state.activeIcons ?: emptyList()
    }

    internal class SavedState : BaseSavedState {
        var notificationIcons: ArrayList<StatusBarNotification>? = null
        var activeIcons: ArrayList<String>? = null

        constructor(superState: Parcelable?) : super(superState)

        private constructor(inParcel: Parcel) : super(inParcel) {
            notificationIcons = inParcel.createTypedArrayList(StatusBarNotification.CREATOR)
            activeIcons = inParcel.createStringArrayList()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeTypedList(notificationIcons)
            out.writeStringList(activeIcons)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }



    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val intent = Intent(context, NotificationListener::class.java)
        context.bindService(intent, notificationListenerConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDetachedFromWindow() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(notificationReceiver)
        super.onDetachedFromWindow()
    }
}