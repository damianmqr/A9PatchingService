package com.lmqr.ha9_comp_service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
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
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import java.io.DataOutputStream
import kotlin.math.max
import kotlin.math.min


class A9AccessibilityService : AccessibilityService() {
    private lateinit var preferences: SharedPreferences

    private val singlePressRunnable = Runnable {
        openFloatingMenu()
    }

    private var isScreenOn = true

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    closeFloatingMenu()
                    turnOffBrightness()
                    handler.removeCallbacks(setBrightnessRunnable)
                    handler.removeCallbacks(periodicBrightnessRunnable)
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    setBrightness()
                    handler.post(periodicBrightnessRunnable)
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(receiver, filter)
    }

    override fun onInterrupt() {

    }

    private val DOUBLE_CLICK_TIME: Long = 250
    private var lastClickTime: Long = 0

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
                            if(isScreenOn)
                                runAsRoot(arrayOf("echo 1 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_force_clear"))
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
    private var process: Process? = null
    private var os: DataOutputStream? = null
    private fun runAsRoot(cmds: Array<String>) {
        if(process?.isAlive != true)
            process = Runtime.getRuntime().exec("su")

        if(os == null)
            os = DataOutputStream(process!!.outputStream)

        os?.run {
            for (tmpCmd in cmds) {
                writeBytes(tmpCmd + "\n")
            }
            flush()
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

    private var brightness: Float = 0.5f
    private var temp: Float = 0.5f
    private val maxBrightness: Int = 2000

    private fun turnOffBrightness(){
        runAsRoot(arrayOf("echo 0 > /sys/class/leds/aw99703-bl-2/brightness"))
        runAsRoot(arrayOf("echo 0 > /sys/class/leds/aw99703-bl-1/brightness"))
    }

    private val brightnessDelay = 100L
    private var nextBrightnessUpdate = 0L

    private val setBrightnessRunnable = Runnable {
        if(isScreenOn)
            runAsRoot(arrayOf("echo ${(temp * brightness * maxBrightness).toInt()}> /sys/class/leds/aw99703-bl-2/brightness;echo ${((1f - temp) * brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-1/brightness"))
    }

    private fun periodicBrightness(){
        if(isScreenOn) {
            runAsRoot(arrayOf("echo ${(temp * brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-2/brightness;echo ${((1f - temp) * brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-1/brightness"))
            handler.postDelayed(periodicBrightnessRunnable, brightnessDelay * 50)
        }
    }

    private val periodicBrightnessRunnable = Runnable {
        periodicBrightness()
    }

    private fun setBrightness(){
        val currentTime = System.currentTimeMillis()
        if(nextBrightnessUpdate < currentTime) {
            nextBrightnessUpdate = currentTime + brightnessDelay
            handler.postDelayed(setBrightnessRunnable, brightnessDelay)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun openFloatingMenu() {
        try {
            if (mLayout == null) {
                runAsRoot(arrayOf("service call SurfaceFlinger 1008 i32 1"))

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
                inflater.inflate(R.layout.floating_menu_layout, mLayout)
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
                val slider = mLayout!!.findViewById<SeekBar>(R.id.slider)
                val slider2 = mLayout!!.findViewById<SeekBar>(R.id.slider2)

                button1.setOnClickListener { v: View? -> runAsRoot(arrayOf("echo 515 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }
                button2.setOnClickListener { v: View? -> runAsRoot(arrayOf("echo 513 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }
                button3.setOnClickListener { v: View? -> runAsRoot(arrayOf("echo 518 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }
                button4.setOnClickListener { v: View? -> runAsRoot(arrayOf("echo 521 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode")) }

                val seekBarValue = mLayout!!.findViewById<TextView>(R.id.slider_value)
                val seekBarValue2 = mLayout!!.findViewById<TextView>(R.id.slider_value2)

                slider.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        seekBarValue.text = progress.toString()
                        temp = min(max(progress.toFloat()/100f, 0f), 1f)
                        setBrightness()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        // Handle slider touch start
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        // Handle slider touch end
                    }
                })

                slider2.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        seekBarValue2.text = progress.toString()
                        brightness = min(max(progress.toFloat()/100f, 0f), 1f)
                        setBrightness()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        // Handle slider touch start
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        // Handle slider touch end
                    }
                })

                slider.progress = (100 * temp).toInt()
                seekBarValue.text = slider.progress.toString()
                slider2.progress = (100 * brightness).toInt()
                seekBarValue2.text = slider2.progress.toString()
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
        unregisterReceiver(receiver);
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        os?.run {
            writeBytes("exit\n")
            flush()
        }
        preferences.edit().putFloat("brightness", brightness).commit()
        preferences.edit().putFloat("temp", temp).commit()
        super.onDestroy()
    }

    private var contentObserver: ContentObserver? = null

    override fun onServiceConnected() {
        //runAsRoot(arrayOf("while :; do sf=\$(service list | grep -c \"SurfaceFlinger\"); if [ \$sf -gt 0 ]; then service call SurfaceFlinger 1008 i32 1; break; else sleep 2; fi; done &"))
        contentObserver = object: ContentObserver(handler)
        {
            override fun onChange(selfChange:Boolean)
            {
                val brightnessAmount = Settings.System.getInt(
                    contentResolver,Settings.System.SCREEN_BRIGHTNESS,0)
                brightness = min(max(brightnessAmount.toFloat()/100f, 0f), 1f)
                setBrightness()
            }
        }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            true, contentObserver as ContentObserver
        )

        val brightnessAmount = min(max(Settings.System.getInt(
            contentResolver,Settings.System.SCREEN_BRIGHTNESS,0), 0), 100)/100f
        brightness = preferences.getFloat("brightness", brightnessAmount)
        temp = preferences.getFloat("temp", 0.5f)
        setBrightness()
        periodicBrightness()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event?.equals(AccessibilityEvent.TYPE_WINDOWS_CHANGED) == true)
            closeFloatingMenu()
    }
}
