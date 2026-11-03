package com.example.knockdock.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

/**
 * Renders the 4-Edge Perimeter Neon Lighting + Action Activated HUD Pill over the Android
 * Homescreen, over other apps when KnockDock is closed, and over the Lockscreen when a desk knock
 * triggers an action.
 *
 * Strictly non-interactive and touch-pass-through:
 * - Uses `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_NOT_TOUCH_MODAL`
 * - Respects Android 12+ (API 31+) `InputManager.maximumObscuringOpacityForTouch` so Android's
 *   `InputDispatcher` NEVER blocks or intercepts user touches while the overlay is visible
 * - Includes a hard watchdog timer so the overlay view is guaranteed to be removed even if
 *   system animations are disabled (`animator_duration_scale = 0`).
 */
object SystemEdgeLightingOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val attachedViews = CopyOnWriteArrayList<View>()
    private var activeAnimator: ValueAnimator? = null
    private var lastOverlayShownUptimeMs: Long = 0L

    private var watchdogCleanupRunnable: Runnable? = null

    fun canDrawSystemOverlay(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Computes a window opacity strictly below Android 12+'s untrusted-touch blocking threshold
     * (`InputManager.maximumObscuringOpacityForTouch`, default 0.80f) so 100% of user touches
     * pass through to the underlying app or homescreen without interruption.
     */
    private fun getTouchPassThroughWindowAlpha(context: Context): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
                if (im != null) {
                    val maxAllowed = im.maximumObscuringOpacityForTouch
                    if (maxAllowed > 0.1f) {
                        return min(0.76f, maxAllowed * 0.90f)
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return 0.76f
    }

    private fun isAppInForeground(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            val packageName = context.packageName
            activityManager.runningAppProcesses?.any { process ->
                process.processName == packageName &&
                    process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } == true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Triggers the 4-Edge Perimeter Neon Lighting over the Homescreen / other apps / Lockscreen
     * whenever the app is in the background or the device is on the lockscreen.
     */
    fun showEdgeLightingIfBackgroundOrLocked(
        context: Context,
        actionName: String,
        gestureName: String,
        impactCount: Int = 2,
        success: Boolean = true
    ) {
        val appContext = context.applicationContext
        mainHandler.post {
            try {
                val now = SystemClock.uptimeMillis()
                if (now - lastOverlayShownUptimeMs < 400L) {
                    return@post
                }

                val appInForeground = isAppInForeground(appContext)
                val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isLocked = keyguardManager?.isKeyguardLocked == true
                val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isInteractive = powerManager?.isInteractive ?: true

                // Briefly wake the display if a knock occurred while screen was off
                if (!isInteractive) {
                    wakeScreenBriefly(appContext)
                }

                // If the app is already open in the foreground and unlocked, MainActivity's in-app
                // KnockEdgeLightingOverlay + LiquidMacroDoneHudBanner handles the visual effect.
                if (appInForeground && !isLocked && isInteractive) {
                    return@post
                }

                // Only use the non-touchable WindowManager overlay so we never steal focus or intercept touch
                if (canDrawSystemOverlay(appContext)) {
                    lastOverlayShownUptimeMs = now
                    showWindowManagerEdgeOverlay(
                        context = appContext,
                        actionName = actionName,
                        gestureName = gestureName,
                        impactCount = impactCount,
                        success = success
                    )
                }
            } catch (_: Throwable) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "KnockDock:EdgeLightingWake"
            )
            wl.acquire(1800L)
        } catch (_: Throwable) {
        }
    }

    private fun showWindowManagerEdgeOverlay(
        context: Context,
        actionName: String,
        gestureName: String,
        impactCount: Int,
        success: Boolean
    ) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        dismissAllOverlays(wm)

        val overlayView = EdgeLightingCanvasView(
            context = context,
            actionName = actionName,
            gestureName = gestureName,
            impactCount = impactCount,
            success = success
        ).apply {
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Critical for Android 12+ (API 31+): keep window alpha strictly below
            // InputManager.maximumObscuringOpacityForTouch (0.8f) so touches pass through 100%
            alpha = getTouchPassThroughWindowAlpha(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(overlayView, params)
            attachedViews.add(overlayView)

            // Hard failsafe watchdog: guarantees removal after 1300ms even if ValueAnimator is scaled to 0
            val cleanupTask = Runnable {
                dismissAllOverlays(wm)
            }
            watchdogCleanupRunnable = cleanupTask
            mainHandler.postDelayed(cleanupTask, 1300L)

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1150L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val progress = anim.animatedValue as Float
                    overlayView.updateAnimationProgress(progress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        dismissAllOverlays(wm)
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        dismissAllOverlays(wm)
                    }
                })
            }
            activeAnimator = animator
            animator.start()
        } catch (_: Throwable) {
            dismissAllOverlays(wm)
        }
    }

    private fun dismissAllOverlays(wm: WindowManager) {
        watchdogCleanupRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogCleanupRunnable = null

        val anim = activeAnimator
        activeAnimator = null
        if (anim != null) {
            try {
                anim.removeAllListeners()
                anim.cancel()
            } catch (_: Throwable) {
            }
        }

        for (view in attachedViews) {
            try {
                wm.removeViewImmediate(view)
            } catch (_: Throwable) {
                try {
                    wm.removeView(view)
                } catch (_: Throwable) {
                }
            }
        }
        attachedViews.clear()
    }
}

/**
 * Legacy manifest-registered activity kept for manifest compatibility; finishes immediately
 * so it never steals focus or intercepts touch events.
 */
class LockscreenEdgeLightingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

