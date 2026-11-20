package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.auth.signOutUser
import com.example.data.auth.switchGoogleAccountClicked
import com.example.data.model.Channel
import com.example.data.model.PakTubeCategories
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.ShortPreviewCard
import com.example.ui.components.VideoFeedCard
import com.example.ui.components.shareVideoIntent
import com.example.ui.theme.PakAmber
import com.example.ui.theme.PakCharcoal
import com.example.ui.theme.PakChipSelected
import com.example.ui.theme.PakError
import com.example.ui.theme.PakGreen
import com.example.ui.theme.PakGreenLight
import com.example.ui.theme.PakOutline
import com.example.ui.theme.PakSubtleText
import com.example.ui.theme.PakSurfaceVariant
import com.example.ui.theme.PakTubeDisplayFontFamily
import com.example.ui.theme.PakWhite
import com.example.ui.viewmodel.MainAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainAppViewModel,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val videosState by viewModel.activeVideos.collectAsStateWithLifecycle()
    val channelsState by viewModel.activeChannels.collectAsStateWithLifecycle()
    val myChannelState by viewModel.myChannel.collectAsStateWithLifecycle()
    val subscriptionsState by viewModel.mySubscriptions.collectAsStateWithLifecycle()
    val savedState by viewModel.savedVideos.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notifications.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfile.collectAsStateWithLifecycle()

    val myChannel = (myChannelState as? UiState.Success)?.data
    val userProfile = (userProfileState as? UiState.Success)?.data

    // Horizontal tabs matching reference screenshot: سب (ہوم), شارٹس, پوسٹس, سبسکرپشنز + categories
    val filterOptions = remember {
        listOf("سب", "شارٹس", "پوسٹس", "سبسکرپشنز") + PakTubeCategories.allCategories
    }
    var selectedFilter by remember { mutableStateOf("سب") }
    var reportTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showCreateChannelSheet by remember { mutableStateOf(false) }

    val unreadCount = remember(notificationsState) {
        (notificationsState as? UiState.Success)?.data?.count { !it.readFlag } ?: 0
    }
    val savedIds = remember(savedState) {
        (savedState as? UiState.Success)?.data?.map { it.videoId }?.toSet() ?: emptySet()
    }
    val subscribedChannelIds = remember(subscriptionsState) {
        (subscriptionsState as? UiState.Success)?.data?.map { it.channelId }?.toSet() ?: emptySet()
    }
    val allChannels = remember(channelsState) {
        (channelsState as? UiState.Success)?.data ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
    ) {
        // 1. TOP HEADER: Original Logo + PakTube on Start, Search + Notifications + Menu on End
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_brand_header")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.paktube_logo),
                    contentDescription = "PakTube Logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Text(
                    text = "PakTube",
                    fontFamily = PakTubeDisplayFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp,
                    color = PakGreen
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "تلاش کریں",
                        tint = PakCharcoal,
                        modifier = Modifier.size(25.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (viewModel.isSignedIn) onOpenNotifications()
                        else viewModel.requestSignIn("اطلاعات دیکھنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge(containerColor = PakError) {
                                    Text(unreadCount.coerceAtMost(99).toString(), color = PakWhite)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.notifications_title),
                            tint = PakAmber,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showHeaderMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "مینو",
                            tint = PakCharcoal,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                        containerColor = PakWhite
                    ) {
                        if (!viewModel.isSignedIn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_in_google)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = PakGreen)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    viewModel.requestSignIn("PakTube اکاؤنٹ فیچرز استعمال کرنے کے لیے گوگل اکاؤنٹ منتخب کریں")
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = userProfile?.displayName ?: "میرا پروفائل",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = PakGreen)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    onOpenProfile()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(if (myChannel == null) "اپنا چینل بنائیں" else "میرا چینل (${myChannel.name})")
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = PakGreen)
                            },
                            onClick = {
                                showHeaderMenu = false
                                if (!viewModel.isSignedIn) {
                                    viewModel.requestSignIn("اپنا چینل بنانے کے لیے گوگل اکاؤنٹ منتخب کریں")
                                } else if (myChannel == null) {
                                    showCreateChannelSheet = true
                                } else {
                                    onOpenChannel(myChannel.id)
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("ویڈیو یا شارٹ اپ لوڈ کریں") },
                            leadingIcon = {
                                Icon(Icons.Filled.VideoCall, contentDescription = null, tint = PakCharcoal)
                            },
                            onClick = {
                                showHeaderMenu = false
                                onOpenCreate()
                            }
                        )

                        if (isAdmin) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.admin_dashboard)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = PakGreen)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    onOpenAdmin()
                                }
                            )
                        }

                        if (viewModel.isSignedIn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.switch_google_account)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = PakCharcoal)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    switchGoogleAccountClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = { viewModel.showMessage("گوگل اکاؤنٹ تبدیل ہو گیا") },
                                        onAuthError = { err -> viewModel.showMessage(err) },
                                        scope = scope
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out), color = PakError) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = PakError)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    signOutUser(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = PakOutline.copy(alpha = 0.6f), thickness = 0.8.dp)

        // 2. CATEGORY TABS ROW matching reference screenshot
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions) { chip ->
                val selected = selectedFilter == chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) PakChipSelected else PakSurfaceVariant,
                    modifier = Modifier
                        .clickable { selectedFilter = chip }
                        .testTag("home_chip_$chip")
                ) {
                    Text(
                        text = chip,
                        color = if (selected) PakWhite else PakCharcoal,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        when (val state = videosState) {
            UiState.Loading -> LoadingStateView("پاک ٹیوب ویڈیوز لوڈ ہو رہی ہیں…")
            is UiState.Error -> ErrorStateView(state.message)
            is UiState.Success -> {
                val allVideos = state.data
                val filteredVideos = remember(allVideos, selectedFilter, subscribedChannelIds) {
                    when (selectedFilter) {
                        "سب" -> allVideos
                        "شارٹس" -> allVideos.filter { it.shortFlag }
                        "پوسٹس" -> allVideos.filter { !it.shortFlag }
                        "سبسکرپشنز" -> allVideos.filter { it.channelId in subscribedChannelIds }
                        else -> allVideos.filter { it.category == selectedFilter }
                    }
                }

                val shorts = remember(filteredVideos) {
                    filteredVideos.filter { it.shortFlag }
                }
                val normalVideos = remember(filteredVideos) {
                    filteredVideos.filter { !it.shortFlag }
                }
                val recentAndRecommended = remember(normalVideos) {
                    normalVideos.sortedByDescending {
                        (it.createdAt?.seconds ?: 0L) + (it.viewCount * 10L) + (it.likeCount * 20L)
                    }
                }
                val popularExtra = remember(normalVideos) {
                    if (normalVideos.size > 3) {
                        normalVideos.sortedByDescending { it.likeCount + it.viewCount }.take(5)
                    } else emptyList()
                }
                val subscribedChannelsList = remember(allChannels, subscribedChannelIds) {
                    allChannels.filter { it.id in subscribedChannelIds }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // 3. CREATE CHANNEL CIRCULAR GREEN (+) + CHANNEL BUBBLES ROW
                    item {
                        val createLabel = if (myChannel != null) {
                            stringResource(R.string.your_studio_btn)
                        } else {
                            stringResource(R.string.create_channel_btn)
                        }
                        val bubbleChannels = subscribedChannelsList.ifEmpty { allChannels.take(12) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        if (!viewModel.isSignedIn) {
                                            viewModel.requestSignIn("اپنا چینل بنانے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
                                        } else if (myChannel == null) {
                                            showCreateChannelSheet = true
                                        } else {
                                            onOpenCreate()
                                        }
                                    }
                                    .testTag("home_create_channel_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(PakWhite)
                                        .border(2.dp, PakGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = createLabel,
                                        tint = PakGreen,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = createLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = PakCharcoal
                                )
                            }

                            if (bubbleChannels.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(12.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(bubbleChannels, key = { it.id }) { chan ->
                                        ChannelBubble(
                                            channel = chan,
                                            onClick = { onOpenChannel(chan.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Subscriptions prompt if guest taps "سبسکرپشنز" chip
                    if (selectedFilter == "سبسکرپشنز" && !viewModel.isSignedIn) {
                        item {
                            AccountSignInPromptCard(
                                reason = "اپنے سبسکرائب کردہ چینلز کی ویڈیوز دیکھنے کے لیے گوگل اکاؤنٹ منتخب کریں",
                                modifier = Modifier.height(300.dp)
                            )
                        }
                    } else {
                        // 4. SHORTS SECTION matching screenshot (green lightning icon + 2-column portrait grid)
                        if (shorts.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PakGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bolt,
                                            contentDescription = null,
                                            tint = Color(0xFFFFE600),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.shorts_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = PakCharcoal
                                    )
                                }
                            }

                            item {
                                val shortsToShow = if (selectedFilter == "شارٹس") shorts else shorts.take(4)
                                val pairs = shortsToShow.chunked(2)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pairs.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            rowItems.forEach { shortItem ->
                                                ShortPreviewCard(
                                                    video = shortItem,
                                                    onClick = { onOpenShort(shortItem.id) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (rowItems.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // 5. YOUTUBE-STYLE VIDEO FEED
                        if (recentAndRecommended.isNotEmpty()) {
                            item {
                                SectionHeaderTitle(
                                    if (selectedFilter == "پوسٹس") "تازہ ترین ویڈیوز اور پوسٹس"
                                    else stringResource(R.string.recent_videos)
                                )
                            }
                            items(recentAndRecommended, key = { "feed_${it.id}" }) { video ->
                                VideoFeedCard(
                                    video = video,
                                    isSaved = video.id in savedIds,
                                    onVideoClick = { onOpenVideo(video.id) },
                                    onChannelClick = { onOpenChannel(video.channelId) },
                                    onSaveClick = { viewModel.toggleSaveVideo(video) },
                                    onShareClick = { shareVideoIntent(context, video) },
                                    onReportClick = { reportTargetVideo = video }
                                )
                            }
                        }

                        if (popularExtra.isNotEmpty() && selectedFilter == "سب") {
                            item {
                                SectionHeaderTitle(stringResource(R.string.popular_videos))
                            }
                            items(popularExtra, key = { "pop_${it.id}" }) { video ->
                                VideoFeedCard(
                                    video = video,
                                    isSaved = video.id in savedIds,
                                    onVideoClick = { onOpenVideo(video.id) },
                                    onChannelClick = { onOpenChannel(video.channelId) },
                                    onSaveClick = { viewModel.toggleSaveVideo(video) },
                                    onShareClick = { shareVideoIntent(context, video) },
                                    onReportClick = { reportTargetVideo = video }
                                )
                            }
                        }

                        if (filteredVideos.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "ابھی کوئی ویڈیو موجود نہیں",
                                    subtitle = "پہلی ویڈیو یا شارٹ اپ لوڈ کر کے PakTube پر آغاز کریں!",
                                    actionLabel = "ویڈیو اپ لوڈ کریں",
                                    onAction = onOpenCreate
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateChannelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateChannelSheet = false },
            containerColor = PakWhite
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                CreateChannelInlineForm(
                    existingChannel = myChannel,
                    onSave = { name, handle, desc, avatar ->
                        viewModel.saveChannel(name, handle, desc, avatar) { ok ->
                            if (ok) showCreateChannelSheet = false
                        }
                    }
                )
            }
        }
    }

    reportTargetVideo?.let { target ->
        ReportContentDialog(
            targetTitle = target.title,
            onDismiss = { reportTargetVideo = null },
            onSubmit = { reason, details ->
                viewModel.submitReport(
                    targetType = "VIDEO",
                    targetId = target.id,
                    targetTitle = target.title,
                    reason = reason,
                    details = details
                )
                reportTargetVideo = null
            }
        )
    }
}

@Composable
private fun SectionHeaderTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = PakCharcoal,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun ChannelBubble(
    channel: Channel,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(66.dp)
            .clickable(onClick = onClick)
    ) {
        ChannelAvatar(
            avatarData = channel.avatarData,
            name = channel.name,
            size = 58.dp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelSmall,
            color = PakCharcoal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
