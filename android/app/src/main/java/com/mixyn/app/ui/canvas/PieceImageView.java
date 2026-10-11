package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * The image of one piece on the canvas. A piece is normally fitted into the middle of its frame.
 * Outerwear is "hung" instead: fitted against a line near the top of the frame — the shoulder line of
 * the top underneath — and centered left to right, so a jacket and a long coat both start at the
 * shoulders. The view can also tell where its own image starts, which is how that line is found.
 */
public class PieceImageView extends AppCompatImageView {

    private boolean hung;
    private float hangFrom;
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
     * Called whenever the image may have moved: a new image arrived (pieces are loaded from the network
     * after the page appears) or the view got its size.
     */
    public void setOnImageChanged(@Nullable Runnable onImageChanged) {
        this.onImageChanged = onImageChanged;
    }

    /**
     * For a piece fitted into the middle: how far below the top of the frame its image starts when the
     * view is drawn at this scale (the size from "Edit photo").
     */
    public float imageTop(float scale) {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || drawable.getIntrinsicWidth() <= 0
                || drawable.getIntrinsicHeight() <= 0) {
            return 0f;
        }
        float fit = Math.min((float) getWidth() / drawable.getIntrinsicWidth(),
                (float) getHeight() / drawable.getIntrinsicHeight());
        return Math.max(0f, (getHeight() - drawable.getIntrinsicHeight() * fit * scale) / 2f);
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
}
