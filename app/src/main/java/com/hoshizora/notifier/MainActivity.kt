package com.hoshizora.notifier

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoshizora.notifier.ui.AddEditLocationScreen
import com.hoshizora.notifier.ui.LocationListScreen
import com.hoshizora.notifier.ui.SettingsScreen
import com.hoshizora.notifier.ui.theme.HoshizoraTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            HoshizoraTheme {
                Surface {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "list") {
                        composable("list") {
                            LocationListScreen(
                                onAddClick = { navController.navigate("edit/-1") },
                                onEditClick = { id -> navController.navigate("edit/$id") },
                                onSettingsClick = { navController.navigate("settings") },
                            )
                        }
                        composable("edit/{locationId}") { backStackEntry ->
                            val locationId = backStackEntry.arguments
                                ?.getString("locationId")?.toLongOrNull() ?: -1L
                            AddEditLocationScreen(locationId = locationId) {
                                navController.popBackStack()
                            }
                        }
                        composable("settings") {
                            SettingsScreen { navController.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}
