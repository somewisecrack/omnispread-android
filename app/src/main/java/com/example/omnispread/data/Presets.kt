package com.example.omnispread.data

/** Ticker universes, mirroring backend/presets.py in the OmniSpread repo. */
data class Preset(val key: String, val label: String, val tickers: List<String>, val us: Boolean)

val PRESETS = listOf(
    Preset("mega_tech", "Mega Tech", listOf("AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX", "AMD", "INTC"), true),
    Preset("semiconductors", "Semis", listOf("NVDA", "AMD", "INTC", "AVGO", "QCOM", "TXN", "MU", "LRCX", "AMAT", "MRVL"), true),
    Preset("financials", "Financials", listOf("JPM", "BAC", "GS", "MS", "WFC", "C", "BLK", "SCHW", "AXP", "USB"), true),
    Preset("energy", "Energy", listOf("XOM", "CVX", "COP", "SLB", "EOG", "MPC", "PSX", "VLO", "OXY", "HAL"), true),
    Preset("healthcare", "Healthcare", listOf("JNJ", "UNH", "PFE", "ABBV", "MRK", "LLY", "TMO", "ABT", "DHR", "BMY"), true),
    Preset("consumer", "Consumer", listOf("KO", "PEP", "PG", "COST", "WMT", "MCD", "NKE", "SBUX", "TGT", "CL"), true),
    Preset("etfs", "Sector ETFs", listOf("SPY", "QQQ", "IWM", "DIA", "XLK", "XLF", "XLE", "XLV", "XLY", "XLP", "XLI", "XLU", "SMH", "GLD", "SLV", "TLT"), true),
    Preset("nifty_50", "Nifty 50", listOf(
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
    ), false),
)

/** US tickers are tradable on tastytrade; NSE tickers (".NS"/".BO") are scan-only. */
fun isUsTicker(t: String) = !t.endsWith(".NS") && !t.endsWith(".BO") && !t.startsWith("^")
