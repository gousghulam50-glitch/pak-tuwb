package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.PakTubeVideoPlayer
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.formatCountUrdu
import com.example.ui.components.formatRelativeTimeUrdu
import com.example.ui.components.shareVideoIntent
import com.example.ui.theme.PakCharcoal
import com.example.ui.theme.PakError
import com.example.ui.theme.PakGreen
import com.example.ui.theme.PakGreenDark
import com.example.ui.theme.PakGreenLight
import com.example.ui.theme.PakOutline
import com.example.ui.theme.PakSubtleText
import com.example.ui.theme.PakSurfaceVariant
import com.example.ui.theme.PakWhite
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun VideoDetailScreen(
    videoId: String,
    viewModel: MainAppViewModel,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val context = LocalContext.current
    val video by remember(videoId) { viewModel.observeSingleVideo(videoId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val reaction by remember(videoId) { viewModel.observeMyReaction(videoId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val comments by remember(videoId) { viewModel.observeComments(videoId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val subscriptionsState by viewModel.mySubscriptions.collectAsStateWithLifecycle()
    val savedState by viewModel.savedVideos.collectAsStateWithLifecycle()

    var playableUri by remember(videoId) { mutableStateOf<Uri?>(null) }
    var loadingUri by remember(videoId) { mutableStateOf(true) }
    var uriError by remember(videoId) { mutableStateOf<String?>(null) }
    var commentText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CommentItem?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    val channel by remember(video?.channelId) {
        val cid = video?.channelId
        if (cid.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(null)
        else viewModel.observeSingleChannel(cid)
    }.collectAsStateWithLifecycle(initialValue = null)

    val isSubscribed = remember(subscriptionsState, video?.channelId) {
        val cid = video?.channelId ?: return@remember false
        (subscriptionsState as? UiState.Success)?.data?.any { it.channelId == cid } == true
    }
    val isSaved = remember(savedState, videoId) {
        (savedState as? UiState.Success)?.data?.any { it.videoId == videoId } == true
    }

    LaunchedEffect(video?.id) {
        val v = video ?: return@LaunchedEffect
        viewModel.recordVideoView(v)
        loadingUri = true
        uriError = null
        viewModel.resolvePlayableVideoUri(context, v)
            .onSuccess {
                playableUri = it
                loadingUri = false
            }
            .onFailure { err ->
                uriError = err.localizedMessage ?: "ویڈیو لوڈ نہ ہو سکی"
                loadingUri = false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Video Player Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            when {
                playableUri != null -> {
                    PakTubeVideoPlayer(
                        uri = playableUri!!,
                        autoPlay = true,
                        repeat = false,
                        useController = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                loadingUri -> {
                    CircularProgressIndicator(
                        color = PakGreen,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Text(
                        text = uriError ?: "ویڈیو دستیاب نہیں",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "واپس",
                    tint = Color.White
                )
            }
        }

        val currentVideo = video
        if (currentVideo == null) {
            LoadingStateView("تفصیلات لوڈ ہو رہی ہیں…")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        color = PakGreenLight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = currentVideo.category,
                            color = PakGreenDark,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentVideo.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = PakCharcoal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatCountUrdu(currentVideo.viewCount)} ملاحظات  •  ${formatRelativeTimeUrdu(currentVideo.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PakSubtleText
                    )
                }

                // Channel row + Subscribe
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PakSurfaceVariant)
                            .clickable { onOpenChannel(currentVideo.channelId) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelAvatar(
                            avatarData = channel?.avatarData ?: currentVideo.channelAvatar,
                            name = channel?.name ?: currentVideo.channelName,
                            size = 44.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel?.name ?: currentVideo.channelName,
                                style = MaterialTheme.typography.titleMedium,
                                color = PakCharcoal
                            )
                            Text(
                                text = "${channel?.subscriberCount ?: 0L} سبسکرائبرز",
                                style = MaterialTheme.typography.labelSmall,
                                color = PakSubtleText
                            )
                        }
                        if (currentVideo.ownerId != viewModel.currentUserId) {
                            Button(
                                onClick = {
                                    channel?.let { viewModel.toggleSubscription(it) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) PakCharcoal else PakGreen
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("detail_subscribe_button")
                            ) {
                                Text(stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe))
                            }
                        }
                    }
                }

                // Action pills: Like, Dislike, Share, Save, Report
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ActionPill(
                                icon = Icons.Filled.ThumbUp,
                                label = "${stringResource(R.string.like)} (${formatCountUrdu(currentVideo.likeCount)})",
                                selected = reaction?.reactionType == "LIKE",
                                onClick = { viewModel.toggleReaction(currentVideo, "LIKE") },
                                testTag = "detail_like_button"
                            )
                        }
                        item {
                            ActionPill(
                                icon = Icons.Filled.ThumbDown,
                                label = "${stringResource(R.string.dislike)} (${formatCountUrdu(currentVideo.dislikeCount)})",
                                selected = reaction?.reactionType == "DISLIKE",
                                onClick = { viewModel.toggleReaction(currentVideo, "DISLIKE") },
                                testTag = "detail_dislike_button"
                            )
                        }
                        item {
                            ActionPill(
                                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                label = stringResource(if (isSaved) R.string.saved else R.string.save),
                                selected = isSaved,
                                onClick = { viewModel.toggleSaveVideo(currentVideo) },
                                testTag = "detail_save_button"
                            )
                        }
                        item {
                            ActionPill(
                                icon = Icons.Filled.Share,
                                label = stringResource(R.string.share),
                                selected = false,
                                onClick = { shareVideoIntent(context, currentVideo) }
                            )
                        }
                        item {
                            ActionPill(
                                icon = Icons.Filled.Flag,
                                label = stringResource(R.string.report),
                                selected = false,
                                onClick = { showReportDialog = true }
                            )
                        }
                    }
                }

                if (currentVideo.description.isNotBlank()) {
                    item {
                        Surface(
                            color = PakSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentVideo.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PakCharcoal,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(color = PakOutline)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "تبصرے (${currentVideo.commentCount})",
                        style = MaterialTheme.typography.titleMedium,
                        color = PakCharcoal
                    )
                }

                val topLevelComments = comments.filter { it.parentCommentId.isBlank() }
                if (topLevelComments.isEmpty()) {
                    item {
                        Text(
                            text = "ابھی کوئی تبصرہ نہیں۔ پہلا تبصرہ لکھیں!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PakSubtleText
                        )
                    }
                } else {
                    items(topLevelComments, key = { it.id }) { comment ->
                        val replies = comments.filter { it.parentCommentId == comment.id }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CommentCardRow(
                                comment = comment,
                                isOwner = comment.authorId == viewModel.currentUserId,
                                onReply = { replyTo = comment },
                                onDelete = { viewModel.deleteComment(currentVideo.id, comment.id) }
                            )
                            replies.forEach { reply ->
                                Box(modifier = Modifier.padding(start = 32.dp)) {
                                    CommentCardRow(
                                        comment = reply,
                                        isOwner = reply.authorId == viewModel.currentUserId,
                                        onReply = null,
                                        onDelete = { viewModel.deleteComment(currentVideo.id, reply.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Comment Input Bar
            Surface(
                color = PakWhite,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    replyTo?.let { parent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${parent.authorName} کو جواب دیں",
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { if (it.length <= 500) commentText = it },
                            placeholder = { Text("تبصرہ لکھیں…") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("comment_input"),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    viewModel.postComment(currentVideo, commentText, replyTo) {
                                        commentText = ""
                                        replyTo = null
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PakGreen)
                                .testTag("send_comment_button")
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "تبصرہ بھیجیں", tint = PakWhite)
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog && video != null) {
        ReportContentDialog(
            targetTitle = video!!.title,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                viewModel.submitReport("VIDEO", video!!.id, video!!.title, reason, details)
                showReportDialog = false
            }
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) PakGreenLight else PakSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) PakGreen else PakOutline),
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) PakGreenDark else PakCharcoal,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) PakGreenDark else PakCharcoal
            )
        }
    }
}

@Composable
private fun CommentCardRow(
    comment: CommentItem,
    isOwner: Boolean,
    onReply: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        ChannelAvatar(avatarData = comment.authorAvatar, name = comment.authorName, size = 34.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    color = PakCharcoal
                )
                Text(
                    text = formatRelativeTimeUrdu(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = PakSubtleText
                )
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = PakCharcoal
            )
            if (onReply != null) {
                Text(
                    text = "جواب دیں",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PakGreen,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onReply)
                )
            }
        }
        if (isOwner) {
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف کریں", tint = PakError, modifier = Modifier.size(18.dp))
            }
        }
    }
}
