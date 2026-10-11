package com.example.omnispread.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistical tests used by the v2 screen, ported from statsmodels so results
 * match the Python OmniSpread backend:
 *  - `coint`         = statsmodels.tsa.stattools.coint(trend="c", autolag="AIC")
 *  - `johansen`      = statsmodels coint_johansen(det_order=0, k_ar_diff=1)
 */
object Stats {

    // ── Small dense linear algebra ──────────────────────────────────────────

    /** OLS fit returning coefficients, residuals and coefficient standard errors. */
    class OlsFit(val params: DoubleArray, val resid: DoubleArray, val bse: DoubleArray) {
        val ssr: Double get() = resid.sumOf { it * it }
    }

    fun ols(X: Array<DoubleArray>, y: DoubleArray): OlsFit? {
        val n = X.size
        if (n == 0) return null
        val k = X[0].size
        val xtx = Array(k) { i -> DoubleArray(k) { j -> var s = 0.0; for (r in 0 until n) s += X[r][i] * X[r][j]; s } }
        val xty = DoubleArray(k) { i -> var s = 0.0; for (r in 0 until n) s += X[r][i] * y[r]; s }
        val inv = invert(xtx) ?: return null
        val b = DoubleArray(k) { i -> var s = 0.0; for (j in 0 until k) s += inv[i][j] * xty[j]; s }
        val resid = DoubleArray(n) { r -> var f = 0.0; for (j in 0 until k) f += X[r][j] * b[j]; y[r] - f }
        val dof = max(1, n - k)
        val sigma2 = resid.sumOf { it * it } / dof
        val bse = DoubleArray(k) { sqrt(max(0.0, inv[it][it] * sigma2)) }
        return OlsFit(b, resid, bse)
    }

