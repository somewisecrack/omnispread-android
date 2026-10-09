package com.example.omnispread.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tunables for turning a pair signal into option spreads. */
data class StrategySettings(
    /** Max loss budget per leg, in dollars (the pair may use 2× this in total). */
    val riskPerLeg: Double = 500.0,
    /** Target holding horizon = halfLife × this, in trading days. */
    val horizonMultiple: Double = 1.5,
    val minDte: Int = 7,
    val maxDte: Int = 60,
    /** Credit spreads: short strike this many expected moves OTM (OmniSpread `sold_sd`). */
    val soldSd: Double = 1.0,
    /** Credit spreads: search long strikes out to this many expected moves (OmniSpread `hedge_sd`). */
    val hedgeSd: Double = 2.5,
    /** Debit spreads: short strike this many expected moves in the favourable direction. */
    val debitTargetSd: Double = 1.0,
    /** IV rank at/above which credit is preferred; below [debitIvRank] debit is preferred. */
    val creditIvRank: Double = 50.0,
    val debitIvRank: Double = 25.0,
)

/**
 * Builds a two-leg options plan for a cointegrated pair:
 *  - the leg we would be long gets a bullish vertical, the short leg a bearish one;
 *  - expiry ≈ half-life × [StrategySettings.horizonMultiple], avoiding earnings when possible;
 *  - credit vs debit is chosen per leg from IV rank and IV of *that expiry* vs realised vol
 *    over a matching window;
 *  - contracts are sized to the pair's hedge ratio in delta-dollars, within the risk budget.
 */
