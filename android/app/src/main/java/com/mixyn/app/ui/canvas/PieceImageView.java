package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.List;

/**
 * The image of one piece on the canvas. A piece is normally fitted into the middle of its frame.
 * Outerwear is "hung" instead: from a line near the top of the frame — the shoulder line of the top
 * underneath — as wide as that top and centered left to right, so a jacket and a long coat both sit on
 * the top's shoulders. A dress in the top's slot is hung too, from the top of its slot, and reaches further down
 * than the view itself: over the bottom's part of the outfit, like a long coat. A hat stands on the
 * bottom edge of its view instead (the top's shoulder line), always at the same width.
 * A piece worn under the top is drawn "behind" it: in two halves, so that its collar shows
 * above the top's neckline and its hem below the top's hem, however long the two pieces are. Under a
 * top that is open down the front it is drawn whole instead, so it fills the opening like a piece the
 * top was put on over. A piece that is itself open down the front can leave its opening out: in a
 * photo of an open cardigan the opening shows the inside of its back, which on an outfit should show
 * whatever is worn under it instead. The view can also tell where its own image is and whether it is
 * open down the front, which is how those places are found.
 */
public class PieceImageView extends AppCompatImageView {

    /** Behind a top: how much of the top's height the collar shows above it and the hem below it… */
    private static final float SHOWS_ABOVE = 0.04f;
    private static final float SHOWS_BELOW = 0.035f;
    /** …and how wide a strip of the piece is drawn, in shares of the top's width: the collar and the body, not the sleeves. */
    private static final float COLLAR_STRIP = 0.45f;
    private static final float HEM_STRIP = 0.7f;
    /** How many rows the outline of a piece is measured at. */
    private static final int OUTLINE_ROWS = 64;
    /** A piece counts as open down the front when this much of a line down its middle is see-through. */
    private static final float OPEN_SHARE = 0.25f;
    private static final int ALPHA_VISIBLE = 24; // fainter pixels count as see-through, like on the backend
    /** A see-through gap between the two fronts narrower than this share of the width is just a notch. */
    private static final float GAP_MIN = 0.03f;
    /** The back of the neck above an opening is a narrow band: never more than this share of the height. */
    private static final float NECK_BAND_MAX = 0.2f;
    private static final float THOUSAND = 1000f;

    private boolean hung;
    private float hangFrom;
    /** How wide a hung image is drawn at most, in pixels; 0 = as wide as the view. */
    private float hangWidth;
    /** A hung image placed on the bottom edge of the view instead of from the top. */
    private boolean standing;
    /** How far down a hung image may reach, in heights of this view: more than 1 for a dress. */
    private float reach = 1f;
    private boolean behind;
    @Nullable
    private RectF behindTop;
    /** The outline of an open top this piece shows through, or null for a closed top. */
    @Nullable
    private Path behindOpening;
    private boolean whole;
    /**
     * What is known about this piece's own image, measured once per image: whether it is open down the
     * front, and where each of OUTLINE_ROWS rows starts and ends, in shares of the image's width.
     */
    private boolean measured;
    private boolean openFront;
    @Nullable
    private float[] rowStart;
    @Nullable
    private float[] rowEnd;
    /**
     * For a piece that is see-through between its fronts but closed across the back of the neck: the
     * band above the gap, as left, right and bottom in shares of the image. Null when there is none.
     */
    @Nullable
    private float[] neckBand;
    /**
     * The opening the backend knows about (an open cardigan whose photo shows the inside of its back):
     * neckLeft, neckRight, hemLeft, hemRight in shares of the width. Null for a piece not marked open.
     */
    @Nullable
    private float[] opening;
    /** While something is worn under this piece, its opening is left out of the drawing. */
    private boolean openingLeftOut;
    @Nullable
    private Runnable onImageChanged;

    public PieceImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** Draws the image from this many pixels below the top of the frame, as large as still fits. */
    public void hangFrom(float y) {
        hangFrom(y, 0f);
    }

    /** The same, but no wider than this many pixels (0 = the view's width): outerwear as wide as the top. */
    public void hangFrom(float y, float width) {
        hung = true;
        standing = false;
        hangFrom = y;
        hangWidth = width;
        setScaleType(ScaleType.MATRIX);
        hang();
    }

