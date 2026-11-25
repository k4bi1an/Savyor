package com.savyor.app.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Settings : Screen("settings")
    object ManageCategories : Screen("manage_categories")
    object Detail : Screen("detail/{shortcode}") {
        fun createRoute(shortcode: String): String = "detail/$shortcode"
    }
}
