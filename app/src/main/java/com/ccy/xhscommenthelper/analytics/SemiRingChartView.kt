package com.ccy.xhscommenthelper.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SemiRingChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class Segment(
        val label: String,
        val count: Int,
        val color: Int
    )

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6C7480")
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF20262D")
        textAlign = Paint.Align.CENTER
        textSize = 46f
        isFakeBoldText = true
    }
    private var segments: List<Segment> = emptyList()

    fun setSegments(value: List<Segment>) {
        segments = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = segments.sumOf { segment -> segment.count }
        val centerX = width / 2f
        val strokeWidth = min(width, height) * 0.12f
        arcPaint.strokeWidth = strokeWidth
        val radius = min(width * 0.72f, height * 1.1f) / 2f
        val rect = RectF(
            centerX - radius,
            height * 0.18f,
            centerX + radius,
            height * 0.18f + radius * 2
        )

        if (total <= 0) {
            arcPaint.color = Color.parseColor("#FFE4E8EE")
            canvas.drawArc(rect, 180f, 180f, false, arcPaint)
            canvas.drawText("暂无数据", centerX, height * 0.58f, textPaint)
            return
        }

        var startAngle = 180f
        segments.forEach { segment ->
            if (segment.count <= 0) return@forEach
            val sweep = 180f * segment.count / total
            arcPaint.color = segment.color
            canvas.drawArc(rect, startAngle, sweep, false, arcPaint)
            startAngle += sweep
        }
        canvas.drawText(total.toString(), centerX, height * 0.56f, totalPaint)
        canvas.drawText("总数", centerX, height * 0.72f, textPaint)
    }
}
