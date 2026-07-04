package com.example.omnispread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.omnispread.ui.BacktestScreen
import com.example.omnispread.ui.MainScreen
import com.example.omnispread.ui.theme.BgPrimary
import com.example.omnispread.ui.theme.OmniSpreadTheme
import com.example.omnispread.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniSpreadTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgPrimary) {
                    val navController = rememberNavController()
                    val mainViewModel: MainViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                viewModel            = mainViewModel,
                                onNavigateToBacktest = { navController.navigate("backtest") },
                            )
                        }
                        composable("backtest") {
                            val request by mainViewModel.pendingBacktest.collectAsState()
                            request?.let {
                                BacktestScreen(
                                    request = it,
                                    onBack  = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
