package com.example.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.auth.authStateFlow
import com.example.data.model.UiState
import com.example.data.repository.PakTubeRepository
import com.example.ui.screens.AccountSignInPromptCard
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.ChannelScreen
import com.example.ui.screens.CreateVideoScreen
import com.example.ui.screens.GoogleSignInDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PakToYouSplashScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.ShortsScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.SuspendedAccountScreen
import com.example.ui.screens.VideoDetailScreen
import com.example.ui.theme.PakAmber
import com.example.ui.theme.PakCharcoal
import com.example.ui.theme.PakGreen
import com.example.ui.theme.PakOutline
import com.example.ui.theme.PakSubtleText
import com.example.ui.theme.PakWhite
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainAppViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data class ShortsRoute(val initialVideoId: String? = null)
@Serializable data object CreateRoute
@Serializable data object SubscriptionsRoute
@Serializable data object ProfileRoute
@Serializable data class VideoDetailRoute(val videoId: String)
@Serializable data class ChannelRoute(val channelId: String)
@Serializable data object SearchRoute
@Serializable data object NotificationsRoute
@Serializable data object AdminRoute

@Composable
fun PakTubeApp(
    auth: FirebaseAuth = Firebase.auth,
    showSplashOnLaunch: Boolean = true
) {
    var showSplash by rememberSaveable { mutableStateOf(showSplashOnLaunch) }

    LaunchedEffect(showSplashOnLaunch) {
        if (showSplashOnLaunch && showSplash) {
            delay(1100L)
            showSplash = false
        }
    }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val currentUserId = currentUser?.uid
    val userKey = currentUserId ?: "guest"
    val navController = rememberNavController()

    val mainViewModel: MainAppViewModel = key(userKey) {
        viewModel(
            key = "paktube_vm_$userKey",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val dbId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(dbId)
                    MainAppViewModel(PakTubeRepository(db, auth), currentUserId)
                }
            }
        )
    }

    Crossfade(
        targetState = showSplash,
        animationSpec = tween(durationMillis = 250),
        label = "splash_to_home"
    ) { isSplash ->
        if (isSplash) {
            PakToYouSplashScreen()
        } else {
            PakTubeShell(
                currentUserId = currentUserId,
                mainViewModel = mainViewModel,
                navController = navController
            )
        }
    }
}