    /** Gauss-Jordan inverse with partial pivoting; null if singular. */
    fun invert(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(2 * n) { j -> if (j < n) m[i][j] else if (j - n == i) 1.0 else 0.0 } }
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(a[it][col]) }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            if (abs(a[col][col]) < 1e-300) return null
            val s = a[col][col]
            for (j in 0 until 2 * n) a[col][j] /= s
            for (r in 0 until n) {
                if (r == col) continue
                val f = a[r][col]
                if (f != 0.0) for (j in 0 until 2 * n) a[r][j] -= f * a[col][j]
            }
        }
        return Array(n) { i -> DoubleArray(n) { j -> a[i][j + n] } }
    }

    // ── Normal CDF (Abramowitz–Stegun erf, |err| < 1.5e-7) ──────────────────

    fun normCdf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.3275911 * abs(x) / sqrt(2.0))
        val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
        val erf = 1.0 - poly * exp(-x * x / 2.0)
        return if (x >= 0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
    }

    // ── MacKinnon (1994) approximate p-value, regression "c", N = 2 ─────────

    private val TAU_SMALLP_C_N2 = doubleArrayOf(2.92, 1.5012, 0.039796)
    private val TAU_LARGEP_C_N2 = doubleArrayOf(2.1945, 0.64695, -0.29198, -0.042377)
    private const val TAU_MAX_C_N2 = 0.92
    private const val TAU_MIN_C_N2 = -18.86
    private const val TAU_STAR_C_N2 = -2.62

    fun mackinnonPCointN2(stat: Double): Double {
        if (stat.isNaN()) return 1.0
        if (stat > TAU_MAX_C_N2) return 1.0
        if (stat < TAU_MIN_C_N2) return 0.0
        val c = if (stat <= TAU_STAR_C_N2) TAU_SMALLP_C_N2 else TAU_LARGEP_C_N2
        var v = 0.0
        for (i in c.indices.reversed()) v = v * stat + c[i]
        return normCdf(v)
    }

    // ── ADF with no deterministic terms and AIC lag selection ───────────────

    /** ADF t-statistic on [x] with regression="n", autolag="AIC" (as used inside coint). */
    fun adfStatNoConstAic(x: DoubleArray): Double {
        val nobs0 = x.size
        var maxlag = ceil(12.0 * (nobs0 / 100.0).pow(0.25)).toInt()
        maxlag = min(nobs0 / 2 - 1, maxlag)
        if (maxlag < 0) return Double.NaN
        val xdiff = DoubleArray(nobs0 - 1) { x[it + 1] - x[it] }

        // Design with `lags` lagged differences, trimmed to a common sample.
        fun design(lags: Int, trimTo: Int): Pair<Array<DoubleArray>, DoubleArray> {
            // rows correspond to t = trimTo .. xdiff.size-1 (index into xdiff)
            val rows = xdiff.size - trimTo
            val X = Array(rows) { r ->
                val t = r + trimTo
                DoubleArray(lags + 1) { c -> if (c == 0) x[t] else xdiff[t - c] }
            }
            val y = DoubleArray(rows) { xdiff[it + trimTo] }
            return X to y
        }

        // AIC search on the common sample defined by maxlag.
        var bestLag = 0
        var bestAic = Double.POSITIVE_INFINITY
        for (lags in 0..maxlag) {
            val (X, y) = design(lags, maxlag)
            val fit = ols(X, y) ?: continue
            val n = y.size.toDouble()
            val llf = -n / 2.0 * (ln(2.0 * Math.PI) + ln(fit.ssr / n) + 1.0)
            val aic = -2.0 * llf + 2.0 * (lags + 1)
            if (aic < bestAic) { bestAic = aic; bestLag = lags }
        }

        // Refit with the selected lag on its full available sample.
        val (X, y) = design(bestLag, bestLag)
        val fit = ols(X, y) ?: return Double.NaN
        if (fit.bse[0] == 0.0) return Double.NaN
        return fit.params[0] / fit.bse[0]
    }

    /** Engle–Granger p-value: regress y0 on [y1, 1], ADF on residuals, MacKinnon N=2. */
    fun cointPValue(y0: DoubleArray, y1: DoubleArray): Double {
        val n = y0.size
        val X = Array(n) { doubleArrayOf(y1[it], 1.0) }
        val fit = ols(X, y0) ?: return 1.0
        val mean = y0.average()
        val sst = y0.sumOf { (it - mean) * (it - mean) }
        val r2 = if (sst > 0) 1.0 - fit.ssr / sst else 1.0
        if (r2 >= 1 - 100 * 1.4901161193847656e-8) return 0.0 // perfectly collinear → stat = -inf
        return mackinnonPCointN2(adfStatNoConstAic(fit.resid))
    }

    // ── Johansen (2 series, det_order = 0, k_ar_diff = 1) ───────────────────

    class JohansenResult(
        val eig: DoubleArray,        // descending
        val evec: Array<DoubleArray>, // evec[i] = eigenvector for eig[i], components (x, y)
        val trace: DoubleArray,      // lr1
        val maxEig: DoubleArray,     // lr2
    )

    // 95% critical values (c_sjt / c_sja, det_order = 0)
    private const val TRACE_CV_R0 = 15.4943
    private const val TRACE_CV_R1 = 3.8415
    private const val MAXEIG_CV_R0 = 14.2639

    fun johansen(x: DoubleArray, y: DoubleArray): JohansenResult? {
        val n = x.size
        if (n < 10) return null
        val mx = x.average(); val my = y.average()
        val e = Array(n) { doubleArrayOf(x[it] - mx, y[it] - my) }       // detrend order 0
        val dx = Array(n - 1) { doubleArrayOf(e[it + 1][0] - e[it][0], e[it + 1][1] - e[it][1]) }
        // z = lagged diffs (row t -> dx[t-1]), aligned with dx[1:]
        val m = n - 2
        fun demean(a: Array<DoubleArray>): Array<DoubleArray> {
            val c0 = a.sumOf { it[0] } / a.size; val c1 = a.sumOf { it[1] } / a.size
            return Array(a.size) { doubleArrayOf(a[it][0] - c0, a[it][1] - c1) }
        }
        val z = demean(Array(m) { dx[it] })
        val d0 = demean(Array(m) { dx[it + 1] })
        val lx = demean(Array(m) { e[it + 1] })

        // Residual of each column of A on z (no constant; z already demeaned)
        val ztz = arrayOf(
            doubleArrayOf(z.sumOf { it[0] * it[0] }, z.sumOf { it[0] * it[1] }),
            doubleArrayOf(z.sumOf { it[1] * it[0] }, z.sumOf { it[1] * it[1] }),
        )
        val ztzInv = invert(ztz) ?: return null
        fun resid(a: Array<DoubleArray>): Array<DoubleArray> {
            val zta = Array(2) { i -> DoubleArray(2) { j -> var s = 0.0; for (r in 0 until m) s += z[r][i] * a[r][j]; s } }
            val b = Array(2) { i -> DoubleArray(2) { j -> ztzInv[i][0] * zta[0][j] + ztzInv[i][1] * zta[1][j] } }
            return Array(m) { r -> DoubleArray(2) { j -> a[r][j] - (z[r][0] * b[0][j] + z[r][1] * b[1][j]) } }
        }
        val r0 = resid(d0)
        val rk = resid(lx)
        fun cross(a: Array<DoubleArray>, b: Array<DoubleArray>) =
            Array(2) { i -> DoubleArray(2) { j -> var s = 0.0; for (r in 0 until m) s += a[r][i] * b[r][j]; s / m } }
        val skk = cross(rk, rk)
        val sk0 = cross(rk, r0)
        val s00 = cross(r0, r0)
        val s00Inv = invert(s00) ?: return null
        val skkInv = invert(skk) ?: return null
        fun mul(a: Array<DoubleArray>, b: Array<DoubleArray>) =
            Array(2) { i -> DoubleArray(2) { j -> a[i][0] * b[0][j] + a[i][1] * b[1][j] } }
        val sk0T = arrayOf(doubleArrayOf(sk0[0][0], sk0[1][0]), doubleArrayOf(sk0[0][1], sk0[1][1]))
        val M = mul(skkInv, mul(sk0, mul(s00Inv, sk0T)))

        // Eigen-decomposition of a general 2×2 (eigenvalues are real here).
        val tr = M[0][0] + M[1][1]
        val det = M[0][0] * M[1][1] - M[0][1] * M[1][0]
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc
        val l2 = tr / 2 - disc
        fun evec(l: Double): DoubleArray {
            val a = doubleArrayOf(M[0][1], l - M[0][0])
            val b = doubleArrayOf(l - M[1][1], M[1][0])
            val v = if (abs(a[0]) + abs(a[1]) >= abs(b[0]) + abs(b[1])) a else b
            val norm = sqrt(v[0] * v[0] + v[1] * v[1])
            return if (norm == 0.0) doubleArrayOf(1.0, 0.0) else doubleArrayOf(v[0] / norm, v[1] / norm)
        }
        val eig = doubleArrayOf(l1, l2)
        val t = m.toDouble()
        val trace = doubleArrayOf(-t * (ln(1 - l1) + ln(1 - l2)), -t * ln(1 - l2))
        val maxEig = doubleArrayOf(-t * ln(1 - l1), -t * ln(1 - l2))
        return JohansenResult(eig, arrayOf(evec(l1), evec(l2)), trace, maxEig)
    }

    /** Estimated rank (0, 1 or 2) at 95%, matching OmniSpreadEngine.johansen_rank. */
    fun johansenRank(j: JohansenResult): Int {
        val r0Rejected = j.trace[0] > TRACE_CV_R0 && j.maxEig[0] > MAXEIG_CV_R0
        if (!r0Rejected) return 0
        return if (j.trace[1] > TRACE_CV_R1) 2 else 1
    }

    // ── Misc ────────────────────────────────────────────────────────────────

    fun std(a: DoubleArray, ddof: Int = 1): Double {
        if (a.size <= ddof) return Double.NaN
        val mean = a.average()
        return sqrt(a.sumOf { (it - mean) * (it - mean) } / (a.size - ddof))
    }

    fun corr(a: DoubleArray, b: DoubleArray): Double {
        if (a.size < 2) return 0.0
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) { val p = a[i] - ma; val q = b[i] - mb; num += p * q; da += p * p; db += q * q }
        return if (da == 0.0 || db == 0.0) 0.0 else num / sqrt(da * db)
    }

    /** Slope of a degree-1 least-squares fit. */
    fun slope(x: DoubleArray, y: DoubleArray): Double {
        val n = x.size.toDouble()
        val sx = x.sum(); val sy = y.sum()
        var sxx = 0.0; var sxy = 0.0
        for (i in x.indices) { sxx += x[i] * x[i]; sxy += x[i] * y[i] }
        val d = n * sxx - sx * sx
        return if (d == 0.0) 0.0 else (n * sxy - sx * sy) / d
    }

    /** Annualised close-to-close realised volatility (%) over the last [days] returns. */
    fun realizedVolPct(prices: DoubleArray, days: Int): Double {
        if (prices.size < days + 1) return Double.NaN
        val tail = prices.copyOfRange(prices.size - days - 1, prices.size)
        val rets = DoubleArray(days) { ln(tail[it + 1] / tail[it]) }
        return std(rets) * sqrt(252.0) * 100.0
    }
}
