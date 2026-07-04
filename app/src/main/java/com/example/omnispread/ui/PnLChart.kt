package com.example.omnispread.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omnispread.data.BacktestPoint
import com.example.omnispread.ui.theme.AccentBlue

@Composable
fun PnLChart(
    points: List<BacktestPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 180.dp,
) {
    if (points.size < 2) return

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight)) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        val n = points.size

        val values = points.map { it.pnl_pct.toFloat() }
        val rawMin = values.min()
        val rawMax = values.max()
        val range = (rawMax - rawMin).coerceAtLeast(0.01f)
        val vMin = rawMin - range * 0.1f
        val vMax = rawMax + range * 0.1f
        val vRange = (vMax - vMin).coerceAtLeast(0.01f)

        fun xOf(i: Int) = pad + (i.toFloat() / (n - 1)) * (w - 2 * pad)
        fun yOf(v: Float) = h - pad - ((v - vMin) / vRange) * (h - 2 * pad)

        // Zero line
        val zeroY = yOf(0f)
        drawLine(
            color       = Color(0xFF71717A),
            start       = Offset(pad, zeroY),
            end         = Offset(w - pad, zeroY),
            strokeWidth = 1f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )

        // PnL line
        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = xOf(i)
            val y = yOf(pt.pnl_pct.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun SpreadChart(
    points: List<BacktestPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        val n = points.size

        val values = points.map { it.spread.toFloat() }
        val vMin = values.min()
        val vMax = values.max()
        val vRange = (vMax - vMin).coerceAtLeast(0.01f)

        fun xOf(i: Int) = pad + (i.toFloat() / (n - 1)) * (w - 2 * pad)
        fun yOf(v: Float) = h - pad - ((v - vMin) / vRange) * (h - 2 * pad)

        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = xOf(i)
            val y = yOf(pt.spread.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = AccentBlue, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
