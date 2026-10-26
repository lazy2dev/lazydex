package app.lazydex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.lazydex.ui.addedit.UnifiedAddEditScreen
import app.lazydex.ui.settings.AboutScreen
import app.lazydex.ui.settings.AppearanceScreen
import app.lazydex.ui.settings.DataAndStorageScreen
import kotlinx.serialization.Serializable

@Serializable
object MainShellRoute

@Serializable
data class AddEditRoute(
    val itemId: String? = null,
    val initialUrl: String? = null
)

@Serializable
object AppearanceRoute

@Serializable
object DataAndStorageRoute

@Serializable
object AboutRoute

@Serializable
data class ExtensionsRoute(
    val initialAddRepoUrl: String? = null
)

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
                onNavigateToAddItem = { initialUrl ->
                    navController.navigate(AddEditRoute(itemId = null, initialUrl = initialUrl))
                },
                onNavigateToEditItem = { itemId ->
                    navController.navigate(AddEditRoute(itemId = itemId, initialUrl = null))
                },
                onNavigateToAppearance = {
                    navController.navigate(AppearanceRoute)
                },
                onNavigateToExtensions = {
                    navController.navigate(ExtensionsRoute())
                },
                onNavigateToDataAndStorage = {
                    navController.navigate(DataAndStorageRoute)
                },
                onNavigateToAbout = {
                    navController.navigate(AboutRoute)
                }
            )
        }

        composable<ExtensionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ExtensionsRoute>()
            app.lazydex.ui.settings.extensions.ExtensionStoreScreen(
                initialAddRepoUrl = route.initialAddRepoUrl,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<AddEditRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AddEditRoute>()
            UnifiedAddEditScreen(
                itemId = route.itemId,
                initialUrl = route.initialUrl,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<AppearanceRoute> {
            AppearanceScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<DataAndStorageRoute> {
            DataAndStorageScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<AboutRoute> {
            AboutScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
