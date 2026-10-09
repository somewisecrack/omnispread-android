package com.example.omnispread.data

/**
 * S&P 100 constituents with GICS sectors (source: Wikipedia "S&P 100", October 2026).
 * Symbols use Yahoo Finance form (BRK-B); see [tastySymbol] for tastytrade form.
 */
val SP100: Map<String, String> = linkedMapOf(
    "AAPL" to "Information Technology", "ABBV" to "Health Care", "ABT" to "Health Care",
    "ACN" to "Information Technology", "ADBE" to "Information Technology", "AMAT" to "Information Technology",
    "AMD" to "Information Technology", "AMGN" to "Health Care", "AMT" to "Real Estate",
    "AMZN" to "Consumer Discretionary", "ANET" to "Information Technology", "AVGO" to "Information Technology",
    "AXP" to "Financials", "BA" to "Industrials", "BAC" to "Financials",
    "BKNG" to "Consumer Discretionary", "BLK" to "Financials", "BMY" to "Health Care",
    "BNY" to "Financials", "BRK-B" to "Financials", "C" to "Financials",
    "CAT" to "Industrials", "CMCSA" to "Communication Services", "COF" to "Financials",
    "COP" to "Energy", "COST" to "Consumer Staples", "CRM" to "Information Technology",
    "CSCO" to "Information Technology", "CVS" to "Health Care", "CVX" to "Energy",
    "DE" to "Industrials", "DELL" to "Information Technology", "DHR" to "Health Care",
    "DIS" to "Communication Services", "DUK" to "Utilities", "EMR" to "Industrials",
    "FDX" to "Industrials", "GD" to "Industrials", "GE" to "Industrials",
    "GEV" to "Industrials", "GILD" to "Health Care", "GM" to "Consumer Discretionary",
    "GOOG" to "Communication Services", "GOOGL" to "Communication Services", "GS" to "Financials",
    "HD" to "Consumer Discretionary", "IBM" to "Information Technology", "INTC" to "Information Technology",
    "INTU" to "Information Technology", "ISRG" to "Health Care", "JNJ" to "Health Care",
    "JPM" to "Financials", "KO" to "Consumer Staples", "LIN" to "Materials",
    "LLY" to "Health Care", "LMT" to "Industrials", "LOW" to "Consumer Discretionary",
    "LRCX" to "Information Technology", "MA" to "Financials", "MCD" to "Consumer Discretionary",
    "MDLZ" to "Consumer Staples", "MDT" to "Health Care", "META" to "Communication Services",
    "MMM" to "Industrials", "MO" to "Consumer Staples", "MRK" to "Health Care",
    "MS" to "Financials", "MSFT" to "Information Technology", "MU" to "Information Technology",
    "NEE" to "Utilities", "NFLX" to "Communication Services", "NOW" to "Information Technology",
    "NVDA" to "Information Technology", "ORCL" to "Information Technology", "PANW" to "Information Technology",
    "PEP" to "Consumer Staples", "PFE" to "Health Care", "PG" to "Consumer Staples",
    "PLTR" to "Information Technology", "PM" to "Consumer Staples", "QCOM" to "Information Technology",
    "RTX" to "Industrials", "SBUX" to "Consumer Discretionary", "SCHW" to "Financials",
    "SNDK" to "Information Technology", "SO" to "Utilities", "T" to "Communication Services",
    "TMO" to "Health Care", "TMUS" to "Communication Services", "TSLA" to "Consumer Discretionary",
    "TXN" to "Information Technology", "UBER" to "Industrials", "UNH" to "Health Care",
    "UNP" to "Industrials", "UPS" to "Industrials", "USB" to "Financials",
    "V" to "Financials", "VZ" to "Communication Services", "WFC" to "Financials",
    "WMT" to "Consumer Staples", "XOM" to "Energy",
)

data class Preset(val key: String, val label: String, val tickers: List<String>)

private val SECTOR_LABELS = linkedMapOf(
    "Information Technology" to "Tech",
    "Financials" to "Financials",
    "Health Care" to "Health Care",
    "Industrials" to "Industrials",
    "Communication Services" to "Comm. Services",
    "Consumer Discretionary" to "Cons. Discretionary",
    "Consumer Staples" to "Cons. Staples",
    "Energy + Utilities" to "Energy, Util. & Other",
)

/** Full S&P 100 first, then sector subsets (small sectors grouped so every chip has ≥ 2 names). */
val PRESETS: List<Preset> = buildList {
    add(Preset("sp100", "S&P 100", SP100.keys.toList()))
    SECTOR_LABELS.forEach { (sector, label) ->
        val names = if (sector == "Energy + Utilities")
            SP100.filterValues { it == "Energy" || it == "Utilities" || it == "Materials" || it == "Real Estate" }.keys
        else SP100.filterValues { it == sector }.keys
        add(Preset(sector, label, names.toList()))
    }
}

/** Yahoo "BRK-B" → tastytrade "BRK/B". */
fun tastySymbol(t: String) = t.replace('-', '/')

/** tastytrade "BRK/B" → Yahoo "BRK-B". */
fun yahooSymbol(t: String) = t.replace('/', '-')
