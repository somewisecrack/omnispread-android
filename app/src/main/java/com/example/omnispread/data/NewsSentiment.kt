package com.example.omnispread.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

/**
 * Headline sentiment per ticker over the last 24 hours (TickerVibe approach): Google News RSS
 * search (free, no key) restricted to finance wording, keeping only headlines published in the
 * last 24 h whose title names the company; each headline scored, then a recency-weighted mean.
 *
 * TickerVibe scores with FinBERT; that model is ~440 MB, so on-device we use a compact
 * finance lexicon with negation handling ([FinanceLexicon]). Scores are -1 … +1.
 */
object NewsSentiment {

    private val client = Net.client(connectSecs = 10, readSecs = 20)

    const val LOOKBACK_HOURS = 24
    private const val MAX_HEADLINES = 40
    private const val RECENCY_HALF_LIFE_HOURS = 8.0
    private const val FINANCE_TERMS = "(stock OR shares OR earnings OR analyst OR price OR investors)"

    fun analyze(symbol: String, companyName: String?): TickerSentiment {
        val aliases = (NEWS_NAMES[symbol]?.split("|") ?: listOfNotNull(companyName?.let(::cleanName)))
            .filter { it.length >= 2 }
            .ifEmpty { listOf(symbol) }
        val who = aliases.joinToString(" OR ") { "\"$it\"" }
        val query = (if (aliases.size > 1) "($who)" else who) + " $FINANCE_TERMS when:1d"

        val now = System.currentTimeMillis()
        val cutoff = now - LOOKBACK_HOURS * 3_600_000L
        val headlines = fetch(query)
            .filter { it.published >= cutoff && mentions(it.title, aliases) }
            .map { it.copy(score = FinanceLexicon.score(it.title)) }
            .sortedByDescending { it.published }
            .take(MAX_HEADLINES)

        if (headlines.isEmpty()) return TickerSentiment(symbol, query, 0.0, "No news", 0, 0, 0, emptyList())

        var wSum = 0.0; var sSum = 0.0
        headlines.forEach { h ->
            val ageHours = ((now - h.published).coerceAtLeast(0)) / 3_600_000.0
            val w = exp(-ln(2.0) * ageHours / RECENCY_HALF_LIFE_HOURS)
            wSum += w; sSum += w * h.score
        }
        val score = if (wSum > 0) sSum / wSum else 0.0
        val pos = headlines.count { it.score > 0.05 }
        val neg = headlines.count { it.score < -0.05 }
        val label = when {
            score > 0.15 -> "Bullish"
            score < -0.15 -> "Bearish"
            else -> "Neutral"
        }
        return TickerSentiment(symbol, query, score, label, pos, neg, headlines.size - pos - neg, headlines)
    }

    private fun fetch(query: String): List<Headline> {
        val (hl, gl, ceid) = Triple("en-US", "US", "US:en")
        val url = "https://news.google.com/rss/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("hl", hl).addQueryParameter("gl", gl).addQueryParameter("ceid", ceid)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14)").build()
        val xml = try { client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } } catch (_: Exception) { null }
            ?: return emptyList()
        return parseRss(xml)
    }

    /** True if the title contains any alias as a whole word (case-insensitive). */
    internal fun mentions(title: String, aliases: List<String>): Boolean {
        val t = title.lowercase()
        return aliases.any { a -> Regex("(?<![a-z0-9])" + Regex.escape(a.lowercase()) + "(?![a-z0-9])").containsMatchIn(t) }
    }

    internal fun parseRss(xml: String): List<Headline> {
        val out = mutableListOf<Headline>()
        val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        val p = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(xml.reader()) }
        var inItem = false
        var title = ""; var link = ""; var pub = ""; var source = ""
        var tag = ""
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") { inItem = true; title = ""; link = ""; pub = ""; source = "" }
                }
                XmlPullParser.TEXT -> if (inItem) when (tag) {
                    "title" -> title += p.text
                    "link" -> link += p.text
                    "pubDate" -> pub += p.text
                    "source" -> source += p.text
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" && inItem) {
                        inItem = false
                        var t = title.trim()
                        val src = source.trim().ifBlank { t.substringAfterLast(" - ", "Unknown") }
                        if (t.endsWith(" - $src")) t = t.removeSuffix(" - $src")
                        val ts = try { dateFmt.parse(pub.trim())?.time } catch (_: Exception) { null }
                        if (t.isNotBlank() && ts != null) out += Headline(t, src, ts, link.trim(), 0.0)
                    }
                    tag = ""
                }
            }
            p.next()
        }
        return out.distinctBy { it.title.lowercase() }
    }

    private val SUFFIXES = Regex(
        "(?i)[,.]?\\s+(inc|incorporated|corp|corporation|co|company|ltd|limited|plc|holdings?|group|class [a-c]|the)\\.?$"
    )

    internal fun cleanName(raw: String): String {
        var n = raw.trim()
        repeat(3) { n = n.replace(SUFFIXES, "").trim() }
        return n
    }
}