    /**
     * Hangs the image from the top of this view and lets it reach this many view heights down, as large
     * as still fits: a dress over the place of the bottoms. The parents must not clip it.
     */
    public void hangLong(float reach) {
        this.reach = reach;
        hangFrom(0f);
    }

    /** Draws the image standing on the bottom edge of the view, at most this many pixels wide: a hat. */
    public void standOn(float width) {
        hangFrom(0f, width);
        standing = true;
        hang();
    }

    /** Back to the usual: the image fitted into the middle of the view (a recycled page that held a dress). */
    public void fitInside() {
        hung = false;
        reach = 1f;
        setScaleType(ScaleType.FIT_CENTER);
    }

    /**
     * Draws the piece behind a top whose image covers this rectangle (in this view's coordinates), as
     * wide as that top. Null while the top isn't known yet: then nothing is drawn.
     *
     * @param opening for a top that is open down the front, its outline (see outline): the piece then
     *                shows through the opening and is drawn whole; null for a closed top
     */
    public void drawBehind(@Nullable RectF top, @Nullable Path opening) {
        behind = true;
        behindTop = top;
        behindOpening = opening;
        invalidate();
    }

    /**
     * What the backend knows about this piece's opening: neckLeft, neckRight, hemLeft, hemRight in
     * thousandths of the picture's width, or null / empty for a piece that isn't marked open.
     */
    public void setOpening(@Nullable List<Integer> thousandths) {
        opening = thousandths == null || thousandths.size() != 4 ? null : new float[] {
                thousandths.get(0) / THOUSAND, thousandths.get(1) / THOUSAND,
                thousandths.get(2) / THOUSAND, thousandths.get(3) / THOUSAND};
        invalidate();
    }

    /**
     * Leaves the opening of an open-front piece out of its drawing, the way the background was left out
     * of its photo: what is worn under it shows there instead of the inside of its back. Does nothing
     * for a closed piece. Only the outfit canvas asks for this, and only while something is worn under
     * the piece; otherwise it is shown as it is.
     */
    public void leaveOpeningOut(boolean leaveOut) {
        if (openingLeftOut == leaveOut) return;
        openingLeftOut = leaveOut;
        invalidate();
    }

    /**
     * Whether this piece is open down the front: the backend marked it so, or a good part of a line
     * down its middle is see-through (an unbuttoned shirt with nothing behind the opening).
     */
    public boolean isOpenFront() {
        measure();
        return opening != null || openFront;
    }

    /**
     * The outline of this piece when its image covers the given rectangle: on every row, from the first
     * pixel of the piece to the last, so openings inside the piece are part of it. Something drawn only
     * inside this outline shows through the piece's openings but never sticks out beside it.
     * Null while there is no image.
     */
    @Nullable
    public Path outline(RectF bounds) {
        measure();
        if (rowStart == null || rowEnd == null) return null;
        int rows = rowStart.length;
        Path path = new Path();
        boolean started = false;
        for (int row = 0; row < rows; row++) { // down the left side
            if (rowStart[row] > rowEnd[row]) continue; // nothing of the piece on this row
            float x = bounds.left + rowStart[row] * bounds.width();
            float y = bounds.top + (row + 0.5f) / rows * bounds.height();
            if (started) path.lineTo(x, y);
            else path.moveTo(x, y);
            started = true;
        }
        for (int row = rows - 1; row >= 0; row--) { // and back up the right side
            if (rowStart[row] > rowEnd[row]) continue;
            path.lineTo(bounds.left + rowEnd[row] * bounds.width(), bounds.top + (row + 0.5f) / rows * bounds.height());
        }
        path.close();
        return started ? path : null;
    }

