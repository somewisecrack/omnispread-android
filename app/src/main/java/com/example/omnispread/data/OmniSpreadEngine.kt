package com.example.omnispread.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Output of a scan: ranked pairs plus the aligned close series used, keyed by ticker. */
data class ScanOutput(
    val results: List<PairResult>,
    val closes: Map<String, DoubleArray>,
)

/**
 * OmniSpread v2 screen (port of the Python backend's `_screen_pair_v2`):
 *  1. Johansen (det_order=0, k_ar_diff=1) must reject rank 0 (rank 1 or 2 accepted).
 *  2. Engle–Granger in both orderings; the larger p-value must be < 0.05.
 *  3. Static hedge from the Johansen vector; spread = Y − β·X on the chosen price basis.
 *  4. Half-life from spread changes on lagged spread; z over a half-life rolling window; |z| > 2.
 * Pairs are ranked by CADF p-value, then |z| descending.
 */
class OmniSpreadEngine(
    private val tickers: List<String>,
    private val period: String = "3y",
    private val interval: String = "1d",
    private val startDate: String? = null,
    private val endDate: String? = null,
    private val priceBasis: String = "raw",
    private val industryMap: Map<String, String> = emptyMap(),
    private val onProgress: (String) -> Unit = {},
) {

    companion object {
        const val Z_SCORE_LIMIT = 2.0
        const val CADF_P_VALUE = 0.05
        const val MIN_QTY = 1e-6
        private const val FETCH_THREADS = 6
    }

    private class Screened(
        val x: String, val y: String,
        val qty: Double, val beta: Double,
        val direction: String, val combo: String,
        val cadfP: Double, val rank: Int,
        val priceCorr: Double,
        val px: Double, val py: Double,
        val spread: DoubleArray, val timestamps: LongArray,
        val halfLife: Int,
        val industryX: String, val industryY: String,
    )

    fun runScan(): ScanOutput {
        onProgress("Fetching price data from Yahoo Finance...")
        val range = if (startDate != null && endDate != null) "5y" else period
        val fmt = dayFormat()
        val startTs = startDate?.let { fmt.parse(it)?.time?.div(1000) }
        val endTs = endDate?.let { fmt.parse(it)?.time?.div(1000)?.plus(86_399) }

        // Download in parallel (a few at a time to stay polite to Yahoo).
        val data = java.util.concurrent.ConcurrentHashMap<String, Pair<LongArray, DoubleArray>>()
        val fetched = java.util.concurrent.atomic.AtomicInteger(0)
        val io = java.util.concurrent.Executors.newFixedThreadPool(FETCH_THREADS)
        try {
            tickers.map { t ->
                io.submit {
                    val raw = YahooFinanceApi.fetchPrices(t, range, interval)
                        .filter { (ts, _) -> (startTs == null || ts >= startTs) && (endTs == null || ts <= endTs) }
                    if (raw.size >= 51) data[t] = raw.map { it.first }.toLongArray() to raw.map { it.second }.toDoubleArray()
                    onProgress("Fetched ${fetched.incrementAndGet()}/${tickers.size} price series...")
                }
            }.forEach { it.get() }
        } finally { io.shutdown() }
        val active = tickers.filter { data.containsKey(it) }
        if (active.size < 2) return ScanOutput(emptyList(), data.mapValues { it.value.second })

        val combos = buildList { for (i in active.indices) for (j in i + 1 until active.size) add(active[i] to active[j]) }
        onProgress("Screening ${combos.size} pairs (Johansen → CADF)...")
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val cpu = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
        val screened = try {
            combos.map { (x, y) ->
                cpu.submit<PairResult?> {
                    val r = try { screenPair(x, data[x]!!, y, data[y]!!)?.let(::buildResult) } catch (_: Exception) { null }
                    val n = done.incrementAndGet()
                    if (n % 100 == 0) onProgress("Screened $n/${combos.size} pairs...")
                    r
                }
            }.mapNotNull { it.get() }
        } finally { cpu.shutdown() }
        val results = screened.sortedWith(compareBy<PairResult> { it.cadf_pvalue }.thenByDescending { abs(it.z_score) })

        return ScanOutput(results, data.mapValues { it.value.second })
    }

    private fun screenPair(
        xSym: String, xd: Pair<LongArray, DoubleArray>,
        ySym: String, yd: Pair<LongArray, DoubleArray>,
    ): Screened? {
        val yMap = HashMap<Long, Double>(yd.first.size * 2).apply { yd.first.forEachIndexed { i, t -> put(t, yd.second[i]) } }
        val ts = mutableListOf<Long>(); val xs = mutableListOf<Double>(); val ys = mutableListOf<Double>()
        xd.first.forEachIndexed { i, t -> yMap[t]?.let { yv -> ts += t; xs += xd.second[i]; ys += yv } }
        if (ts.size < 51) return null // need ≥ 50 return observations
        val xP = xs.toDoubleArray(); val yP = ys.toDoubleArray()
        val px = xP.last(); val py = yP.last()

        val log = priceBasis == "log"
        if (log && (xP.any { it <= 0 } || yP.any { it <= 0 })) return null
        val xb = if (log) DoubleArray(xP.size) { ln(xP[it]) } else xP
        val yb = if (log) DoubleArray(yP.size) { ln(yP[it]) } else yP

        val jr = Stats.johansen(xb, yb) ?: return null
        val rank = Stats.johansenRank(jr)
        if (rank < 1) return null

        val cadfP = max(Stats.cointPValue(yb, xb), Stats.cointPValue(xb, yb))
        if (!(cadfP < CADF_P_VALUE)) return null

        val v = jr.evec[0]
        if (v[1] == 0.0) return null
        val beta = -v[0] / v[1]
        if (!beta.isFinite() || beta <= 0) return null

        val spread = DoubleArray(xb.size) { yb[it] - beta * xb[it] }
        val hl = halfLife(spread)
        val mavg = rollingMean(spread, hl); val mstd = rollingStd(spread, hl)
        val sd = mstd.last()
        if (!sd.isFinite() || sd == 0.0) return null
        val z = ((spread.last() - mavg.last()) / sd).roundTo(1)
        if (!z.isFinite() || abs(z) <= Z_SCORE_LIMIT) return null

        val qty = if (log) beta * py / px else beta
        if (!qty.isFinite() || qty < MIN_QTY) return null

        val ix = industryMap[xSym] ?: "Unknown"; val iy = industryMap[ySym] ?: "Unknown"
        val q = "%.4g".format(qty)
        val (direction, combo) = if (z > 0)
            "SHORT_SPREAD" to "Buy $q of $xSym (${fmtPx(px)})  &  Sell 1 of $ySym (${fmtPx(py)})"
        else
            "LONG_SPREAD" to "Sell $q of $xSym (${fmtPx(px)})  &  Buy 1 of $ySym (${fmtPx(py)})"

        return Screened(
            x = xSym, y = ySym, qty = qty, beta = beta, direction = direction, combo = combo,
            cadfP = cadfP, rank = rank, priceCorr = Stats.corr(xb, yb).roundTo(2),
            px = px, py = py, spread = spread, timestamps = ts.toLongArray(), halfLife = hl,
            industryX = ix, industryY = iy,
        )
    }

    private fun buildResult(s: Screened): PairResult {
        val hl = s.halfLife
        val mavg = rollingMean(s.spread, hl); val mstd = rollingStd(s.spread, hl)
        val sdLast = mstd.last()
        val z = ((s.spread.last() - mavg.last()) / (if (sdLast != 0.0) sdLast else 1e-12)).roundTo(1)
        val moveRaw = if (z.isFinite() && sdLast.isFinite()) -z * sdLast else 0.0
        val unit = (abs(s.qty * s.px) + abs(s.py)).roundTo(2)
        val expR = if (priceBasis == "log") abs(moveRaw * 100 / (1.0 + abs(s.beta))).roundTo(1)
                   else if (unit != 0.0) abs(moveRaw * 100 / unit).roundTo(1) else 0.0

        val allZ = DoubleArray(s.spread.size) { (s.spread[it] - mavg[it]) / mstd[it] }
        val history = allZ.indices.filter { allZ[it].isFinite() }
            .map { HistoricalZScore(s.timestamps[it], allZ[it].roundTo(2)) }

        var extremeFlag = "No"; var extremeDetail = ""; var profitable = "N/A"; var pnl = 0.0
        val start = max(0, s.spread.size - hl)
        val window = (start until allZ.size).filter { allZ[it].isFinite() }
        if (window.isNotEmpty()) {
            val cur = allZ.last()
            val extIdx = if (cur > 0) window.maxBy { allZ[it] } else window.minBy { allZ[it] }
            val ext = allZ[extIdx]
            if (isClose(cur, ext)) extremeFlag = "Yes"
            extremeDetail = "${ext.roundTo(1)} (${dayFormat().format(Date(s.timestamps[extIdx] * 1000))})"
            if (extremeFlag == "No") {
                val sign = if (ext > 0) -1.0 else 1.0
                pnl = (sign * (s.spread.last() - s.spread[extIdx])).roundTo(2)
                profitable = if (pnl > 0) "Yes" else "No"
            }
        }

        return PairResult(
            pair = "${s.x}/${s.y}",
            x = s.x, y = s.y,
            qty = s.qty, beta = s.beta,
            direction = s.direction, combo = s.combo,
            method = "CADF+Johansen", engine_version = "v2", price_basis = priceBasis,
            cadf_pvalue = s.cadfP, johansen_rank = s.rank,
            price_corr = s.priceCorr,
            z_score = z, half_life = hl,
            move_to_mean = moveRaw.roundTo(2), exp_return = expR, unit_price = unit,
            same_sector = if (s.industryX != "Unknown" && s.industryX == s.industryY) "Yes" else "No",
            industry_x = s.industryX, industry_y = s.industryY,
            extreme_z_in_hl = extremeFlag, extreme_z_detail = extremeDetail,
            profitable_since_extreme = profitable, pnl_since_extreme = pnl,
            historical_z_scores = history,
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    internal fun halfLife(spread: DoubleArray): Int {
        val lag = DoubleArray(spread.size) { if (it == 0) spread[0] else spread[it - 1] }
        val ret = DoubleArray(spread.size) { spread[it] - lag[it] }
        val b = if (Stats.std(lag, 0) > 0) Stats.slope(lag, ret) else 0.0
        return if (b != 0.0) max(1, (-ln(2.0) / b).roundToInt()) else 1
    }

    /** pandas rolling(window).mean() — NaN until a full window is available. */
    internal fun rollingMean(a: DoubleArray, w: Int) = DoubleArray(a.size) { i ->
        if (i < w - 1) Double.NaN else { var s = 0.0; for (k in i - w + 1..i) s += a[k]; s / w }
    }

    /** pandas rolling(window).std() (ddof = 1). */
    internal fun rollingStd(a: DoubleArray, w: Int) = DoubleArray(a.size) { i ->
        if (i < w - 1 || w < 2) Double.NaN else Stats.std(a.copyOfRange(i - w + 1, i + 1))
    }

    private fun isClose(a: Double, b: Double) = abs(a - b) <= 1e-8 + 1e-5 * abs(b)
    private fun fmtPx(v: Double) = if (abs(v) >= 1) "%.2f".format(v) else "%.4g".format(v)
    private fun dayFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
}

internal fun Double.roundTo(decimals: Int): Double {
    if (!isFinite()) return this
    val f = 10.0.pow(decimals)
    return (this * f).roundToLong() / f
}
