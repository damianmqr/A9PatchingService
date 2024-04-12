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
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
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
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands
import com.lmqr.ha9_comp_service.command_runners.UnixSocketCommandRunner
import com.lmqr.ha9_comp_service.databinding.AodLayoutBinding
import com.lmqr.ha9_comp_service.databinding.FloatingMenuLayoutBinding
import java.io.File
import kotlin.math.max
import kotlin.math.min


class A9AccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var commandRunner: CommandRunner
    private lateinit var temperatureModeManager: TemperatureModeManager
    private lateinit var refreshModeManager: RefreshModeManager
    private lateinit var sharedPreferences: SharedPreferences
    private var isScreenOn = true

    private val gameManager = GameManager()
    private fun moveChess() {
        gameManager.move(this)
        gameManager.game?.let { game ->
            aodLayoutBinding?.let { bnd ->
                bnd.whitePlayer.text = game.whitePlayer
                bnd.blackPlayer.text = game.blackPlayer
                bnd.whitePlayerResult.text = game.currentWhiteResult
                bnd.blackPlayerResult.text = game.currentBlackResult
                bnd.venue.text = game.event
                bnd.opening.text = game.opening
            }
        }
    }

    private val moveChessRunnable = object : Runnable {
        override fun run() {
            if (isScreenOn)
                return
            moveChess()
            aodLayoutBinding?.chessboard?.updatePieces(gameManager.getPieces())
            handler.postDelayed(this, 60 * 1000)
        }
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    temperatureModeManager.onScreenChange(false)
                    menuBinding.close()
                    openAOD()
                    if (sharedPreferences.getBoolean("overlay_chess", false))
                        handler.post(moveChessRunnable)
                }

                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    closeAOD()
                    handler.removeCallbacks(moveChessRunnable)
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
            UnixSocketCommandRunner()//RootCommandRunner()//FIFOCommandRunner(filesDir.absolutePath)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
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
    }

    override fun onInterrupt() {

    }

    private var lastClickTime: Long = 0

    private fun handleSinglePress() {
        when {
            !isScreenOn -> {}
            !sharedPreferences.getBoolean("swap_clear_button", false) -> commandRunner.runCommands(
                arrayOf(Commands.FORCE_CLEAR)
            )

            else -> openFloatingMenu()
        }
    }

    private fun handleDoublePress() {
        when {
            !isScreenOn -> {
                if (sharedPreferences.getBoolean("overlay_chess", false)) {
                    handler.removeCallbacks(moveChessRunnable)
                    handler.post(moveChessRunnable)
                }
            }

            sharedPreferences.getBoolean("swap_clear_button", false) -> commandRunner.runCommands(
                arrayOf(Commands.FORCE_CLEAR)
            )

            else -> openFloatingMenu()
        }
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
    private fun openFloatingMenu() {
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

    private fun AodLayoutBinding.loadBackgroundImage() {
        val file = File(filesDir, "bg_image")

        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(resources, bitmap)
                    root.background = drawable
                } else {
                    root.setBackgroundColor(Color.WHITE)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                root.setBackgroundColor(Color.WHITE)
            }
        } else {
            root.setBackgroundColor(Color.WHITE)
        }
    }

    private fun AodLayoutBinding.updateChessboardVisibility() {
        if (!sharedPreferences.getBoolean("overlay_chess", false)) {
            chessboard.visibility = View.GONE
            chessboardWhitePanel.visibility = View.GONE
            chessboardBlackPanel.visibility = View.GONE
            venue.visibility = View.GONE
            opening.visibility = View.GONE
        } else {
            chessboard.visibility = View.VISIBLE
            chessboardWhitePanel.visibility = View.VISIBLE
            chessboardBlackPanel.visibility = View.VISIBLE
            venue.visibility = View.VISIBLE
            opening.visibility = View.VISIBLE
        }
    }

    private var aodLayoutBinding: AodLayoutBinding? = null
    private fun openAOD() {
        if (!sharedPreferences.getBoolean("overlay_aod", false))
            return

        aodLayoutBinding?.root?.run { visibility = View.VISIBLE } ?: run {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.aod_layout, null, false)

            val layoutParams = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.OPAQUE
                flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }

            aodLayoutBinding = AodLayoutBinding.bind(view).apply {
                wm.addView(root, layoutParams)

                updateChessboardVisibility()

                loadBackgroundImage()
            }
        }
    }

    private fun closeAOD() {
        aodLayoutBinding?.root?.visibility = View.GONE
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

            "overlay_chess" -> aodLayoutBinding?.updateChessboardVisibility()

            "aod_image_updated" -> aodLayoutBinding?.loadBackgroundImage()
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