class OptionStrategy(
    private val api: TastytradeApi,
    private val settings: StrategySettings = StrategySettings(),
) {

    fun plan(pair: PairResult, insights: Map<String, TickerInsight>, closes: Map<String, DoubleArray>, interval: String): PairTradePlan {
        val notes = mutableListOf<String>()
        val bullX = pair.direction == "SHORT_SPREAD"   // SHORT_SPREAD = buy X, sell Y
        val hlDays = halfLifeDays(pair.half_life, interval)
        val target = ceil(hlDays * settings.horizonMultiple * 365.0 / 252.0).toInt().coerceIn(settings.minDte, settings.maxDte)
        notes += "Half-life ≈ ${"%.1f".format(hlDays)} trading days → target ≥ $target calendar days to expiry."

        val legX = runCatching { buildLeg(pair.x, bullX, target, insights[pair.x], closes[pair.x]) }
            .onFailure { notes += "${pair.x}: ${it.message}" }.getOrNull()
        val legY = runCatching { buildLeg(pair.y, !bullX, target, insights[pair.y], closes[pair.y]) }
            .onFailure { notes += "${pair.y}: ${it.message}" }.getOrNull()

        val sized = if (legX != null && legY != null) size(pair, legX, legY, notes) else listOfNotNull(legX, legY).map { sizeAlone(it) }.let {
            Pair(it.firstOrNull { p -> p.underlying == pair.x }, it.firstOrNull { p -> p.underlying == pair.y })
        }
        listOfNotNull(sized.first, sized.second).filter { it.earningsBeforeExpiry }.forEach {
            notes += "${it.underlying}: earnings fall before the ${it.expiry} expiry — expect an IV crush / gap risk."
        }
        return PairTradePlan(pair.pair, pair.direction, sized.first, sized.second, notes)
    }

    // ── one leg ─────────────────────────────────────────────────────────────

    private fun buildLeg(sym: String, bullish: Boolean, targetDte: Int, insight: TickerInsight?, closes: DoubleArray?): SpreadProposal {
        val chain = api.nestedChain(sym)
        if (chain.isEmpty()) throw IllegalStateException("no option chain")
        val earnings = insight?.vol?.nextEarnings
        val candidates = chain.filter { it.dte >= targetDte && it.dte <= settings.maxDte + 30 }
        if (candidates.isEmpty()) throw IllegalStateException("no expiry between $targetDte and ${settings.maxDte + 30} days")
        // Prefer the first expiry that ends before earnings; otherwise the first eligible one.
        val expiry = candidates.firstOrNull { earnings == null || it.date < earnings } ?: candidates.first()
        val earningsBefore = earnings != null && earnings >= todayNy() && earnings <= expiry.date

        val spot = api.equityPrice(sym) ?: closes?.lastOrNull() ?: throw IllegalStateException("no underlying price")
        val vol = insight?.vol
        val expiryIv = vol?.expiries?.firstOrNull { it.date == expiry.date }?.iv ?: vol?.ivIndex
        val tradingDays = max(5, (expiry.dte * 252.0 / 365.0).roundToInt())
        val matchedRv = closes?.let { Stats.realizedVolPct(it, tradingDays) }?.takeIf { it.isFinite() }
            ?: vol?.hv30
        val ivRank = vol?.ivRank

        val (credit, reason) = chooseCredit(ivRank, expiryIv, matchedRv)
        val kind = when {
            credit && bullish -> SpreadKind.BULL_PUT_CREDIT
            credit -> SpreadKind.BEAR_CALL_CREDIT
            bullish -> SpreadKind.BULL_CALL_DEBIT
            else -> SpreadKind.BEAR_PUT_DEBIT
        }
        val ivForMove = (expiryIv ?: vol?.ivIndex ?: matchedRv ?: 30.0) / 100.0
        val em = spot * ivForMove * sqrt(expiry.dte / 365.0)

        val usePuts = kind == SpreadKind.BULL_PUT_CREDIT || kind == SpreadKind.BEAR_PUT_DEBIT
        val strikes = expiry.strikes.filter { abs(it.strike - spot) <= 3.2 * em + 1e-9 }
        if (strikes.size < 2) throw IllegalStateException("too few strikes near the money")
        val quotes = api.optionQuotes(strikes.map { if (usePuts) it.put else it.call })
        fun leg(s: ChainStrike, action: String): OptionLeg? {
            val osym = if (usePuts) s.put else s.call
            val q = quotes[osym] ?: return null
            return OptionLeg(osym, s.strike, if (usePuts) "P" else "C", action, q.bid, q.ask, q.mid, q.delta, q.iv)
        }

        val (shortLeg, longLeg) = if (kind.isCredit) pickCredit(strikes, spot, em, kind, ::leg)
                                  else pickDebit(strikes, spot, em, kind, ::leg)
        val width = abs(shortLeg.strike - longLeg.strike)
        val net = if (kind.isCredit) shortLeg.mid - longLeg.mid else longLeg.mid - shortLeg.mid
        if (net <= 0 || net >= width) throw IllegalStateException("no sensible ${kind.label.lowercase()} spread (net ${"%.2f".format(net)})")

        return SpreadProposal(
            underlying = sym, spot = spot, bullish = bullish, kind = kind,
            expiry = expiry.date, dte = expiry.dte,
            expiryIv = expiryIv, matchedRv = matchedRv, ivRank = ivRank, reason = reason,
            shortLeg = shortLeg, longLeg = longLeg,
            contracts = 1,
            netPrice = if (kind.isCredit) net else -net,
            maxProfit = (if (kind.isCredit) net else width - net) * 100,
            maxLoss = (if (kind.isCredit) width - net else net) * 100,
            deltaDollars = positionDelta(shortLeg, longLeg) * spot * 100,
            earningsBeforeExpiry = earningsBefore,
        )
    }

    /** Credit if premium looks rich at this expiry, debit if cheap. */
    internal fun chooseCredit(ivRank: Double?, iv: Double?, rv: Double?): Pair<Boolean, String> {
        val gap = if (iv != null && rv != null) iv - rv else null
        val gapTxt = gap?.let { "IV ${"%.1f".format(iv)} vs RV ${"%.1f".format(rv)} (${"%+.1f".format(it)} pts)" } ?: "IV−RV n/a"
        val ivrTxt = ivRank?.let { "IVR ${"%.0f".format(it)}" } ?: "IVR n/a"
        return when {
            ivRank != null && ivRank >= settings.creditIvRank -> true to "$ivrTxt ≥ ${settings.creditIvRank.toInt()}: sell premium. $gapTxt"
            ivRank != null && ivRank < settings.debitIvRank -> false to "$ivrTxt < ${settings.debitIvRank.toInt()}: buy premium. $gapTxt"
            gap != null -> (gap > 0) to "$ivrTxt (middle); $gapTxt → ${if (gap > 0) "options rich: credit" else "options cheap: debit"}"
            else -> true to "$ivrTxt; no IV/RV comparison available — defaulting to credit"
        }
    }

    /** OmniSpread `vol` rule: sell ~soldSd EM OTM, then choose the long strike (≤ hedgeSd EM) with the best credit/risk. */
    private fun pickCredit(
        strikes: List<ChainStrike>, spot: Double, em: Double, kind: SpreadKind,
        leg: (ChainStrike, String) -> OptionLeg?,
    ): Pair<OptionLeg, OptionLeg> {
        val put = kind == SpreadKind.BULL_PUT_CREDIT
        val shortTarget = if (put) spot - settings.soldSd * em else spot + settings.soldSd * em
        val otm = strikes.filter { if (put) it.strike < spot else it.strike > spot }
        val shortStrike = otm.minByOrNull { abs(it.strike - shortTarget) } ?: throw IllegalStateException("no OTM strikes")
        val short = leg(shortStrike, "Sell to Open") ?: throw IllegalStateException("no quote for short strike")
        val limit = if (put) spot - settings.hedgeSd * em else spot + settings.hedgeSd * em
        val wings = strikes.filter { if (put) it.strike < shortStrike.strike && it.strike >= limit else it.strike > shortStrike.strike && it.strike <= limit }
            .ifEmpty { strikes.filter { if (put) it.strike < shortStrike.strike else it.strike > shortStrike.strike }.take(1) }
        val best = wings.mapNotNull { leg(it, "Buy to Open") }
            .mapNotNull { l ->
                val credit = short.mid - l.mid
                val risk = abs(short.strike - l.strike) - credit
                if (credit >= 0.05 && risk > 0 && l.ask > 0) l to credit / risk else null
            }
            .maxByOrNull { it.second }?.first ?: throw IllegalStateException("no hedge strike gives a usable credit")
        return short to best
    }

    /** Debit: buy the strike nearest spot, sell ~debitTargetSd EM in the favourable direction. */
    private fun pickDebit(
        strikes: List<ChainStrike>, spot: Double, em: Double, kind: SpreadKind,
        leg: (ChainStrike, String) -> OptionLeg?,
    ): Pair<OptionLeg, OptionLeg> {
        val call = kind == SpreadKind.BULL_CALL_DEBIT
        val longStrike = strikes.minBy { abs(it.strike - spot) }
        val target = if (call) spot + settings.debitTargetSd * em else spot - settings.debitTargetSd * em
        val shortStrike = strikes.filter { if (call) it.strike > longStrike.strike else it.strike < longStrike.strike }
            .minByOrNull { abs(it.strike - target) } ?: throw IllegalStateException("no strike beyond ATM")
        val long = leg(longStrike, "Buy to Open") ?: throw IllegalStateException("no quote for long strike")
        val short = leg(shortStrike, "Sell to Open") ?: throw IllegalStateException("no quote for short strike")
        return short to long
    }

    // ── sizing ──────────────────────────────────────────────────────────────

    private fun positionDelta(short: OptionLeg, long: OptionLeg): Double =
        (long.delta ?: 0.0) - (short.delta ?: 0.0)

    private fun scaled(p: SpreadProposal, n: Int) = p.copy(
        contracts = n,
        maxProfit = p.maxProfit / p.contracts * n,
        maxLoss = p.maxLoss / p.contracts * n,
        deltaDollars = p.deltaDollars / p.contracts * n,
    )

    private fun sizeAlone(p: SpreadProposal): SpreadProposal =
        scaled(p, max(1, floor(settings.riskPerLeg / p.maxLoss).toInt()))

    /**
     * Match delta-dollars to the hedge ratio: the X leg should carry qty·px dollars of
     * exposure for every py dollars on the Y leg. Largest size that keeps both legs within budget.
     */
    private fun size(pair: PairResult, x: SpreadProposal, y: SpreadProposal, notes: MutableList<String>): Pair<SpreadProposal, SpreadProposal> {
        val dX = abs(x.deltaDollars); val dY = abs(y.deltaDollars)
        if (dX < 1e-6 || dY < 1e-6) {
            notes += "Deltas unavailable — legs sized to equal risk instead of the hedge ratio."
            return sizeAlone(x) to sizeAlone(y)
        }
        val ratio = pair.qty * x.spot / y.spot               // target X$ per Y$
        val budget = 2 * settings.riskPerLeg                 // whole pair
        // Closest delta-dollar ratio within the pair budget; ties → larger size.
        var bestX = 1; var bestY = 1; var bestErr = Double.POSITIVE_INFINITY; var bestRisk = 0.0
        for (nX in 1..30) for (nY in 1..30) {
            val risk = nX * x.maxLoss + nY * y.maxLoss
            if (risk > budget) continue
            val err = abs(ln((nX * dX) / (nY * dY) / ratio))
            if (err < bestErr - 1e-9 || (abs(err - bestErr) <= 1e-9 && risk > bestRisk)) {
                bestX = nX; bestY = nY; bestErr = err; bestRisk = risk
            }
        }
        if (bestErr.isInfinite()) notes += "One contract per leg (\$${"%.0f".format(x.maxLoss + y.maxLoss)} max loss) exceeds the \$${budget.toInt()} pair budget; showing 1×1."
        val achieved = (bestX * dX) / (bestY * dY)
        notes += "Delta-dollar ratio X:Y = ${"%.2f".format(achieved)} (target ${"%.2f".format(ratio)} from hedge qty ${"%.3g".format(pair.qty)})."
        return scaled(x, bestX) to scaled(y, bestY)
    }

    private fun halfLifeDays(hl: Int, interval: String): Double = when (interval) {
        "15m" -> hl * 15.0 / 390
        "30m" -> hl * 30.0 / 390
        "1h", "60m" -> hl * 60.0 / 390
        else -> hl.toDouble()
    }

    private fun todayNy(): String = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).toString()
}
