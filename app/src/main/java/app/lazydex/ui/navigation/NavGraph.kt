package app.lazydex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.lazydex.ui.details.MediaDetailsScreen
import app.lazydex.ui.settings.AboutScreen
import app.lazydex.ui.settings.AppearanceScreen
import app.lazydex.ui.settings.DataAndStorageScreen
import app.lazydex.ui.settings.SettingsTrackingScreen
import kotlinx.serialization.Serializable

@Serializable
object MainShellRoute

@Serializable
data class MediaDetailsRoute(
    val itemId: String? = null,
    val initialUrl: String? = null
)

@Serializable
object AppearanceRoute

@Serializable
object TrackingRoute

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
                onNavigateToAppearance = {
                    navController.navigate(AppearanceRoute)
                },
                onNavigateToTracking = {
                    navController.navigate(TrackingRoute)
                },
                onNavigateToExtensions = {
                    navController.navigate(ExtensionsRoute())
                },
                onNavigateToDataAndStorage = {
                    navController.navigate(DataAndStorageRoute)
                },
                onNavigateToAbout = {
                    navController.navigate(AboutRoute)
                },
                onNavigateToAddItem = { initialUrl ->
                    navController.navigate(MediaDetailsRoute(itemId = null, initialUrl = initialUrl))
                },
                onNavigateToEditItem = { itemId ->
                    navController.navigate(MediaDetailsRoute(itemId = itemId, initialUrl = null))
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

        composable<MediaDetailsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<MediaDetailsRoute>()
            MediaDetailsScreen(
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

        composable<TrackingRoute> {
            SettingsTrackingScreen(
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
