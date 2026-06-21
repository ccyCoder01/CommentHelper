package com.ccy.xhscommenthelper.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class RoseChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class Slice(
        val label: String,
        val count: Int,
        val color: Int
    )

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6C7480")
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }
    private var slices: List<Slice> = emptyList()

    fun setSlices(value: List<Slice>) {
        slices = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visibleSlices = slices.filter { slice -> slice.count > 0 }
        if (visibleSlices.isEmpty()) {
            canvas.drawText("暂无失败原因", width / 2f, height / 2f, textPaint)
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = min(width, height) * 0.42f
        val maxCount = visibleSlices.maxOf { slice -> slice.count }.coerceAtLeast(1)
        val sweepAngle = 360f / visibleSlices.size

        visibleSlices.forEachIndexed { index, slice ->
            val radius = maxRadius * (0.32f + 0.68f * slice.count / maxCount)
            val rect = RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )
            slicePaint.color = slice.color
            canvas.drawArc(
                rect,
                -90f + index * sweepAngle,
                sweepAngle - 4f,
                true,
                slicePaint
            )
        }
    }
}
