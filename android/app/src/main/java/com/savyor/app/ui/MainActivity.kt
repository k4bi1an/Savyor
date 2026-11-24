package com.savyor.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.savyor.app.ui.navigation.Screen
import com.savyor.app.ui.screens.detail.ReelDetailScreen
import com.savyor.app.ui.screens.detail.ReelDetailViewModel
import com.savyor.app.ui.screens.library.LibraryScreen
import com.savyor.app.ui.screens.library.LibraryViewModel
import com.savyor.app.ui.screens.settings.SettingsScreen
import com.savyor.app.ui.screens.settings.SettingsViewModel
import com.savyor.app.ui.theme.SavyorTheme

class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SavyorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Library.route
                    ) {
                        composable(Screen.Library.route) {
                            LibraryScreen(
                                viewModel = libraryViewModel,
                                onNavigateToDetail = { shortcode ->
                                    navController.navigate(Screen.Detail.createRoute(shortcode))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.Detail.route,
                            arguments = listOf(navArgument("shortcode") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val shortcode = backStackEntry.arguments?.getString("shortcode") ?: ""
                            val detailViewModel = remember(shortcode) {
                                ReelDetailViewModel(application, shortcode)
                            }
                            ReelDetailScreen(
                                viewModel = detailViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }

        handleIncomingUrl(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleIncomingUrl(it) }
    }

    private fun handleIncomingUrl(intent: Intent) {
        val testUrl = intent.getStringExtra("test_url")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)

        if (!testUrl.isNullOrBlank()) {
            libraryViewModel.enqueueReel(testUrl)
        }
    }
}
