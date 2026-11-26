package com.savyor.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savyor.app.ui.components.SavyorMiniEmblem
import com.savyor.app.ui.theme.SavyorSansFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.ui.navigation.Screen
import com.savyor.app.ui.screens.categories.ManageCategoriesScreen
import com.savyor.app.ui.screens.categories.ManageCategoriesViewModel
import com.savyor.app.ui.screens.detail.ReelDetailScreen
import com.savyor.app.ui.screens.detail.ReelDetailViewModel
import com.savyor.app.ui.screens.library.LibraryScreen
import com.savyor.app.ui.screens.library.LibraryViewModel
import com.savyor.app.ui.screens.settings.SettingsScreen
import com.savyor.app.ui.screens.settings.SettingsViewModel
import com.savyor.app.ui.theme.LocalSavyorColors
import com.savyor.app.ui.theme.SavyorAppTheme
import com.savyor.app.ui.theme.SavyorThemeMode
import com.savyor.app.ui.theme.SavyorThemeState

class MainActivity : ComponentActivity() {

    companion object {
        private var hasPlayedColdStartReveal = false
    }

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val categoriesViewModel: ManageCategoriesViewModel by viewModels()
    private val targetRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val savedThemeMode = SavyorThemeMode.fromString(EncryptedSettingsManager(this).themeMode)
        SavyorThemeState.setThemeMode(savedThemeMode)
        val isDark = when (savedThemeMode) {
            SavyorThemeMode.OBSIDIAN -> true
            SavyorThemeMode.PORCELAIN -> false
            SavyorThemeMode.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if (isDark) 0xFF0B0B0D.toInt() else 0xFFF6F6F8.toInt()))
        targetRouteState.value = intent.getStringExtra("navigate_to")

        setContent {
            val themeMode by SavyorThemeState.themeMode.collectAsState()
            SavyorAppTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalSavyorColors.current.bgCanvas
                ) {
                    val animScale = remember {
                        runCatching {
                            Settings.Global.getFloat(
                                contentResolver,
                                Settings.Global.ANIMATOR_DURATION_SCALE,
                                1f
                            )
                        }.getOrDefault(1f)
                    }
                    var showLaunchReveal by remember {
                        mutableStateOf(!hasPlayedColdStartReveal && animScale > 0f)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()

                        val targetRoute = targetRouteState.value
                        LaunchedEffect(targetRoute) {
                            if (!targetRoute.isNullOrBlank()) {
                                if (targetRoute == Screen.Library.route) {
                                    navController.popBackStack(Screen.Library.route, false)
                                } else {
                                    navController.navigate(targetRoute)
                                }
                            }
                        }

                        NavHost(
                        navController = navController,
                        startDestination = Screen.Library.route,
                        enterTransition = {
                            fadeIn(animationSpec = tween(240)) +
                                slideInHorizontally(animationSpec = tween(240)) { it / 10 }
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(240)) +
                                slideOutHorizontally(animationSpec = tween(240)) { -it / 16 }
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(240)) +
                                slideInHorizontally(animationSpec = tween(240)) { -it / 16 }
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(240)) +
                                slideOutHorizontally(animationSpec = tween(240)) { it / 10 }
                        }
                    ) {
                        composable(Screen.Library.route) {
                            LibraryScreen(
                                viewModel = libraryViewModel,
                                onNavigateToDetail = { shortcode ->
                                    navController.navigate(Screen.Detail.createRoute(shortcode))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onNavigateToManageCategories = {
                                    navController.navigate(Screen.ManageCategories.route)
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToManageCategories = {
                                    navController.navigate(Screen.ManageCategories.route)
                                }
                            )
                        }

                        composable(Screen.ManageCategories.route) {
                            ManageCategoriesScreen(
                                viewModel = categoriesViewModel,
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

                        if (showLaunchReveal) {
                            SavyorLaunchRevealOverlay(
                                onFinished = {
                                    hasPlayedColdStartReveal = true
                                    showLaunchReveal = false
                                }
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
        targetRouteState.value = intent?.getStringExtra("navigate_to")
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

@Composable
private fun SavyorLaunchRevealOverlay(onFinished: () -> Unit) {
    val colors = LocalSavyorColors.current
    val emblemScale = remember { Animatable(0.86f) }
    val sweepProgress = remember { Animatable(-0.5f) }
    val rippleProgress = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffsetY = remember { Animatable(8f) }
    val overlayAlpha = remember { Animatable(1f) }
    val overlayScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val p1 = launch {
            emblemScale.animateTo(1.0f, animationSpec = tween(250, easing = FastOutSlowInEasing))
        }
        val p2 = launch {
            sweepProgress.animateTo(1.5f, animationSpec = tween(280, easing = FastOutSlowInEasing))
        }
        p1.join()
        p2.join()

        val r1 = launch {
            rippleProgress.animateTo(1f, animationSpec = tween(270, easing = FastOutSlowInEasing))
        }
        val r2 = launch {
            wordmarkAlpha.animateTo(1f, animationSpec = tween(240, easing = FastOutSlowInEasing))
        }
        val r3 = launch {
            wordmarkOffsetY.animateTo(0f, animationSpec = tween(240, easing = FastOutSlowInEasing))
        }
        r1.join()
        r2.join()
        r3.join()

        val d1 = launch {
            overlayAlpha.animateTo(0f, animationSpec = tween(230, easing = FastOutSlowInEasing))
        }
        val d2 = launch {
            overlayScale.animateTo(1.06f, animationSpec = tween(230, easing = FastOutSlowInEasing))
        }
        d1.join()
        d2.join()
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha.value
                scaleX = overlayScale.value
                scaleY = overlayScale.value
            }
            .background(colors.bgCanvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rp = rippleProgress.value
                    if (rp > 0f && rp < 1f) {
                        val ringScale = 0.8f + (1.4f * rp)
                        val ringAlpha = 0.45f * (1f - rp)
                        drawCircle(
                            color = colors.inkPrimary.copy(alpha = ringAlpha),
                            radius = (size.minDimension * 0.34f) * ringScale,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = emblemScale.value
                            scaleY = emblemScale.value
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SavyorMiniEmblem(size = 72.dp)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val sp = sweepProgress.value
                        if (sp in -0.5f..1.5f) {
                            val startX = size.width * sp
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.inkPrimary.copy(alpha = 0.35f),
                                        Color.Transparent
                                    ),
                                    start = Offset(startX - size.width * 0.4f, 0f),
                                    end = Offset(startX + size.width * 0.4f, size.height)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Savyor",
                fontFamily = SavyorSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.5).sp,
                color = colors.inkPrimary,
                modifier = Modifier
                    .alpha(wordmarkAlpha.value)
                    .offset(y = wordmarkOffsetY.value.dp)
            )
        }
    }
}
