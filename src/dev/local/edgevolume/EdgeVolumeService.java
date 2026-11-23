package dev.local.edgevolume;

import android.accessibilityservice.AccessibilityService;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;

/**
 * Accessibility service that draws an invisible strip on the right screen edge
 * (TYPE_ACCESSIBILITY_OVERLAY, which sits above the keyguard and immersive apps).
 * Vertical drags on the strip change the media volume: up = louder, down = quieter.
 */
public class EdgeVolumeService extends AccessibilityService {

    private static final float STRIP_WIDTH_DP = 22f;  // touch strip width
    private static final float STEP_DP = 14f;         // finger travel per step (portrait)
    private static final float STEP_DP_LAND = 9f;     // shorter strip in landscape -> finer steps

    private WindowManager wm;
    private AudioManager am;
    private View strip;
    private WindowManager.LayoutParams lp;
    private int stepPx;
    private float density;
    private float anchorY;

    // Double tap on the strip = screenshot (replaces Power + Volume down).
    private static final long TAP_MAX_MS = 250;
    private static final long DOUBLE_TAP_GAP_MS = 400;
    private int touchSlop;
    private float downX, downY;
    private long downTime, lastTapUp;
    private boolean stepped;

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        strip = new View(this);
        strip.setBackgroundColor(0x00000000); // fully invisible
        strip.setHapticFeedbackEnabled(true);
        strip.setOnTouchListener(this::onStripTouch);
        // Keep the system back gesture from stealing drags that start on the strip.
        strip.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) ->
                v.setSystemGestureExclusionRects(
                        Collections.singletonList(new Rect(0, 0, r - l, b - t))));

        lp = new WindowManager.LayoutParams(
                Math.round(STRIP_WIDTH_DP * density),
                100,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        lp.setTitle("EdgeVolumeStrip");
        applyGeometry();
        wm.addView(strip, lp);
    }

    /**
     * Keeps the strip clear of MIUI's right-edge "back" gesture stub (GestureStubRight),
     * which the miui-gesture monitor uses to steal touches regardless of exclusion rects.
     * Stub on this device: portrait y=37%..97%, landscape y=20%..80%.
     * Portrait: just above the stub, next to the physical volume rocker.
     * Landscape: top-right corner, above the stub.
     */
    private void applyGeometry() {
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        int h = size.y;
        boolean portrait = size.y >= size.x;
        if (portrait) {
            // Bottom edge stays ~16px above the stub (y=892 of 2400).
            lp.y = Math.round(h * 0.20f);
            lp.height = Math.round(h * 0.365f) - lp.y;
            stepPx = Math.round(STEP_DP * density);
        } else {
            lp.y = 0;
            lp.height = Math.round(h * 0.19f);
            stepPx = Math.round(STEP_DP_LAND * density);
        }
    }

    private boolean onStripTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                anchorY = e.getRawY();
                downX = e.getRawX();
                downY = e.getRawY();
                downTime = e.getEventTime();
                stepped = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getRawY() - anchorY;
                while (dy <= -stepPx) {
                    adjust(v, AudioManager.ADJUST_RAISE);
                    anchorY -= stepPx;
                    dy += stepPx;
                }
                while (dy >= stepPx) {
                    adjust(v, AudioManager.ADJUST_LOWER);
                    anchorY += stepPx;
                    dy -= stepPx;
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean isTap = !stepped
                        && Math.abs(e.getRawX() - downX) < touchSlop
                        && Math.abs(e.getRawY() - downY) < touchSlop
                        && e.getEventTime() - downTime < TAP_MAX_MS;
                if (isTap && downTime - lastTapUp < DOUBLE_TAP_GAP_MS) {
                    lastTapUp = 0;
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                } else {
                    lastTapUp = isTap ? e.getEventTime() : 0;
                }
                return true;
            default:
                return true;
        }
    }

    private void adjust(View v, int direction) {
        int mode = am.getMode();
        int stream = (mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_IN_COMMUNICATION)
                ? AudioManager.STREAM_VOICE_CALL
                : AudioManager.STREAM_MUSIC;
        stepped = true;
        am.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI);
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (strip != null && strip.isAttachedToWindow()) {
            applyGeometry();
            wm.updateViewLayout(strip, lp);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (strip != null && strip.isAttachedToWindow()) {
            wm.removeView(strip);
        }
        super.onDestroy();
    }
}
