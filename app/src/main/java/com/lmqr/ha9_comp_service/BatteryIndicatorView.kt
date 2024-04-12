package com.lmqr.ha9_comp_service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.lmqr.ha9_comp_service.databinding.BatteryIndicatorViewBinding


class BatteryIndicatorView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private val binding: BatteryIndicatorViewBinding
    private val connectivityManager: ConnectivityManager

    init {
        binding = BatteryIndicatorViewBinding.bind(
            inflate(
                context,
                R.layout.battery_indicator_view,
                this
            )
        )
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val systemStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                when (it.action) {
                    Intent.ACTION_BATTERY_CHANGED -> updateBattery(
                        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                        scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                    )

                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> updateAirplaneMode()
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> context?.let { ctx ->
                        updateDoNotDisturb(
                            ctx
                        )
                    }

                    else -> {}
                }
            }
        }

        private fun updateBattery(
            level: Int,
            scale: Int,
            status: Int,
        ) {
            val batteryPct = level * 100 / scale
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            binding.batteryPercentage.text = "$batteryPct%"
            binding.batteryIcon.setImageResource(
                when {
                    isCharging -> R.drawable.baseline_battery_charging_full_24
                    level >= 95 -> R.drawable.baseline_battery_full_24
                    level >= 85 -> R.drawable.baseline_battery_6_bar_24
                    level >= 70 -> R.drawable.baseline_battery_5_bar_24
                    level >= 55 -> R.drawable.baseline_battery_4_bar_24
                    level >= 40 -> R.drawable.baseline_battery_3_bar_24
                    level >= 25 -> R.drawable.baseline_battery_2_bar_24
                    level >= 10 -> R.drawable.baseline_battery_1_bar_24
                    else -> R.drawable.baseline_battery_0_bar_24
                }
            )
        }

        fun updateAirplaneMode() {
            val isAirplaneModeOn = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
            binding.iconFlightMode.visibility = if (isAirplaneModeOn) View.VISIBLE else View.GONE
        }

        fun updateDoNotDisturb(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            binding.iconDoNotDisturb.visibility =
                if (notificationManager.isNotificationPolicyAccessGranted &&
                    notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                )
                    View.VISIBLE else View.GONE
        }
    }

    private var networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            handler?.post {
                binding.iconWifi.visibility = View.GONE
            }
        }

        override fun onAvailable(network: Network) {
            handler?.post {
                binding.iconWifi.visibility = View.VISIBLE
            }
        }
    }

    init {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        context.registerReceiver(systemStatusReceiver, intentFilter)
        systemStatusReceiver.updateAirplaneMode()
        systemStatusReceiver.updateDoNotDisturb(context)

        connectivityManager.activeNetwork?.let {
            val actNw = connectivityManager.getNetworkCapabilities(it)
            if (actNw?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                binding.iconWifi.visibility = View.VISIBLE
        }

        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(), networkCallback
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(systemStatusReceiver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}