/**
 * Compact finance sentiment lexicon (hand-curated for headlines) with simple negation.
 * Score = (positive − negative) / (positive + negative), in [-1, 1]; 0 when no hits.
 */
object FinanceLexicon {

    private val POSITIVE = setOf(
        "beat", "beats", "surpass", "surpasses", "tops", "exceed", "exceeds", "exceeded", "record", "records",
        "surge", "surges", "surged", "soar", "soars", "soared", "jump", "jumps", "jumped", "rally", "rallies",
        "rallied", "gain", "gains", "gained", "rise", "rises", "rising", "rose", "climb", "climbs", "climbed",
        "upgrade", "upgrades", "upgraded", "outperform", "outperforms", "buy", "bullish", "boost", "boosts",
        "boosted", "strong", "stronger", "strength", "robust", "growth", "grow", "grows", "grew", "expand",
        "expands", "expansion", "profit", "profits", "profitable", "raise", "raises", "raised", "hike",
        "approval", "approved", "approves", "win", "wins", "won", "award", "awarded", "partnership",
        "breakthrough", "launch", "launches", "innovative", "optimistic", "optimism", "upbeat", "positive",
        "recover", "recovers", "recovery", "rebound", "rebounds", "rebounded", "high", "highs", "momentum",
        "dividend", "buyback", "buybacks", "accelerate", "accelerates", "accelerating", "milestone",
        "favorable", "tailwind", "tailwinds", "upside", "overweight", "top", "best", "success", "successful",
        "secure", "secures", "secured", "deal", "demand", "lifts", "lifted", "rallying", "outpace", "outpaces",
        "advances", "advanced", "gains", "pops", "popped", "upgrades",
    )

    private val NEGATIVE = setOf(
        "miss", "misses", "missed", "plunge", "plunges", "plunged", "drop", "drops", "dropped", "fall", "falls",
        "fell", "falling", "slide", "slides", "slid", "sink", "sinks", "sank", "tumble", "tumbles", "tumbled",
        "slump", "slumps", "slumped", "crash", "crashes", "crashed", "decline", "declines", "declined", "loss",
        "losses", "lose", "loses", "lost", "downgrade", "downgrades", "downgraded", "underperform", "sell",
        "bearish", "weak", "weaker", "weakness", "cut", "cuts", "slash", "slashes", "slashed", "lawsuit",
        "lawsuits", "sue", "sues", "sued", "probe", "investigation", "investigated", "fraud", "recall",
        "recalls", "fine", "fined", "penalty", "layoff", "layoffs", "warning", "warns", "warned", "risk",
        "risks", "concern", "concerns", "fear", "fears", "worry", "worries", "pressure", "headwind",
        "headwinds", "downside", "underweight", "low", "lows", "volatile", "volatility", "uncertain",
        "uncertainty", "delay", "delays", "delayed", "halt", "halted", "bankruptcy", "default", "debt",
        "shortfall", "disappoint", "disappoints", "disappointing", "disappointed", "pessimistic", "negative",
        "worst", "slowdown", "slows", "slowing", "struggle", "struggles", "struggling", "tariff", "tariffs",
        "ban", "bans", "banned", "antitrust", "breach", "hack", "outage", "resign", "resigns", "resigned",
        "ousted", "scandal", "selloff", "sell-off", "dive", "dives", "dived", "retreat", "retreats",
        "dip", "dips", "dipped", "trims", "trimmed", "lowers", "lowered", "slips", "slipped", "sheds", "skid",
    )

    private val NEGATORS = setOf("not", "no", "never", "without", "fails", "fail", "failed", "isn't", "wasn't", "won't", "doesn't", "didn't")

    fun score(text: String): Double {
        val words = text.lowercase().replace(Regex("[^a-z0-9'\\- ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        var pos = 0; var neg = 0
        words.forEachIndexed { i, w ->
            val polarity = when (w) { in POSITIVE -> 1; in NEGATIVE -> -1; else -> 0 }
            if (polarity == 0) return@forEachIndexed
            val negated = (maxOf(0, i - 3) until i).any { words[it] in NEGATORS }
            if ((polarity > 0) xor negated) pos++ else neg++
        }
        return if (pos + neg == 0) 0.0 else (pos - neg).toDouble() / (pos + neg)
    }
}
