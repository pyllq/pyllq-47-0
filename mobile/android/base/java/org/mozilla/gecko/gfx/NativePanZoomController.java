/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.gfx;

import org.mozilla.gecko.GeckoAppShell;
import org.mozilla.gecko.GeckoEvent;
import org.mozilla.gecko.GeckoThread;
import org.mozilla.gecko.PrefsHelper;
import org.mozilla.gecko.annotation.WrapForJNI;
import org.mozilla.gecko.gfx.DynamicToolbarAnimator.PinReason;
import org.mozilla.gecko.mozglue.JNIObject;
import org.mozilla.gecko.util.ThreadUtils;

import org.json.JSONObject;

import org.mozilla.gecko.ZoomConstraints;

import android.graphics.RectF;
import android.graphics.PointF;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.InputDevice;


class NativePanZoomController extends JNIObject implements PanZoomController {
    private static final String LOGTAG = "NativePanZoomController";

    private final PanZoomTarget mTarget;
    private final LayerView mView;
    private boolean mDestroyed;
    private Overscroll mOverscroll;
    boolean mNegateWheelScroll;
    private float mPointerScrollFactor;
    private final PrefsHelper.PrefHandler mPrefsObserver;
    private long mLastDownTime;
    private static final float MAX_SCROLL = 0.075f * GeckoAppShell.getDpi();

    // The maximum amount we allow you to zoom into a page
    private static final float MAX_ZOOM = 8.0f;

    private static Long mLastMultitouchTimestamp = 0L;

    // see widget/android/nsWindow.cpp:708
    @WrapForJNI
    private native boolean handleMotionEvent(
            int action, int actionIndex, long time, int metaState,
            int pointerId[], float x[], float y[], float orientation[], float pressure[],
            float toolMajor[], float toolMinor[]);

    @WrapForJNI
    private native boolean handleScrollEvent(
            long time, int metaState,
            float x, float y,
            float hScroll, float vScroll);

    @WrapForJNI
    private native boolean handleMouseEvent(
            int action, long time, int metaState,
            float x, float y, int buttons);

    @WrapForJNI
    private native void handleMotionEventVelocity(long time, float ySpeed);

    private boolean handleMotionEvent(MotionEvent event) {
        if (mDestroyed) {
            return false;
        }

        final int action = event.getActionMasked();
        final int count = event.getPointerCount();

        if (action == MotionEvent.ACTION_DOWN) {
            mLastDownTime = event.getDownTime();
        } else if (mLastDownTime != event.getDownTime()) {
            return false;
        }

        final int[] pointerId = new int[count];
        final float[] x = new float[count];
        final float[] y = new float[count];
        final float[] orientation = new float[count];
        final float[] pressure = new float[count];
        final float[] toolMajor = new float[count];
        final float[] toolMinor = new float[count];

        final MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();

        for (int i = 0; i < count; i++) {
            pointerId[i] = event.getPointerId(i);
            event.getPointerCoords(i, coords);

            x[i] = coords.x;
            y[i] = coords.y;

            orientation[i] = coords.orientation;
            pressure[i] = coords.pressure;

            // If we are converting to CSS pixels, we should adjust the radii as well.
            toolMajor[i] = coords.toolMajor;
            toolMinor[i] = coords.toolMinor;
        }

        return handleMotionEvent(action, event.getActionIndex(), event.getEventTime(),
                event.getMetaState(), pointerId, x, y, orientation, pressure,
                toolMajor, toolMinor);
    }

