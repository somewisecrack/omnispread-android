package com.example.omnispread.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * Parity with the Python OmniSpread backend. Fixtures were generated with statsmodels 0.14.6
 * (`coint(trend="c", autolag="AIC")`, `coint_johansen(det_order=0, k_ar_diff=1)`) and the
 * backend's half-life / z-score code, on synthetic series and on 3y of real daily prices.
 */
class StatsParityTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun JSONArray.doubles() = DoubleArray(length()) { getDouble(it) }

    private fun assertPValue(expected: Double, actual: Double, label: String) {
        // p-values agree to ~1e-6; allow a little slack near the 0.05 cutoff is unnecessary
        assertEquals("$label p-value", expected, actual, 1e-4)
    }

    private fun check(o: JSONObject, x: DoubleArray, y: DoubleArray, label: String) {
        assertPValue(o.getDouble("p_yx"), Stats.cointPValue(y, x), "$label y~x")
        assertPValue(o.getDouble("p_xy"), Stats.cointPValue(x, y), "$label x~y")

        val j = Stats.johansen(x, y)!!
        val eig = o.getJSONArray("eig").doubles()
        assertEquals("$label eig0", eig[0], j.eig[0], 1e-7)
        assertEquals("$label eig1", eig[1], j.eig[1], 1e-7)
        val trace = o.getJSONArray("trace").doubles()
        val maxeig = o.getJSONArray("maxeig").doubles()
        assertEquals("$label trace0", trace[0], j.trace[0], 1e-4 * maxOf(1.0, abs(trace[0])))
        assertEquals("$label maxeig0", maxeig[0], j.maxEig[0], 1e-4 * maxOf(1.0, abs(maxeig[0])))
        assertEquals("$label rank", o.getInt("rank"), Stats.johansenRank(j))
        val beta = -j.evec[0][0] / j.evec[0][1]
        assertEquals("$label beta", o.getDouble("beta"), beta, 1e-6 * maxOf(1.0, abs(o.getDouble("beta"))))

        val spread = DoubleArray(x.size) { y[it] - beta * x[it] }
        val engine = OmniSpreadEngine(emptyList())
        val hl = engine.halfLife(spread)
        assertEquals("$label half-life", o.getInt("half_life"), hl)
        if (!o.isNull("z")) {
            val z = (spread.last() - engine.rollingMean(spread, hl).last()) / engine.rollingStd(spread, hl).last()
            assertEquals("$label z", o.getDouble("z"), z, 1e-6)
        }
    }

    @Test
    fun syntheticSeriesMatchStatsmodels() {
        val cases = JSONArray(resource("stats_cases.json"))
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            check(c, c.getJSONArray("x").doubles(), c.getJSONArray("y").doubles(), "synthetic#$i")
        }
    }

    @Test
    fun realPairsMatchPythonBackend() {
        val f = JSONObject(resource("screen_fixture.json"))
        val prices = f.getJSONObject("prices")
        fun series(t: String): Map<Long, Double> {
            val a = prices.getJSONArray(t)
            return (0 until a.length()).associate { a.getJSONArray(it).getLong(0) to a.getJSONArray(it).getDouble(1) }
        }
        var checked = 0
        for (basis in listOf("raw", "log")) {
            val rows = f.getJSONObject("pairs").getJSONArray(basis)
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val sx = series(r.getString("x")); val sy = series(r.getString("y"))
                val ts = sx.keys.filter { it in sy }.sorted()
                val log = basis == "log"
                val x = DoubleArray(ts.size) { sx[ts[it]]!!.let { v -> if (log) ln(v) else v } }
                val y = DoubleArray(ts.size) { sy[ts[it]]!!.let { v -> if (log) ln(v) else v } }
                check(r, x, y, "${r.getString("x")}/${r.getString("y")} $basis")
                checked++
            }
        }
        assertTrue(checked >= 150)
    }

    @Test
    fun mackinnonTailsAndLexicon() {
        assertEquals(1.0, Stats.mackinnonPCointN2(1.5), 0.0)
        assertEquals(0.0, Stats.mackinnonPCointN2(-25.0), 0.0)
        assertTrue(FinanceLexicon.score("Apple beats estimates, shares surge to record high") > 0.5)
        assertTrue(FinanceLexicon.score("Exxon misses, cuts outlook as oil prices plunge") < -0.5)
        assertTrue(FinanceLexicon.score("Pfizer shares fail to rally after approval") < 0.5)
        assertEquals(0.0, FinanceLexicon.score("Company to hold annual meeting on Tuesday"), 0.0)
        assertEquals("Apple", NewsSentiment.cleanName("Apple Inc."))
        assertEquals("Exxon Mobil", NewsSentiment.cleanName("Exxon Mobil Corporation"))
    }
}
