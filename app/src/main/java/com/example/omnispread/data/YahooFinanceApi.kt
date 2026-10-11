package com.example.omnispread.data

import okhttp3.Request
import org.json.JSONObject

object YahooFinanceApi {

    private val client = Net.client(connectSecs = 15, readSecs = 60)

    /**
     * Fetches adjusted-close prices for [symbol] from Yahoo Finance chart API.
     * Returns a list of (unix_timestamp_seconds, price) sorted by timestamp.
     */
    fun fetchPrices(symbol: String, range: String = "3y", interval: String = "1d"): List<Pair<Long, Double>> =
        fetchUrl("https://query1.finance.yahoo.com/v8/finance/chart/$symbol" +
            "?range=$range&interval=$interval&events=history&includeAdjustedClose=true")

    /** Prices between two unix timestamps (seconds), e.g. for forward backtests from a past date. */
    fun fetchPricesBetween(symbol: String, startSec: Long, endSec: Long, interval: String = "1d"): List<Pair<Long, Double>> =
        fetchUrl("https://query1.finance.yahoo.com/v8/finance/chart/$symbol" +
            "?period1=$startSec&period2=$endSec&interval=$interval&events=history&includeAdjustedClose=true")

    private fun fetchUrl(url: String): List<Pair<Long, Double>> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .build()
        return try {
            val body = client.newCall(request).execute().use { it.body?.string() } ?: return emptyList()
            parsePrices(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePrices(json: String): List<Pair<Long, Double>> {
        return try {
            val root = JSONObject(json)
            val result = root.getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
            val timestamps = result.getJSONArray("timestamp")
            val indicators = result.getJSONObject("indicators")

            // Prefer adjclose; fall back to close
            val priceArr = try {
                indicators.getJSONArray("adjclose")
                    .getJSONObject(0)
                    .getJSONArray("adjclose")
            } catch (_: Exception) {
                indicators.getJSONArray("quote")
                    .getJSONObject(0)
                    .getJSONArray("close")
            }

            // Intraday responses carry each day's regular-session window. Yahoo also returns a bar
            // stamped exactly at the session close (e.g. 16:00 ET, or 13:00 on half-days); yfinance —
            // which the OmniSpread/TastySpread backends use — drops it, so drop it here too.
            val sessionEnds = HashSet<Long>()
            result.optJSONObject("meta")?.optJSONArray("tradingPeriods")?.let { days ->
                for (d in 0 until days.length()) {
                    val periods = days.optJSONArray(d) ?: continue
                    for (k in 0 until periods.length()) periods.optJSONObject(k)?.optLong("end")?.let(sessionEnds::add)
                }
            }

            val pairs = mutableListOf<Pair<Long, Double>>()
            for (i in 0 until timestamps.length()) {
                if (priceArr.isNull(i) || timestamps.getLong(i) in sessionEnds) continue
                val price = priceArr.getDouble(i)
                if (price.isFinite() && price > 0) {
                    pairs.add(timestamps.getLong(i) to price)
                }
            }
            pairs.sortedBy { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Company name from the chart endpoint's metadata (longName, else shortName). */
    fun fetchName(symbol: String): String? {
        val request = Request.Builder()
            .url("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?range=1d&interval=1d")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .build()
        return try {
            val body = client.newCall(request).execute().use { it.body?.string() } ?: return null
            val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            meta.optString("longName").ifBlank { meta.optString("shortName") }.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /** Fetch prices as a map keyed by date-string "yyyy-MM-dd" for alignment across tickers. */
    fun fetchPriceMap(symbol: String, range: String = "3y", interval: String = "1d"): Map<String, Double> {
        return fetchPrices(symbol, range, interval).associate { (ts, price) ->
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(ts * 1000L))
            date to price
        }
    }
}
