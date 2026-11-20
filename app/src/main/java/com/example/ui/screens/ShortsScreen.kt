package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.CommentItem
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.PakTubeImage
import com.example.ui.components.PakTubeVideoPlayer
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.formatCountUrdu
import com.example.ui.components.shareVideoIntent
import com.example.ui.theme.PakCharcoal
import com.example.ui.theme.PakGreen
import com.example.ui.theme.PakSubtleText
import com.example.ui.theme.PakWhite
import com.example.ui.viewmodel.MainAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    viewModel: MainAppViewModel,
    initialVideoId: String? = null,
    onOpenCreate: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val videosState by viewModel.activeVideos.collectAsStateWithLifecycle()
    val subscriptionsState by viewModel.mySubscriptions.collectAsStateWithLifecycle()
    val savedState by viewModel.savedVideos.collectAsStateWithLifecycle()
    val channelsState by viewModel.activeChannels.collectAsStateWithLifecycle()

    val subscribedIds = remember(subscriptionsState) {
        (subscriptionsState as? UiState.Success)?.data?.map { it.channelId }?.toSet() ?: emptySet()
    }
    val savedIds = remember(savedState) {
        (savedState as? UiState.Success)?.data?.map { it.videoId }?.toSet() ?: emptySet()
    }
    val channelMap = remember(channelsState) {
        ((channelsState as? UiState.Success)?.data ?: emptyList()).associateBy { it.id }
    }

    var activeCommentsVideo by remember { mutableStateOf<VideoItem?>(null) }
    var reportVideo by remember { mutableStateOf<VideoItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = videosState) {
            UiState.Loading -> LoadingStateView("شارٹس لوڈ ہو رہی ہیں…")
            is UiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PakWhite)
            ) {
                ErrorStateView(state.message)
            }
            is UiState.Success -> {
                val shorts = remember(state.data) { state.data.filter { it.shortFlag } }
                if (shorts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PakWhite),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            title = "کوئی شارٹ ویڈیو موجود نہیں",
                            subtitle = "9:16 عمودی شارٹ اپ لوڈ کر کے یہاں دیکھیں",
                            actionLabel = "شارٹ بنائیں",
                            onAction = onOpenCreate
                        )
                    }
                } else {
                    val startIndex = remember(shorts, initialVideoId) {
                        if (initialVideoId != null) {
                            shorts.indexOfFirst { it.id == initialVideoId }.coerceAtLeast(0)
                        } else 0
                    }
                    val pagerState = rememberPagerState(
                        initialPage = startIndex,
                        pageCount = { shorts.size }
                    )

                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val shortVideo = shorts[page]
                        val isCurrentPage = pagerState.currentPage == page
                        ShortFullScreenPlayerPage(
                            video = shortVideo,
                            isCurrentPage = isCurrentPage,
                            isSubscribed = shortVideo.channelId in subscribedIds,
                            isSaved = shortVideo.id in savedIds,
                            viewModel = viewModel,
                            onSubscribeClick = {
                                channelMap[shortVideo.channelId]?.let { chan ->
                                    viewModel.toggleSubscription(chan)
                                }
                            },
                            onChannelClick = { onOpenChannel(shortVideo.channelId) },
                            onCommentsClick = { activeCommentsVideo = shortVideo },
                            onReportClick = { reportVideo = shortVideo }
                        )
                    }
                }
            }
        }
    }

    activeCommentsVideo?.let { video ->
        val comments by remember(video.id) { viewModel.observeComments(video.id) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        var commentText by remember { mutableStateOf("") }
        var replyTo by remember { mutableStateOf<CommentItem?>(null) }

        ModalBottomSheet(
            onDismissRequest = { activeCommentsVideo = null },
            containerColor = PakWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "تبصرے (${video.commentCount})",
                    style = MaterialTheme.typography.titleLarge,
                    color = PakCharcoal
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val topLevel = comments.filter { it.parentCommentId.isBlank() }
                    items(topLevel, key = { it.id }) { comment ->
                        val replies = comments.filter { it.parentCommentId == comment.id }
                        Column {
                            ShortCommentRow(
                                comment = comment,
                                onReply = { replyTo = comment }
                            )
                            replies.forEach { reply ->
                                Box(modifier = Modifier.padding(start = 28.dp, top = 4.dp)) {
                                    ShortCommentRow(comment = reply, onReply = null)
                                }
                            }
                        }
                    }
                }

                replyTo?.let { parent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${parent.authorName} کو جواب دے رہے ہیں",
                            style = MaterialTheme.typography.labelSmall,
                            color = PakGreen
                        )
                        Text(
                            text = "منسوخ",
                            style = MaterialTheme.typography.labelSmall,
                            color = PakSubtleText,
                            modifier = Modifier.clickable { replyTo = null }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 500) commentText = it },
                        placeholder = { Text("اپنا تبصرہ لکھیں…") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                viewModel.postComment(video, commentText, replyTo) {
                                    commentText = ""
                                    replyTo = null
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PakGreen)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "بھیجیں", tint = PakWhite)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    reportVideo?.let { target ->
        ReportContentDialog(
            targetTitle = target.title,
            onDismiss = { reportVideo = null },
            onSubmit = { reason, details ->
                viewModel.submitReport("VIDEO", target.id, target.title, reason, details)
                reportVideo = null
            }
        )
    }
}

