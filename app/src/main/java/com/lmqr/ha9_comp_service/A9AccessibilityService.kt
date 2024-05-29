package com.lmqr.ha9_comp_service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
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
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.button_mapper.ButtonActionManager
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands
import com.lmqr.ha9_comp_service.command_runners.UnixSocketCommandRunner
import com.lmqr.ha9_comp_service.databinding.FloatingMenuLayoutBinding
import kotlin.math.max
import kotlin.math.min


class A9AccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var commandRunner: CommandRunner
    private lateinit var temperatureModeManager: TemperatureModeManager
    private lateinit var refreshModeManager: RefreshModeManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var buttonActionManager: ButtonActionManager
    private lateinit var alwaysOnDisplay: AlwaysOnDisplay
    private var isScreenOn = true

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    temperatureModeManager.onScreenChange(false)
                    menuBinding.close()
                    alwaysOnDisplay.openAOD()
                    if(sharedPreferences.getBoolean("refresh_on_lock", false))
                        handler.postDelayed({
                            commandRunner.runCommands(arrayOf(Commands.FORCE_CLEAR))
                        }, 150)
                }

                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    alwaysOnDisplay.closeAOD()
                    temperatureModeManager.onScreenChange(true)
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
        ), 255
    ) / 255f

    override fun onCreate() {
        super.onCreate()
        commandRunner =
            UnixSocketCommandRunner()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        alwaysOnDisplay = AlwaysOnDisplay(this)
        refreshModeManager = RefreshModeManager(
            sharedPreferences,
            commandRunner
        )
        temperatureModeManager = TemperatureModeManager(
            if (sharedPreferences.getBoolean("temperature_slider", false))
                TemperatureMode.Slider
            else if (sharedPreferences.getBoolean("night_mode", false))
                TemperatureMode.Night
            else
                TemperatureMode.White,
            getBrightnessFromSetting(),
            commandRunner,
        )

        buttonActionManager = ButtonActionManager(commandRunner)

        val filterScreen = IntentFilter()
        filterScreen.addAction(Intent.ACTION_SCREEN_ON)
        filterScreen.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(receiver, filterScreen)

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                temperatureModeManager.brightness = getBrightnessFromSetting()
            }
        }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            true, contentObserver as ContentObserver
        )

        updateColorScheme(sharedPreferences)
    }

    override fun onInterrupt() {

    }

    private var lastClickTime: Long = 0

    private fun handleSinglePress() {
        if(isScreenOn)
            buttonActionManager.executeSinglePress(this)
        else
            buttonActionManager.executeSinglePressScreenOff(this)
    }

    private fun handleDoublePress() {
        if(isScreenOn)
            buttonActionManager.executeDoublePress(this)
        else
            buttonActionManager.executeDoublePressScreenOff(this)
    }

    private val singlePressRunnable = Runnable {
        handleSinglePress()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.scanCode) {
                766 -> {
                    if (!Settings.canDrawOverlays(baseContext))
                        requestOverlayPermission()
                    else {

                        val clickTime: Long = System.currentTimeMillis()
                        if (clickTime - lastClickTime < 350) {
                            handler.removeCallbacks(singlePressRunnable)
                            handleDoublePress()
                        } else {
                            handler.postDelayed(singlePressRunnable, 350)
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

    private var menuBinding: FloatingMenuLayoutBinding? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun openFloatingMenu() {
        try {
            menuBinding?.run {
                if (root.visibility == View.VISIBLE) {
                    root.visibility = View.GONE
                } else {
                    root.visibility = View.VISIBLE

                    nightSwitch.visibility =
                        if (sharedPreferences.getBoolean("disable_nightmode", false)
                            || sharedPreferences.getBoolean("temperature_slider", false)
                        )
                            View.GONE
                        else View.VISIBLE

                    lightSeekbarContainer.visibility =
                        if (sharedPreferences.getBoolean("disable_nightmode", false)
                            || !sharedPreferences.getBoolean("temperature_slider", false)
                        )
                            View.GONE
                        else View.VISIBLE
                    updateButtons(refreshModeManager.currentMode)
                }
            } ?: run {

                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                val inflater = LayoutInflater.from(this)
                val view = inflater.inflate(R.layout.floating_menu_layout, null, false)

                val layoutParams = WindowManager.LayoutParams().apply {
                    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    format = PixelFormat.TRANSLUCENT
                    flags =
                        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.BOTTOM
                    y = getNavBarHeight()
                }

                menuBinding = FloatingMenuLayoutBinding.bind(view).apply {
                    root.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_OUTSIDE)
                            close()
                        false
                    }

                    button1.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.CLEAR)
                        updateButtons(refreshModeManager.currentMode)
                    }
                    button2.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.BALANCED)
                        updateButtons(refreshModeManager.currentMode)
                    }
                    button3.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.SMOOTH)
                        updateButtons(refreshModeManager.currentMode)
                    }
                    button4.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.SPEED)
                        updateButtons(refreshModeManager.currentMode)
                    }

                    settingsIcon.setOnClickListener {
                        val settingsIntent = Intent(
                            this@A9AccessibilityService,
                            SettingsActivity::class.java
                        )
                        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        close()
                        startActivity(settingsIntent)
                    }
                    nightSwitch.setOnCheckedChangeListener { _, checked ->
                        val currentMode = sharedPreferences.getBoolean("night_mode", false)
                        if (currentMode != checked) {
                            sharedPreferences.edit().putBoolean("night_mode", checked).apply()
                        }
                    }
                    nightSwitch.isChecked = sharedPreferences.getBoolean("night_mode", false)

                    lightSeekbar.progress = 100
                    lightSeekbar.setOnSeekBarChangeListener(
                        object : OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                progress: Int,
                                fromUser: Boolean
                            ) {
                                temperatureModeManager.whiteToYellow = progress / 100.0
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            }

                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            }

                        }
                    )

                    nightSwitch.visibility =
                        if (sharedPreferences.getBoolean("disable_nightmode", false)
                            || sharedPreferences.getBoolean("temperature_slider", false)
                        )
                            View.GONE
                        else View.VISIBLE
                    lightSeekbarContainer.visibility =
                        if (sharedPreferences.getBoolean("disable_nightmode", false)
                            || !sharedPreferences.getBoolean("temperature_slider", false)
                        )
                            View.GONE
                        else View.VISIBLE

                    updateButtons(refreshModeManager.currentMode)

                    wm.addView(root, layoutParams)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun onDestroy() {
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        commandRunner.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private var contentObserver: ContentObserver? = null

    override fun onServiceConnected() {
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event.letPackageNameClassName { pkgName, clsName ->
            val componentName = ComponentName(
                pkgName,
                clsName
            )
            try {
                packageManager.getActivityInfo(componentName, 0)
                refreshModeManager.onAppChange(pkgName)
                menuBinding.updateButtons(refreshModeManager.currentMode)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
    }

    private fun setCorrectMode(sharedPreferences: SharedPreferences) = sharedPreferences.run {
        if (getBoolean("temperature_slider", false))
            temperatureModeManager.setMode(TemperatureMode.Slider)
        else if (getBoolean("night_mode", false))
            temperatureModeManager.setMode(TemperatureMode.Night)
        else
            temperatureModeManager.setMode(TemperatureMode.White)
    }

    private fun updateColorScheme(sharedPreferences: SharedPreferences) = sharedPreferences.run {
        val type = getString("color_scheme_type", "5")
        val colorString = getInt("color_scheme_color", 20).progressToHex()
        commandRunner.runCommands(arrayOf("theme $type $colorString"))
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "disable_nightmode" -> {
                temperatureModeManager.isDisabled =
                    (sharedPreferences?.getBoolean("disable_nightmode", false) == true)
                if (!temperatureModeManager.isDisabled) {
                    sharedPreferences?.let { setCorrectMode(it) }
                }
            }

            "night_mode" -> {
                menuBinding?.nightSwitch?.isChecked =
                    sharedPreferences?.getBoolean("night_mode", false) == true

                sharedPreferences?.let { setCorrectMode(it) }
            }

            "temperature_slider" -> {
                sharedPreferences?.let { setCorrectMode(it) }
            }

            "color_scheme_type", "color_scheme_color" -> {
                sharedPreferences?.let { updateColorScheme(it) }
            }

            "overlay_chess", "aod_image_updated" -> alwaysOnDisplay.update()
        }
    }
}

fun AccessibilityEvent?.letPackageNameClassName(block: (String, String) -> Unit) {
    this?.run {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            this.className?.let { clsName ->
                this.packageName?.let { pkgName -> block(pkgName.toString(), clsName.toString()) }
            }
    }
}

fun FloatingMenuLayoutBinding?.close() = this?.run {
    root.visibility = View.GONE
}

fun FloatingMenuLayoutBinding?.updateButtons(mode: RefreshMode) = this?.run {
    listOf(button1, button2, button3, button4).forEach(Button::deselect)
    when (mode) {
        RefreshMode.CLEAR -> button1
        RefreshMode.BALANCED -> button2
        RefreshMode.SMOOTH -> button3
        RefreshMode.SPEED -> button4
    }.select()
}

fun Button.deselect() {
    setBackgroundResource(R.drawable.drawable_border_normal)
    setTextColor(Color.BLACK)
}

fun Button.select() {
    setBackgroundResource(R.drawable.drawable_border_pressed)
    setTextColor(Color.WHITE)
}
