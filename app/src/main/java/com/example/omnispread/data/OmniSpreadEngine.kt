package com.example.omnispread.data

import kotlin.math.*

// ─────────────────────────────────────────────────────────
//  Internal data holders (not exposed outside engine)
// ─────────────────────────────────────────────────────────

private data class ScreenedPair(
    val x: String,
    val y: String,
    val betaTs: DoubleArray,      // Kalman beta series aligned with spread
    val spread: DoubleArray,
    val timestamps: LongArray,
    val px: Double,
    val py: Double,
    val qty: Double,
    val direction: String,
    val method: String,
    val priceCorr: Double,
    val returnCorr: Double,
    val comboStr: String,
    val halfLife: Int,
)

// ─────────────────────────────────────────────────────────
//  Matrix / linear algebra helpers (no external deps)
// ─────────────────────────────────────────────────────────

private object LA {

    /** Standard deviation (ddof=1) */
    fun std(a: DoubleArray, ddof: Int = 1): Double {
        if (a.size <= ddof) return 0.0
        val mean = a.average()
        return sqrt(a.sumOf { (it - mean).pow(2) } / (a.size - ddof))
    }

    /** Degree-1 polyfit — returns slope of best-fit line through (x, y). */
    fun polyfit1(x: DoubleArray, y: DoubleArray): Double {
        val n = x.size.toDouble()
        val sx = x.sum(); val sy = y.sum()
        val sxx = x.sumOf { it * it }
        val sxy = x.indices.sumOf { x[it] * y[it] }
        val denom = n * sxx - sx * sx
        return if (denom == 0.0) 0.0 else (n * sxy - sx * sy) / denom
    }

    /** Pearson correlation */
    fun corr(a: DoubleArray, b: DoubleArray): Double {
        if (a.size < 2) return 0.0
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) { val ai = a[i] - ma; val bi = b[i] - mb; num += ai * bi; da += ai * ai; db += bi * bi }
        return if (da == 0.0 || db == 0.0) 0.0 else num / sqrt(da * db)
    }

    /** Multiply two matrices (row-major 2-D arrays) */
    fun matMul(A: Array<DoubleArray>, B: Array<DoubleArray>): Array<DoubleArray> {
        val rows = A.size; val inner = B.size; val cols = B[0].size
        return Array(rows) { i -> DoubleArray(cols) { j -> (0 until inner).sumOf { k -> A[i][k] * B[k][j] } } }
    }

    /** Transpose a matrix */
    fun T(A: Array<DoubleArray>): Array<DoubleArray> =
        Array(A[0].size) { j -> DoubleArray(A.size) { i -> A[i][j] } }

    /** Invert a 2×2 matrix; returns null if singular. */
    fun inv2(m: Array<DoubleArray>): Array<DoubleArray>? {
        val det = m[0][0] * m[1][1] - m[0][1] * m[1][0]
        if (abs(det) < 1e-15) return null
        val inv = 1.0 / det
        return arrayOf(
            doubleArrayOf(m[1][1] * inv, -m[0][1] * inv),
            doubleArrayOf(-m[1][0] * inv, m[0][0] * inv),
        )
    }

    /** Invert an n×n matrix via Gaussian elimination with partial pivoting. */
    fun inv(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(2 * n) { j -> if (j < n) m[i][j] else if (j - n == i) 1.0 else 0.0 } }
        for (col in 0 until n) {
            val pivot = (col until n).maxByOrNull { abs(a[it][col]) } ?: return null
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            if (abs(a[col][col]) < 1e-14) return null
            val scale = a[col][col]
            for (j in 0 until 2 * n) a[col][j] /= scale
            for (row in 0 until n) {
                if (row == col) continue
                val factor = a[row][col]
                for (j in 0 until 2 * n) a[row][j] -= factor * a[col][j]
            }
        }
        return Array(n) { i -> DoubleArray(n) { j -> a[i][j + n] } }
    }

    /**
     * OLS: solve β = (X'X)^{-1} X'y.
     * X is nObs×nParams (row-major), y is nObs.
     * Returns null if singular.
     */
    fun ols(X: Array<DoubleArray>, y: DoubleArray): DoubleArray? {
        val Xt = T(X)
        val XtX = matMul(Xt, X)
        val XtXInv = inv(XtX) ?: return null
        val Xty = DoubleArray(X[0].size) { j -> X.indices.sumOf { i -> Xt[j][i] * y[i] } }
        return DoubleArray(X[0].size) { j -> XtXInv[j].indices.sumOf { k -> XtXInv[j][k] * Xty[k] } }
    }

    /**
     * Eigenvalues and eigenvectors of a 2×2 symmetric matrix (closed-form).
     * Returns Pair(eigenvalues sorted descending, eigenvectors as columns).
     */
    fun eig2(m: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val a = m[0][0]; val b = m[0][1]; val d = m[1][1]
        val trace = a + d
        val disc = sqrt(max(0.0, (a - d).pow(2) + 4 * b * b))
        val l1 = (trace + disc) / 2.0
        val l2 = (trace - disc) / 2.0
        // Eigenvectors
        fun evec(lam: Double): DoubleArray {
            return if (abs(b) > 1e-14) {
                val v = doubleArrayOf(b, lam - a)
                val norm = sqrt(v[0].pow(2) + v[1].pow(2))
                doubleArrayOf(v[0] / norm, v[1] / norm)
            } else {
                if (abs(a - lam) < abs(d - lam)) doubleArrayOf(1.0, 0.0) else doubleArrayOf(0.0, 1.0)
            }
        }
        val e1 = evec(l1); val e2 = evec(l2)
        // Return eigenvectors as columns of a 2×2 matrix
        return Pair(doubleArrayOf(l1, l2), arrayOf(doubleArrayOf(e1[0], e2[0]), doubleArrayOf(e1[1], e2[1])))
    }
}

