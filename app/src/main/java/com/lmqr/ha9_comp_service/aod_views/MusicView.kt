package com.lmqr.ha9_comp_service.aod_views

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.R
import java.lang.NumberFormatException
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MusicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    @Volatile
    private var musicState: MusicState = MusicState()
        set(value) {
            field = value
            invalidate()
            handler.removeCallbacks(pollingRunnable)
            if (field.isPlaying && field.title.isNotEmpty()) {
                val pollingTime = min(field.length + 500L, 10 * 60 * 1000L)
                Log.d("MusicView", "Polling in $pollingTime")
                handler.postDelayed(pollingRunnable, pollingTime)
            }
        }

    private val titlePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            16f,
            context.resources.displayMetrics)
        isFakeBoldText = true
    }

    private val artistPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0.9f, 0.9f, 0.9f)
        textAlign = Paint.Align.LEFT
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            context.resources.displayMetrics)
    }

    private val contrastMultiplier = 0.9f
    private val redValue = 0.3086f * contrastMultiplier
    private val greenValue = 0.6094f * contrastMultiplier
    private val blueValue = 0.0820f * contrastMultiplier
    private val shiftColor = 20f

    private val albumPaint: Paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            set(
                floatArrayOf(
                    redValue, redValue, redValue, 0f, shiftColor,
                    greenValue, greenValue, greenValue, 0f, shiftColor,
                    blueValue, blueValue, blueValue, 0f, shiftColor,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        })
    }

    private val shaderPaint: Paint = Paint().apply {
        isAntiAlias = true
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val albumMatrix: Matrix = Matrix()

    private val albumOutline: Path = Path()

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val pauseIndicatorPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate = false

    private val pollingRunnable = Runnable {
        retrieveAndSetMetadata()
    }

    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            if (!pendingUpdate) {
                pendingUpdate = true
                handler.postDelayed({
                    pendingUpdate = false
                    retrieveAndSetMetadata()
                }, 500)
            }
        }
    }

    private var sharedPreferences: SharedPreferences? = null

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(context, NotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private var baseSongDrawable: Drawable? = null

    private val hideViewRunnable = Runnable {
        musicState = MusicState()
    }

    private fun retrieveAndSetMetadata(retriesLeft: Int = 7) {
        if(!mediaCallbackRegistered)
            return

        val controllers = mediaSessionManager.getActiveSessions(
            ComponentName(context, NotificationListener::class.java)
        )

        synchronized(this@MusicView) {
            controllers.firstOrNull(MediaController::isActive)?.run {
                val metadata = metadata
                val playbackState = playbackState
                val totalDuration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
                val currentPosition = (playbackState?.position ?: 0L)
                val playbackSpeed = max((playbackState?.playbackSpeed ?: 1f), 0.1f)
                val side = max(width, height)
                val retrievedAlbumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                val isPlaying = isPlaying()
                val shouldRetry = retrievedAlbumArt == null

                handler.removeCallbacks(hideViewRunnable)
                if(!isPlaying){
                    try {
                        val timeout = sharedPreferences?.getString(
                            "music_view_timeout",
                            "-1"
                        )?.let {
                            Integer.parseInt(
                                it
                            )
                        }?:-1

                        if(timeout == 0) {
                            musicState = MusicState()
                            return
                        } else if(timeout > 0) {
                            handler.postDelayed(hideViewRunnable, timeout * 60 * 1000L)
                        }
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                    }
                }
                musicState = MusicState(
                    albumArt = retrievedAlbumArt?:baseSongDrawable?.toBitmap(side, side),
                    title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                    artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                    isPlaying = isPlaying,
                    length = ((totalDuration - currentPosition) / playbackSpeed).toLong()
                )
                if(shouldRetry && retriesLeft in 1..10){
                    pendingUpdate = true
                    handler.postDelayed({
                        pendingUpdate = false
                        retrieveAndSetMetadata(retriesLeft - 1)
                    }, 200)
                }
                return
            }
            musicState = MusicState()
        }
    }

    private fun drawTextMultiline(canvas: Canvas,
                                  top: Int,
                                  left: Int,
                                  right: Int,
                                  text: String,
                                  paint: Paint,
                                  alignLeft: Boolean = true): Float {
        val lineWidth = right - left
        val textQueue = LinkedList(text.split("\\s+".toRegex()))
        var currentLineHeight = paint.descent() - paint.ascent()
        var currentLine = ""
        while (!textQueue.isEmpty()) {
            val currentWord = textQueue.pop()
            val afterConcat = "$currentLine $currentWord"
            if (paint.measureText(afterConcat) > lineWidth) {
                val offset = left + if (alignLeft) 0f else lineWidth - paint.measureText(currentLine)
                canvas.drawText(currentLine, offset, top + currentLineHeight, paint)
                currentLineHeight += paint.descent() - paint.ascent()
                currentLine = currentWord
            } else {
                currentLine = afterConcat
            }
        }
        if (currentLine.isNotEmpty()) {
            val offset = left + if (alignLeft) 0f else lineWidth - paint.measureText(currentLine)
            canvas.drawText(currentLine, offset, top + currentLineHeight, paint)
            return currentLineHeight
        }
        return currentLineHeight - paint.ascent() + paint.descent()
    }

    private var lastCenterX = -1f
    private var lastCenterY = -1f
    private var lastRadius = -1f

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        musicState.run {
            albumArt?.let {
                val scaleFactor = width.toFloat() / it.width
                val scaledHeight = it.height * scaleFactor
                val top = (height - scaledHeight) / 2

                albumOutline.run {
                    reset()
                    addRoundRect(0f, max(0f, top),
                        width.toFloat(), min(height.toFloat(), top + scaledHeight),
                        20f, 20f,
                        Path.Direction.CW)
                }

                canvas.save()
                canvas.clipPath(albumOutline)

                albumMatrix.run {
                    setScale(scaleFactor, scaleFactor)
                    postTranslate(0f, top)
                }

                canvas.drawBitmap(it, albumMatrix, albumPaint)

                val centerX = width / 2f
                val centerY = height / 2f
                val radius = sqrt((width * width + height * height).toFloat()) / 2f

                if(lastCenterX != centerX || lastCenterY != centerY || lastRadius != radius) {
                    shaderPaint.shader = RadialGradient(
                        centerX, centerY, radius,
                        intArrayOf(
                            Color.argb(0.02f, 0.1f, 0.1f, 0.1f),
                            Color.argb(0.1f, 0f, 0f, 0f),
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    lastCenterX = centerX
                    lastCenterY = centerY
                    lastRadius = radius
                }
                canvas.drawCircle(centerX, centerY, radius, shaderPaint)

                canvas.restore()

                if (!isPlaying) {
                    val symbolSize = width / 10f
                    val symbolLeft = paddingLeft.toFloat()
                    val symbolTop = height - paddingBottom - symbolSize

                    val pauseWidth = symbolSize / 4
                    canvas.drawRect(symbolLeft, symbolTop, symbolLeft + pauseWidth, symbolTop + symbolSize, pauseIndicatorPaint)
                    canvas.drawRect(
                        symbolLeft + 2 * pauseWidth,
                        symbolTop,
                        symbolLeft + 3 * pauseWidth,
                        symbolTop + symbolSize,
                        pauseIndicatorPaint
                    )
                }
            }

            val titleHeight = drawTextMultiline(canvas, paddingTop, paddingLeft, width - paddingRight, title, titlePaint, false)

            drawTextMultiline(canvas, (paddingTop + titleHeight).toInt(), paddingLeft, width - paddingRight, artist, artistPaint, false)
        }
    }

    private val mediaSessionManager: MediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val activeSessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        Log.d("MusicStateManager", "Active sessions changed")
        synchronized(this) {
            if (controllers != null) {
                pendingUpdate = true
                handler.postDelayed({
                    pendingUpdate = false
                    retrieveAndSetMetadata()
                }, 500)
            } else {
                synchronized(this) {
                    musicState = MusicState()
                }
            }
        }
    }

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (isNotificationListenerEnabled()) {
                if(!mediaCallbackRegistered)
                    initMediaControllerCallbacks()
            } else {
                if(mediaCallbackRegistered) {
                    musicState = MusicState()
                    mediaSessionManager.removeOnActiveSessionsChangedListener(
                        activeSessionsChangedListener
                    )
                    audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback)
                    mediaCallbackRegistered = false
                }
            }
        }
    }

    private var settingCallbackRegistered = false
    private var mediaCallbackRegistered = false
    private fun initMediaControllerCallbacks() {
        if (isNotificationListenerEnabled()) {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener,
                ComponentName(context, NotificationListener::class.java)
            )
            retrieveAndSetMetadata()
            audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, null)
            mediaCallbackRegistered = true
        } else {
            Log.d("MusicView", "Notification listener service is not enabled")
        }
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        baseSongDrawable = ContextCompat.getDrawable(context, R.drawable.empty_music_box)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (isNotificationListenerEnabled()) {
            initMediaControllerCallbacks()
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("enabled_notification_listeners"),
            false, settingsObserver)
        settingCallbackRegistered = true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(pollingRunnable)
        if(mediaCallbackRegistered) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
            audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback)
        }
        if(settingCallbackRegistered)
            context.contentResolver.unregisterContentObserver(settingsObserver)
        super.onDetachedFromWindow()
    }
}

private fun MediaController.isPlaying() = playbackState?.state?.let { state ->
    state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
} ?: false

private fun MediaController.isActive() = playbackState?.state?.let { state ->
    state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR
} ?: false

data class MusicState(
    val albumArt: Bitmap? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val length: Long = 0L
)
