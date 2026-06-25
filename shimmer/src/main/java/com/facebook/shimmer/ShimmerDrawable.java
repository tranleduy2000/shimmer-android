/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.shimmer;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ShimmerDrawable extends Drawable {
    private final ValueAnimator.AnimatorUpdateListener mUpdateListener =
        animation -> invalidateSelf();

    private final Paint mShimmerPaint = new Paint();
    private final Rect mDrawRect = new Rect();
    private final Matrix mShaderMatrix = new Matrix();

    private @Nullable ValueAnimator mValueAnimator;
    private float mStaticAnimationProgress = -1f;

    private @Nullable Shimmer mShimmer;

    public ShimmerDrawable() {
        mShimmerPaint.setAntiAlias(true);
    }

    public void setShimmer(@Nullable Shimmer shimmer) {
        mShimmer = shimmer;
        if (mShimmer != null) {
            mShimmerPaint.setXfermode(
                new PorterDuffXfermode(
                    mShimmer.alphaShimmer ? PorterDuff.Mode.DST_IN : PorterDuff.Mode.SRC_IN));
        }
        updateShader();
        updateValueAnimator();
        invalidateSelf();
    }

    public @Nullable Shimmer getShimmer() {
        return mShimmer;
    }

    /**
     * Starts the shimmer animation.
     */
    public void startShimmer() {
        if (mValueAnimator != null && !isShimmerStarted() && getCallback() != null) {
            mValueAnimator.start();
        }
    }

    /**
     * Stops the shimmer animation.
     */
    public void stopShimmer() {
        if (mValueAnimator != null && isShimmerStarted()) {
            mValueAnimator.cancel();
        }
    }

    /**
     * Return whether the shimmer animation has been started.
     */
    public boolean isShimmerStarted() {
        return mValueAnimator != null && mValueAnimator.isStarted();
    }

    /**
     * Return whether the shimmer animation is running.
     */
    public boolean isShimmerRunning() {
        return mValueAnimator != null && mValueAnimator.isRunning();
    }

    /**
     * Registers a listener on the underlying shimmer animator. Use {@code onAnimationRepeat} to be
     * notified when each shimmer sweep finishes (the animation repeats infinitely by default).
     */
    public void addAnimatorListener(@NonNull Animator.AnimatorListener listener) {
        if (mValueAnimator != null) {
            mValueAnimator.addListener(listener);
        }
    }

    /**
     * Removes a listener previously added via {@link #addAnimatorListener(Animator.AnimatorListener)}.
     */
    public void removeAnimatorListener(@NonNull Animator.AnimatorListener listener) {
        if (mValueAnimator != null) {
            mValueAnimator.removeListener(listener);
        }
    }

    @Override
    public void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        mDrawRect.set(bounds);
        updateShader();
        maybeStartShimmer();
    }

    public void setStaticAnimationProgress(float value) {
        if (Float.compare(value, mStaticAnimationProgress) == 0
            || (value < 0f && mStaticAnimationProgress < 0f)) {
            return;
        }
        mStaticAnimationProgress = Math.min(value, 1f);
        invalidateSelf();
    }

    public void clearStaticAnimationProgress() {
        setStaticAnimationProgress(-1f);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mShimmer == null || mShimmerPaint.getShader() == null) {
            return;
        }

        final float tiltTan = (float) Math.tan(Math.toRadians(mShimmer.tilt));
        final float translateHeight = mDrawRect.height() + tiltTan * mDrawRect.width();
        final float translateWidth = mDrawRect.width() + tiltTan * mDrawRect.height();
        final float dx;
        final float dy;
        final float animatedValue;

        if (mStaticAnimationProgress < 0f) {
            animatedValue = mValueAnimator != null ? (float) mValueAnimator.getAnimatedValue() : 0f;
        } else {
            animatedValue = mStaticAnimationProgress;
        }

        switch (mShimmer.direction) {
            default:
            case Shimmer.Direction.LEFT_TO_RIGHT:
                dx = offset(-translateWidth, translateWidth, animatedValue);
                dy = 0;
                break;
            case Shimmer.Direction.RIGHT_TO_LEFT:
                dx = offset(translateWidth, -translateWidth, animatedValue);
                dy = 0f;
                break;
            case Shimmer.Direction.TOP_TO_BOTTOM:
                dx = 0f;
                dy = offset(-translateHeight, translateHeight, animatedValue);
                break;
            case Shimmer.Direction.BOTTOM_TO_TOP:
                dx = 0f;
                dy = offset(translateHeight, -translateHeight, animatedValue);
                break;
        }

        mShaderMatrix.reset();
        mShaderMatrix.setRotate(mShimmer.tilt, mDrawRect.width() / 2f, mDrawRect.height() / 2f);
        mShaderMatrix.preTranslate(dx, dy);
        mShimmerPaint.getShader().setLocalMatrix(mShaderMatrix);
        canvas.drawRect(mDrawRect, mShimmerPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        // No-op, modify the Shimmer object you pass in instead
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // No-op, modify the Shimmer object you pass in instead
    }

    @Override
    public int getOpacity() {
        return mShimmer != null && (mShimmer.clipToChildren || mShimmer.alphaShimmer)
            ? PixelFormat.TRANSLUCENT
            : PixelFormat.OPAQUE;
    }

    private float offset(float start, float end, float percent) {
        return start + (end - start) * percent;
    }

    private void updateValueAnimator() {
        if (mShimmer == null) {
            return;
        }

        final boolean started;
        if (mValueAnimator != null) {
            started = mValueAnimator.isStarted();
            mValueAnimator.cancel();
            mValueAnimator.removeAllUpdateListeners();
        } else {
            started = false;
        }

        mValueAnimator =
            ValueAnimator.ofFloat(0f, 1f + (float) (mShimmer.repeatDelay / mShimmer.animationDuration));
        mValueAnimator.setInterpolator(new LinearInterpolator());
        mValueAnimator.setRepeatMode(mShimmer.repeatMode);
        mValueAnimator.setStartDelay(mShimmer.startDelay);
        mValueAnimator.setRepeatCount(mShimmer.repeatCount);
        mValueAnimator.setDuration(mShimmer.animationDuration + mShimmer.repeatDelay);
        mValueAnimator.addUpdateListener(mUpdateListener);
        if (started) {
            mValueAnimator.start();
        }
    }

    void maybeStartShimmer() {
        if (mValueAnimator != null
            && !mValueAnimator.isStarted()
            && mShimmer != null
            && mShimmer.autoStart
            && getCallback() != null) {
            mValueAnimator.start();
        }
    }

    private void updateShader() {
        final Rect bounds = getBounds();
        final int boundsWidth = bounds.width();
        final int boundsHeight = bounds.height();
        if (boundsWidth == 0 || boundsHeight == 0 || mShimmer == null) {
            return;
        }
        final int width = mShimmer.width(boundsWidth);
        final int height = mShimmer.height(boundsHeight);

        final Shader shader;
        switch (mShimmer.shape) {
            default:
            case Shimmer.Shape.LINEAR:
                boolean vertical =
                    mShimmer.direction == Shimmer.Direction.TOP_TO_BOTTOM
                        || mShimmer.direction == Shimmer.Direction.BOTTOM_TO_TOP;
                int endX = vertical ? 0 : width;
                int endY = vertical ? height : 0;
                shader =
                    new LinearGradient(
                        0, 0, endX, endY, mShimmer.colors, mShimmer.positions, Shader.TileMode.CLAMP);
                break;
            case Shimmer.Shape.RADIAL:
                shader =
                    new RadialGradient(
                        width / 2f,
                        height / 2f,
                        (float) (Math.max(width, height) / Math.sqrt(2)),
                        mShimmer.colors,
                        mShimmer.positions,
                        Shader.TileMode.CLAMP);
                break;
        }

        mShimmerPaint.setShader(shader);
    }

    public void setBaseColor(int baseColor, float alpha) {
        baseColor = baseColor | 0xFF000000; // Ensure baseColor is opaque
        baseColor = setAlphaComponent(baseColor, (int) (alpha * 255f));

        if (mShimmer != null && mShimmer.baseColor != baseColor) {
            mShimmer.baseColor = baseColor;
            updateShader();
            invalidateSelf();
        }
    }

    public void setHighlightColor(int highlightColor, float alpha) {
        highlightColor = highlightColor | 0xFF000000; // Ensure highlightColor is opaque
        highlightColor = setAlphaComponent(highlightColor, (int) (alpha * 255f));

        if (mShimmer != null && mShimmer.highlightColor != highlightColor) {
            mShimmer.highlightColor = highlightColor;
            updateShader();
            invalidateSelf();
        }
    }

    /**
     * Sets the dropoff value for the shimmer effect, controlling the fade-out intensity of the shimmer.
     * If the dropoff value differs from the current one and a shimmer is configured, the shader will be updated
     * and the drawable will be invalidated to reflect the change.
     *
     * @param dropoff The new dropoff value for the shimmer effect. This value determines how quickly
     *                the shimmer fades out from its highlight color to its base color.
     */
    public void setDropoff(float dropoff) {
        if (mShimmer != null && mShimmer.dropoff != dropoff) {
            mShimmer.dropoff = dropoff;
            updateShader();
            invalidateSelf();
        }
    }

    public void setIntensity(float intensity) {
        if (mShimmer != null && mShimmer.intensity != intensity) {
            mShimmer.intensity = intensity;
            updateShader();
            invalidateSelf();
        }
    }

    /**
     * Set the alpha component of {@code color} to be {@code alpha}.
     */
    @ColorInt
    public static int setAlphaComponent(@ColorInt int color,
                                        @IntRange(from = 0x0, to = 0xFF) int alpha) {
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("alpha must be between 0 and 255.");
        }
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
