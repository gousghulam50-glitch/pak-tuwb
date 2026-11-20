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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.ShortPreviewCard
import com.example.ui.components.VideoFeedCard
import com.example.ui.components.formatRelativeTimeUrdu
import com.example.ui.components.shareVideoIntent
import com.example.ui.theme.PakCharcoal
import com.example.ui.theme.PakGreen
import com.example.ui.theme.PakGreenDark
import com.example.ui.theme.PakGreenLight
import com.example.ui.theme.PakOutline
import com.example.ui.theme.PakSubtleText
import com.example.ui.theme.PakSurfaceVariant
import com.example.ui.theme.PakWhite
import com.example.ui.viewmodel.MainAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    viewModel: MainAppViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit
) {
    val context = LocalContext.current
    val channel by remember(channelId) { viewModel.observeSingleChannel(channelId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val videosState by viewModel.activeVideos.collectAsStateWithLifecycle()
    val subscriptionsState by viewModel.mySubscriptions.collectAsStateWithLifecycle()
    val savedState by viewModel.savedVideos.collectAsStateWithLifecycle()

    var showEditSheet by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<VideoItem?>(null) }

    val isSubscribed = remember(subscriptionsState, channelId) {
        (subscriptionsState as? UiState.Success)?.data?.any { it.channelId == channelId } == true
    }
    val savedIds = remember(savedState) {
        (savedState as? UiState.Success)?.data?.map { it.videoId }?.toSet() ?: emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "واپس", tint = PakCharcoal)
            }
            Text(
                text = channel?.name ?: "چینل",
                style = MaterialTheme.typography.titleLarge,
                color = PakCharcoal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(color = PakOutline)

        val chan = channel
        if (chan == null) {
            LoadingStateView("چینل لوڈ ہو رہا ہے…")
        } else {
            val channelVideos = remember(videosState, channelId) {
                ((videosState as? UiState.Success)?.data ?: emptyList())
                    .filter { it.channelId == channelId }
            }
            val channelShorts = channelVideos.filter { it.shortFlag }
            val channelNormalVideos = channelVideos.filter { !it.shortFlag }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PakGreenLight.copy(alpha = 0.35f))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ChannelAvatar(avatarData = chan.avatarData, name = chan.name, size = 80.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(chan.name, style = MaterialTheme.typography.headlineMedium, color = PakCharcoal)
                        Text(chan.handle, style = MaterialTheme.typography.bodyMedium, color = PakGreenDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${chan.subscriberCount} سبسکرائبرز  •  ${channelVideos.size} ویڈیوز",
                            style = MaterialTheme.typography.labelLarge,
                            color = PakSubtleText
                        )
                        if (chan.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = chan.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PakCharcoal
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        if (chan.ownerId == viewModel.currentUserId) {
                            OutlinedButton(
                                onClick = { showEditSheet = true },
                                border = BorderStroke(1.dp, PakGreen)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = PakGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("چینل میں ترمیم کریں", color = PakGreenDark)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.toggleSubscription(chan) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) PakCharcoal else PakGreen
                                ),
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.testTag("channel_subscribe_button")
                            ) {
                                Text(stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe))
                            }
                        }
                    }
                }

                if (channelShorts.isNotEmpty()) {
                    item {
                        Text(
                            text = "شارٹس (${channelShorts.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = PakCharcoal,
                            modifier = Modifier.padding(16.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            channelShorts.take(2).forEach { s ->
                                ShortPreviewCard(
                                    video = s,
                                    onClick = { onOpenShort(s.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (channelShorts.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (channelNormalVideos.isNotEmpty()) {
                    item {
                        Text(
                            text = "ویڈیوز (${channelNormalVideos.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = PakCharcoal,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(channelNormalVideos, key = { it.id }) { vid ->
                        VideoFeedCard(
                            video = vid,
                            isSaved = vid.id in savedIds,
                            onVideoClick = { onOpenVideo(vid.id) },
                            onChannelClick = {},
                            onSaveClick = { viewModel.toggleSaveVideo(vid) },
                            onShareClick = { shareVideoIntent(context, vid) },
                            onReportClick = { reportTarget = vid }
                        )
                    }
                }

                if (channelVideos.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = "اس چینل پر ابھی کوئی ویڈیو نہیں",
                            subtitle = "نئی ویڈیوز جلد یہاں نظر آئیں گی"
                        )
                    }
                }
            }
        }
    }

    if (showEditSheet && channel != null) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = PakWhite
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                CreateChannelInlineForm(
                    existingChannel = channel,
                    onSave = { name, handle, desc, avatar ->
                        viewModel.saveChannel(name, handle, desc, avatar) { ok ->
                            if (ok) showEditSheet = false
                        }
                    }
                )
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

@Composable
fun SearchScreen(
    viewModel: MainAppViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val context = LocalContext.current
    val videosState by viewModel.activeVideos.collectAsStateWithLifecycle()
    val channelsState by viewModel.activeChannels.collectAsStateWithLifecycle()
    val savedState by viewModel.savedVideos.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("سب") }
    var reportTarget by remember { mutableStateOf<VideoItem?>(null) }

    val savedIds = remember(savedState) {
        (savedState as? UiState.Success)?.data?.map { it.videoId }?.toSet() ?: emptySet()
    }
    val allVideos = (videosState as? UiState.Success)?.data ?: emptyList()
    val allChannels = (channelsState as? UiState.Success)?.data ?: emptyList()

    val q = query.trim().lowercase()
    val matchedChannels = remember(allChannels, q) {
        if (q.isBlank()) allChannels
        else allChannels.filter {
            it.name.lowercase().contains(q) ||
                it.handle.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
        }
    }
    val matchedVideos = remember(allVideos, q, filterTab) {
        allVideos.filter { v ->
            val typeOk = when (filterTab) {
                "ویڈیوز" -> !v.shortFlag
                "شارٹس" -> v.shortFlag
                "چینلز" -> false
                else -> true
            }
            val queryOk = q.isBlank() ||
                v.title.lowercase().contains(q) ||
                v.description.lowercase().contains(q) ||
                v.channelName.lowercase().contains(q) ||
                v.category.lowercase().contains(q)
            typeOk && queryOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "واپس")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = PakGreen) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_query_input")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("سب", "ویڈیوز", "شارٹس", "چینلز").forEach { tab ->
                FilterChip(
                    selected = filterTab == tab,
                    onClick = { filterTab = tab },
                    label = { Text(tab) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PakGreen,
                        selectedLabelColor = PakWhite
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            if (filterTab == "سب" || filterTab == "چینلز") {
                if (matchedChannels.isNotEmpty()) {
                    item {
                        Text(
                            text = "چینلز و تخلیق کار",
                            style = MaterialTheme.typography.titleMedium,
                            color = PakCharcoal,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(matchedChannels, key = { "chan_${it.id}" }) { chan ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PakOutline),
                            color = PakWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { onOpenChannel(chan.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChannelAvatar(chan.avatarData, chan.name, size = 46.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(chan.name, style = MaterialTheme.typography.titleMedium, color = PakCharcoal)
                                    Text(
                                        "${chan.handle} • ${chan.subscriberCount} سبسکرائبرز",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PakSubtleText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (matchedVideos.isNotEmpty()) {
                item {
                    Text(
                        text = "ویڈیوز اور شارٹس",
                        style = MaterialTheme.typography.titleMedium,
                        color = PakCharcoal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(matchedVideos, key = { "vid_${it.id}" }) { vid ->
                    VideoFeedCard(
                        video = vid,
                        isSaved = vid.id in savedIds,
                        onVideoClick = {
                            if (vid.shortFlag) onOpenShort(vid.id) else onOpenVideo(vid.id)
                        },
                        onChannelClick = { onOpenChannel(vid.channelId) },
                        onSaveClick = { viewModel.toggleSaveVideo(vid) },
                        onShareClick = { shareVideoIntent(context, vid) },
                        onReportClick = { reportTarget = vid }
                    )
                }
            }

            if (matchedVideos.isEmpty() && (filterTab != "چینلز" && matchedChannels.isEmpty())) {
                item {
                    EmptyStateView(
                        title = "کوئی نتیجہ نہیں ملا",
                        subtitle = "مختلف الفاظ کے ساتھ دوبارہ تلاش کریں",
                        icon = Icons.Filled.Search
                    )
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

@Composable
fun NotificationsScreen(
    viewModel: MainAppViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit
) {
    val notificationsState by viewModel.notifications.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "واپس")
                }
                Text(
                    text = stringResource(R.string.notifications_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = PakCharcoal
                )
            }
            IconButton(
                onClick = { viewModel.markAllNotificationsRead() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.DoneAll, contentDescription = "سب پڑھے ہوئے نشان زد کریں", tint = PakGreen)
            }
        }
        HorizontalDivider(color = PakOutline)

        if (!viewModel.isSignedIn) {
            AccountSignInPromptCard(
                reason = "اپنی اطلاعات دیکھنے کے لیے گوگل اکاؤنٹ منتخب کریں"
            )
            return
        }

        when (val st = notificationsState) {
            UiState.Loading -> LoadingStateView("اطلاعات لوڈ ہو رہی ہیں…")
            is UiState.Error -> ErrorStateView(st.message)
            is UiState.Success -> {
                if (st.data.isEmpty()) {
                    EmptyStateView(
                        title = "ابھی کوئی اطلاع نہیں",
                        subtitle = "نئے سبسکرائبرز، تبصرے اور چینل اپ لوڈز کی اطلاعات یہاں آئیں گی",
                        icon = Icons.Filled.NotificationsNone
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(st.data, key = { it.id }) { notif ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (notif.readFlag) PakWhite else PakGreenLight.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, PakOutline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.markNotificationRead(notif.id)
                                        if (notif.videoId.isNotBlank()) {
                                            onOpenVideo(notif.videoId)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (!notif.readFlag) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(PakGreen)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = notif.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = PakCharcoal
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PakSubtleText
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatRelativeTimeUrdu(notif.createdAt),
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
}