// ─────────────────────────────────────────────────────────
//  OmniSpreadEngine
// ─────────────────────────────────────────────────────────

class OmniSpreadEngine(
    private val tickers: List<String>,
    private val period: String = "3y",
    private val interval: String = "1d",
    private val startDate: String? = null,
    private val endDate: String? = null,
    private val onProgress: (String) -> Unit = {},
) {

    companion object {
        private const val ENSEMBLE_M = 80
        private const val SIMS_PER_DRAW = 2000
        private const val BLOCK_LEN_FACTOR = 0.25
        private const val Z_SCORE_LIMIT = 2.0
        private const val ADF_P_THRESHOLD = 0.10
        private const val HURST_LIMIT = 0.45
        private const val RNG_SEED = 42L

        // MacKinnon (1994) 10% critical value for ADF with constant, large sample
        private const val ADF_CV_10PCT = -2.5671
    }

    // ── Hurst exponent ──────────────────────────────────────────────────────

    private fun hurst(ts: DoubleArray): Double {
        if (ts.size < 20) return Double.NaN
        val maxLag = min(100, ts.size - 1)
        val lags = (2..maxLag).toList()
        val tau = lags.map { lag ->
            val diffs = DoubleArray(ts.size - lag) { ts[it + lag] - ts[it] }
            LA.std(diffs)
        }
        val logLags = lags.map { ln(it.toDouble()) }.toDoubleArray()
        val logTau = tau.map { ln(it) }.toDoubleArray()
        return LA.polyfit1(logLags, logTau) * 2.0
    }

    // ── Kalman filter beta series ──────────────────────────────────────────

    private fun kalmanBetaSeries(x: DoubleArray, y: DoubleArray, beta0: Double): DoubleArray {
        val n = x.size
        var beta = beta0
        var P = 1.0
        val Q = LA.std(y.mapIndexed { i, yi -> yi - beta0 * x[i] }.toDoubleArray()).pow(2)
        val R = 1e-5
        return DoubleArray(n) { t ->
            P += R
            val H = x[t]
            val resid = y[t] - H * beta
            val S = H * P * H + Q
            val K = P * H / S
            beta += K * resid
            P *= (1 - K * H)
            beta
        }
    }

    // ── ADF test: returns p-value (approximate via MacKinnon threshold) ────

    private fun adfTest(series: DoubleArray, nLags: Int = 1): Double {
        val n = series.size
        val needed = nLags + 3
        if (n < needed) return 1.0

        val dy = DoubleArray(n - 1) { series[it + 1] - series[it] }
        val nObs = n - 1 - nLags
        val k = 2 + nLags  // intercept + lagged level + nLags lagged diffs

        val X = Array(nObs) { i ->
            DoubleArray(k).also { row ->
                row[0] = 1.0
                row[1] = series[i + nLags]
                for (lag in 1..nLags) row[1 + lag] = dy[i + nLags - lag]
            }
        }
        val Y = DoubleArray(nObs) { dy[it + nLags] }

        val beta = LA.ols(X, Y) ?: return 1.0
        val fitted = DoubleArray(nObs) { i -> X[i].indices.sumOf { j -> X[i][j] * beta[j] } }
        val resid = DoubleArray(nObs) { Y[it] - fitted[it] }
        val sigma2 = resid.sumOf { it * it } / max(1, nObs - k).toDouble()

        // Standard error of beta[1] (lagged level coefficient)
        val XtX = LA.matMul(LA.T(X), X)
        val XtXInv = LA.inv(XtX) ?: return 1.0
        val se = sqrt(XtXInv[1][1] * sigma2)
        if (se == 0.0) return 1.0
        val tStat = beta[1] / se

        // Approximate MacKinnon p-value:
        // We only care whether p < 0.10, so threshold at ADF_CV_10PCT
        return if (tStat < ADF_CV_10PCT) 0.05 else 0.50
    }

    // ── Johansen cointegration test (2-variable, det_order=0, k_ar_diff=1) ─

    private data class JohansenResult(
        val pass: Boolean,
        val beta0: Double,   // cointegrating coefficient if pass
    )

    private fun johansenTest(xPrices: DoubleArray, yPrices: DoubleArray): JohansenResult {
        val fail = JohansenResult(false, 0.0)
        // Stack into T×2 matrix
        val T = xPrices.size
        if (T < 10) return fail

        // dY: (T-1)×2
        val dY = Array(T - 1) { t -> doubleArrayOf(xPrices[t + 1] - xPrices[t], yPrices[t + 1] - yPrices[t]) }
        val Y  = Array(T) { t -> doubleArrayOf(xPrices[t], yPrices[t]) }

        // With k_ar_diff=1: Z0 = dY[1:], Z1 = Y[1:T-1], Z2 = dY[0:T-2]
        val nObs = T - 2
        if (nObs < 4) return fail

        val Z0 = Array(nObs) { i -> dY[i + 1] }
        val Z1 = Array(nObs) { i -> Y[i + 1] }
        val Z2 = Array(nObs) { i -> dY[i] }  // 1-column "lag-difference" predictor

        // Partial-out Z2 from Z0 and Z1 via OLS
        fun partialOut(Z: Array<DoubleArray>): Array<DoubleArray> {
            // Regress each column of Z on Z2
            return Array(Z[0].size) { col ->
                val y = DoubleArray(nObs) { Z[it][col] }
                val B = LA.ols(Z2, y) ?: return@Array y
                val fitted = DoubleArray(nObs) { i -> Z2[i].indices.sumOf { j -> Z2[i][j] * B[j] } }
                DoubleArray(nObs) { y[it] - fitted[it] }
            }.let { cols ->
                // Transpose back to nObs×p
                Array(nObs) { i -> DoubleArray(cols.size) { j -> cols[j][i] } }
            }
        }

        val R0 = partialOut(Z0)
        val R1 = partialOut(Z1)

        // Sij = Ri' Rj / nObs  (2×2 matrices)
        fun scatter(A: Array<DoubleArray>, B: Array<DoubleArray>): Array<DoubleArray> {
            val p = A[0].size; val q = B[0].size
            return Array(p) { r -> DoubleArray(q) { c ->
                (0 until nObs).sumOf { i -> A[i][r] * B[i][c] } / nObs
            }}
        }

        val S00 = scatter(R0, R0)
        val S01 = scatter(R0, R1)
        val S10 = scatter(R1, R0)
        val S11 = scatter(R1, R1)

        val S11Inv = LA.inv2(S11) ?: return fail
        val S00Inv = LA.inv2(S00) ?: return fail

        // M = S11^{-1} * S10 * S00^{-1} * S01
        val M = LA.matMul(LA.matMul(LA.matMul(S11Inv, S10), S00Inv), S01)

        // Eigenvalues & eigenvectors of M (2×2 symmetric — use symmetrized M for stability)
        val Msym = arrayOf(
            doubleArrayOf(M[0][0], (M[0][1] + M[1][0]) / 2),
            doubleArrayOf((M[0][1] + M[1][0]) / 2, M[1][1]),
        )
        val (eigenvalues, eigenvectors) = LA.eig2(Msym)

        // Clamp eigenvalues to [0, 1) to avoid log(negative)
        val lam = eigenvalues.map { it.coerceIn(0.0, 0.9999) }
        val n = nObs.toDouble()

        // Trace statistic: -n * sum(ln(1-lam_i)) for i=r..p-1
        val trace0 = -n * (ln(1 - lam[0]) + ln(1 - lam[1]))  // r=0
        val trace1 = -n * ln(1 - lam[1])                       // r=1
        // Max-eigenvalue statistic: -n * ln(1-lam_r)
        val maxe0 = -n * ln(1 - lam[0])                        // r=0
        val maxe1 = -n * ln(1 - lam[1])                        // r=1

        // 5% critical values for p=2, det_order=0 (no constant in CE)
        val traceCv0 = 15.41; val traceCv1 = 3.76
        val maxeCv0  = 14.07; val maxeCv1  = 3.76

        val pass = (trace0 > traceCv0 && maxe0 > maxeCv0) ||
                   (trace1 > traceCv1 && maxe1 > maxeCv1)
        if (!pass) return fail

        // Cointegrating vector from eigenvector of largest eigenvalue
        val v1 = eigenvectors[0][0]
        val v2 = eigenvectors[1][0]
        val beta0j = if (abs(v2) > 1e-10) -v1 / v2 else 0.0
        return JohansenResult(true, beta0j)
    }

    // ── Screen a single pair ───────────────────────────────────────────────

    private fun screenPair(
        xSym: String, xPrices: DoubleArray, xTs: LongArray,
        ySym: String, yPrices: DoubleArray, yTs: LongArray,
    ): ScreenedPair? {
        // Align on common timestamps
        val xMap = xTs.zip(xPrices.toList()).toMap()
        val yMap = yTs.zip(yPrices.toList()).toMap()
        val common = xTs.filter { it in yMap }.sorted()
        if (common.size < 50) return null

        val tsArr = common.toLongArray()
        val xArr = DoubleArray(common.size) { xMap[common[it]]!! }
        val yArr = DoubleArray(common.size) { yMap[common[it]]!! }

        // Price correlation
        val priceCorr = (LA.corr(xArr, yArr) * 100).roundTo(2) / 100

        // Return series for correlation
        val xRet = DoubleArray(xArr.size - 1) { (xArr[it + 1] - xArr[it]) / xArr[it] }
        val yRet = DoubleArray(yArr.size - 1) { (yArr[it + 1] - yArr[it]) / yArr[it] }

        var cadfPass = false
        var johansen_pass = false
        var betaTs = DoubleArray(0)
        var spread = DoubleArray(0)
        var beta0J = 0.0

        // ── CADF with Kalman ───────────────────────────────────────────────
        try {
            // Simple OLS: y = a + β*x
            val Xols = Array(xArr.size) { i -> doubleArrayOf(1.0, xArr[i]) }
            val bOls = LA.ols(Xols, yArr)
            if (bOls != null) {
                val beta0 = bOls[1]
                betaTs = kalmanBetaSeries(xArr, yArr, beta0)
                spread = DoubleArray(xArr.size) { i -> yArr[i] - betaTs[i] * xArr[i] }
                val pval = adfTest(spread)
                if (pval < ADF_P_THRESHOLD) cadfPass = true
            }
        } catch (_: Exception) { cadfPass = false }

        // ── Johansen ───────────────────────────────────────────────────────
        val jr = try { johansenTest(xArr, yArr) } catch (_: Exception) { JohansenResult(false, 0.0) }
        johansen_pass = jr.pass

        if (!cadfPass && !johansen_pass) return null

        // ── Determine spread using best method ────────────────────────────
        val method = when {
            cadfPass && johansen_pass -> "Both"
            cadfPass -> "CADF"
            else -> {
                beta0J = jr.beta0
                try {
                    betaTs = kalmanBetaSeries(xArr, yArr, beta0J)
                    spread = DoubleArray(xArr.size) { i -> yArr[i] - betaTs[i] * xArr[i] }
                } catch (_: Exception) { return null }
                "Johansen"
            }
        }

        // ── Metrics for filtering ─────────────────────────────────────────
        val lag  = DoubleArray(spread.size) { if (it == 0) spread[0] else spread[it - 1] }
        val ret  = DoubleArray(spread.size) { spread[it] - lag[it] }
        val b    = if (LA.std(lag) > 0) LA.polyfit1(lag, ret) else 0.0
        val hl   = if (b != 0.0) max(1, (-ln(2.0) / b).roundToInt()) else 1

        val mavg = rollingMean(spread, hl)
        val mstd = rollingStd(spread, hl)

        val lastStd = mstd.last()
        if (lastStd == 0.0 || !lastStd.isFinite()) return null
        val z = ((spread.last() - mavg.last()) / lastStd).roundTo(1)
        val hurstVal = hurst(spread)

        if (!z.isFinite() || abs(z) <= Z_SCORE_LIMIT) return null
        if (!hurstVal.isFinite() || hurstVal >= HURST_LIMIT) return null

        val qty = abs(betaTs.last()).roundTo(2)
        val px  = xArr.last().roundTo(2)
        val py  = yArr.last().roundTo(2)
        val direction = if (z > 0) "SHORT_SPREAD" else "LONG_SPREAD"
        val comboStr  = if (z > 0)
            "Buy $qty of $xSym ($px)  &  Sell 1 of $ySym ($py)"
        else
            "Sell $qty of $xSym ($px)  &  Buy 1 of $ySym ($py)"

        return ScreenedPair(
            x = xSym, y = ySym,
            betaTs = betaTs, spread = spread, timestamps = tsArr,
            px = px, py = py, qty = qty, direction = direction, method = method,
            priceCorr = priceCorr,
            returnCorr = (LA.corr(xRet, yRet) * 100).roundTo(2) / 100,
            comboStr = comboStr, halfLife = hl,
        )
    }

    // ── Block-bootstrap residual sampler ──────────────────────────────────

    private fun blockBootstrap(resid: DoubleArray, horizon: Int, rng: java.util.Random, blockLen: Int): DoubleArray {
        val n = resid.size
        if (n == 0) return DoubleArray(horizon) { rng.nextGaussian() }
        val bl = max(1, blockLen)
        val samples = mutableListOf<Double>()
        while (samples.size < horizon) {
            val start = rng.nextInt(n)
            for (j in 0 until bl) {
                samples.add(resid[(start + j) % n])
                if (samples.size >= horizon) break
            }
        }
        return samples.take(horizon).toDoubleArray()
    }

    // ── AR(1) path simulation ─────────────────────────────────────────────

    private fun simulateAR1(a: Double, phi: Double, r0: Double, eps: DoubleArray): DoubleArray {
        val path = DoubleArray(eps.size + 1)
        path[0] = r0
        for (t in 1..eps.size) path[t] = a + phi * path[t - 1] + eps[t - 1]
        return path
    }

    // ── Ensemble Monte Carlo ───────────────────────────────────────────────

    private fun runEnsembleMC(item: ScreenedPair): PairResult {
        val spread = item.spread
        val betaTs = item.betaTs
        val hl     = item.halfLife
        val px     = item.px
        val py     = item.py
        val qty    = item.qty

        val blockLen = max(1, (hl * BLOCK_LEN_FACTOR).roundToInt())

        // ── Fit AR(1) ───────────────────────────────────────────────────
        val yVals = spread.drop(1).toDoubleArray()
        val xVals = spread.dropLast(1).toDoubleArray()
        val Xc = Array(xVals.size) { i -> doubleArrayOf(1.0, xVals[i]) }
        val model = LA.ols(Xc, yVals) ?: doubleArrayOf(0.0, 0.0)
        val aHat   = model[0]
        val phiHat = model[1].coerceIn(-0.999, 0.999)
        val fitted = DoubleArray(xVals.size) { i -> Xc[i][0] * model[0] + Xc[i][1] * model[1] }
        val resid  = DoubleArray(yVals.size) { yVals[it] - fitted[it] }
        val sigmaHat = LA.std(resid)

        val nObs = yVals.size
        val sse  = resid.sumOf { it * it }
        val mse  = sse / max(1, nObs - 2).toDouble()
        val XtX  = LA.matMul(LA.T(Xc), Xc)
        val XtXInv = LA.inv2(XtX) ?: arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
        val se0  = sqrt(XtXInv[0][0] * mse)
        val se1  = sqrt(XtXInv[1][1] * mse)

        val rng = java.util.Random(RNG_SEED)

        // Rolling stats for z / trade signal
        val mavg = rollingMean(spread, hl)
        val mstd = rollingStd(spread, hl)
        val lastSpread = spread.last()
        val mavgLast   = mavg.last()
        val mstdLast   = if (mstd.last() != 0.0) mstd.last() else 1e-12
        val r0         = lastSpread
        val tradeSign  = if ((r0 - mavgLast) > 0) -1.0 else 1.0

        // ── MC ensemble ────────────────────────────────────────────────
        val pDraws = DoubleArray(ENSEMBLE_M)
        repeat(ENSEMBLE_M) { m ->
            // Parameter uncertainty draw
            val aS   = aHat   + rng.nextGaussian() * se0
            val phiS = (phiHat + rng.nextGaussian() * se1).coerceIn(-0.999, 0.999)

            // Chi2 variance draw (approximate)
            val df   = max(1, nObs - 2).toDouble()
            val chi2 = chiSquareDraw(df, rng)
            val sigS = if (chi2 > 0) sigmaHat * sqrt(df / chi2) else sigmaHat

            var wins = 0
            repeat(SIMS_PER_DRAW) {
                val eps  = blockBootstrap(resid, hl, rng, blockLen)
                val path = simulateAR1(aS, phiS, r0, eps)
                for (t in 1..hl) {
                    if (tradeSign * (path[t] - r0) > 0) { wins++; return@repeat }
                }
            }
            pDraws[m] = wins.toDouble() / SIMS_PER_DRAW
        }

        val pMedian = median(pDraws)
        val pLow    = percentile(pDraws, 5.0)
        val pHigh   = percentile(pDraws, 95.0)

        val zDisplay = ((lastSpread - mavgLast) / mstdLast).roundTo(1)
        val move     = (-zDisplay * mstdLast).roundTo(2)
        val unit     = (abs(betaTs.last() * px) + abs(py)).let { if (it == 0.0) 1.0 else it }
        val expR     = abs((move * 100.0 / unit)).roundTo(1)
        val hurstVal = hurst(spread)

        // ── Extreme-Z tracking ────────────────────────────────────────
        val allZ = DoubleArray(spread.size) { i ->
            if (mstd[i] != 0.0 && mstd[i].isFinite()) (spread[i] - mavg[i]) / mstd[i] else Double.NaN
        }
        val zWindow = allZ.takeLast(hl).filter { it.isFinite() }
        var extremeZInHl = "No"
        var extremeZDetail = ""
        var profitableSinceExtreme = "N/A"
        var pnlSinceExtreme = 0.0

        if (zWindow.isNotEmpty() && zDisplay.isFinite()) {
            val curZ = allZ.last()
            val extZ = if (curZ > 0) zWindow.max()!! else zWindow.min()!!
            if (curZ > 0 && curZ >= extZ * 0.999) extremeZInHl = "Yes"
            if (curZ < 0 && curZ <= extZ * 0.999) extremeZInHl = "Yes"

            // Find index of extreme in last hl window
            val windowStart = max(0, spread.size - hl)
            val extIdx = if (curZ > 0) {
                (windowStart until allZ.size).maxByOrNull { if (allZ[it].isFinite()) allZ[it] else Double.MIN_VALUE } ?: -1
            } else {
                (windowStart until allZ.size).minByOrNull { if (allZ[it].isFinite()) allZ[it] else Double.MAX_VALUE } ?: -1
            }
            if (extIdx >= 0 && item.timestamps.size > extIdx) {
                val extDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date(item.timestamps[extIdx] * 1000L))
                extremeZDetail = "${extZ.roundTo(1)} ($extDate)"

                if (extremeZInHl == "No" && extIdx < spread.size) {
                    val spreadAtExt = spread[extIdx]
                    val tradeAtExt  = if (extZ > 0) -1.0 else 1.0
                    pnlSinceExtreme = (tradeAtExt * (lastSpread - spreadAtExt)).roundTo(2)
                    profitableSinceExtreme = if (pnlSinceExtreme > 0) "Yes" else "No"
                }
            }
        }

        // Historical Z-scores for chart
        val historicalZ = allZ.indices
            .filter { allZ[it].isFinite() && it < item.timestamps.size }
            .map { HistoricalZScore(time = item.timestamps[it], value = allZ[it].roundTo(2)) }

        val xLabel = item.x.replace(".NS", "").replace(".BO", "")
        val yLabel = item.y.replace(".NS", "").replace(".BO", "")

        return PairResult(
            pair = "$xLabel/$yLabel",
            x = item.x, y = item.y,
            qty = qty.safeFinite(),
            direction = item.direction,
            combo = item.comboStr,
            method = item.method,
            price_corr = item.priceCorr.safeFinite(),
            z_score = zDisplay.safeFinite(),
            half_life = hl,
            move_to_mean = move.safeFinite(),
            exp_return = expR.safeFinite(),
            unit_price = unit.safeFinite(),
            hurst = if (hurstVal.isFinite()) hurstVal.roundTo(2) else 0.5,
            prob_profit = (pMedian * 100.0).roundTo(1).safeFinite(),
            prob_profit_low = (pLow * 100.0).roundTo(1).safeFinite(),
            prob_profit_high = (pHigh * 100.0).roundTo(1).safeFinite(),
            same_sector = "N/A",
            extreme_z_in_hl = extremeZInHl,
            extreme_z_detail = extremeZDetail,
            profitable_since_extreme = profitableSinceExtreme,
            pnl_since_extreme = pnlSinceExtreme.safeFinite(),
            historical_z_scores = historicalZ,
        )
    }

    // ── Main scan ────────────────────────────────────────────────────────

    fun runScan(): List<PairResult> {
        onProgress("Fetching price data from Yahoo Finance...")

        // Determine range for Yahoo Finance
        val range = when {
            startDate != null && endDate != null -> "5y"  // fetch generous range, trim below
            else -> period
        }

        // Fetch all tickers
        val priceData = mutableMapOf<String, Pair<LongArray, DoubleArray>>()
        for ((idx, ticker) in tickers.withIndex()) {
            onProgress("Fetching ${idx + 1}/${tickers.size}: $ticker")
            try {
                val raw = YahooFinanceApi.fetchPrices(ticker, range, interval)
                if (raw.size >= 50) {
                    priceData[ticker] = raw.map { it.first }.toLongArray() to raw.map { it.second }.toDoubleArray()
                }
            } catch (_: Exception) {}
            // Small pause to avoid rate-limiting
            if (idx % 5 == 4) Thread.sleep(500)
        }

        val active = tickers.filter { it in priceData }
        if (active.size < 2) return emptyList()

        onProgress("Screening ${active.size * (active.size - 1) / 2} pairs for cointegration...")

        val pairs = mutableListOf<ScreenedPair>()
        val combinations = mutableListOf<Pair<String, String>>()
        for (i in active.indices) for (j in i + 1 until active.size) combinations.add(active[i] to active[j])

        for ((xSym, ySym) in combinations) {
            try {
                val (xTs, xP) = priceData[xSym]!!
                val (yTs, yP) = priceData[ySym]!!
                val sp = screenPair(xSym, xP, xTs, ySym, yP, yTs)
                if (sp != null) pairs.add(sp)
            } catch (_: Exception) {}
        }

        if (pairs.isEmpty()) return emptyList()
        onProgress("Running Monte Carlo on ${pairs.size} cointegrated pairs...")

        val results = pairs.mapNotNull { pair ->
            try { runEnsembleMC(pair) } catch (_: Exception) { null }
        }

        return results.sortedByDescending { it.prob_profit }
    }

    // ─────────────────────────────────────────────────────────
    //  Statistics helpers
    // ─────────────────────────────────────────────────────────

    private fun rollingMean(arr: DoubleArray, window: Int): DoubleArray {
        return DoubleArray(arr.size) { i ->
            val from = max(0, i - window + 1)
            arr.slice(from..i).average()
        }
    }

    private fun rollingStd(arr: DoubleArray, window: Int): DoubleArray {
        return DoubleArray(arr.size) { i ->
            val from = max(0, i - window + 1)
            LA.std(arr.slice(from..i).toDoubleArray())
        }
    }

    private fun median(arr: DoubleArray): Double {
        val sorted = arr.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return 0.0
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun percentile(arr: DoubleArray, pct: Double): Double {
        val sorted = arr.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return 0.0
        val idx = ((pct / 100.0) * (sorted.size - 1)).coerceIn(0.0, (sorted.size - 1).toDouble())
        val lo = sorted[idx.toInt()]
        val hi = sorted[min(idx.toInt() + 1, sorted.size - 1)]
        return lo + (hi - lo) * (idx - idx.toLong())
    }

    /** Approximate chi-squared draw via sum of squared normals (for variance uncertainty). */
    private fun chiSquareDraw(df: Double, rng: java.util.Random): Double {
        var sum = 0.0
        repeat(df.roundToInt().coerceAtLeast(1)) { val z = rng.nextGaussian(); sum += z * z }
        return sum
    }
}

// ─────────────────────────────────────────────────────────
//  Extension helpers
// ─────────────────────────────────────────────────────────

private fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToLong() / factor
}

private fun Double.safeFinite(default: Double = 0.0) = if (this.isFinite()) this else default

private fun Double.roundToInt(): Int = this.roundTo(0).toInt()
