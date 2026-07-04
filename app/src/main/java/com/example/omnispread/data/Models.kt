package com.example.omnispread.data

data class PairResult(
    val pair: String = "",
    val x: String = "",
    val y: String = "",
    val qty: Double = 0.0,
    val direction: String = "",
    val combo: String = "",
    val method: String = "",
    val price_corr: Double = 0.0,
    val z_score: Double = 0.0,
    val half_life: Int = 0,
    val move_to_mean: Double = 0.0,
    val exp_return: Double = 0.0,
    val unit_price: Double = 0.0,
    val hurst: Double = 0.0,
    val prob_profit: Double = 0.0,
    val prob_profit_low: Double = 0.0,
    val prob_profit_high: Double = 0.0,
    val same_sector: String = "",
    val extreme_z_in_hl: String = "",
    val extreme_z_detail: String = "",
    val profitable_since_extreme: String = "",
    val pnl_since_extreme: Double = 0.0,
    val historical_z_scores: List<HistoricalZScore> = emptyList(),
)

data class HistoricalZScore(
    val time: Long = 0L,
    val value: Double = 0.0,
)

data class BacktestRequest(
    val x: String,
    val y: String,
    val qty: Double,
    val direction: String,
    val interval: String = "1d",
    val half_life: Int,
    val end_date: String,
)

data class BacktestPoint(
    val time: Long = 0L,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val spread: Double = 0.0,
    val pnl_pct: Double = 0.0,
)

data class BacktestResult(
    val status: String = "",
    val pair: String? = null,
    val x: String? = null,
    val y: String? = null,
    val qty: Double? = null,
    val direction: String? = null,
    val interval: String? = null,
    val half_life: Int? = null,
    val entry_time: Long? = null,
    val exit_time: Long? = null,
    val final_pnl_pct: Double? = null,
    val max_profit_pct: Double? = null,
    val points: List<BacktestPoint>? = null,
    val note: String? = null,
    val error: String? = null,
)

val NIFTY50_TICKERS = listOf(
    "TCS.NS", "INFY.NS", "TECHM.NS", "LTIM.NS", "HCLTECH.NS",
    "HINDALCO.NS", "EICHERMOT.NS", "WIPRO.NS", "TATASTEEL.NS", "HEROMOTOCO.NS",
    "TATACONSUM.NS", "DIVISLAB.NS", "NESTLEIND.NS", "UPL.NS", "ADANIPORTS.NS",
    "CIPLA.NS", "LT.NS", "ICICIBANK.NS", "HINDUNILVR.NS", "ADANIENT.NS",
    "ASIANPAINT.NS", "BRITANNIA.NS", "ONGC.NS", "COALINDIA.NS", "TATAMOTORS.NS",
    "SBILIFE.NS", "JSWSTEEL.NS", "BHARTIARTL.NS", "ITC.NS", "BAJFINANCE.NS",
    "RELIANCE.NS", "HDFCBANK.NS", "KOTAKBANK.NS", "APOLLOHOSP.NS", "INDUSINDBK.NS",
    "NTPC.NS", "BPCL.NS", "BAJAJ-AUTO.NS", "SBIN.NS", "BAJAJFINSV.NS",
    "GRASIM.NS", "AXISBANK.NS", "SUNPHARMA.NS", "M&M.NS", "MARUTI.NS",
    "TITAN.NS", "ULTRACEMCO.NS", "DRREDDY.NS", "POWERGRID.NS", "HDFCLIFE.NS",
)
