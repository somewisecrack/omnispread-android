package com.example.omnispread.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TastyApiException(val status: Int, message: String) : IOException(message)

/** Strike row of a nested option chain. */
data class ChainStrike(val strike: Double, val call: String, val put: String)
data class ChainExpiry(val date: String, val dte: Int, val type: String, val strikes: List<ChainStrike>)

/** REST quote for one option (Greeks are included by /market-data/by-type). */
data class OptionQuote(
    val symbol: String,
    val bid: Double, val ask: Double, val mid: Double,
    val delta: Double?, val iv: Double?, val openInterest: Int?,
)

/**
 * Minimal tastytrade Open API client.
 * Auth: OAuth2 refresh-token grant → 15-minute bearer token, refreshed lazily.
 * Docs: https://developer.tastytrade.com/
 */
class TastytradeApi(private val cfg: TastyConfig) {

    private val base = if (cfg.sandbox) "https://api.cert.tastyworks.com" else "https://api.tastyworks.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var token: String? = null
    private var tokenExpiry = 0L

    // ── transport ───────────────────────────────────────────────────────────

    private fun auth(): String {
        val now = System.currentTimeMillis()
        token?.let { if (now < tokenExpiry) return it }
        val body = JSONObject()
            .put("grant_type", "refresh_token")
            .put("refresh_token", cfg.refreshToken)
            .put("client_secret", cfg.clientSecret)
            .toString().toRequestBody(JSON)
        val req = Request.Builder().url("$base/oauth/token").post(body).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw TastyApiException(r.code, "tastytrade login failed (${r.code}): ${errorMessage(text)}")
            val t = JSONObject(text).getString("access_token")
            token = t
            tokenExpiry = now + 14 * 60 * 1000
            return t
        }
    }

    private fun call(method: String, path: String, params: Map<String, String> = emptyMap(), json: JSONObject? = null): JSONObject {
        var attempt = 0
        while (true) {
            val url = "$base$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val b = Request.Builder().url(url)
                .header("Authorization", "Bearer ${auth()}")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
            when (method) {
                "GET" -> b.get()
                "POST" -> b.post((json ?: JSONObject()).toString().toRequestBody(JSON))
            }
            client.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                when {
                    r.code == 429 && attempt < 4 -> { Thread.sleep(1000L shl attempt); attempt++; return@use }
                    r.code == 401 && attempt < 1 -> { token = null; attempt++; return@use }
                    !r.isSuccessful -> throw TastyApiException(r.code, "tastytrade ${r.code}: ${errorMessage(text)}")
                    else -> return JSONObject(text)
                }
            }
        }
    }

    private fun get(path: String, params: Map<String, String> = emptyMap()) = call("GET", path, params).optJSONObject("data") ?: JSONObject()

    // ── accounts ────────────────────────────────────────────────────────────

    fun accountNumbers(): List<String> {
        val items = get("/customers/me/accounts").optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { i ->
            val a = items.getJSONObject(i).optJSONObject("account") ?: return@mapNotNull null
            if (a.optBoolean("is-closed")) null else a.optString("account-number")
        }
    }

    // ── market metrics: IV rank/percentile, HV, per-expiry IV, earnings ──────

    fun marketMetrics(symbols: List<String>): Map<String, TickerVol> {
        val out = mutableMapOf<String, TickerVol>()
        symbols.chunked(50).forEach { chunk ->
            val items = get("/market-metrics", mapOf("symbols" to chunk.joinToString(","))).optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val m = items.getJSONObject(i)
                val sym = m.optString("symbol")
                val exps = m.optJSONArray("option-expiration-implied-volatilities") ?: JSONArray()
                val expiries = (0 until exps.length()).mapNotNull { k ->
                    val e = exps.getJSONObject(k)
                    val iv = e.optString("implied-volatility").toDoubleOrNull() ?: return@mapNotNull null
                    val date = e.optString("expiration-date")
                    ExpiryVol(date, daysUntil(date), iv * 100)
                }.filter { it.dte >= 0 }.sortedBy { it.dte }
                out[sym] = TickerVol(
                    symbol = sym,
                    ivIndex = m.pct("implied-volatility-index"),
                    ivRank = m.pct("implied-volatility-index-rank") ?: m.pct("implied-volatility-rank"),
                    ivPercentile = m.pct("implied-volatility-percentile"),
                    hv30 = m.optString("historical-volatility-30-day").toDoubleOrNull(),
                    hv60 = m.optString("historical-volatility-60-day").toDoubleOrNull(),
                    hv90 = m.optString("historical-volatility-90-day").toDoubleOrNull(),
                    liquidityRating = if (m.has("liquidity-rating") && !m.isNull("liquidity-rating")) m.optInt("liquidity-rating") else null,
                    industry = m.optString("industry").ifBlank { null },
                    sector = m.optString("sector").ifBlank { null },
                    nextEarnings = m.optJSONObject("earnings")?.optString("expected-report-date")?.ifBlank { null },
                    expiries = expiries,
                )
            }
        }
        return out
    }

    // ── option chains & quotes ──────────────────────────────────────────────

    fun nestedChain(symbol: String): List<ChainExpiry> {
        val items = get("/option-chains/$symbol/nested").optJSONArray("items") ?: return emptyList()
        val chain = (0 until items.length()).map { items.getJSONObject(it) }
            .firstOrNull { it.optString("option-chain-type") == "Standard" } ?: items.optJSONObject(0) ?: return emptyList()
        val exps = chain.optJSONArray("expirations") ?: return emptyList()
        return (0 until exps.length()).map { i ->
            val e = exps.getJSONObject(i)
            val s = e.optJSONArray("strikes") ?: JSONArray()
            ChainExpiry(
                date = e.optString("expiration-date"),
                dte = e.optInt("days-to-expiration"),
                type = e.optString("expiration-type"),
                strikes = (0 until s.length()).map { k ->
                    val st = s.getJSONObject(k)
                    ChainStrike(st.optString("strike-price").toDouble(), st.optString("call"), st.optString("put"))
                }.sortedBy { it.strike },
            )
        }.sortedBy { it.dte }
    }

    fun equityPrice(symbol: String): Double? {
        val items = get("/market-data/by-type", mapOf("equity" to symbol)).optJSONArray("items") ?: return null
        val q = items.optJSONObject(0) ?: return null
        return q.optString("mark").toDoubleOrNull() ?: q.optString("last").toDoubleOrNull()
    }

    fun optionQuotes(symbols: List<String>): Map<String, OptionQuote> {
        val out = mutableMapOf<String, OptionQuote>()
        symbols.distinct().chunked(100).forEach { chunk ->
            val items = get("/market-data/by-type", mapOf("equity-option" to chunk.joinToString(","))).optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val q = items.getJSONObject(i)
                val bid = q.optString("bid").toDoubleOrNull() ?: 0.0
                val ask = q.optString("ask").toDoubleOrNull() ?: 0.0
                out[q.optString("symbol")] = OptionQuote(
                    symbol = q.optString("symbol"),
                    bid = bid, ask = ask,
                    mid = q.optString("mid").toDoubleOrNull() ?: ((bid + ask) / 2),
                    delta = q.optString("delta").toDoubleOrNull(),
                    iv = q.optString("volatility").toDoubleOrNull()?.times(100),
                    openInterest = if (q.has("open-interest")) q.optInt("open-interest") else null,
                )
            }
        }
        return out
    }

    // ── orders ──────────────────────────────────────────────────────────────

    /** Limit order JSON for one vertical spread. [price] is per share, positive. */
    fun verticalOrder(p: SpreadProposal, price: Double, externalId: String): JSONObject {
        fun leg(l: OptionLeg) = JSONObject()
            .put("instrument-type", "Equity Option")
            .put("symbol", l.symbol)
            .put("quantity", p.contracts)
            .put("action", l.action)
        return JSONObject()
            .put("time-in-force", "Day")
            .put("order-type", "Limit")
            .put("price", String.format(java.util.Locale.US, "%.2f", price))
            .put("price-effect", if (p.kind.isCredit) "Credit" else "Debit")
            .put("external-identifier", externalId)
            .put("legs", JSONArray().put(leg(p.shortLeg)).put(leg(p.longLeg)))
    }

    fun dryRun(account: String, order: JSONObject, underlying: String): DryRunResult =
        submitInternal(account, order, underlying, dryRun = true)

    fun submit(account: String, order: JSONObject, underlying: String): DryRunResult =
        submitInternal(account, order, underlying, dryRun = false)

    private fun submitInternal(account: String, order: JSONObject, underlying: String, dryRun: Boolean): DryRunResult {
        val path = "/accounts/$account/orders" + if (dryRun) "/dry-run" else ""
        return try {
            val data = call("POST", path, json = order).optJSONObject("data") ?: JSONObject()
            val bp = data.optJSONObject("buying-power-effect")
            val bpText = bp?.let {
                val v = it.optString("change-in-buying-power")
                val eff = it.optString("change-in-buying-power-effect")
                if (v.isNotBlank()) "$v ($eff)" else null
            }
            val fees = data.optJSONObject("fee-calculation")?.optString("total-fees")?.ifBlank { null }
            val warnings = data.optJSONArray("warnings")?.let { w ->
                (0 until w.length()).map { w.optJSONObject(it)?.optString("message") ?: w.optString(it) }
            } ?: emptyList()
            DryRunResult(underlying, ok = true, buyingPowerEffect = bpText, fees = fees, warnings = warnings,
                errors = emptyList(), orderId = if (dryRun) null else data.optJSONObject("order")?.optString("id"))
        } catch (e: TastyApiException) {
            DryRunResult(underlying, ok = false, buyingPowerEffect = null, fees = null, warnings = emptyList(),
                errors = listOf(e.message ?: "Request failed"))
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun JSONObject.pct(key: String): Double? = optString(key).toDoubleOrNull()?.times(100)

    companion object {
        private const val UA = "omnispread-android/2.0"
        private val JSON = "application/json".toMediaType()

        fun errorMessage(body: String): String = try {
            val err = JSONObject(body).optJSONObject("error")
            val msgs = mutableListOf<String>()
            err?.optString("message")?.takeIf { it.isNotBlank() }?.let(msgs::add)
            err?.optJSONArray("errors")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.optString("message")?.takeIf { it.isNotBlank() }?.let(msgs::add)
            }
            JSONObject(body).optString("error_description").takeIf { it.isNotBlank() }?.let(msgs::add)
            msgs.joinToString("; ").ifBlank { body.take(200) }
        } catch (_: Exception) { body.take(200) }

        fun daysUntil(date: String): Int = try {
            val d = java.time.LocalDate.parse(date)
            java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")), d).toInt()
        } catch (_: Exception) { -1 }
    }
}
