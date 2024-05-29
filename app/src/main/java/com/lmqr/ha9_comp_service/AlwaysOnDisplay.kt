package com.lmqr.ha9_comp_service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.view.children
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.aod_views.AODExtraView
import com.lmqr.ha9_comp_service.aod_views.chess_view.ChessboardView
import com.lmqr.ha9_comp_service.databinding.AodLayoutBinding
import java.io.File
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
            ?.run { visibility = View.VISIBLE }
            ?: contextWeakReference.get()?.let {
                attachToWindowManager(
                    it
                )
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
}

private fun AodLayoutBinding.loadBackgroundImage(ctx: Context) {
    val file = File(ctx.filesDir, "bg_image")

    if (file.exists()) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                val drawable = BitmapDrawable(ctx.resources, bitmap)
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