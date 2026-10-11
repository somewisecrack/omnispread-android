package com.example.omnispread.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.pow

object BacktestEngine {

    fun run(request: BacktestRequest): BacktestResult {
        if (request.half_life < 1) return BacktestResult(status = "failed", error = "Half-life must be at least 1 bar")

        // Determine how many calendar days to fetch forward
        val barsPerDay = when (request.interval) {
            "15m" -> 26; "30m" -> 13; "60m", "1h" -> 7
            else -> 1
        }
        val tradingDays = max(3, request.half_life / barsPerDay + 3)
        val calendarDays = minOf(370, tradingDays * 3 + 10)

        // Forward window: from the scan end date (00:00 UTC) for `calendarDays` days.
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val startSec = (sdf.parse(request.end_date) ?: return BacktestResult(status = "failed", error = "Invalid end_date")).time / 1000
        val endSec = startSec + calendarDays * 86_400L
        val xRaw = YahooFinanceApi.fetchPricesBetween(request.x, startSec, endSec, request.interval).filter { it.first >= startSec }
        val yRaw = YahooFinanceApi.fetchPricesBetween(request.y, startSec, endSec, request.interval).filter { it.first >= startSec }

        if (xRaw.size < 2 || yRaw.size < 2) {
            return BacktestResult(status = "failed", error = "No forward price data available for this pair.")
        }

        // Align on common timestamps
        val xMap = xRaw.toMap()
        val yMap = yRaw.toMap()
        val common = xRaw.map { it.first }.filter { it in yMap }.sorted()
        if (common.size < 2) {
            return BacktestResult(status = "failed", error = "No overlapping price data for the pair.")
        }

        // Take first half_life+1 points (or fewer if not enough data)
        val bars = common.take(request.half_life + 1)

        val x0 = xMap[bars.first()]!!
        val y0 = yMap[bars.first()]!!
        val unit = abs(request.qty * x0) + abs(y0)
        if (unit == 0.0) return BacktestResult(status = "failed", error = "Entry unit value is zero.")

        val points = bars.map { ts ->
            val xPrice = xMap[ts]!!
            val yPrice = yMap[ts]!!
            val spread = yPrice - request.qty * xPrice
            val pnlCurrency = if (request.direction in setOf("LONG_SPREAD", "short_x_long_y")) {
                -request.qty * (xPrice - x0) + (yPrice - y0)
            } else {
                request.qty * (xPrice - x0) - (yPrice - y0)
            }
            BacktestPoint(
                time = ts,
                x = xPrice.roundTo(4),
                y = yPrice.roundTo(4),
                spread = spread.roundTo(4),
                pnl_pct = (pnlCurrency * 100.0 / unit).roundTo(4),
            )
        }

        val xLabel = request.x
        val yLabel = request.y

        return BacktestResult(
            status = "completed",
            pair = "$xLabel/$yLabel",
            x = request.x, y = request.y,
            qty = request.qty, direction = request.direction,
            interval = request.interval, half_life = request.half_life,
            entry_time = points.first().time,
            exit_time = points.last().time,
            final_pnl_pct = points.last().pnl_pct,
            max_profit_pct = points.maxOf { it.pnl_pct }.roundTo(4),
            points = points,
            note = "Forward data fetched from Yahoo Finance.",
        )
    }

    private fun Double.roundTo(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (this * factor).roundToLong() / factor
    }
}
