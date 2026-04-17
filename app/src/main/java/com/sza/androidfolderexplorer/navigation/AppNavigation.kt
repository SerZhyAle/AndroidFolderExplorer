package com.sza.androidfolderexplorer.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sza.androidfolderexplorer.presentation.browser.FileBrowserScreen
import com.sza.androidfolderexplorer.presentation.permissions.PermissionScreen
import kotlinx.serialization.Serializable

@Serializable
object PermissionRoute

@Serializable
object BrowserRoute

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = PermissionRoute) {
        composable<PermissionRoute> {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(BrowserRoute) {
                        popUpTo<PermissionRoute> { inclusive = true }
                    }
                }
            )
        }
        composable<BrowserRoute> {
            FileBrowserScreen()
        }
    }
}
