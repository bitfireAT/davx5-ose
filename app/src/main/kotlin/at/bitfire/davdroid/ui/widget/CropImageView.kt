/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageView
import at.bitfire.davdroid.R

/**
 * [android.widget.ImageView] that supports directional cropping in both vertical and
 * horizontal directions instead of being restricted to center-crop. Automatically sets [ ] to MATRIX and defaults to center-crop.
 *
 * @author Based on source code found on https://stackoverflow.com/a/26031741, by qix (CC BY-SA 4.0).
 */
class CropImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        @AttrRes defStyleAttr: Int = 0)
    : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_HORIZONTAL_OFFSET = 0.5f
        private const val DEFAULT_VERTICAL_OFFSET = 0.5f
    }

    private var mHorizontalOffsetPercent = DEFAULT_HORIZONTAL_OFFSET
    private var mVerticalOffsetPercent = DEFAULT_VERTICAL_OFFSET

    init {
        scaleType = ScaleType.MATRIX

        if (attrs != null) {
            context.theme.obtainStyledAttributes(attrs, R.styleable.CropImageView, 0, 0).apply {
                mHorizontalOffsetPercent = getFloat(R.styleable.CropImageView_horizontalOffsetPercent, DEFAULT_HORIZONTAL_OFFSET)
                mVerticalOffsetPercent = getFloat(R.styleable.CropImageView_verticalOffsetPercent, DEFAULT_VERTICAL_OFFSET)
            }
            applyCropOffset()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCropOffset()
    }

    /**
     * Sets the crop box offset by the specified percentage values. For example, a center-crop would
     * be (0.5, 0.5), a top-left crop would be (0, 0), and a bottom-center crop would be (0.5, 1)
     */
    fun setCropOffset(horizontalOffsetPercent: Float, verticalOffsetPercent: Float) {
        require(!(mHorizontalOffsetPercent < 0 || mVerticalOffsetPercent < 0 || mHorizontalOffsetPercent > 1 || mVerticalOffsetPercent > 1)) { "Offset values must be a float between 0.0 and 1.0" }
        mHorizontalOffsetPercent = horizontalOffsetPercent
        mVerticalOffsetPercent = verticalOffsetPercent
        applyCropOffset()
    }

    private fun applyCropOffset() {
        val matrix = imageMatrix
        val scale: Float
        val viewWidth = width - paddingLeft - paddingRight
        val viewHeight = height - paddingTop - paddingBottom
        var drawableWidth = 0
        var drawableHeight = 0
        // Allow for setting the drawable later in code by guarding ourselves here.
        if (drawable != null) {
            drawableWidth = drawable.intrinsicWidth
            drawableHeight = drawable.intrinsicHeight
        }

        // Get the scale.
        scale = if (drawableWidth * viewHeight > drawableHeight * viewWidth) {
            // Drawable is flatter than view. Scale it to fill the view height.
            // A Top/Bottom crop here should be identical in this case.
            viewHeight.toFloat() / drawableHeight.toFloat()
        } else {
            // Drawable is taller than view. Scale it to fill the view width.
            // Left/Right crop here should be identical in this case.
            viewWidth.toFloat() / drawableWidth.toFloat()
        }
        val viewToDrawableWidth = viewWidth / scale
        val viewToDrawableHeight = viewHeight / scale
        val xOffset = mHorizontalOffsetPercent * (drawableWidth - viewToDrawableWidth)
        val yOffset = mVerticalOffsetPercent * (drawableHeight - viewToDrawableHeight)

        // Define the rect from which to take the image portion.
        val drawableRect = RectF(
                xOffset,
                yOffset,
                xOffset + viewToDrawableWidth,
                yOffset + viewToDrawableHeight)
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        matrix.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.FILL)
        imageMatrix = matrix
    }

}