/**
 * Hardware-accelerated View that renders KnockDock's 4-Edge Perimeter Neon Lighting
 * and the top capsule HUD banner ("KNOCK DETECTED • ACTION ACTIVATED").
 * Explicitly rejects all touch events so user interaction is never intercepted.
 */
internal class EdgeLightingCanvasView(
    context: Context,
    private val actionName: String,
    private val gestureName: String,
    impactCount: Int,
    success: Boolean
) : View(context) {

    private val density = resources.displayMetrics.density
    private var progress: Float = 0f

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false
    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    private val primaryColor: Int = when {
        !success -> Color.rgb(255, 183, 77)
        impactCount == 1 -> Color.rgb(64, 224, 255)
        impactCount == 2 -> Color.rgb(124, 104, 255)
        else -> Color.rgb(52, 211, 153)
    }
    private val secondaryColor: Int = Color.rgb(56, 189, 248)
    private val tertiaryColor: Int = Color.rgb(52, 211, 153)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val perimeterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(103, 232, 249)
        textSize = 9.5f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val actionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(203, 213, 225)
        textSize = 10.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val rectF = RectF()
    private val sweepMatrix = Matrix()

    fun updateAnimationProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val a = (255f * factor.coerceIn(0f, 1f)).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Smooth attack + decay envelope
        val alpha = if (progress < 0.14f) {
            (progress / 0.14f).coerceIn(0f, 1f)
        } else {
            (1f - (progress - 0.14f) / 0.86f).coerceIn(0f, 1f)
        }
        if (alpha <= 0.01f) return

        val cornerR = 36f * density
        val glowDepth = 26f * density * (0.5f + 0.5f * alpha)

        // 1. Top edge inward glow
        glowPaint.shader = LinearGradient(
            0f, 0f, 0f, glowDepth,
            withAlpha(primaryColor, 0.44f * alpha),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, glowDepth, glowPaint)

        // 2. Bottom edge inward glow
        glowPaint.shader = LinearGradient(
            0f, h - glowDepth, 0f, h,
            Color.TRANSPARENT,
            withAlpha(secondaryColor, 0.44f * alpha),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h - glowDepth, w, h, glowPaint)

        // 3. Left edge inward glow
        glowPaint.shader = LinearGradient(
            0f, 0f, glowDepth, 0f,
            withAlpha(tertiaryColor, 0.38f * alpha),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, glowDepth, h, glowPaint)

        // 4. Right edge inward glow
        glowPaint.shader = LinearGradient(
            w - glowDepth, 0f, w, 0f,
            Color.TRANSPARENT,
            withAlpha(primaryColor, 0.38f * alpha),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(w - glowDepth, 0f, w, h, glowPaint)

        // 5. Rotating 4-edge sweep gradient perimeter stroke
        val sweepColors = intArrayOf(
            withAlpha(primaryColor, 0.94f * alpha),
            withAlpha(secondaryColor, 0.90f * alpha),
            withAlpha(tertiaryColor, 0.94f * alpha),
            withAlpha(Color.WHITE, 0.85f * alpha),
            withAlpha(primaryColor, 0.94f * alpha)
        )
        val sweepShader = SweepGradient(w / 2f, h / 2f, sweepColors, null)
        sweepMatrix.reset()
        sweepMatrix.postRotate(progress * 360f, w / 2f, h / 2f)
        sweepShader.setLocalMatrix(sweepMatrix)

        val outerStrokePx = (6.0f * alpha + 2.2f) * density
        perimeterPaint.strokeWidth = outerStrokePx
        perimeterPaint.shader = sweepShader
        val inset = outerStrokePx * 0.4f
        rectF.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rectF, cornerR, cornerR, perimeterPaint)

        // 6. Crisp specular inner core line along all 4 edges
        val coreInset = 2.0f * density
        corePaint.color = withAlpha(Color.WHITE, 0.78f * alpha)
        rectF.set(coreInset, coreInset, w - coreInset, h - coreInset)
        canvas.drawRoundRect(rectF, cornerR, cornerR, corePaint)

        // 7. Top Capsule HUD Pill ("KNOCK DETECTED • ACTION ACTIVATED")
        val pillWidth = min(w - 32f * density, 380f * density)
        val pillHeight = 62f * density
        val pillLeft = (w - pillWidth) / 2f
        val pillTop = 44f * density
        val pillRadius = pillHeight / 2f

        rectF.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)
        pillBgPaint.color = Color.argb((215 * alpha).toInt(), 15, 23, 42)
        canvas.drawRoundRect(rectF, pillRadius, pillRadius, pillBgPaint)

        pillBorderPaint.color = withAlpha(primaryColor, 0.85f * alpha)
        canvas.drawRoundRect(rectF, pillRadius, pillRadius, pillBorderPaint)

        titlePaint.alpha = (255 * alpha).toInt()
        actionPaint.alpha = (255 * alpha).toInt()
        subtitlePaint.alpha = (230 * alpha).toInt()

        val textStartX = pillLeft + 20f * density
        canvas.drawText("KNOCK DETECTED • ACTION ACTIVATED", textStartX, pillTop + 20f * density, titlePaint)

        val trimmedAction = if (actionName.length > 34) actionName.take(31) + "..." else actionName
        canvas.drawText(trimmedAction, textStartX, pillTop + 38f * density, actionPaint)

        val trimmedGesture = if (gestureName.length > 42) gestureName.take(39) + "..." else gestureName
        canvas.drawText("Triggered by $trimmedGesture", textStartX, pillTop + 53f * density, subtitlePaint)
    }
}
