package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * The image of one piece on the canvas. A piece is normally fitted into the middle of its frame.
 * Outerwear is "hung" instead: fitted against a line near the top of the frame — the shoulder line of
 * the top underneath — and centered left to right, so a jacket and a long coat both start at the
 * shoulders. A piece worn under the top is drawn "behind" it: in two halves, so that its collar shows
 * above the top's neckline and its hem below the top's hem, however long the two pieces are.
 * The view can also tell where its own image is, which is how those places are found.
 */
public class PieceImageView extends AppCompatImageView {

    /** Behind a top: how much of the top's height the collar shows above it and the hem below it… */
    private static final float SHOWS_ABOVE = 0.04f;
    private static final float SHOWS_BELOW = 0.035f;
    /** …and how wide a strip of the piece is drawn, in shares of the top's width: the collar and the body, not the sleeves. */
    private static final float COLLAR_STRIP = 0.45f;
    private static final float HEM_STRIP = 0.7f;

    private boolean hung;
    private float hangFrom;
    private boolean behind;
    @Nullable
    private RectF behindTop;
    private boolean whole;
    @Nullable
    private Runnable onImageChanged;

    public PieceImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** Draws the image from this many pixels below the top of the frame, as large as still fits. */
    public void hangFrom(float y) {
        hung = true;
        hangFrom = y;
        setScaleType(ScaleType.MATRIX);
        hang();
    }

    /**
     * Draws the piece behind a top whose image covers this rectangle (in this view's coordinates), as
     * wide as that top. Null while the top isn't known yet: then nothing is drawn.
     */
    public void drawBehind(@Nullable RectF top) {
        behind = true;
        behindTop = top;
        invalidate();
    }

    /**
     * While a piece that goes behind a top is only peeking in from the side (it is the previous or next
     * choice, with no top in front of it), it is drawn whole, so it can be recognized.
     */
    public void setWhole(boolean whole) {
        if (this.whole == whole) return;
        this.whole = whole;
        invalidate();
    }

    /**
     * Called whenever the image may have moved: a new image arrived (pieces are loaded from the network
     * after the page appears) or the view got its size.
     */
    public void setOnImageChanged(@Nullable Runnable onImageChanged) {
        this.onImageChanged = onImageChanged;
    }

    /**
     * For a piece fitted into the middle: the rectangle its image covers when the view is drawn at this
     * scale (the size from "Edit photo"). Null while there is no image or no size yet.
     */
    @Nullable
    public RectF imageBounds(float scale) {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0
                || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return null;
        }
        float fit = scale * Math.min((float) getWidth() / drawable.getIntrinsicWidth(),
                (float) getHeight() / drawable.getIntrinsicHeight());
        float halfWidth = drawable.getIntrinsicWidth() * fit / 2f;
        float halfHeight = drawable.getIntrinsicHeight() * fit / 2f;
        return new RectF(getWidth() / 2f - halfWidth, getHeight() / 2f - halfHeight,
                getWidth() / 2f + halfWidth, getHeight() / 2f + halfHeight);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        hang();
        if (onImageChanged != null) onImageChanged.run();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        hang();
        if (changed && onImageChanged != null) onImageChanged.run();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (behind) drawBehindTop(canvas);
        else super.onDraw(canvas);
    }

    private void hang() {
        Drawable drawable = getDrawable();
        if (!hung || drawable == null) return;
        float frameWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float frameHeight = getHeight() - getPaddingTop() - getPaddingBottom() - hangFrom;
        float width = drawable.getIntrinsicWidth();
        float height = drawable.getIntrinsicHeight();
        if (frameWidth <= 0 || frameHeight <= 0 || width <= 0 || height <= 0) return;
        float fit = Math.min(frameWidth / width, frameHeight / height);
        Matrix matrix = new Matrix();
        matrix.setScale(fit, fit);
        matrix.postTranslate((frameWidth - width * fit) / 2f, hangFrom);
        setImageMatrix(matrix);
    }

    /**
     * The piece is as wide as the top and centered on it. Its upper half is placed by the collar (a
     * little above the top), its lower half by the hem (a little below the top); the two meet behind
     * the middle of the top, where the seam can't be seen. Only a strip down the middle is drawn, so the
     * sleeves of a T-shirt don't stick out from behind a sweater's shoulders.
     */
    private void drawBehindTop(Canvas canvas) {
        Drawable drawable = getDrawable();
        RectF top = behindTop;
        if (drawable == null || top == null || drawable.getIntrinsicWidth() <= 0) return;
        float scale = top.width() / drawable.getIntrinsicWidth();
        float height = drawable.getIntrinsicHeight() * scale;
        float left = top.centerX() - top.width() / 2f;
        if (whole) {
            canvas.save();
            canvas.translate(left, top.top);
            canvas.scale(scale, scale);
            drawable.draw(canvas);
            canvas.restore();
            return;
        }
        float seam = top.centerY();
        float collarHalf = top.width() * COLLAR_STRIP / 2f;
        float hemHalf = top.width() * HEM_STRIP / 2f;

        canvas.save();
        canvas.clipRect(top.centerX() - collarHalf, top.top - top.height(), top.centerX() + collarHalf, seam);
        canvas.translate(left, top.top - top.height() * SHOWS_ABOVE);
        canvas.scale(scale, scale);
        drawable.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.clipRect(top.centerX() - hemHalf, seam, top.centerX() + hemHalf, top.bottom + top.height());
        canvas.translate(left, top.bottom + top.height() * SHOWS_BELOW - height);
        canvas.scale(scale, scale);
        drawable.draw(canvas);
        canvas.restore();
    }
}
