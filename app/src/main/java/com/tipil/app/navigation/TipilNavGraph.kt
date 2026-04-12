package com.tipil.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tipil.app.ui.auth.AuthViewModel
import com.tipil.app.ui.auth.SignInScreen
import com.tipil.app.ui.bookdetail.BookDetailScreen
import com.tipil.app.ui.bookdetail.BookDetailViewModel
import com.tipil.app.ui.library.LibraryScreen
import com.tipil.app.ui.library.LibraryViewModel
import com.tipil.app.ui.recommendations.RecommendationsScreen
import com.tipil.app.ui.recommendations.RecommendationsViewModel
import com.tipil.app.data.local.MediaType
import com.tipil.app.ui.scanner.ScannerScreen
import com.tipil.app.ui.scanner.ScannerViewModel
import com.tipil.app.ui.settings.ThemePickerScreen
import com.tipil.app.ui.theme.ThemeViewModel

object Routes {
    const val SIGN_IN = "sign_in"
    const val LIBRARY = "library"
    const val SCANNER = "scanner/{mediaType}"
    const val BOOK_DETAIL = "book_detail/{bookId}"
    const val RECOMMENDATIONS = "recommendations"
    const val THEME_PICKER = "theme_picker"

    fun scanner(mediaType: MediaType = MediaType.BOOK) = "scanner/${mediaType.name}"
    fun bookDetail(bookId: Long) = "book_detail/$bookId"
}

@Composable
fun TipilNavGraph(
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.uiState.collectAsState()

    val startDestination = if (authState.isSignedIn) Routes.LIBRARY else Routes.SIGN_IN

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                viewModel = authViewModel,
                onSignedIn = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LIBRARY) {
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            val libraryState by libraryViewModel.uiState.collectAsState()
            LibraryScreen(
                viewModel = libraryViewModel,
                userId = authState.userId,
                displayName = authState.displayName,
                onScanClick = {
                    val mt = libraryState.selectedMediaType ?: MediaType.BOOK
                    navController.navigate(Routes.scanner(mt))
                },
                onBookClick = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                onRecommendationsClick = { navController.navigate(Routes.RECOMMENDATIONS) },
                onThemeClick = { navController.navigate(Routes.THEME_PICKER) },
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.SCANNER,
            arguments = listOf(navArgument("mediaType") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediaTypeName = backStackEntry.arguments?.getString("mediaType") ?: MediaType.BOOK.name
            val mediaType = MediaType.fromName(mediaTypeName)
            val scannerViewModel: ScannerViewModel = hiltViewModel()
            val context = LocalContext.current
            var hasCameraPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasCameraPermission = granted
            }

            LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            if (hasCameraPermission) {
                ScannerScreen(
                    viewModel = scannerViewModel,
                    userId = authState.userId,
                    mediaType = mediaType,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            val bookDetailViewModel: BookDetailViewModel = hiltViewModel()
            BookDetailScreen(
                viewModel = bookDetailViewModel,
                bookId = bookId,
                userId = authState.userId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RECOMMENDATIONS) {
            val recommendationsViewModel: RecommendationsViewModel = hiltViewModel()
            RecommendationsScreen(
                viewModel = recommendationsViewModel,
                userId = authState.userId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.THEME_PICKER) {
            ThemePickerScreen(
                themeViewModel = themeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
