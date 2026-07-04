package com.example.omnispread.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.omnispread.data.BacktestEngine
import com.example.omnispread.data.BacktestRequest
import com.example.omnispread.data.BacktestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BacktestState {
    object Idle : BacktestState
    object Loading : BacktestState
    data class Success(val result: BacktestResult) : BacktestState
    data class Error(val message: String) : BacktestState
}

class BacktestViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<BacktestState>(BacktestState.Idle)
    val state: StateFlow<BacktestState> = _state.asStateFlow()

    fun runBacktest(request: BacktestRequest) {
        _state.value = BacktestState.Loading
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { BacktestEngine.run(request) }
                _state.value = if (result.status == "completed")
                    BacktestState.Success(result)
                else
                    BacktestState.Error(result.error ?: "Backtest failed")
            } catch (e: Exception) {
                _state.value = BacktestState.Error(e.message ?: "An error occurred")
            }
        }
    }
}
