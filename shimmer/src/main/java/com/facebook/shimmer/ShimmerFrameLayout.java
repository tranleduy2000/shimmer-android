/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.shimmer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shimmer is an Android library that provides an easy way to add a shimmer effect to any {@link
 * android.view.View}. It is useful as an unobtrusive loading indicator, and was originally
 * developed for Facebook Home.
 *
 * <p>Find more examples and usage instructions over at: facebook.github.io/shimmer-android
 */
public class ShimmerFrameLayout extends FrameLayout {
    private final Paint mContentPaint = new Paint();
    private final ShimmerDrawable mShimmerDrawable = new ShimmerDrawable();

    private boolean mShowShimmer = true;
    private boolean mStoppedShimmerBecauseVisibility = false;
    private @Nullable Animator.AnimatorListener mPendingHideListener;

    public ShimmerFrameLayout(Context context) {
        super(context);
        init(context, null);
    }

    public ShimmerFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ShimmerFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setWillNotDraw(false);
        mShimmerDrawable.setCallback(this);

        if (attrs == null) {
            setShimmer(new Shimmer.AlphaHighlightBuilder().build());
            return;
        }

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ShimmerFrameLayout, 0, 0);
        try {
            Shimmer.Builder<?> shimmerBuilder =
                a.hasValue(R.styleable.ShimmerFrameLayout_shimmer_colored)
                    && a.getBoolean(R.styleable.ShimmerFrameLayout_shimmer_colored, false)
                    ? new Shimmer.ColorHighlightBuilder()
                    : new Shimmer.AlphaHighlightBuilder();
            setShimmer(shimmerBuilder.consumeAttributes(a).build());
        } finally {
            a.recycle();
        }
    }

    public ShimmerFrameLayout setShimmer(@Nullable Shimmer shimmer) {
        mShimmerDrawable.setShimmer(shimmer);
        if (shimmer != null && shimmer.clipToChildren) {
            setLayerType(LAYER_TYPE_HARDWARE, mContentPaint);
        } else {
            setLayerType(LAYER_TYPE_NONE, null);
        }

        return this;
    }

    /**
     * @noinspection unused
     */
    public @Nullable Shimmer getShimmer() {
        return mShimmerDrawable.getShimmer();
    }

    /**
     * Starts the shimmer animation.
     */
    public void startShimmer() {
        if (isAttachedToWindow()) {
            mShimmerDrawable.startShimmer();
        }
    }

    /**
     * Stops the shimmer animation.
     */
    public void stopShimmer() {
        mStoppedShimmerBecauseVisibility = false;
        mShimmerDrawable.stopShimmer();
    }

    /**
     * Return whether the shimmer animation has been started.
     */
    public boolean isShimmerStarted() {
        return mShimmerDrawable.isShimmerStarted();
    }

    /**
     * Registers a listener on the underlying shimmer animator. Use {@code onAnimationRepeat} to be
     * notified when each shimmer sweep finishes, so a caller can hide the view without cutting the
     * animation off mid-sweep.
     */
    public void addAnimatorListener(@NonNull Animator.AnimatorListener listener) {
        mShimmerDrawable.addAnimatorListener(listener);
    }

    /**
     * Removes a listener previously added via {@link #addAnimatorListener(Animator.AnimatorListener)}.
     */
    public void removeAnimatorListener(@NonNull Animator.AnimatorListener listener) {
        mShimmerDrawable.removeAnimatorListener(listener);
    }

    /**
     * Hides this view ({@link View#GONE}) once the current shimmer sweep finishes, instead of
     * cutting the animation off mid-sweep. Because the shimmer animation repeats infinitely, this
     * waits for the next repeat (the end of a sweep), then stops the shimmer and hides the view. If
     * the shimmer is not currently animating, it hides immediately. Calling
     * {@link #cancelPendingShimmerHide()} (e.g. when showing the shimmer again) cancels a pending hide.
     */
    public void hideShimmerWhenSweepEnds() {
        hideShimmerWhenSweepEnds(null);
    }

    /**
     * Same as {@link #hideShimmerWhenSweepEnds()}, but runs {@code onHidden} right after the view is
     * actually hidden — immediately when the shimmer is not animating, or once the current sweep
     * ends when it is. Lets a caller sync a sibling animation (e.g. fading a dim overlay out) with
     * the moment the shimmer truly disappears, rather than the moment the hide is requested.
     *
     * @param onHidden optional callback run on the main thread after the view goes {@link View#GONE}.
     */
    public void hideShimmerWhenSweepEnds(@Nullable Runnable onHidden) {
        if (mPendingHideListener != null) {
            return; // a hide is already pending
        }
        if (!isShimmerStarted()) {
            setVisibility(GONE);
            stopShimmer();
            if (onHidden != null) {
                onHidden.run();
            }
            return;
        }
        Animator.AnimatorListener listener =
            new AnimatorListenerAdapter() {
                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {
                    final Animator.AnimatorListener self = this;
                    // Defer out of the animator callback before touching the animation / its listeners.
                    post(
                        () -> {
                            if (mPendingHideListener != self) {
                                return; // superseded by a re-show / cancel
                            }
                            removeAnimatorListener(self);
                            mPendingHideListener = null;
                            setVisibility(GONE);
                            stopShimmer();
                            if (onHidden != null) {
                                onHidden.run();
                            }
                        });
                }
            };
        mPendingHideListener = listener;
        addAnimatorListener(listener);
    }

    /**
     * Cancels a hide scheduled by {@link #hideShimmerWhenSweepEnds()} that has not run yet.
     */
    public void cancelPendingShimmerHide() {
        if (mPendingHideListener != null) {
            removeAnimatorListener(mPendingHideListener);
            mPendingHideListener = null;
        }
    }

    /**
     * Sets the ShimmerDrawable to be visible.
     *
     * @param startShimmer Whether to start the shimmer again.
     * @noinspection unused
     */
    public void showShimmer(boolean startShimmer) {
        mShowShimmer = true;
        if (startShimmer) {
            startShimmer();
        }
        invalidate();
    }

    /**
     * Sets the ShimmerDrawable to be invisible, stopping it in the process.
     *
     * @noinspection unused
     */
    public void hideShimmer() {
        stopShimmer();
        mShowShimmer = false;
        invalidate();
    }

    /**
     * Return whether the shimmer drawable is visible.
     *
     * @noinspection unused
     */
    public boolean isShimmerVisible() {
        return mShowShimmer;
    }

    /**
     * @noinspection unused
     */
    public boolean isShimmerRunning() {
        return mShimmerDrawable.isShimmerRunning();
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final int width = getWidth();
        final int height = getHeight();
        mShimmerDrawable.setBounds(0, 0, width, height);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // View's constructor directly invokes this method, in which case no fields on
        // this clazz have been fully initialized yet.
        if (mShimmerDrawable == null) {
            return;
        }
        if (visibility != View.VISIBLE) {
            // GONE or INVISIBLE
            if (isShimmerStarted()) {
                stopShimmer();
                mStoppedShimmerBecauseVisibility = true;
            }
        } else if (mStoppedShimmerBecauseVisibility) {
            mShimmerDrawable.maybeStartShimmer();
            mStoppedShimmerBecauseVisibility = false;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mShimmerDrawable.maybeStartShimmer();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShimmer();
    }

    @Override
    public void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mShowShimmer) {
            mShimmerDrawable.draw(canvas);
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || who == mShimmerDrawable;
    }

    @NonNull
    public ShimmerDrawable getShimmerDrawable() {
        return mShimmerDrawable;
    }

    /**
     * @noinspection unused
     */
    public void setStaticAnimationProgress(float value) {
        mShimmerDrawable.setStaticAnimationProgress(value);
    }

    /**
     * @noinspection unused
     */
    public void clearStaticAnimationProgress() {
        mShimmerDrawable.clearStaticAnimationProgress();
    }
}
