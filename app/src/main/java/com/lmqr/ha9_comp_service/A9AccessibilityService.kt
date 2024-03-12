package com.lmqr.ha9_comp_service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import kotlin.math.max
import kotlin.math.min

class A9AccessibilityService : AccessibilityService() {
    private val rootCommandRunner = RootCommandRunner()
    private val temperatureModeManager = TemperatureModeManager(TemperatureMode.White, rootCommandRunner)

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){
                Intent.ACTION_SCREEN_OFF -> {
                    if(BuildConfig.USE_TEMPERATURE) {
                        temperatureModeManager.onScreenChange(false)
                    }
                    closeFloatingMenu()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if(BuildConfig.USE_TEMPERATURE) {
                        temperatureModeManager.onScreenChange(true)
                    }
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    fun getBrightnessFromSetting() = min(
        max(
            Settings.System.getInt(
                contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0
            ) - 1, 0
        ), 100
    ) / 100f

    override fun onCreate() {
        super.onCreate()
        if(BuildConfig.USE_TEMPERATURE) {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            registerReceiver(receiver, filter)

            contentObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    temperatureModeManager.brightness = getBrightnessFromSetting()
                }
            }

            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                true, contentObserver as ContentObserver
            )

            temperatureModeManager.brightness = getBrightnessFromSetting()
        }
    }

    override fun onInterrupt() {

    }

    private val DOUBLE_CLICK_TIME: Long = 250
    private var lastClickTime: Long = 0

    private val singlePressRunnable = Runnable {
        rootCommandRunner.runAsRoot(arrayOf("echo 1 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_force_clear"))
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.scanCode) {
                766 -> {
                    if (!Settings.canDrawOverlays(baseContext))
                        requestOverlayPermission()
                    else {

                        val clickTime: Long = System.currentTimeMillis()
                        if (clickTime - lastClickTime < DOUBLE_CLICK_TIME) {
                            handler.removeCallbacks(singlePressRunnable)
                            openFloatingMenu()
                        }else{
                            handler.postDelayed(singlePressRunnable, DOUBLE_CLICK_TIME)
                        }
                        lastClickTime = clickTime
                    }
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }



    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("OverlayPermission", "Activity not found exception", e)
        }
    }

    private fun getAppUsableScreenSize(context: Context): Point {
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        return size
    }

    private fun getRealScreenSize(context: Context): Point {
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getRealSize(size)
        return size
    }

    private fun getNavBarHeight(): Int {
        val appUsableSize: Point = getAppUsableScreenSize(this)
        val realScreenSize: Point = getRealScreenSize(this)

        if (appUsableSize.y < realScreenSize.y)
            return (realScreenSize.y - appUsableSize.y)

        return 0
    }

    private var mLayout: LinearLayout? = null

    private fun closeFloatingMenu() = mLayout?.run{
        visibility = View.GONE
    }

    @SuppressLint("ClickableViewAccessibility", "UseSwitchCompatOrMaterialCode")
    private fun openFloatingMenu() {
        try {
            if (mLayout == null) {
                rootCommandRunner.runAsRoot(arrayOf("service call SurfaceFlinger 1008 i32 1"))

                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                mLayout = LinearLayout(this)
                val lp = WindowManager.LayoutParams()
                lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                lp.format = PixelFormat.TRANSLUCENT
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                lp.gravity = Gravity.BOTTOM
                lp.y = getNavBarHeight()
                val inflater = LayoutInflater.from(this)
                if(BuildConfig.USE_TEMPERATURE) {
                    inflater.inflate(R.layout.floating_menu_layout, mLayout)
                }else{
                    inflater.inflate(R.layout.floating_menu_layout_no_temp, mLayout)
                }
                mLayout!!.setPadding(20, 0, 20, 0)
                mLayout!!.setOnTouchListener { v, event ->
                    if(event.action == MotionEvent.ACTION_OUTSIDE)
                        closeFloatingMenu()
                    false
                }
                wm.addView(mLayout, lp)

                val button1 = mLayout!!.findViewById<Button>(R.id.button1)
                val button2 = mLayout!!.findViewById<Button>(R.id.button2)
                val button3 = mLayout!!.findViewById<Button>(R.id.button3)
                val button4 = mLayout!!.findViewById<Button>(R.id.button4)


                button1.setOnClickListener { v: View? -> rootCommandRunner.runAsRoot(arrayOf("echo 515 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }
                button2.setOnClickListener { v: View? -> rootCommandRunner.runAsRoot(arrayOf("echo 513 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }
                button3.setOnClickListener { v: View? -> rootCommandRunner.runAsRoot(arrayOf("echo 518 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }
                button4.setOnClickListener { v: View? -> rootCommandRunner.runAsRoot(arrayOf("echo 521 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }

                if(BuildConfig.USE_TEMPERATURE) {
                    val switch = mLayout!!.findViewById<Switch>(R.id.night_switch)
                    switch.setOnCheckedChangeListener{ v, checked ->
                        if(checked)
                            temperatureModeManager.setMode(TemperatureMode.Night)
                        else
                            temperatureModeManager.setMode(TemperatureMode.White)
                    }
                }
            } else {
                if (mLayout?.visibility == View.VISIBLE)
                    closeFloatingMenu()
                else
                    mLayout?.visibility = View.VISIBLE
            }
        }catch (ex: Exception){
            ex.printStackTrace()
        }
    }

    override fun onDestroy() {
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        if(BuildConfig.USE_TEMPERATURE) {
            rootCommandRunner.runAsRoot(
                arrayOf(
                    "chmod 644 /sys/class/leds/aw99703-bl-2/brightness",
                    "chmod 644 /sys/class/leds/aw99703-bl-1/brightness",
                    "echo 0 > /sys/class/leds/aw99703-bl-1/brightness"
                )
            )
            unregisterReceiver(receiver);
            rootCommandRunner.onDestroy()
        }
        super.onDestroy()
    }

    private var contentObserver: ContentObserver? = null

    override fun onServiceConnected() {
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event?.equals(AccessibilityEvent.TYPE_WINDOWS_CHANGED) == true)
            closeFloatingMenu()
    }
}
