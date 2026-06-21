package com.ccy.xhscommenthelper.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class FailureReasonBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class Bar(
        val label: String,
        val count: Int,
        val color: Int
    )

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE4E8EE")
        strokeWidth = 2f
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4C5663")
        textSize = 30f
    }
    private val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF20262D")
        textAlign = Paint.Align.RIGHT
        textSize = 30f
        isFakeBoldText = true
    }
    private val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6C7480")
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }

    private var bars: List<Bar> = emptyList()

    fun setBars(value: List<Bar>) {
        bars = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visibleBars = bars.filter { bar -> bar.count > 0 }.take(MAX_VISIBLE_BARS)
        if (visibleBars.isEmpty()) {
            canvas.drawText("暂无失败原因", width / 2f, height / 2f, emptyPaint)
            return
        }

        val leftPadding = 24f
        val rightPadding = 54f
        val topPadding = 18f
        val bottomPadding = 18f
        val labelWidth = width * 0.34f
        val chartLeft = leftPadding + labelWidth + 12f
        val chartRight = width - rightPadding
        val chartWidth = max(1f, chartRight - chartLeft)
        val rowHeight = (height - topPadding - bottomPadding) / visibleBars.size
        val barHeight = (rowHeight * 0.48f).coerceAtLeast(12f)
        val maxCount = visibleBars.maxOf { bar -> bar.count }.coerceAtLeast(1)

        canvas.drawLine(chartLeft, topPadding, chartLeft, height - bottomPadding, axisPaint)

        visibleBars.forEachIndexed { index, bar ->
            val centerY = topPadding + rowHeight * index + rowHeight / 2f
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            val barRight = chartLeft + chartWidth * bar.count / maxCount
            val rect = RectF(chartLeft, top, barRight, bottom)

            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(shortLabel(bar.label), leftPadding, centerY + labelTextOffset(), labelPaint)

            barPaint.color = bar.color
            canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, barPaint)

            canvas.drawText(bar.count.toString(), width - 16f, centerY + labelTextOffset(), countPaint)
        }
    }

    private fun shortLabel(value: String): String {
        return if (value.length <= MAX_LABEL_LENGTH) value else value.take(MAX_LABEL_LENGTH) + "..."
    }

    private fun labelTextOffset(): Float {
        return -(labelPaint.ascent() + labelPaint.descent()) / 2f
    }

    private companion object {
        const val MAX_VISIBLE_BARS = 6
        const val MAX_LABEL_LENGTH = 6
    }
}
