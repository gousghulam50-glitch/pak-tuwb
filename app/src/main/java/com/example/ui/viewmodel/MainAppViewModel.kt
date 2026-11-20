package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreErrorMessage
import com.example.data.model.Channel
import com.example.data.model.CommentItem
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformConfig
import com.example.data.model.Subscription
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserVideoEntry
import com.example.data.model.VideoItem
import com.example.data.model.VideoReaction
import com.example.data.repository.PakTubeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UploadProgressState(
    val uploading: Boolean = false,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val successVideoId: String? = null
)

class MainAppViewModel(
    private val repository: PakTubeRepository,
    val currentUserId: String?
) : ViewModel() {

    val isSignedIn: Boolean
        get() = !currentUserId.isNullOrBlank()

    init {
        if (!currentUserId.isNullOrBlank()) {
            viewModelScope.launch {
                repository.ensureUserProfile()
            }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(
        fallbackMessage: String,
        initial: UiState<T> = UiState.Loading
    ): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Stream error: $fallbackMessage", firestoreError ?: error)
                emit(UiState.Error(unwrapFirestoreErrorMessage(error, fallbackMessage)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = initial
            )

    // Public catalog streams (work with or without sign-in)
    val platformConfig: StateFlow<PlatformConfig> =
        repository.observePlatformConfig()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(PlatformConfig())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlatformConfig())

    val activeVideos: StateFlow<UiState<List<VideoItem>>> =
        repository.observeActiveVideos()
            .toUiStateFlow("ویڈیوز لوڈ کرنے میں مسئلہ پیش آیا")

    val activeChannels: StateFlow<UiState<List<Channel>>> =
        repository.observeActiveChannels()
            .toUiStateFlow("چینلز لوڈ کرنے میں مسئلہ پیش آیا")

    // Authenticated user-scoped streams
    val userProfile: StateFlow<UiState<UserProfile?>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<UserProfile?>>(UiState.Success(null)).asStateFlow()
        } else {
            repository.observeUserProfile(currentUserId)
                .toUiStateFlow("پروفائل لوڈ کرنے میں مسئلہ پیش آیا")
        }

    val isAdmin: StateFlow<Boolean> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow(false).asStateFlow()
        } else {
            repository.observeIsAdmin(currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(false)
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
        }

    val myVideos: StateFlow<UiState<List<VideoItem>>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<List<VideoItem>>>(UiState.Success(emptyList())).asStateFlow()
        } else {
            repository.observeMyVideos(currentUserId)
                .toUiStateFlow("آپ کی ویڈیوز لوڈ نہ ہو سکیں")
        }

    val myChannel: StateFlow<UiState<Channel?>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<Channel?>>(UiState.Success(null)).asStateFlow()
        } else {
            repository.observeChannel(currentUserId)
                .toUiStateFlow("چینل لوڈ نہ ہو سکا")
        }

    val mySubscriptions: StateFlow<UiState<List<Subscription>>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<List<Subscription>>>(UiState.Success(emptyList())).asStateFlow()
        } else {
            repository.observeMySubscriptions(currentUserId)
                .toUiStateFlow("سبسکرپشنز لوڈ نہ ہو سکیں")
        }

    val savedVideos: StateFlow<UiState<List<UserVideoEntry>>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<List<UserVideoEntry>>>(UiState.Success(emptyList())).asStateFlow()
        } else {
            repository.observeSavedVideos(currentUserId)
                .toUiStateFlow("محفوظ ویڈیوز لوڈ نہ ہو سکیں")
        }

    val watchHistory: StateFlow<UiState<List<UserVideoEntry>>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<List<UserVideoEntry>>>(UiState.Success(emptyList())).asStateFlow()
        } else {
            repository.observeWatchHistory(currentUserId)
                .toUiStateFlow("واچ ہسٹری لوڈ نہ ہو سکی")
        }

    val likedVideos: StateFlow<UiState<List<UserVideoEntry>>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<List<UserVideoEntry>>>(UiState.Success(emptyList())).asStateFlow()
        } else {
            repository.observeLikedVideos(currentUserId)
                .toUiStateFlow("پسندیدہ ویڈیوز لوڈ نہ ہو سکیں")
        }

    val notifications: StateFlow<UiState<List<NotificationItem>>> =
        if (currentUserId.isNullOrBlank()) {
            MutableStateFlow<UiState<List<NotificationItem>>>(UiState.Success(emptyList())).asStateFlow()
        } else {
            repository.observeNotifications(currentUserId)
                .toUiStateFlow("اطلاعات لوڈ نہ ہو سکیں")
        }

    private val _uploadState = MutableStateFlow(UploadProgressState())
    val uploadState: StateFlow<UploadProgressState> = _uploadState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _pendingAuthReason = MutableStateFlow<String?>(null)
    val pendingAuthReason: StateFlow<String?> = _pendingAuthReason.asStateFlow()

    fun requestSignIn(reason: String = "اس فیچر کو استعمال کرنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں") {
        _pendingAuthReason.value = reason
    }

    fun dismissSignInPrompt() {
        _pendingAuthReason.value = null
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun observeSingleVideo(videoId: String): Flow<VideoItem?> =
        repository.observeVideo(videoId).catch { e ->
            if (e is CancellationException) throw e
            emit(null)
        }

    fun observeSingleChannel(channelId: String): Flow<Channel?> =
        repository.observeChannel(channelId).catch { e ->
            if (e is CancellationException) throw e
            emit(null)
        }

    fun observeMyReaction(videoId: String): Flow<VideoReaction?> {
        val uid = currentUserId
        if (uid.isNullOrBlank()) return flowOf(null)
        return repository.observeMyReaction(videoId, uid).catch { e ->
            if (e is CancellationException) throw e
            emit(null)
        }
    }

    fun observeComments(videoId: String): Flow<List<CommentItem>> =
        repository.observeComments(videoId).catch { e ->
            if (e is CancellationException) throw e
            emit(emptyList())
        }

    suspend fun resolvePlayableVideoUri(context: Context, video: VideoItem): Result<Uri> =
        repository.resolvePlayableVideoUri(context, video)

    fun recordVideoView(video: VideoItem) {
        if (!isSignedIn) return
        viewModelScope.launch {
            repository.incrementViewAndRecordHistory(video)
        }
    }

    fun saveChannel(
        name: String,
        handle: String,
        description: String,
        avatarData: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isSignedIn) {
            requestSignIn("چینل بنانے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = repository.createOrUpdateChannel(name, handle, description, avatarData)
            result.onSuccess {
                _snackbarMessage.value = "چینل کی معلومات محفوظ ہو گئیں"
                onComplete(true)
            }.onFailure { err ->
                _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "چینل محفوظ نہ ہو سکا")
                onComplete(false)
            }
        }
    }

    fun toggleSubscription(channel: Channel) {
        if (!isSignedIn) {
            requestSignIn("چینل کو سبسکرائب کرنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
            return
        }
        viewModelScope.launch {
            repository.toggleSubscription(channel)
                .onSuccess { subscribed ->
                    _snackbarMessage.value = if (subscribed) {
                        "${channel.name} کو سبسکرائب کر لیا گیا"
                    } else {
                        "${channel.name} کی سبسکرپشن ختم کر دی گئی"
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "سبسکرپشن اپ ڈیٹ نہ ہو سکی")
                }
        }
    }

    fun toggleReaction(video: VideoItem, reactionType: String) {
        if (!isSignedIn) {
            requestSignIn("پسند یا ناپسند کرنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
            return
        }
        viewModelScope.launch {
            repository.toggleReaction(video, reactionType)
                .onFailure { err ->
                    _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "ردعمل محفوظ نہ ہو سکا")
                }
        }
    }

    fun toggleSaveVideo(video: VideoItem) {
        if (!isSignedIn) {
            requestSignIn("ویڈیو محفوظ کرنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
            return
        }
        viewModelScope.launch {
            repository.toggleSaveVideo(video)
                .onSuccess { saved ->
                    _snackbarMessage.value = if (saved) "ویڈیو محفوظ کر لی گئی" else "ویڈیو محفوظ فہرست سے ہٹا دی گئی"
                }
                .onFailure { err ->
                    _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "محفوظ کرنے میں مسئلہ")
                }
        }
    }

    fun postComment(video: VideoItem, text: String, parentComment: CommentItem? = null, onSuccess: () -> Unit = {}) {
        if (!isSignedIn) {
            requestSignIn("تبصرہ کرنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
            return
        }
        viewModelScope.launch {
            repository.postComment(video, text, parentComment)
                .onSuccess {
                    onSuccess()
                }
                .onFailure { err ->
                    _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "تبصرہ پوسٹ نہ ہو سکا")
                }
        }
    }

    fun deleteComment(videoId: String, commentId: String) {
        if (!isSignedIn) {
            requestSignIn("تبصرہ حذف کرنے کے لیے سائن ان کریں")
            return
        }
        viewModelScope.launch {
            repository.deleteComment(videoId, commentId)
                .onFailure { err ->
                    _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "تبصرہ حذف نہ ہو سکا")
                }
        }
    }

    fun uploadVideo(
        context: Context,
        channel: Channel,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        videoUri: Uri?,
        remoteVideoUrl: String,
        thumbnailUri: Uri?,
        durationSecondsOverride: Long
    ) {
        if (!isSignedIn) {
            requestSignIn("ویڈیو اپ لوڈ کرنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
            return
        }
        if (_uploadState.value.uploading) return
        viewModelScope.launch {
            _uploadState.value = UploadProgressState(uploading = true, progress = 0.05f)
            val result = repository.uploadVideo(
                context = context,
                channel = channel,
                title = title,
                description = description,
                category = category,
                isShort = isShort,
                videoUri = videoUri,
                remoteVideoUrl = remoteVideoUrl,
                thumbnailUri = thumbnailUri,
                durationSecondsOverride = durationSecondsOverride,
                platformConfig = platformConfig.value,
                onProgress = { pct ->
                    _uploadState.value = _uploadState.value.copy(progress = pct)
                }
            )
            result.onSuccess { videoId ->
                _uploadState.value = UploadProgressState(
                    uploading = false,
                    progress = 1f,
                    successVideoId = videoId
                )
                _snackbarMessage.value = if (isShort) "شارٹ کامیابی سے اپ لوڈ ہو گئی" else "ویڈیو کامیابی سے اپ لوڈ ہو گئی"
            }.onFailure { err ->
                val msg = unwrapFirestoreErrorMessage(err, "اپ لوڈ ناکام ہو گیا")
                _uploadState.value = UploadProgressState(uploading = false, progress = 0f, errorMessage = msg)
                _snackbarMessage.value = msg
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadProgressState()
    }

    fun submitReport(
        targetType: String,
        targetId: String,
        targetTitle: String,
        reason: String,
        details: String
    ) {
        if (!isSignedIn) {
            requestSignIn("مواد کی رپورٹ کرنے کے لیے اپنا گوگل اکاؤنٹ منتخب کریں")
            return
        }
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, targetTitle, reason, details)
                .onSuccess {
                    _snackbarMessage.value = "آپ کی رپورٹ موصول ہو گئی ہے، شکریہ"
                }
                .onFailure { err ->
                    _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "رپورٹ جمع نہ ہو سکی")
                }
        }
    }

    fun markNotificationRead(notifId: String) {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            repository.markNotificationRead(uid, notifId)
        }
    }

    fun markAllNotificationsRead() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            repository.markAllNotificationsRead(uid)
        }
    }

    fun deleteOwnVideo(videoId: String) {
        if (!isSignedIn) return
        viewModelScope.launch {
            repository.deleteVideoCompletely(videoId)
                .onSuccess { _snackbarMessage.value = "ویڈیو حذف کر دی گئی" }
                .onFailure { err -> _snackbarMessage.value = unwrapFirestoreErrorMessage(err, "ویڈیو حذف نہ ہو سکی") }
        }
    }

    private companion object {
        const val TAG = "MainAppVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
