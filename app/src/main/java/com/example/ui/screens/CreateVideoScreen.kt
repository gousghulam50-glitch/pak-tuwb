package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.media.ExtractedVideoInfo
import com.example.data.media.MediaProcessor
import com.example.data.model.Channel
import com.example.data.model.PakTubeCategories
import com.example.data.model.UiState
import com.example.ui.components.LoadingStateView
import com.example.ui.components.PakTubeImage
import com.example.ui.components.formatDuration
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateVideoScreen(
    viewModel: MainAppViewModel,
    onUploadSuccess: (videoId: String, isShort: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myChannelState by viewModel.myChannel.collectAsStateWithLifecycle()
    val config by viewModel.platformConfig.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()

    LaunchedEffect(uploadState.successVideoId) {
        uploadState.successVideoId?.let { id ->
            val wasShort = false
            viewModel.resetUploadState()
            onUploadSuccess(id, wasShort)
        }
    }

    if (!viewModel.isSignedIn) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PakWhite)
                .statusBarsPadding()
        ) {
            AccountSignInPromptCard(
                reason = "ویڈیو اپ لوڈ کرنے یا اپنا چینل بنانے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں"
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PakWhite)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "نئی ویڈیو یا شارٹ بنائیں",
            style = MaterialTheme.typography.headlineMedium,
            color = PakCharcoal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "لائیو اسٹریمنگ اور طویل ویڈیوز کی اجازت نہیں ہے۔ زیادہ سے زیادہ دورانیہ: نارمل ${config.maxNormalDurationSeconds}s • شارٹس ${config.maxShortDurationSeconds}s",
            style = MaterialTheme.typography.bodyMedium,
            color = PakSubtleText
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (val chanState = myChannelState) {
            UiState.Loading -> LoadingStateView("چینل کی معلومات لوڈ ہو رہی ہیں…")
            is UiState.Error -> Text(chanState.message, color = PakError)
            is UiState.Success -> {
                val channel = chanState.data
                if (channel == null) {
                    CreateChannelInlineForm(
                        onSave = { name, handle, desc, avatar ->
                            viewModel.saveChannel(name, handle, desc, avatar)
                        }
                    )
                } else {
                    UploadVideoForm(
                        channel = channel,
                        maxShortSeconds = config.maxShortDurationSeconds,
                        maxNormalSeconds = config.maxNormalDurationSeconds,
                        maxUploadMb = config.maxUploadSizeMb,
                        uploading = uploadState.uploading,
                        uploadProgress = uploadState.progress,
                        uploadError = uploadState.errorMessage,
                        onUpload = { title, desc, cat, isShort, videoUri, remoteUrl, thumbUri, durationSec ->
                            viewModel.uploadVideo(
                                context = context,
                                channel = channel,
                                title = title,
                                description = desc,
                                category = cat,
                                isShort = isShort,
                                videoUri = videoUri,
                                remoteVideoUrl = remoteUrl,
                                thumbnailUri = thumbUri,
                                durationSecondsOverride = durationSec
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateChannelInlineForm(
    existingChannel: Channel? = null,
    onSave: (name: String, handle: String, description: String, avatarData: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(existingChannel) { mutableStateOf(existingChannel?.name ?: "") }
    var handle by remember(existingChannel) { mutableStateOf(existingChannel?.handle ?: "@paktube_creator") }
    var description by remember(existingChannel) { mutableStateOf(existingChannel?.description ?: "") }
    var avatarData by remember(existingChannel) { mutableStateOf(existingChannel?.avatarData ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                avatarData = MediaProcessor.compressImageUriToDataUri(context, it, maxDim = 320, quality = 75)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PakWhite),
        border = BorderStroke(1.dp, PakOutline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (existingChannel == null) "پہلے اپنا چینل بنائیں" else "چینل کی معلومات میں ترمیم کریں",
                style = MaterialTheme.typography.titleLarge,
                color = PakGreenDark
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ویڈیو اپ لوڈ کرنے سے پہلے آپ کا PakTube چینل ہونا ضروری ہے",
                style = MaterialTheme.typography.bodyMedium,
                color = PakSubtleText
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(PakSurfaceVariant)
                        .clickable {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarData.isNotBlank()) {
                        PakTubeImage(avatarData, "Avatar", Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.Image, contentDescription = "تصویر منتخب کریں", tint = PakGreen)
                    }
                }
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Text("چینل کی تصویر منتخب کریں", color = PakGreen)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                label = { Text("چینل کا نام") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("channel_name_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = handle,
                onValueChange = { if (it.length <= 40) handle = it },
                label = { Text("چینل ہینڈل (مثلاً @mychannel)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 500) description = it },
                label = { Text("چینل کی تفصیل") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            if (validationError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(validationError ?: "", color = PakError, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (name.trim().length < 2) {
                        validationError = "چینل کا نام کم از کم 2 حروف کا ہونا چاہیے"
                    } else {
                        validationError = null
                        onSave(name, handle, description, avatarData)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PakGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_channel_button")
            ) {
                Text(if (existingChannel == null) "چینل بنائیں" else "تبدیلیاں محفوظ کریں")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadVideoForm(
    channel: Channel,
    maxShortSeconds: Long,
    maxNormalSeconds: Long,
    maxUploadMb: Long,
    uploading: Boolean,
    uploadProgress: Float,
    uploadError: String?,
    onUpload: (
        title: String,
        desc: String,
        category: String,
        isShort: Boolean,
        videoUri: Uri?,
        remoteUrl: String,
        thumbUri: Uri?,
        durationSecondsOverride: Long
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isShort by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PakTubeCategories.allCategories.first()) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoInfo by remember { mutableStateOf<ExtractedVideoInfo?>(null) }
    var remoteVideoUrl by remember { mutableStateOf("") }
    var useRemoteUrl by remember { mutableStateOf(false) }
    var selectedThumbUri by remember { mutableStateOf<Uri?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedVideoUri = it
            localError = null
            scope.launch {
                runCatching { MediaProcessor.extractVideoInfo(context, it) }
                    .onSuccess { info ->
                        videoInfo = info
                        val maxAllowed = if (isShort) maxShortSeconds else maxNormalSeconds
                        if (info.durationSeconds > maxAllowed) {
                            localError = "منتخب ویڈیو ${info.durationSeconds}s کی ہے جو مقررہ حد ($maxAllowed s) سے زیادہ ہے"
                        }
                    }
                    .onFailure { err ->
                        localError = err.localizedMessage ?: "ویڈیو کا معائنہ نہ ہو سکا"
                    }
            }
        }
    }

    val thumbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedThumbUri = uri
    }

    // Format selector: Normal vs Short (Strictly no Long-Form or Live)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FormatChoiceCard(
            title = "نارمل ویڈیو",
            subtitle = "زیادہ سے زیادہ ${maxNormalSeconds}s",
            icon = Icons.Filled.Videocam,
            selected = !isShort,
            onClick = { isShort = false },
            modifier = Modifier.weight(1f)
        )
        FormatChoiceCard(
            title = "شارٹ ویڈیو (9:16)",
            subtitle = "زیادہ سے زیادہ ${maxShortSeconds}s",
            icon = Icons.Filled.Bolt,
            selected = isShort,
            onClick = { isShort = true },
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Surface(
        color = PakGreenLight.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "چینل: ${channel.name} (${channel.handle})",
            style = MaterialTheme.typography.labelLarge,
            color = PakGreenDark,
            modifier = Modifier.padding(12.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !useRemoteUrl,
            onClick = { useRemoteUrl = false },
            label = { Text("فون سے ویڈیو منتخب کریں") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PakGreenLight,
                selectedLabelColor = PakGreenDark
            )
        )
        FilterChip(
            selected = useRemoteUrl,
            onClick = { useRemoteUrl = true },
            label = { Text("HTTPS ویڈیو لنک") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PakGreenLight,
                selectedLabelColor = PakGreenDark
            )
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (!useRemoteUrl) {
        OutlinedButton(
            onClick = {
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            border = BorderStroke(1.2.dp, PakGreen),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("pick_video_button")
        ) {
            Icon(Icons.Filled.VideoFile, contentDescription = null, tint = PakGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedVideoUri == null) "گیلری سے ویڈیو منتخب کریں (زیادہ سے زیادہ ${maxUploadMb}MB)" else "ویڈیو تبدیل کریں",
                color = PakGreenDark
            )
        }

        videoInfo?.let { info ->
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = PakSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (info.autoThumbnailDataUri.isNotBlank()) {
                        PakTubeImage(
                            source = info.autoThumbnailDataUri,
                            contentDescription = "Auto preview",
                            modifier = Modifier
                                .size(72.dp, 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "دورانیہ: ${formatDuration(info.durationSeconds)} (${info.durationSeconds}s)",
                            style = MaterialTheme.typography.labelLarge,
                            color = PakCharcoal
                        )
                        val mb = String.format("%.2f MB", info.sizeBytes / (1024f * 1024f))
                        Text(
                            text = "سائز: $mb • ریزولوشن: ${info.width}×${info.height}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PakSubtleText
                        )
                    }
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PakGreen)
                }
            }
        }
    } else {
        OutlinedTextField(
            value = remoteVideoUrl,
            onValueChange = { remoteVideoUrl = it },
            label = { Text("براہِ راست MP4/HLS HTTPS لنک") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    if (!isShort) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                thumbPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("pick_thumbnail_button")
        ) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = PakGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedThumbUri == null) "کسٹم تھمب نیل اپ لوڈ کریں (اختیاری)" else "تھمب نیل منتخب ہو گیا ✓",
                color = PakCharcoal
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = title,
        onValueChange = { if (it.length <= 120) title = it },
        label = { Text("ویڈیو کا عنوان") },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_title_input"),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = description,
        onValueChange = { if (it.length <= 1000) description = it },
        label = { Text("تفصیل") },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_description_input"),
        maxLines = 4
    )

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "کیٹیگری منتخب کریں",
        style = MaterialTheme.typography.labelLarge,
        color = PakCharcoal
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PakTubeCategories.allCategories.forEach { cat ->
            FilterChip(
                selected = category == cat,
                onClick = { category = cat },
                label = { Text(cat) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PakGreen,
                    selectedLabelColor = PakWhite
                )
            )
        }
    }

    val errorToShow = localError ?: uploadError
    if (!errorToShow.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(errorToShow, color = PakError, style = MaterialTheme.typography.bodyMedium)
    }

    if (uploading) {
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { uploadProgress.coerceIn(0f, 1f) },
            color = PakGreen,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "اپ لوڈ ہو رہا ہے… ${(uploadProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = PakGreenDark
        )
    }

    Spacer(modifier = Modifier.height(18.dp))
    Button(
        onClick = {
            localError = null
            if (title.trim().length < 2) {
                localError = "براہ کرم کم از کم 2 حروف کا عنوان درج کریں"
                return@Button
            }
            if (!useRemoteUrl && selectedVideoUri == null) {
                localError = "براہ کرم ویڈیو فائل منتخب کریں"
                return@Button
            }
            if (useRemoteUrl && !remoteVideoUrl.trim().startsWith("https://")) {
                localError = "درست HTTPS ویڈیو لنک درج کریں"
                return@Button
            }
            onUpload(
                title,
                description,
                category,
                isShort,
                if (useRemoteUrl) null else selectedVideoUri,
                if (useRemoteUrl) remoteVideoUrl else "",
                selectedThumbUri,
                videoInfo?.durationSeconds ?: 30L
            )
        },
        enabled = !uploading,
        colors = ButtonDefaults.buttonColors(containerColor = PakGreen),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("publish_video_button")
    ) {
        Icon(Icons.Filled.CloudUpload, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isShort) "شارٹ شائع کریں" else "ویڈیو شائع کریں",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun FormatChoiceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PakGreenLight else PakSurfaceVariant,
        border = BorderStroke(1.5.dp, if (selected) PakGreen else PakOutline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) PakGreen else PakSubtleText)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) PakGreenDark else PakCharcoal
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = PakSubtleText
            )
        }
    }
}