    private void measure() {
        if (measured) return;
        measured = true;
        openFront = false;
        rowStart = null;
        rowEnd = null;
        neckBand = null;
        if (!(getDrawable() instanceof BitmapDrawable drawable) || drawable.getBitmap() == null) return;
        Bitmap bitmap = drawable.getBitmap();
        // Pixels of a hardware bitmap can't be read, so such a bitmap is copied first.
        Bitmap readable = bitmap.getConfig() == Bitmap.Config.HARDWARE
                ? bitmap.copy(Bitmap.Config.ARGB_8888, false) : bitmap;
        if (readable == null) return;
        int width = readable.getWidth();
        int height = readable.getHeight();
        int[] pixels = new int[width * height];
        readable.getPixels(pixels, 0, width, 0, 0, width, height);
        if (readable != bitmap) readable.recycle();

        // Three lines down the middle are tried, because the two fronts rarely part exactly in the middle.
        float mostOpen = 0f;
        for (float line : new float[] {0.44f, 0.5f, 0.56f}) {
            int x = Math.round(width * line);
            int clear = 0;
            for (int y = 0; y < height; y++) {
                if (Color.alpha(pixels[y * width + x]) < ALPHA_VISIBLE) clear++;
            }
            mostOpen = Math.max(mostOpen, (float) clear / height);
        }
        openFront = mostOpen > OPEN_SHARE;

        int rows = Math.min(height, OUTLINE_ROWS);
        rowStart = new float[rows];
        rowEnd = new float[rows];
        for (int row = 0; row < rows; row++) {
            int y = Math.min(height - 1, Math.round((row + 0.5f) * height / rows));
            int first = 0;
            while (first < width && Color.alpha(pixels[y * width + first]) < ALPHA_VISIBLE) first++;
            int last = width - 1;
            while (last > first && Color.alpha(pixels[y * width + last]) < ALPHA_VISIBLE) last--;
            rowStart[row] = first == width ? 1f : (float) first / width;
            rowEnd[row] = first == width ? 0f : (float) (last + 1) / width;
        }
        if (openFront) neckBand = findNeckBand(pixels, width, height);
    }

    /**
     * Goes down the middle of an open piece: first the empty space above its neckline, then the back of
     * the neck, then the see-through gap between its fronts. The back of the neck, as wide as the gap
     * just below it, is the band that hides the collar of whatever is worn underneath.
     * Null when the middle has no such band (the piece is open right from the neckline) or the "band"
     * is too tall to be one.
     */
    @Nullable
    private static float[] findNeckBand(int[] pixels, int width, int height) {
        int middle = width / 2;
        int y = 0;
        while (y < height && Color.alpha(pixels[y * width + middle]) < ALPHA_VISIBLE) y++;
        int bandTop = y;
        while (y < height && Color.alpha(pixels[y * width + middle]) >= ALPHA_VISIBLE) y++;
        if (y == height || y == bandTop || y - bandTop > height * NECK_BAND_MAX) return null;
        // The gap usually opens with a rounded corner, so its width is taken a little further down.
        int row = Math.min(height - 1, y + height / 20);
        int left = middle;
        while (left > 0 && Color.alpha(pixels[row * width + left - 1]) < ALPHA_VISIBLE) left--;
        int right = middle;
        while (right < width - 1 && Color.alpha(pixels[row * width + right + 1]) < ALPHA_VISIBLE) right++;
        boolean between = left > 0 && right < width - 1; // a gap inside the piece, not the background
        if (!between || right - left < width * GAP_MIN) return null;
        return new float[] {(float) left / width, (float) (right + 1) / width, (float) y / height};
    }