    private boolean handleScrollEvent(MotionEvent event) {
        if (mDestroyed) {
            return false;
        }

        final int count = event.getPointerCount();

        if (count <= 0) {
            return false;
        }

        final MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        event.getPointerCoords(0, coords);
        final float x = coords.x;
        final float y = coords.y;

        final float flipFactor = mNegateWheelScroll ? -1.0f : 1.0f;
        final float hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL) * flipFactor * mPointerScrollFactor;
        final float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL) * flipFactor * mPointerScrollFactor;

        return handleScrollEvent(event.getEventTime(), event.getMetaState(), x, y, hScroll, vScroll);
    }

    private boolean handleMouseEvent(MotionEvent event) {
        if (mDestroyed) {
            return false;
        }

        final int count = event.getPointerCount();

        if (count <= 0) {
            return false;
        }

        final MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        event.getPointerCoords(0, coords);
        final float x = coords.x;
        final float y = coords.y;

        return handleMouseEvent(event.getActionMasked(), event.getEventTime(), event.getMetaState(), x, y, event.getButtonState());
    }


    NativePanZoomController(PanZoomTarget target, View view) {
        mTarget = target;
        mView = (LayerView) view;

        String[] prefs = { "ui.scrolling.negate_wheel_scroll" };
        mPrefsObserver = new PrefsHelper.PrefHandlerBase() {
            @Override public void prefValue(String pref, boolean value) {
                if (pref.equals("ui.scrolling.negate_wheel_scroll")) {
                    mNegateWheelScroll = value;
                }
            }
        };
        PrefsHelper.addObserver(prefs, mPrefsObserver);

        TypedValue outValue = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(android.R.attr.listPreferredItemHeight, outValue, true)) {
            mPointerScrollFactor = outValue.getDimension(view.getContext().getResources().getDisplayMetrics());
        } else {
            mPointerScrollFactor = MAX_SCROLL;
        }
    }

    private ImmutableViewportMetrics getMetrics() {
        return mTarget.getViewportMetrics();
    }

    /* Returns the nearest viewport metrics with no overscroll visible. */
    private ImmutableViewportMetrics getValidViewportMetrics() {
        return getValidViewportMetrics(getMetrics());
    }

    private ImmutableViewportMetrics getValidViewportMetrics(ImmutableViewportMetrics viewportMetrics) {
        /* First, we adjust the zoom factor so that we can make no overscrolled area visible. */
        float zoomFactor = viewportMetrics.zoomFactor;
        RectF pageRect = viewportMetrics.getPageRect();
        RectF viewport = viewportMetrics.getViewport();

        float focusX = viewport.width() / 2.0f;
        float focusY = viewport.height() / 2.0f;

        float minZoomFactor = 0.0f;
        float maxZoomFactor = MAX_ZOOM;

        ZoomConstraints constraints = mTarget.getZoomConstraints();

        if (constraints.getMinZoom() > 0 || !constraints.getAllowZoom()) {
            minZoomFactor = constraints.getMinZoom();
        }
        if (constraints.getMaxZoom() > 0 || !constraints.getAllowZoom()) {
            maxZoomFactor = constraints.getMaxZoom();
        }

        // Ensure minZoomFactor keeps the page at least as big as the viewport.
        if (pageRect.width() > 0) {
            float pageWidth = pageRect.width();
            float scaleFactor = viewport.width() / pageWidth;
            minZoomFactor = Math.max(minZoomFactor, zoomFactor * scaleFactor);
            if (viewport.width() > pageWidth)
                focusX = 0.0f;
        }
        if (pageRect.height() > 0) {
            float pageHeight = pageRect.height();
            float scaleFactor = viewport.height() / pageHeight;
            minZoomFactor = Math.max(minZoomFactor, zoomFactor * scaleFactor);
            if (viewport.height() > pageHeight)
                focusY = 0.0f;
        }

        maxZoomFactor = Math.max(maxZoomFactor, minZoomFactor);

        if (zoomFactor < minZoomFactor) {
            // if one (or both) of the page dimensions is smaller than the viewport,
            // zoom using the top/left as the focus on that axis. this prevents the
            // scenario where, if both dimensions are smaller than the viewport, but
            // by different scale factors, we end up scrolled to the end on one axis
            // after applying the scale
            PointF center = new PointF(focusX, focusY);
            viewportMetrics = viewportMetrics.scaleTo(minZoomFactor, center);
        } else if (zoomFactor > maxZoomFactor) {
            PointF center = new PointF(viewport.width() / 2.0f, viewport.height() / 2.0f);
            viewportMetrics = viewportMetrics.scaleTo(maxZoomFactor, center);
        }

        /* Now we pan to the right origin. */
        viewportMetrics = viewportMetrics.clamp();

        return viewportMetrics;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
// NOTE: This commented out block of code allows Fennec to generate
//       mouse event instead of converting them to touch events.
//       This gives Fennec similar behaviour to desktop when using
//       a mouse.
//
//        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
//            return handleMouseEvent(event);
//        } else {
//            return handleMotionEvent(event);
//        }
        boolean result = handleMotionEvent(event);

        int count = event.getPointerCount();
        if (count==2 && 
            (System.currentTimeMillis() - mLastMultitouchTimestamp > 1000)
                ) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                //onTouchStart(event);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                //onTouchStart(event);
                break;
            case MotionEvent.ACTION_MOVE:
                //onTouchMove(event);
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                //onTouchEnd(event);
                synchronized (mTarget.getLock()) {
                    Log.e(LOGTAG, "trigger mTarget.setViewportMetrics");
                    mTarget.setAnimationTarget(getValidViewportMetrics());
                }            
                break;
            }
        }
        if (count > 2)
            mLastMultitouchTimestamp = System.currentTimeMillis();

        return result;
    }

    @Override
    public boolean onMotionEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_SCROLL) {
            if (event.getDownTime() >= mLastDownTime) {
                mLastDownTime = event.getDownTime();
            } else if ((InputDevice.getDevice(event.getDeviceId()).getSources() & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
                return false;
            }
            return handleScrollEvent(event);
        } else if ((action == MotionEvent.ACTION_HOVER_MOVE) ||
                   (action == MotionEvent.ACTION_HOVER_ENTER) ||
                   (action == MotionEvent.ACTION_HOVER_EXIT)) {
            return handleMouseEvent(event);
        } else {
            return false;
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        // FIXME implement this
        return false;
    }

    @Override
    public void onMotionEventVelocity(final long aEventTime, final float aSpeedY) {
        handleMotionEventVelocity(aEventTime, aSpeedY);
    }

    @Override
    public PointF getVelocityVector() {
        // FIXME implement this
        return new PointF(0, 0);
    }

    @Override
    public void pageRectUpdated() {
        // no-op in APZC, I think
    }

    @Override
    public void abortPanning() {
        // no-op in APZC, I think
    }

    @Override
    public void notifyDefaultActionPrevented(boolean prevented) {
        // no-op: This could get called if accessibility is enabled and the events
        // are sent to Gecko directly without going through APZ. In this case
        // we just want to ignore this callback.
    }

    @WrapForJNI(stubName = "AbortAnimation")
    private native void nativeAbortAnimation();

    @Override // PanZoomController
    public void abortAnimation()
    {
        if (!mDestroyed) {
            nativeAbortAnimation();
        }
    }

    @Override // PanZoomController
    public boolean getRedrawHint()
    {
        // FIXME implement this
        return true;
    }

    @Override @WrapForJNI(allowMultithread = true) // PanZoomController
    public void destroy() {
        if (mDestroyed || !mTarget.isGeckoReady()) {
            return;
        }
        mDestroyed = true;
        disposeNative();
    }

    @Override @WrapForJNI // JNIObject
    protected native void disposeNative();

    @Override
    public void setOverScrollMode(int overscrollMode) {
        // FIXME implement this
    }

    @Override
    public int getOverScrollMode() {
        // FIXME implement this
        return 0;
    }

    @WrapForJNI(allowMultithread = true, stubName = "RequestContentRepaintWrapper")
    private void requestContentRepaint(float x, float y, float width, float height, float resolution) {
        mTarget.forceRedraw(new DisplayPortMetrics(x, y, x + width, y + height, resolution));
    }

    @Override
    public void setOverscrollHandler(final Overscroll handler) {
        mOverscroll = handler;
    }

    @WrapForJNI(stubName = "SetIsLongpressEnabled")
    private native void nativeSetIsLongpressEnabled(boolean isLongpressEnabled);

    @Override // PanZoomController
    public void setIsLongpressEnabled(boolean isLongpressEnabled) {
        if (!mDestroyed) {
            nativeSetIsLongpressEnabled(isLongpressEnabled);
        }
    }

    @WrapForJNI(stubName = "AdjustScrollForSurfaceShift")
    private native void adjustScrollForSurfaceShift(float aX, float aY);

    @Override // PanZoomController
    public ImmutableViewportMetrics adjustScrollForSurfaceShift(ImmutableViewportMetrics aMetrics, PointF aShift) {
        adjustScrollForSurfaceShift(aShift.x, aShift.y);
        return aMetrics.offsetViewportByAndClamp(aShift.x, aShift.y);
    }

    @WrapForJNI(allowMultithread = true)
    private void updateOverscrollVelocity(final float x, final float y) {
        if (mOverscroll != null) {
            if (ThreadUtils.isOnUiThread() == true) {
                mOverscroll.setVelocity(x * 1000.0f, Overscroll.Axis.X);
                mOverscroll.setVelocity(y * 1000.0f, Overscroll.Axis.Y);
            } else {
                ThreadUtils.postToUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Multiply the velocity by 1000 to match what was done in JPZ.
                        mOverscroll.setVelocity(x * 1000.0f, Overscroll.Axis.X);
                        mOverscroll.setVelocity(y * 1000.0f, Overscroll.Axis.Y);
                    }
                });
            }
        }
    }

    @WrapForJNI(allowMultithread = true)
    private void updateOverscrollOffset(final float x, final float y) {
        if (mOverscroll != null) {
            if (ThreadUtils.isOnUiThread() == true) {
                mOverscroll.setDistance(x, Overscroll.Axis.X);
                mOverscroll.setDistance(y, Overscroll.Axis.Y);
            } else {
                ThreadUtils.postToUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mOverscroll.setDistance(x, Overscroll.Axis.X);
                        mOverscroll.setDistance(y, Overscroll.Axis.Y);
                    }
                });
            }
        }
    }

    @WrapForJNI
    private void setScrollingRootContent(final boolean isRootContent) {
        mTarget.setScrollingRootContent(isRootContent);
    }

    /**
     * Active SelectionCaretDrag requires DynamicToolbarAnimator to be pinned
     * to avoid unwanted scroll interactions.
     */
    @WrapForJNI
    private void onSelectionDragState(boolean state) {
        mView.getDynamicToolbarAnimator().setPinned(state, PinReason.CARET_DRAG);
    }
}
