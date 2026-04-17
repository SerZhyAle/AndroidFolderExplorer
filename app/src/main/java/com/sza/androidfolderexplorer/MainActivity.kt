package com.sza.androidfolderexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sza.androidfolderexplorer.navigation.AppNavigation
import com.sza.androidfolderexplorer.ui.theme.FolderExplorerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolderExplorerTheme {
                AppNavigation()
            }
        }
    }
}