@Composable
private fun ShortCommentRow(
    comment: CommentItem,
    onReply: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        ChannelAvatar(avatarData = comment.authorAvatar, name = comment.authorName, size = 32.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.authorName,
                style = MaterialTheme.typography.labelLarge,
                color = PakCharcoal
            )
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = PakCharcoal
            )
            if (onReply != null) {
                Text(
                    text = "جواب دیں",
                    style = MaterialTheme.typography.labelSmall,
                    color = PakGreen,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = onReply)
                )
            }
        }
    }
}

@Composable
private fun ShortFullScreenPlayerPage(
    video: VideoItem,
    isCurrentPage: Boolean,
    isSubscribed: Boolean,
    isSaved: Boolean,
    viewModel: MainAppViewModel,
    onSubscribeClick: () -> Unit,
    onChannelClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onReportClick: () -> Unit
) {
    val context = LocalContext.current
    val reaction by remember(video.id) { viewModel.observeMyReaction(video.id) }
        .collectAsStateWithLifecycle(initialValue = null)

    var playableUri by remember(video.id) { mutableStateOf<Uri?>(null) }
    var loadingUri by remember(video.id) { mutableStateOf(true) }
    var uriError by remember(video.id) { mutableStateOf<String?>(null) }
    var isPaused by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(video.id, isCurrentPage) {
        if (isCurrentPage) {
            viewModel.recordVideoView(video)
        }
    }

    LaunchedEffect(video.id) {
        loadingUri = true
        uriError = null
        viewModel.resolvePlayableVideoUri(context, video)
            .onSuccess {
                playableUri = it
                loadingUri = false
            }
            .onFailure {
                uriError = it.localizedMessage ?: "ویڈیو لوڈ نہ ہو سکی"
                loadingUri = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { isPaused = !isPaused }
    ) {
        if (playableUri != null && isCurrentPage) {
            PakTubeVideoPlayer(
                uri = playableUri!!,
                autoPlay = true,
                repeat = true,
                useController = false,
                isPortraitShort = true,
                isPausedExternally = isPaused,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PakTubeImage(
                source = video.thumbnailData,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize()
            )
            if (loadingUri && isCurrentPage) {
                CircularProgressIndicator(
                    color = PakGreen,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Gradient scrim for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        if (isPaused) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "چلائیں",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        // Top Bar inside Shorts
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PakTube شارٹس",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            IconButton(
                onClick = { isPaused = !isPaused },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPaused) "چلائیں" else "روکیں",
                    tint = Color.White
                )
            }
        }

        // Action column on End edge
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShortOverlayAction(
                icon = Icons.Filled.ThumbUp,
                label = formatCountUrdu(video.likeCount),
                active = reaction?.reactionType == "LIKE",
                activeTint = PakGreen,
                onClick = { viewModel.toggleReaction(video, "LIKE") },
                testTag = "short_like_${video.id}"
            )
            ShortOverlayAction(
                icon = Icons.Filled.ThumbDown,
                label = formatCountUrdu(video.dislikeCount),
                active = reaction?.reactionType == "DISLIKE",
                activeTint = Color(0xFFFF6E6E),
                onClick = { viewModel.toggleReaction(video, "DISLIKE") },
                testTag = "short_dislike_${video.id}"
            )
            ShortOverlayAction(
                icon = Icons.Filled.ChatBubble,
                label = formatCountUrdu(video.commentCount),
                active = false,
                onClick = onCommentsClick,
                testTag = "short_comment_${video.id}"
            )
            ShortOverlayAction(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                label = stringResource(R.string.save),
                active = isSaved,
                activeTint = PakGreen,
                onClick = { viewModel.toggleSaveVideo(video) }
            )
            ShortOverlayAction(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.share),
                active = false,
                onClick = { shareVideoIntent(context, video) }
            )
            ShortOverlayAction(
                icon = Icons.Filled.Flag,
                label = stringResource(R.string.report),
                active = false,
                onClick = onReportClick
            )
        }

        // Creator info & description at bottom Start
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChannelAvatar(
                    avatarData = video.channelAvatar,
                    name = video.channelName,
                    size = 38.dp,
                    modifier = Modifier.clickable(onClick = onChannelClick)
                )
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onChannelClick)
                )
                if (video.ownerId != viewModel.currentUserId) {
                    Button(
                        onClick = onSubscribeClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSubscribed) Color.White.copy(alpha = 0.25f) else PakGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (video.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2
                )
            }
            if (uriError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uriError ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB4AB)
                )
            }
        }
    }
}

@Composable
private fun ShortOverlayAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeTint: Color = PakGreen,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeTint else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
