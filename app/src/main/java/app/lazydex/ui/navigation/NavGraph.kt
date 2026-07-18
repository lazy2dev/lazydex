package app.lazydex.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.lazydex.ui.addedit.UnifiedAddEditScreen
import app.lazydex.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object MainShellRoute

@Serializable
data class AddEditRoute(val itemId: String? = null)

@Serializable
object SettingsRoute

@Serializable
object AppearanceRoute

@Serializable
object DataAndStorageRoute

@Serializable
object AboutRoute

@Composable
fun LazyDexNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MainShellRoute,
        modifier = modifier
    ) {
        composable<MainShellRoute> {
            MainShellScreen(
                onNavigateToAddItem = {
                    navController.navigate(AddEditRoute(itemId = null))
                },
                onNavigateToEditItem = { itemId ->
                    navController.navigate(AddEditRoute(itemId = itemId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }
        composable<AddEditRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AddEditRoute>()
            UnifiedAddEditScreen(
                itemId = route.itemId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<AppearanceRoute> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Appearance Screen Placeholder")
            }
        }
        composable<DataAndStorageRoute> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Data and Storage Screen Placeholder")
            }
        }
        composable<AboutRoute> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("About Screen Placeholder")
            }
        }
    }
}
