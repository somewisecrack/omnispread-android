# OmniSpread

**Statistical pairs-trading scanner and backtester for Indian equities — runs entirely on-device, no server required.**

OmniSpread fetches live price data from Yahoo Finance, screens every pair in your watchlist for cointegration, and ranks them by Monte Carlo–estimated probability of profit. Built with Kotlin + Jetpack Compose.

---

## Features

| Feature | Details |
|---|---|
| **Cointegration screening** | CADF (Kalman-filtered OLS + ADF) and Johansen tests run in parallel; a pair must pass at least one |
| **Kalman beta tracking** | Dynamic hedge ratio updated online — no look-ahead bias |
| **Hurst exponent filter** | Spreads with H ≥ 0.45 are discarded (non-mean-reverting) |
| **Ensemble Monte Carlo** | 80 parameter draws × 2 000 block-bootstrap simulations per pair → median P(profit) with 5th/95th CI |
| **Z-score chart** | Interactive historical z-score chart per pair |
| **Backtest** | Forward-looking P&L simulation from scan date using live Yahoo Finance prices |
| **Nifty 50 preset** | One-tap scan of all 50 Nifty constituents; custom tickers also supported |
| **Fully offline engine** | All maths (OLS, eigendecomposition, ADF, Johansen) implemented from scratch in Kotlin — no Python, no server |

---

## Screenshots

> _Add screenshots here_

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM (ViewModel + StateFlow)
- **Navigation:** Navigation Compose
- **Networking:** OkHttp (Yahoo Finance chart API)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

---

## How It Works

### 1. Price Fetch
Prices are pulled from the Yahoo Finance chart endpoint (no API key needed) for the selected period (`1y`, `3y`, or `5y`) and interval (`1d` by default).

### 2. Pair Screening
For every pair `(X, Y)` in the ticker list:

1. **CADF path** — OLS regression `Y = α + β·X` → Kalman filter refines β → ADF test on the spread. Passes if p-value < 10 %.
2. **Johansen path** — Full Johansen trace/max-eigenvalue test at 5 % level. Passes if at least one rank hypothesis is rejected.

A pair advances if it passes either test **and** its current |z-score| > 2 **and** its Hurst exponent < 0.45.

### 3. Monte Carlo
For each screened pair an AR(1) model is fit to the spread. Then:
- 80 parameter-uncertainty draws (from OLS standard errors + χ² variance draw)
- Each draw runs 2 000 block-bootstrap residual simulations over one half-life horizon
- A "win" = spread reverts toward the mean at least once within the half-life

Output: **P(profit) median** and 90 % credible interval.

### 4. Backtest
The backtest engine fetches the most recent 60 days of prices forward from the scan date, aligns timestamps, and simulates a long/short spread trade entry at bar 0, tracking P&L through `half_life` bars.

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 35
- JDK 11+

### Build

```bash
git clone https://github.com/YOUR_USERNAME/OmniSpread.git
cd OmniSpread
./gradlew assembleDebug
```

Install on a connected device:

```bash
./gradlew installDebug
```

### Run

1. Open the app.
2. Choose a preset (e.g. **Nifty 50**) or enter custom tickers.
3. Select period and interval.
4. Tap **Scan** — results appear ranked by P(profit).
5. Tap any pair for a detail sheet with z-score chart and backtest button.

---

## Project Structure

```
app/src/main/java/com/example/omnispread/
├── data/
│   ├── Models.kt            # Data classes (PairResult, BacktestResult, …)
│   ├── OmniSpreadEngine.kt  # Core engine: cointegration + Monte Carlo
│   ├── BacktestEngine.kt    # Forward P&L simulation
│   └── YahooFinanceApi.kt   # Yahoo Finance HTTP client
├── viewmodel/
│   ├── MainViewModel.kt     # Scan orchestration + state
│   └── BacktestViewModel.kt # Backtest state
└── ui/
    ├── MainScreen.kt        # Scan form + results list
    ├── ScanForm.kt          # Ticker / period / interval picker
    ├── ResultCard.kt        # Per-pair result card
    ├── PairDetailSheet.kt   # Bottom sheet: metrics + chart
    ├── ZScoreChart.kt       # Historical z-score chart
    ├── PnLChart.kt          # Backtest P&L chart
    ├── BacktestScreen.kt    # Backtest screen
    ├── SettingsDialog.kt    # App settings
    └── theme/               # Material 3 theme
```

---

## Statistical Methods

| Method | Reference |
|---|---|
| ADF test | Dickey & Fuller (1979); MacKinnon (1994) critical values |
| Johansen cointegration | Johansen (1988); trace & max-eigenvalue statistics |
| Kalman filter | Kalman (1960) — 1-D scalar filter for dynamic β |
| Hurst exponent | Rescaled-range analysis via log-log OLS on lagged variances |
| Block bootstrap | Künsch (1989) — preserves autocorrelation in residuals |
| AR(1) simulation | Standard autoregressive model with parameter-uncertainty draws |

---

## Disclaimer

OmniSpread is **for educational and research purposes only**. It is not financial advice. Past statistical relationships do not guarantee future performance. Always consult a qualified financial professional before trading.

---

## License

```
MIT License

Copyright (c) 2025 Rahul Girish Kumar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
