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
import androidx.compose.ui.unit.dp
import com.example.omnispread.data.HistoricalZScore
import com.example.omnispread.ui.theme.AccentBlue

@Composable
fun ZScoreChart(
    dataPoints: List<HistoricalZScore>,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.size < 2) return

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()

        val values = dataPoints.map { it.value.toFloat() }
        val minV = minOf(values.min(), -2.5f)
        val maxV = maxOf(values.max(), 2.5f)
        val range = (maxV - minV).coerceAtLeast(0.01f)

        fun xOf(i: Int) = pad + (i.toFloat() / (dataPoints.size - 1)) * (w - 2 * pad)
        fun yOf(v: Float) = h - pad - ((v - minV) / range) * (h - 2 * pad)

        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

        // ±2 and 0 reference lines
        listOf(
            2f  to Color(0x80F87171),
            0f  to Color(0xFF71717A),
            -2f to Color(0x8034D399),
        ).forEach { (level, color) ->
            val y = yOf(level)
            drawLine(
                color       = color,
                start       = Offset(pad, y),
                end         = Offset(w - pad, y),
                strokeWidth = if (level == 0f) 1f else 1.5f,
                pathEffect  = if (level == 0f) null else dash,
            )
        }

        // Z-score line
        val path = Path()
        dataPoints.forEachIndexed { i, pt ->
            val x = xOf(i)
            val y = yOf(pt.value.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = AccentBlue, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
