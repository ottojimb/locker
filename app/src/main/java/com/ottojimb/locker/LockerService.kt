package com.ottojimb.locker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * Keeps a recent "screenshot + overlay" composite and shows it in an accessibility overlay
 * (above Bigme's ScreenSaveActivity) when the screen turns off, so the e-ink panel keeps it.
 */
class LockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private lateinit var windowManager: WindowManager

    private var overlay: Bitmap? = null
    private var composite: Bitmap? = null
    private var overlayView: ImageView? = null
    private var captureInFlight = false
    private var screenOffAt = 0L

    private val captureRunnable = Runnable { capture(force = false, onDone = null) }
    private val periodicRunnable = object : Runnable {
        override fun run() {
            if (canCaptureNow()) capture(force = false, onDone = null)
            handler.postDelayed(this, PERIODIC_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> hideOverlay("screen on")
                Intent.ACTION_USER_PRESENT -> scheduleCapture()
            }
        }
    }

    override fun onServiceConnected() {
        power = getSystemService(PowerManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        windowManager = getSystemService(WindowManager::class.java)
        reloadOverlay()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        instance = this
        handler.postDelayed(periodicRunnable, PERIODIC_MS)
        scheduleCapture()
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == STANDBY_PKG) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Log.i(TAG, "standby window seen ${sinceScreenOff()}")
                onStandbyWindow()
            }
            return
        }
        if (pkg == packageName || pkg == SYSTEMUI_PKG) return
        scheduleCapture()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        hideOverlay("destroy")
        loader.shutdownNow()
        super.onDestroy()
    }

    /** Called by the UI after the overlay PNG changes. */
    /** Loads the overlay (a new random one in folder mode) off the main thread. */
    fun reloadOverlay() {
        loader.execute {
            val next = OverlayStore.load(this)
            handler.post {
                overlay = next
                scheduleCapture()
                Log.i(TAG, "overlay loaded: ${next?.let { "${it.width}x${it.height}" }}")
            }
        }
    }

    /** Quick Settings fallback: capture now, show the composite and lock. */
    fun lockNow() {
        if (overlayView != null) return
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        handler.postDelayed({
            capture(force = true) {
                showOverlay("manual")
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, LOCK_DELAY_MS)
            }
        }, SHADE_CLOSE_MS)
    }

    private fun canCaptureNow() =
        OverlayStore.isEnabled(this) && overlayView == null &&
            power.isInteractive && !keyguard.isKeyguardLocked

    private fun scheduleCapture() {
        if (!canCaptureNow()) return
        handler.removeCallbacks(captureRunnable)
        handler.postDelayed(captureRunnable, DEBOUNCE_MS)
    }

    private fun onScreenOff() {
        screenOffAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(captureRunnable)
        if (!OverlayStore.isEnabled(this) || overlayView != null) return
        // Bigme's light standby (5 s idle) also sends SCREEN_OFF; only refresh the cache here.
        // Showing the overlay now would flash it when the user touches the screen again.
        Log.i(TAG, "screen off, refreshing cache")
        capture(force = true, onDone = null)
    }

    /** Bigme's sleep dialog appeared (super standby): capture what is under it and show the composite. */
    private fun onStandbyWindow() {
        if (!OverlayStore.isEnabled(this) || overlayView != null) return
        handler.removeCallbacks(captureRunnable)
        handler.postDelayed({ showOverlay("fallback timeout") }, FRESH_TIMEOUT_MS)
        capture(force = true) { showOverlay("fresh capture") }
    }

    private fun capture(force: Boolean, onDone: (() -> Unit)?) {
        if (captureInFlight) {
            if (onDone != null) handler.postDelayed({ onDone() }, 50)
            return
        }
        captureInFlight = true
        val requestedAt = SystemClock.uptimeMillis()
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                captureInFlight = false
                val shot = Compositor.toBitmap(result.hardwareBuffer, result.colorSpace)
                // A non-forced capture that lands after the screen went off may contain the standby screen.
                val stale = !force && (overlayView != null || !power.isInteractive)
                if (shot != null && !stale && overlayView == null) {
                    composite = Compositor.compose(shot, overlay)
                } else {
                    shot?.recycle()
                }
                if (force) Log.i(TAG, "capture took ${SystemClock.uptimeMillis() - requestedAt}ms ${sinceScreenOff()}")
                onDone?.invoke()
            }

            override fun onFailure(errorCode: Int) {
                captureInFlight = false
                Log.w(TAG, "screenshot failed: $errorCode ${sinceScreenOff()}")
                if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && onDone == null) {
                    scheduleCapture()
                }
                onDone?.invoke()
            }
        })
    }

    private fun showOverlay(reason: String) {
        if (overlayView != null) return
        val bitmap = composite ?: run {
            Log.w(TAG, "no composite to show ($reason)")
            return
        }
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(bitmap)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        windowManager.addView(view, params)
        overlayView = view
        Log.i(TAG, "overlay shown ($reason) ${sinceScreenOff()}")
    }

    private fun hideOverlay(reason: String) {
        val view = overlayView ?: return
        runCatching { windowManager.removeView(view) }
        overlayView = null
        Log.i(TAG, "overlay hidden ($reason)")
        // Prepare the next random image for the following lock.
        if (reason != "destroy" && OverlayStore.folder(this) != null) reloadOverlay()
    }

    private fun sinceScreenOff() =
        if (screenOffAt == 0L) "" else "(+${SystemClock.uptimeMillis() - screenOffAt}ms after screen off)"

    companion object {
        private const val TAG = "LockerService"
        private const val STANDBY_PKG = "com.xrz.standby"
        private const val SYSTEMUI_PKG = "com.android.systemui"
        private const val DEBOUNCE_MS = 600L
        private const val PERIODIC_MS = 5_000L
        private const val FRESH_TIMEOUT_MS = 250L
        private const val SHADE_CLOSE_MS = 700L
        private const val LOCK_DELAY_MS = 300L

        @Volatile
        var instance: LockerService? = null
            private set
    }
}
