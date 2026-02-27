package az.tribe.lifeplanner.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import kotlin.math.min

/**
 * Renders ring progress indicators as Bitmaps for Glance widgets.
 * Glance doesn't support custom Canvas drawing, so we pre-render rings as images.
 */
object RingProgressRenderer {

    /**
     * Render a large progress ring with percentage text in center.
     */
    fun renderMainRing(
        sizePx: Int,
        progress: Float,
        strokeWidthPx: Float,
        ringStartColor: Int,
        ringEndColor: Int,
        trackColor: Int,
        textColor: Int,
        subTextColor: Int,
        completedText: String,
        totalText: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val padding = strokeWidthPx / 2f + 4f
        val rect = RectF(padding, padding, sizePx - padding, sizePx - padding)

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        // Progress arc with gradient
        val sweepAngle = progress.coerceIn(0f, 1f) * 360f
        if (sweepAngle > 0f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidthPx
                strokeCap = Paint.Cap.ROUND
                shader = SweepGradient(
                    sizePx / 2f, sizePx / 2f,
                    intArrayOf(ringStartColor, ringEndColor, ringStartColor),
                    floatArrayOf(0f, 0.5f, 1f)
                )
            }
            // Rotate canvas so gradient aligns with arc start at top
            canvas.save()
            canvas.rotate(-90f, sizePx / 2f, sizePx / 2f)
            canvas.drawArc(rect, 0f, sweepAngle, false, progressPaint)
            canvas.restore()
        }

        // Center percentage text
        val percentText = "${(progress * 100).toInt()}%"
        val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = sizePx * 0.22f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val percentY = sizePx / 2f + percentPaint.textSize * 0.35f - 8f
        canvas.drawText(percentText, sizePx / 2f, percentY, percentPaint)

        // Sub text ("3/5 done" or completed/total)
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = sizePx * 0.10f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(totalText, sizePx / 2f, percentY + subPaint.textSize + 6f, subPaint)

        return bitmap
    }

    /**
     * Render a small habit ring for individual habits.
     */
    fun renderMiniRing(
        sizePx: Int,
        progress: Float,
        strokeWidthPx: Float,
        ringColor: Int,
        trackColor: Int,
        isCompleted: Boolean,
        checkColor: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val padding = strokeWidthPx / 2f + 2f
        val rect = RectF(padding, padding, sizePx - padding, sizePx - padding)

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        // Progress
        val sweepAngle = progress.coerceIn(0f, 1f) * 360f
        if (sweepAngle > 0f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidthPx
                strokeCap = Paint.Cap.ROUND
                color = ringColor
            }
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        }

        // Check mark if completed
        if (isCompleted) {
            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidthPx * 0.6f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = checkColor
            }
            val cx = sizePx / 2f
            val cy = sizePx / 2f
            val s = sizePx * 0.18f
            // Draw checkmark path
            val path = android.graphics.Path().apply {
                moveTo(cx - s, cy)
                lineTo(cx - s * 0.2f, cy + s * 0.7f)
                lineTo(cx + s, cy - s * 0.5f)
            }
            canvas.drawPath(path, checkPaint)
        }

        return bitmap
    }

    /**
     * Render a compact ring for the small dashboard widget.
     */
    fun renderCompactRing(
        sizePx: Int,
        progress: Float,
        strokeWidthPx: Float,
        ringStartColor: Int,
        ringEndColor: Int,
        trackColor: Int,
        centerEmoji: String? = null,
        centerText: String? = null,
        textColor: Int = 0
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val padding = strokeWidthPx / 2f + 2f
        val rect = RectF(padding, padding, sizePx - padding, sizePx - padding)

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        // Progress with gradient
        val sweepAngle = progress.coerceIn(0f, 1f) * 360f
        if (sweepAngle > 0f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidthPx
                strokeCap = Paint.Cap.ROUND
                shader = SweepGradient(
                    sizePx / 2f, sizePx / 2f,
                    intArrayOf(ringStartColor, ringEndColor, ringStartColor),
                    floatArrayOf(0f, 0.5f, 1f)
                )
            }
            canvas.save()
            canvas.rotate(-90f, sizePx / 2f, sizePx / 2f)
            canvas.drawArc(rect, 0f, sweepAngle, false, progressPaint)
            canvas.restore()
        }

        // Center text
        centerText?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = sizePx * 0.28f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(it, sizePx / 2f, sizePx / 2f + paint.textSize * 0.35f, paint)
        }

        return bitmap
    }

    /**
     * Get category color as ARGB int.
     */
    fun getCategoryColor(category: String): Int {
        return when (category.uppercase()) {
            "CAREER" -> 0xFF4A6FFF.toInt()
            "FINANCIAL" -> 0xFF28C76F.toInt()
            "PHYSICAL" -> 0xFFFF9F43.toInt()
            "SOCIAL" -> 0xFF7A5AF8.toInt()
            "EMOTIONAL" -> 0xFF00CFE8.toInt()
            "SPIRITUAL" -> 0xFFEA5455.toInt()
            "FAMILY" -> 0xFF6236FF.toInt()
            else -> 0xFF667EEA.toInt()
        }
    }
}
