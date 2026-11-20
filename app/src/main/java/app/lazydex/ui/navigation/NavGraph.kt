package app.lazydex.ui.navigation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
    val initialUrl: String? = null,
    val initialTitle: String? = null
)

@Serializable
object AppearanceRoute

@Serializable
object TrackingRoute

@Serializable
object DataAndStorageRoute

@Serializable
object AboutRoute

@Composable
fun LazyDexNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val slideDistance = with(LocalDensity.current) { 32.dp.roundToPx() }
    val moveSpec = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)
    val inFadeSpec = tween<Float>(durationMillis = 210, delayMillis = 90, easing = LinearOutSlowInEasing)
    val outFadeSpec = tween<Float>(durationMillis = 90, easing = FastOutLinearInEasing)

    NavHost(
        navController = navController,
        startDestination = MainShellRoute,
        enterTransition = { slideInHorizontally(moveSpec) { slideDistance } + fadeIn(inFadeSpec) },
        exitTransition = { slideOutHorizontally(moveSpec) { -slideDistance } + fadeOut(outFadeSpec) },
        popEnterTransition = { slideInHorizontally(moveSpec) { -slideDistance } + fadeIn(inFadeSpec) },
        popExitTransition = { slideOutHorizontally(moveSpec) { slideDistance } + fadeOut(outFadeSpec) },
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
                onNavigateToDataAndStorage = {
                    navController.navigate(DataAndStorageRoute)
                },
                onNavigateToAbout = {
                    navController.navigate(AboutRoute)
                },
                onNavigateToAddItem = { initialUrl ->
                    navController.navigate(MediaDetailsRoute(itemId = null, initialUrl = initialUrl))
                },
                onNavigateToAddItemWithTitle = { title ->
                    navController.navigate(MediaDetailsRoute(itemId = null, initialTitle = title))
                },
                onNavigateToEditItem = { itemId ->
                    navController.navigate(MediaDetailsRoute(itemId = itemId, initialUrl = null))
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
                },
                onNavigateToTracking = {
                    navController.navigate(TrackingRoute)
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
