package com.lmqr.ha9_comp_service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.view.children
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.aod_views.AODExtraView
import com.lmqr.ha9_comp_service.aod_views.chess_view.ChessboardView
import com.lmqr.ha9_comp_service.databinding.AodLayoutBinding
import java.lang.ref.WeakReference

class AlwaysOnDisplay(
    context: Context
) {
    private val handler = Handler(Looper.getMainLooper())
    private val contextWeakReference: WeakReference<Context>
    init {
        contextWeakReference = WeakReference(context)
    }

    private var aodLayoutBinding: AodLayoutBinding? = null
    private fun attachToWindowManager(ctx: Context){
        val wm = ctx.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(ctx)
        val view = inflater.inflate(R.layout.aod_layout, null, false)

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.OPAQUE
            flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        aodLayoutBinding = AodLayoutBinding.bind(view).apply {
            wm.addView(root, layoutParams)

            updateExtraViewVisibility(ctx)

            loadBackgroundImage(ctx)
        }
    }

    private val performExtraActionRunnable = object : Runnable {
        override fun run() {
            performExtraAction()
            handler.postDelayed(this, 60 * 1000)
        }
    }

    fun openAOD() {
        aodLayoutBinding?.root
            ?.run {
                visibility = View.VISIBLE
                contextWeakReference.get()?.let { aodLayoutBinding?.loadBackgroundImage(it) }
            } ?: contextWeakReference.get()?.let {
                attachToWindowManager(
                    it
                )
                handler.post{
                    update()
                }
            }
        handler.post(performExtraActionRunnable)
    }

    fun closeAOD() {
        handler.removeCallbacks(performExtraActionRunnable)
        aodLayoutBinding?.root?.visibility = View.GONE
    }

    fun performExtraAction() = contextWeakReference.get()?.run{
        (aodLayoutBinding?.extraView?.children?.firstOrNull() as? AODExtraView)
            ?.performAction(this)
    }

    fun update() =
        contextWeakReference.get()?.let { ctx ->
            aodLayoutBinding?.run{
                loadBackgroundImage(ctx)
                updateExtraViewVisibility(ctx)
            }
        }

    private var lastTimeImage = 0L

    private fun AodLayoutBinding.loadBackgroundImage(ctx: Context) {
        val file = getBackgroundFileImage(ctx)

        if (file.exists()) {
            if(lastTimeImage == file.lastModified())
                return
            lastTimeImage = file.lastModified()

            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val viewWidth = root.width
                    val viewHeight = root.height
                    var offsetX = 0
                    var offsetY = 0
                    val scaledBitmap = if(viewWidth * bitmap.height < bitmap.width * viewHeight){
                        val newWidth = viewHeight * bitmap.width / bitmap.height
                        offsetX = (newWidth - viewWidth) / 2
                        Bitmap.createScaledBitmap(
                            bitmap,
                            newWidth,
                            viewHeight,
                            true
                        )
                    } else {
                        val newHeight = viewWidth * bitmap.height / bitmap.width
                        offsetY = (newHeight - viewHeight) / 2
                        Bitmap.createScaledBitmap(
                            bitmap,
                            viewWidth,
                            newHeight,
                            true
                        )
                    }


                    val scaledDrawable = BitmapDrawable(ctx.resources, scaledBitmap)

                    scaledDrawable.gravity = Gravity.CENTER
                    root.background = scaledDrawable

                    timeClock.setTextColor(
                        if(timeClock.getLuminance(scaledBitmap, offsetX, offsetY) > 0.5)
                            Color.BLACK
                        else
                            Color.WHITE
                    )
                    dateClock.setTextColor(
                        if(dateClock.getLuminance(scaledBitmap, offsetX, offsetY) > 0.5)
                            Color.BLACK
                        else
                            Color.WHITE
                    )
                    notificationIconView.adjustToLighten = notificationIconView.getLuminance(scaledBitmap, offsetX, offsetY) < 0.5

                    batteryIndicator.run {
                        batteryIndicator.setWhite(
                            left = getLuminanceForView(
                                scaledBitmap,
                                offsetX,
                                offsetY,
                                left,
                                top,
                                width / 3,
                                height
                            ) < 0.5,
                            right = getLuminanceForView(
                                scaledBitmap,
                                offsetX,
                                offsetY,
                                left + width * 2 / 3,
                                top,
                                width / 3,
                                height
                            ) < 0.5,
                        )
                    }
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lastTimeImage = 0L
        root.setBackgroundColor(Color.WHITE)
        timeClock.setTextColor(Color.BLACK)
        dateClock.setTextColor(Color.BLACK)
        notificationIconView.adjustToLighten = false
        batteryIndicator.setWhite(left = false, right = false)
    }
}

private fun getLuminanceForView(bitmap: Bitmap, offsetX: Int, offsetY: Int, viewLeft: Int, viewTop: Int, viewWidth: Int, viewHeight: Int): Float {
    val startX = maxOf(offsetX + viewLeft, 0)
    val startY = maxOf(offsetY + viewTop, 0)
    val width = minOf(viewWidth, bitmap.width - startX)
    val height = minOf(viewHeight, bitmap.height - startY)
    if(width < 0 || height < 0)
        return 0f
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, startX, startY, width, height)
    return Color.luminance(
        Color.rgb(
            pixels.sumOf(Color::red) / pixels.size,
            pixels.sumOf(Color::green) / pixels.size,
            pixels.sumOf(Color::blue) / pixels.size
        )
    )
}

private fun View.getLuminance(bitmap: Bitmap, offsetX: Int, offsetY: Int) =
    getLuminanceForView(bitmap, offsetX, offsetY, left, top, width, height)


private fun AodLayoutBinding.updateExtraViewVisibility(ctx: Context) {
    if (!PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("overlay_chess", false)) {
        extraView.visibility = View.GONE
    } else {
        if(extraView.childCount == 0){
            extraView.addView(
                ChessboardView(ctx)
            )
        }
        extraView.visibility = View.VISIBLE
    }
}