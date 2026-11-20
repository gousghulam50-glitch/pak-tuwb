package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.auth.signOutUser
import com.example.data.auth.switchGoogleAccountClicked
import com.example.data.model.Channel
import com.example.data.model.UiState
import com.example.data.model.UserVideoEntry
import com.example.data.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.PakTubeImage
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.VideoFeedCard
import com.example.ui.components.formatDuration
import com.example.ui.components.shareVideoIntent
import com.example.ui.theme.PakCharcoal
import com.example.ui.theme.PakError
import com.example.ui.theme.PakGreen
import com.example.ui.theme.PakGreenDark
import com.example.ui.theme.PakGreenLight
import com.example.ui.theme.PakOutline
import com.example.ui.theme.PakSubtleText
import com.example.ui.theme.PakWhite
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun SubscriptionsScreen(
    viewModel: MainAppViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onExploreChannels: () -> Unit
) {
    val context = LocalContext.current
    val subscriptionsState by viewModel.mySubscriptions.collectAsStateWithLifecycle()
    val videosState by viewModel.activeVideos.collectAsStateWithLifecycle()
    val savedState by viewModel.savedVideos.collectAsStateWithLifecycle()
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<VideoItem?>(null) }

    val savedIds = remember(savedState) {
        (savedState as? UiState.Success)?.data?.map { it.videoId }?.toSet() ?: emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
    ) {
        Text(
            text = stringResource(R.string.nav_subscriptions),
            style = MaterialTheme.typography.headlineMedium,
            color = PakCharcoal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        HorizontalDivider(color = PakOutline)

        if (!viewModel.isSignedIn) {
            AccountSignInPromptCard(
                reason = "اپنے سبسکرائب کردہ چینلز اور فیڈ دیکھنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں"
            )
            return
        }

        when (val subState = subscriptionsState) {
            UiState.Loading -> LoadingStateView("سبسکرپشنز لوڈ ہو رہی ہیں…")
            is UiState.Error -> ErrorStateView(subState.message)
            is UiState.Success -> {
                val subs = subState.data
                if (subs.isEmpty()) {
                    EmptyStateView(
                        title = "ابھی کوئی چینل سبسکرائب نہیں کیا",
                        subtitle = "اپنے پسندیدہ تخلیق کاروں کے چینلز کو سبسکرائب کریں تاکہ ان کی نئی ویڈیوز یہاں نظر آئیں",
                        actionLabel = "ویڈیوز دیکھیں",
                        onAction = onExploreChannels
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedChannelId == null) PakGreen else PakGreenLight,
                                modifier = Modifier.clickable { selectedChannelId = null }
                            ) {
                                Text(
                                    text = "تمام چینلز",
                                    color = if (selectedChannelId == null) PakWhite else PakGreenDark,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                        items(subs, key = { it.id }) { sub ->
                            val active = selectedChannelId == sub.channelId
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable {
                                        if (active) onOpenChannel(sub.channelId)
                                        else selectedChannelId = sub.channelId
                                    }
                            ) {
                                ChannelAvatar(
                                    avatarData = sub.channelAvatar,
                                    name = sub.channelName,
                                    size = 52.dp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = sub.channelName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (active) PakGreen else PakCharcoal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = PakOutline.copy(alpha = 0.6f))

                    val subIds = subs.map { it.channelId }.toSet()
                    val feedVideos = ((videosState as? UiState.Success)?.data ?: emptyList())
                        .filter { video ->
                            if (selectedChannelId != null) video.channelId == selectedChannelId
                            else video.channelId in subIds
                        }

                    if (feedVideos.isEmpty()) {
                        EmptyStateView(
                            title = "اس فیڈ میں کوئی نئی ویڈیو نہیں",
                            subtitle = "جب چینل نئی ویڈیو اپ لوڈ کرے گا تو یہاں ظاہر ہو گی"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            items(feedVideos, key = { it.id }) { video ->
                                VideoFeedCard(
                                    video = video,
                                    isSaved = video.id in savedIds,
                                    onVideoClick = {
                                        if (video.shortFlag) onOpenShort(video.id) else onOpenVideo(video.id)
                                    },
                                    onChannelClick = { onOpenChannel(video.channelId) },
                                    onSaveClick = { viewModel.toggleSaveVideo(video) },
                                    onShareClick = { shareVideoIntent(context, video) },
                                    onReportClick = { reportTarget = video }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    reportTarget?.let { target ->
        ReportContentDialog(
            targetTitle = target.title,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                viewModel.submitReport("VIDEO", target.id, target.title, reason, details)
                reportTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainAppViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenCreate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val userState by viewModel.userProfile.collectAsStateWithLifecycle()
    val myChannelState by viewModel.myChannel.collectAsStateWithLifecycle()
    val myVideosState by viewModel.myVideos.collectAsStateWithLifecycle()
    val likedState by viewModel.likedVideos.collectAsStateWithLifecycle()
    val historyState by viewModel.watchHistory.collectAsStateWithLifecycle()
    val savedState by viewModel.savedVideos.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showChannelEditor by remember { mutableStateOf(false) }

    val userProfile = (userState as? UiState.Success)?.data
    val myChannel = (myChannelState as? UiState.Success)?.data

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
    ) {
        if (!viewModel.isSignedIn) {
            AccountSignInPromptCard(
                reason = "اپنا پروفائل، چینل، محفوظ ویڈیوز اور واچ ہسٹری دیکھنے کے لیے گوگل اکاؤنٹ منتخب کریں"
            )
            return
        }

        // Profile Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PakGreenLight.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, PakOutline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ChannelAvatar(
                        avatarData = myChannel?.avatarData?.ifBlank { userProfile?.photoUrl ?: "" }
                            ?: (userProfile?.photoUrl ?: ""),
                        name = userProfile?.displayName ?: "PakTube",
                        size = 64.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName ?: "صارف",
                            style = MaterialTheme.typography.titleLarge,
                            color = PakCharcoal
                        )
                        if (myChannel != null) {
                            Text(
                                text = "${myChannel.name} • ${myChannel.handle}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = PakGreenDark
                            )
                            Text(
                                text = "${myChannel.subscriberCount} سبسکرائبرز • ${myChannel.videoCount} ویڈیوز",
                                style = MaterialTheme.typography.labelSmall,
                                color = PakSubtleText
                            )
                        } else {
                            Text(
                                text = userProfile?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PakSubtleText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showChannelEditor = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PakGreen),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_edit_channel_button")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (myChannel == null) "چینل بنائیں" else "چینل میں ترمیم")
                    }

                    if (myChannel != null) {
                        OutlinedButton(
                            onClick = { onOpenChannel(myChannel.id) },
                            border = BorderStroke(1.dp, PakGreen)
                        ) {
                            Text("میرا چینل", color = PakGreenDark)
                        }
                    }

                    IconButton(
                        onClick = {
                            switchGoogleAccountClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = { viewModel.showMessage("گوگل اکاؤنٹ تبدیل ہو گیا") },
                                onAuthError = { err -> viewModel.showMessage(err) },
                                scope = scope
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("switch_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.switch_google_account),
                            tint = PakGreenDark
                        )
                    }

                    IconButton(
                        onClick = { signOutUser(credentialManager, scope) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.sign_out),
                            tint = PakError
                        )
                    }
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = PakCharcoal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_admin_button")
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.admin_dashboard))
                    }
                }
            }
        }

        val tabs = listOf(
            "میری ویڈیوز" to Icons.Filled.VideoLibrary,
            "پسندیدہ" to Icons.Filled.ThumbUp,
            "ہسٹری" to Icons.Filled.History,
            "محفوظ" to Icons.Filled.Bookmark
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = PakWhite,
            contentColor = PakGreen,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(label)
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    when (val st = myVideosState) {
                        UiState.Loading -> LoadingStateView()
                        is UiState.Error -> ErrorStateView(st.message)
                        is UiState.Success -> {
                            if (st.data.isEmpty()) {
                                EmptyStateView(
                                    title = "آپ نے ابھی کوئی ویڈیو اپ لوڈ نہیں کی",
                                    subtitle = "اپنی پہلی ویڈیو یا شارٹ شائع کریں",
                                    actionLabel = "ویڈیو بنائیں",
                                    onAction = onOpenCreate
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(st.data, key = { it.id }) { vid ->
                                        ProfileUploadedVideoRow(
                                            video = vid,
                                            onOpen = {
                                                if (vid.shortFlag) onOpenShort(vid.id) else onOpenVideo(vid.id)
                                            },
                                            onDelete = { viewModel.deleteOwnVideo(vid.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> UserVideoEntryList(
                    state = likedState,
                    emptyTitle = "کوئی پسندیدہ ویڈیو نہیں",
                    emptySubtitle = "جن ویڈیوز کو آپ لائک کریں گے وہ یہاں نظر آئیں گی",
                    onOpenVideo = onOpenVideo,
                    onOpenShort = onOpenShort
                )
                2 -> UserVideoEntryList(
                    state = historyState,
                    emptyTitle = "واچ ہسٹری خالی ہے",
                    emptySubtitle = "آپ کی دیکھی گئی ویڈیوز یہاں محفوظ رہیں گی",
                    onOpenVideo = onOpenVideo,
                    onOpenShort = onOpenShort
                )
                else -> UserVideoEntryList(
                    state = savedState,
                    emptyTitle = "کوئی محفوظ ویڈیو نہیں",
                    emptySubtitle = "بعد میں دیکھنے کے لیے ویڈیوز محفوظ کریں",
                    onOpenVideo = onOpenVideo,
                    onOpenShort = onOpenShort
                )
            }
        }
    }

    if (showChannelEditor) {
        ModalBottomSheet(
            onDismissRequest = { showChannelEditor = false },
            containerColor = PakWhite
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                CreateChannelInlineForm(
                    existingChannel = myChannel,
                    onSave = { name, handle, desc, avatar ->
                        viewModel.saveChannel(name, handle, desc, avatar) { ok ->
                            if (ok) showChannelEditor = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileUploadedVideoRow(
    video: VideoItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PakOutline),
        color = PakWhite,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PakTubeImage(
                source = video.thumbnailData,
                contentDescription = video.title,
                modifier = Modifier
                    .size(104.dp, 62.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PakCharcoal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (video.shortFlag) "شارٹ" else "ویڈیو"} • ${video.viewCount} ملاحظات • ${formatDuration(video.durationSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PakSubtleText
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف کریں", tint = PakError)
            }
        }
    }
}

@Composable
private fun UserVideoEntryList(
    state: UiState<List<UserVideoEntry>>,
    emptyTitle: String,
    emptySubtitle: String,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit
) {
    when (state) {
        UiState.Loading -> LoadingStateView()
        is UiState.Error -> ErrorStateView(state.message)
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                EmptyStateView(title = emptyTitle, subtitle = emptySubtitle)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.data, key = { it.id.ifBlank { it.videoId } }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PakOutline),
                            color = PakWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.shortFlag) onOpenShort(entry.videoId)
                                    else onOpenVideo(entry.videoId)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PakTubeImage(
                                    source = entry.thumbnailData,
                                    contentDescription = entry.title,
                                    modifier = Modifier
                                        .size(104.dp, 62.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PakCharcoal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.channelName} • ${formatDuration(entry.durationSeconds)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PakSubtleText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
