package app.lazydex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.lazydex.ui.addedit.UnifiedAddEditScreen
import app.lazydex.ui.home.HomeScreen
import app.lazydex.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class AddEditRoute(val itemId: String? = null)

@Serializable
object SettingsRoute

@Composable
fun LazyDexNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ) {
        composable<HomeRoute> {
            HomeScreen(
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
    }
}
