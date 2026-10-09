package com.example.omnispread.data

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Full S&P 100 scan over the network (Yahoo). Skipped unless OMNISPREAD_SCAN_OUT is set to a
 * file path; results are written there as TSV for comparison with the Python backend.
 */
class Sp100ScanLiveTest {

    @Test
    fun scanSp100() {
        val out = System.getenv("OMNISPREAD_SCAN_OUT")
        assumeTrue("set OMNISPREAD_SCAN_OUT to run", out != null)
        val basis = System.getenv("OMNISPREAD_BASIS") ?: "raw"
        val period = System.getenv("OMNISPREAD_PERIOD") ?: "3y"
        val t0 = System.currentTimeMillis()
        val res = OmniSpreadEngine(SP100.keys.toList(), period = period, priceBasis = basis).runScan()
        val secs = (System.currentTimeMillis() - t0) / 1000.0
        File(out!!).writeText(buildString {
            appendLine("# ${res.closes.size} series, ${res.results.size} pairs, ${"%.1f".format(secs)}s")
            res.results.forEach { r ->
                appendLine(listOf(r.x, r.y, "%.6f".format(r.cadf_pvalue), r.johansen_rank, r.z_score, r.half_life, "%.6f".format(r.qty)).joinToString("\t"))
            }
        })
    }
}