@Composable
private fun PakTubeShell(
    currentUserId: String?,
    mainViewModel: MainAppViewModel,
    navController: NavHostController
) {
    val userState by mainViewModel.userProfile.collectAsStateWithLifecycle()
    val profile = (userState as? UiState.Success)?.data
    if (currentUserId != null && profile?.suspendedFlag == true) {
        SuspendedAccountScreen(reason = profile.suspendedReason)
        return
    }

    val isAdmin by mainViewModel.isAdmin.collectAsStateWithLifecycle()
    val snackbarMsg by mainViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val pendingAuthReason by mainViewModel.pendingAuthReason.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearSnackbar()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val showBottomBar = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<ShortsRoute>() ||
            dest.hasRoute<CreateRoute>() ||
            dest.hasRoute<SubscriptionsRoute>() ||
            dest.hasRoute<ProfileRoute>()
    } ?: true

    val navigateTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = PakWhite,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                PakTubeBottomNavBar(
                    isHome = currentDest?.hasRoute<HomeRoute>() == true,
                    isShorts = currentDest?.hasRoute<ShortsRoute>() == true,
                    isCreate = currentDest?.hasRoute<CreateRoute>() == true,
                    isSubs = currentDest?.hasRoute<SubscriptionsRoute>() == true,
                    isProfile = currentDest?.hasRoute<ProfileRoute>() == true,
                    onHome = { navigateTab(HomeRoute) },
                    onShorts = { navigateTab(ShortsRoute(null)) },
                    onCreate = { navigateTab(CreateRoute) },
                    onSubs = { navigateTab(SubscriptionsRoute) },
                    onProfile = { navigateTab(ProfileRoute) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = mainViewModel,
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenNotifications = { navController.navigate(NotificationsRoute) },
                    onOpenAdmin = { navController.navigate(AdminRoute) },
                    onOpenCreate = { navigateTab(CreateRoute) },
                    onOpenProfile = { navigateTab(ProfileRoute) },
                    onOpenVideo = { id -> navController.navigate(VideoDetailRoute(id)) },
                    onOpenShort = { id -> navController.navigate(ShortsRoute(id)) },
                    onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) }
                )
            }

            composable<ShortsRoute> { entry ->
                val route = entry.toRoute<ShortsRoute>()
                ShortsScreen(
                    viewModel = mainViewModel,
                    initialVideoId = route.initialVideoId,
                    onOpenCreate = { navigateTab(CreateRoute) },
                    onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) }
                )
            }

            composable<CreateRoute> {
                CreateVideoScreen(
                    viewModel = mainViewModel,
                    onUploadSuccess = { _, _ ->
                        navigateTab(HomeRoute)
                    }
                )
            }

            composable<SubscriptionsRoute> {
                SubscriptionsScreen(
                    viewModel = mainViewModel,
                    onOpenVideo = { id -> navController.navigate(VideoDetailRoute(id)) },
                    onOpenShort = { id -> navController.navigate(ShortsRoute(id)) },
                    onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) },
                    onExploreChannels = { navigateTab(HomeRoute) }
                )
            }

            composable<ProfileRoute> {
                ProfileScreen(
                    viewModel = mainViewModel,
                    onOpenVideo = { id -> navController.navigate(VideoDetailRoute(id)) },
                    onOpenShort = { id -> navController.navigate(ShortsRoute(id)) },
                    onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) },
                    onOpenAdmin = { navController.navigate(AdminRoute) },
                    onOpenCreate = { navigateTab(CreateRoute) }
                )
            }

            composable<VideoDetailRoute> { entry ->
                val route = entry.toRoute<VideoDetailRoute>()
                VideoDetailScreen(
                    videoId = route.videoId,
                    viewModel = mainViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) }
                )
            }

            composable<ChannelRoute> { entry ->
                val route = entry.toRoute<ChannelRoute>()
                ChannelScreen(
                    channelId = route.channelId,
                    viewModel = mainViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id -> navController.navigate(VideoDetailRoute(id)) },
                    onOpenShort = { id -> navController.navigate(ShortsRoute(id)) }
                )
            }

            composable<SearchRoute> {
                SearchScreen(
                    viewModel = mainViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id -> navController.navigate(VideoDetailRoute(id)) },
                    onOpenShort = { id -> navController.navigate(ShortsRoute(id)) },
                    onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) }
                )
            }

            composable<NotificationsRoute> {
                NotificationsScreen(
                    viewModel = mainViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id -> navController.navigate(VideoDetailRoute(id)) }
                )
            }

            composable<AdminRoute> {
                if (currentUserId == null) {
                    AccountSignInPromptCard(
                        reason = "ایڈمن ڈیش بورڈ کھولنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں"
                    )
                } else {
                    val adminViewModel: AdminViewModel = viewModel(
                        key = "admin_$currentUserId",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val dbId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(dbId)
                                AdminViewModel(PakTubeRepository(db), currentUserId)
                            }
                        }
                    )
                    AdminDashboardScreen(
                        isAuthorizedAdmin = isAdmin,
                        adminViewModel = adminViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    pendingAuthReason?.let { reason ->
        GoogleSignInDialog(
            reason = reason,
            onDismiss = { mainViewModel.dismissSignInPrompt() },
            onAuthSuccess = { mainViewModel.dismissSignInPrompt() }
        )
    }
}

@Composable
private fun PakTubeBottomNavBar(
    isHome: Boolean,
    isShorts: Boolean,
    isCreate: Boolean,
    isSubs: Boolean,
    isProfile: Boolean,
    onHome: () -> Unit,
    onShorts: () -> Unit,
    onCreate: () -> Unit,
    onSubs: () -> Unit,
    onProfile: () -> Unit
) {
    Surface(
        color = PakWhite,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = PakOutline, thickness = 0.8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // In RTL layout, index 0 renders on the rightmost (Start): ہوم -> شارٹس -> بنائیں -> سبسکرپشنز -> آپ
                BottomTabItem(
                    label = stringResource(R.string.nav_home),
                    selected = isHome,
                    icon = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
                    selectedTint = PakGreen,
                    onClick = onHome,
                    testTag = "nav_home"
                )
                BottomTabItem(
                    label = stringResource(R.string.nav_shorts),
                    selected = isShorts,
                    icon = if (isShorts) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                    selectedTint = PakAmber,
                    onClick = onShorts,
                    testTag = "nav_shorts"
                )

                // Prominent Green Center Create Button (+)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onCreate)
                        .testTag("nav_create")
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PakGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.nav_create),
                            tint = PakWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.nav_create),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isCreate) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isCreate) PakGreen else PakSubtleText
                    )
                }

                BottomTabItem(
                    label = stringResource(R.string.nav_subscriptions),
                    selected = isSubs,
                    icon = if (isSubs) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions,
                    selectedTint = PakGreen,
                    onClick = onSubs,
                    testTag = "nav_subscriptions"
                )
                BottomTabItem(
                    label = stringResource(R.string.nav_profile),
                    selected = isProfile,
                    icon = if (isProfile) Icons.Filled.Person else Icons.Outlined.Person,
                    selectedTint = PakGreen,
                    onClick = onProfile,
                    testTag = "nav_profile"
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) selectedTint else PakCharcoal.copy(alpha = 0.72f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) selectedTint else PakSubtleText
        )
    }
}
