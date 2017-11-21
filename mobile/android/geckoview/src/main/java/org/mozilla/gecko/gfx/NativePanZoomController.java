/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.gfx;

import org.mozilla.gecko.GeckoAppShell;
import org.mozilla.gecko.GeckoThread;
import org.mozilla.gecko.PrefsHelper;
import org.mozilla.gecko.annotation.WrapForJNI;
import org.mozilla.gecko.gfx.DynamicToolbarAnimator.PinReason;
import org.mozilla.gecko.mozglue.JNIObject;
import org.mozilla.gecko.util.ThreadUtils;

import org.json.JSONObject;

//import org.mozilla.gecko.ZoomConstraints;

import android.graphics.RectF;
import android.graphics.PointF;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.InputDevice;

class NativePanZoomController extends JNIObject implements PanZoomController {
    private static final String LOGTAG = "GeckoNativePanZoomController";

    private final PanZoomTarget mTarget;
    private final LayerView mView;
    private boolean mDestroyed;
    private Overscroll mOverscroll;
    boolean mNegateWheelScroll;
    private float mPointerScrollFactor;
    private PrefsHelper.PrefHandler mPrefsObserver;
    private long mLastDownTime;
    private static final float MAX_SCROLL = 0.075f * GeckoAppShell.getDpi();

    // The maximum amount we allow you to zoom into a page
    private static final float MAX_ZOOM = 8.0f;

    private int mNumberTouchCount = 0;

    @WrapForJNI(calledFrom = "ui")
    private native boolean handleMotionEvent(
            int action, int actionIndex, long time, int metaState,
            int pointerId[], float x[], float y[], float orientation[], float pressure[],
            float toolMajor[], float toolMinor[]);

    @WrapForJNI(calledFrom = "ui")
    private native boolean handleScrollEvent(
            long time, int metaState,
            float x, float y,
            float hScroll, float vScroll);

    @WrapForJNI(calledFrom = "ui")
    private native boolean handleMouseEvent(
            int action, long time, int metaState,
            float x, float y, int buttons);

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

//            Log.e(LOGTAG, "coords:" + String.valueOf(i) +"/"+ String.valueOf(count) +":"+ 
//                String.valueOf(x[i]) +"/"+ String.valueOf(y[i]) +
//                (i==event.getActionIndex()?"*":"") );

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
        // Scroll events are not adjusted by the AndroidDyanmicToolbarAnimator so adjust the offset here.
        final float y = coords.y - mView.getCurrentToolbarHeight();

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
        // Mouse events are not adjusted by the AndroidDyanmicToolbarAnimator so adjust the offset
        // here.
        final float y = coords.y - mView.getCurrentToolbarHeight();

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

    /* Returns the nearest viewport metrics with no overscroll visible. */
    private ImmutableViewportMetrics getValidViewportMetrics() {
        return getValidViewportMetrics(mView.getViewportMetrics());
    }

    private ImmutableViewportMetrics getValidViewportMetrics(ImmutableViewportMetrics viewportMetrics) {
        /* First, we adjust the zoom factor so that we can make no overscrolled area visible. */
        float zoomFactor = viewportMetrics.zoomFactor;
//        RectF pageRect = viewportMetrics.getPageRect();
        RectF viewport = viewportMetrics.getViewport();

        float focusX = viewport.width() / 2.0f;
        float focusY = viewport.height() / 2.0f;

        float minZoomFactor = 0.0f;
        float maxZoomFactor = MAX_ZOOM;

        // Ensure minZoomFactor keeps the page at least as big as the viewport.
//x        if (pageRect.width() > 0) {
//x            float pageWidth = pageRect.width();
//x            float scaleFactor = viewport.width() / pageWidth;
//x            minZoomFactor = Math.max(minZoomFactor, zoomFactor * scaleFactor);
//x            if (viewport.width() > pageWidth)
//x                focusX = 0.0f;
//x        }
//x        if (pageRect.height() > 0) {
//x            float pageHeight = pageRect.height();
//x            float scaleFactor = viewport.height() / pageHeight;
//x            minZoomFactor = Math.max(minZoomFactor, zoomFactor * scaleFactor);
//x            if (viewport.height() > pageHeight)
//x                focusY = 0.0f;
//x        }

        maxZoomFactor = Math.max(maxZoomFactor, minZoomFactor);

        if (zoomFactor < minZoomFactor) {
            // if one (or both) of the page dimensions is smaller than the viewport,
            // zoom using the top/left as the focus on that axis. this prevents the
            // scenario where, if both dimensions are smaller than the viewport, but
            // by different scale factors, we end up scrolled to the end on one axis
            // after applying the scale
            PointF center = new PointF(focusX, focusY);
//x            viewportMetrics = viewportMetrics.scaleTo(minZoomFactor, center);
        } else if (zoomFactor > maxZoomFactor) {
            PointF center = new PointF(viewport.width() / 2.0f, viewport.height() / 2.0f);
//x            viewportMetrics = viewportMetrics.scaleTo(maxZoomFactor, center);
        }

        /* Now we pan to the right origin. */
//x        viewportMetrics = viewportMetrics.clamp();

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

//        Log.e(LOGTAG, "onTouchEvent:" + 
//            String.valueOf(event.getPointerCount()) +
//            " vs " + String.valueOf(mNumberTouchCount) +
//            ":" + String.valueOf(event.getAction() & MotionEvent.ACTION_MASK));

        int count = event.getPointerCount();
        if (count<2) mNumberTouchCount = 0;

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            //onTouchStart(event);
            break;
        case MotionEvent.ACTION_POINTER_DOWN:
            //onTouchStart(event);
            break;
        case MotionEvent.ACTION_MOVE:
            //onTouchMove(event);
            if (count >= 2 && count > mNumberTouchCount) {
                mNumberTouchCount = count;
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_POINTER_UP:
            //onTouchEnd(event);
            if (count==mNumberTouchCount) {
                synchronized (mTarget.getLock()) {
                    mTarget.setAnimationTarget(null,event);
                }
            }
            break;
        }

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

    @Override @WrapForJNI(calledFrom = "ui") // PanZoomController
    public void destroy() {
        if (mPrefsObserver != null) {
            PrefsHelper.removeObserver(mPrefsObserver);
            mPrefsObserver = null;
        }
//x mTarget.isGeckoReady()
        if (mDestroyed || !mView.isGeckoReady()) {
            return;
        }
        mDestroyed = true;
        disposeNative();
    }

    @WrapForJNI(calledFrom = "ui", dispatchTo = "gecko_priority") @Override // JNIObject
    protected native void disposeNative();

    @Override
    public void setOverscrollHandler(final Overscroll handler) {
        mOverscroll = handler;
    }

    @WrapForJNI(stubName = "SetIsLongpressEnabled") // Called from test thread.
    private native void nativeSetIsLongpressEnabled(boolean isLongpressEnabled);

    @Override // PanZoomController
    public void setIsLongpressEnabled(boolean isLongpressEnabled) {
        if (!mDestroyed) {
            nativeSetIsLongpressEnabled(isLongpressEnabled);
        }
    }

    @WrapForJNI
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

    @WrapForJNI
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

    /**
     * Active SelectionCaretDrag requires DynamicToolbarAnimator to be pinned
     * to avoid unwanted scroll interactions.
     */
    @WrapForJNI(calledFrom = "gecko")
    private void onSelectionDragState(boolean state) {
        mView.getDynamicToolbarAnimator().setPinned(state, PinReason.CARET_DRAG);
    }
}
