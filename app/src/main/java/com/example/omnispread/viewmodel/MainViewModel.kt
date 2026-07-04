package com.example.omnispread.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.omnispread.data.ApiClientProvider
import com.example.omnispread.data.BacktestRequest
import com.example.omnispread.data.OmniSpreadRepository
import com.example.omnispread.data.PairResult
import com.example.omnispread.data.ScanRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanState {
    object Idle : ScanState
    data class Scanning(val status: String) : ScanState
    data class Success(val results: List<PairResult>, val message: String) : ScanState
    data class Error(val message: String) : ScanState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: OmniSpreadRepository
        get() = OmniSpreadRepository(ApiClientProvider.getService(getApplication()))

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _selectedPair = MutableStateFlow<PairResult?>(null)
    val selectedPair: StateFlow<PairResult?> = _selectedPair.asStateFlow()

    private val _pendingBacktest = MutableStateFlow<BacktestRequest?>(null)
    val pendingBacktest: StateFlow<BacktestRequest?> = _pendingBacktest.asStateFlow()

    private val _currentInterval = MutableStateFlow("1d")
    val currentInterval: StateFlow<String> = _currentInterval.asStateFlow()

    private val _currentEndDate = MutableStateFlow("")
    val currentEndDate: StateFlow<String> = _currentEndDate.asStateFlow()

    fun selectPair(pair: PairResult?) { _selectedPair.value = pair }

    fun setPendingBacktest(request: BacktestRequest?) { _pendingBacktest.value = request }

    fun reset() {
        _scanState.value = ScanState.Idle
        _selectedPair.value = null
        _currentInterval.value = "1d"
        _currentEndDate.value = ""
    }

    fun startScan(
        tickers: List<String>,
        period: String,
        interval: String = "1d",
        startDate: String? = null,
        endDate: String? = null,
    ) {
        _currentInterval.value = interval
        _currentEndDate.value = endDate ?: ""
        _scanState.value = ScanState.Scanning("Starting scan...")

        viewModelScope.launch {
            try {
                val response = repo.startScan(ScanRequest(tickers, period, interval, startDate, endDate))
                _scanState.value = ScanState.Scanning("Scanning pairs — this may take a moment...")

                val result = repo.pollResults(
                    taskId = response.task_id,
                    onUpdate = { update ->
                        if (update.status == "processing") {
                            _scanState.value = ScanState.Scanning("Analyzing cointegration & running Monte Carlo...")
                        }
                    },
                )

                when (result.status) {
                    "completed" -> {
                        val msg = if (result.results.isEmpty())
                            "No pairs met the Z > 2.0 threshold"
                        else
                            "Found ${result.results.size} actionable pair${if (result.results.size > 1) "s" else ""}"
                        _scanState.value = ScanState.Success(result.results, msg)
                    }
                    "failed" -> _scanState.value = ScanState.Error(result.error ?: "Scan failed unexpectedly")
                    else -> _scanState.value = ScanState.Error("Unexpected scan status: ${result.status}")
                }
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.message ?: "An error occurred")
            }
        }
    }
}