    /**
     * The part of an open-front piece that is left out while something is worn under it, for an image
     * covering this rectangle: the whole opening between the fronts when the backend described it, or
     * just the back of the neck above a gap that is see-through anyway. Null for a closed piece.
     */
    @Nullable
    private Path openingPath(RectF bounds) {
        measure();
        Path path = new Path();
        if (opening != null) {
            path.moveTo(bounds.left + opening[0] * bounds.width(), bounds.top);
            path.lineTo(bounds.left + opening[1] * bounds.width(), bounds.top);
            path.lineTo(bounds.left + opening[3] * bounds.width(), bounds.bottom);
            path.lineTo(bounds.left + opening[2] * bounds.width(), bounds.bottom);
            path.close();
            return path;
        }
        if (neckBand != null) {
            path.addRect(bounds.left + neckBand[0] * bounds.width(), bounds.top,
                    bounds.left + neckBand[1] * bounds.width(), bounds.top + neckBand[2] * bounds.height(),
                    Path.Direction.CW);
            return path;
        }
        return null;
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
     * The rectangle the image covers when the view is drawn at this scale (the size from "Edit photo"),
     * which is applied around the middle of the view. Null while there is no image or no size yet.
     */
    @Nullable
    public RectF imageBounds(float scale) {
        RectF drawn = drawnBounds();
        if (drawn == null) return null;
        float middleX = getWidth() / 2f;
        float middleY = getHeight() / 2f;
        return new RectF(middleX + (drawn.left - middleX) * scale, middleY + (drawn.top - middleY) * scale,
                middleX + (drawn.right - middleX) * scale, middleY + (drawn.bottom - middleY) * scale);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        measured = false;
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
        if (behind) {
            drawBehindTop(canvas);
            return;
        }
        RectF bounds = openingLeftOut ? drawnBounds() : null;
        Path leftOut = bounds == null ? null : openingPath(bounds);
        if (leftOut == null) {
            super.onDraw(canvas);
            return;
        }
        canvas.save();
        canvas.clipOutPath(leftOut);
        super.onDraw(canvas);
        canvas.restore();
    }

    /** Where the image is drawn inside this view right now, hung or fitted; null while there is none. */
    @Nullable
    private RectF drawnBounds() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0
                || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return null;
        }
        if (hung) {
            RectF bounds = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            getImageMatrix().mapRect(bounds);
            bounds.offset(getPaddingLeft(), getPaddingTop());
            return bounds;
        }
        float fit = Math.min((float) getWidth() / drawable.getIntrinsicWidth(),
                (float) getHeight() / drawable.getIntrinsicHeight());
        float halfWidth = drawable.getIntrinsicWidth() * fit / 2f;
        float halfHeight = drawable.getIntrinsicHeight() * fit / 2f;
        return new RectF(getWidth() / 2f - halfWidth, getHeight() / 2f - halfHeight,
                getWidth() / 2f + halfWidth, getHeight() / 2f + halfHeight);
    }

    private void hang() {
        Drawable drawable = getDrawable();
        if (!hung || drawable == null) return;
        float frameWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float frameHeight = getHeight() * reach - getPaddingTop() - getPaddingBottom() - hangFrom;
        float width = drawable.getIntrinsicWidth();
        float height = drawable.getIntrinsicHeight();
        if (frameWidth <= 0 || frameHeight <= 0 || width <= 0 || height <= 0) return;
        float fitWidth = hangWidth > 0 ? Math.min(hangWidth, frameWidth) : frameWidth;
        float fit = Math.min(fitWidth / width, frameHeight / height);
        Matrix matrix = new Matrix();
        matrix.setScale(fit, fit);
        matrix.postTranslate((frameWidth - width * fit) / 2f,
                standing ? frameHeight - height * fit : hangFrom);
        setImageMatrix(matrix);
    }

    /**
     * The piece is as wide as the top and centered on it. Its upper half is placed by the collar (a
     * little above the top), its lower half by the hem (a little below the top); the two meet behind
     * the middle of the top, where the seam can't be seen. Only a strip down the middle is drawn, so the
     * sleeves of a T-shirt don't stick out from behind a sweater's shoulders. Under a top that is open
     * down the front the seam would show, so the piece is drawn in one piece there, placed by the collar.
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
        float collarHalf = top.width() * COLLAR_STRIP / 2f;
        float hemHalf = top.width() * HEM_STRIP / 2f;
        if (behindOpening != null) {
            // One piece, placed by the collar. Above and below the top it shows as a strip (collar, hem);
            // level with the top it shows only inside the top's outline: through the opening, never
            // beside the shoulders.
            float y = top.top - top.height() * SHOWS_ABOVE;
            drawStrip(canvas, drawable, top.centerX(), collarHalf, top.top - top.height(), top.top, left, y, scale);
            canvas.save();
            canvas.clipPath(behindOpening);
            canvas.translate(left, y);
            canvas.scale(scale, scale);
            drawable.draw(canvas);
            canvas.restore();
            drawStrip(canvas, drawable, top.centerX(), hemHalf, top.bottom, top.bottom + top.height(), left, y, scale);
            return;
        }
        float seam = top.centerY();

        drawStrip(canvas, drawable, top.centerX(), collarHalf, top.top - top.height(), seam,
                left, top.top - top.height() * SHOWS_ABOVE, scale);
        drawStrip(canvas, drawable, top.centerX(), hemHalf, seam, top.bottom + top.height(),
                left, top.bottom + top.height() * SHOWS_BELOW - height, scale);
    }

    /** Draws the image at (x, y) and this scale, but only the strip around centerX between two heights. */
    private static void drawStrip(Canvas canvas, Drawable drawable, float centerX, float halfWidth,
            float fromY, float toY, float x, float y, float scale) {
        canvas.save();
        canvas.clipRect(centerX - halfWidth, fromY, centerX + halfWidth, toY);
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        drawable.draw(canvas);
        canvas.restore();
    }
}
