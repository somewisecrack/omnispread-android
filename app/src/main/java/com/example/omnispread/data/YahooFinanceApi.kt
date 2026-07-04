package com.example.omnispread.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YahooFinanceApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches adjusted-close prices for [symbol] from Yahoo Finance chart API.
     * Returns a list of (unix_timestamp_seconds, price) sorted by timestamp.
     */
    fun fetchPrices(symbol: String, range: String = "3y", interval: String = "1d"): List<Pair<Long, Double>> {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol" +
            "?range=$range&interval=$interval&events=history&includeAdjustedClose=true"
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

            val pairs = mutableListOf<Pair<Long, Double>>()
            for (i in 0 until timestamps.length()) {
                if (priceArr.isNull(i)) continue
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
