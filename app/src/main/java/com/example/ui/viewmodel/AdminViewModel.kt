package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreErrorMessage
import com.example.data.model.Channel
import com.example.data.model.ContentReport
import com.example.data.model.PlatformConfig
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.repository.PakTubeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val repository: PakTubeRepository,
    val adminUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.toAdminState(fallback: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w("AdminVM", fallback, firestoreError ?: error)
                emit(UiState.Error(unwrapFirestoreErrorMessage(error, fallback)))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allUsers: StateFlow<UiState<List<UserProfile>>> =
        repository.observeAllUsersAdmin().toAdminState("صارفین لوڈ نہ ہو سکے")

    val allChannels: StateFlow<UiState<List<Channel>>> =
        repository.observeAllChannelsAdmin().toAdminState("چینلز لوڈ نہ ہو سکے")

    val allVideos: StateFlow<UiState<List<VideoItem>>> =
        repository.observeAllVideosAdmin().toAdminState("ویڈیوز لوڈ نہ ہو سکیں")

    val allReports: StateFlow<UiState<List<ContentReport>>> =
        repository.observeAllReportsAdmin().toAdminState("رپورٹس لوڈ نہ ہو سکیں")

    val platformConfig: StateFlow<PlatformConfig> =
        repository.observePlatformConfig()
            .catch { if (it is CancellationException) throw it else emit(PlatformConfig()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlatformConfig())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun updateDurationLimits(maxShortSeconds: Long, maxNormalSeconds: Long, maxUploadMb: Long) {
        viewModelScope.launch {
            repository.updatePlatformConfig(maxShortSeconds, maxNormalSeconds, maxUploadMb)
                .onSuccess { _statusMessage.value = "پلیٹ فارم کی حدود اپ ڈیٹ ہو گئیں" }
                .onFailure { _statusMessage.value = unwrapFirestoreErrorMessage(it, "ترتیبات محفوظ نہ ہو سکیں") }
        }
    }

    fun toggleUserSuspension(user: UserProfile, reason: String) {
        viewModelScope.launch {
            val nextSuspended = !user.suspendedFlag
            repository.setUserSuspended(user.userId, nextSuspended, reason)
                .onSuccess {
                    _statusMessage.value = if (nextSuspended) "اکاؤنٹ معطل کر دیا گیا" else "اکاؤنٹ بحال کر دیا گیا"
                }
                .onFailure { _statusMessage.value = unwrapFirestoreErrorMessage(it, "اکاؤنٹ اپ ڈیٹ نہ ہو سکا") }
        }
    }

    fun toggleChannelActive(channel: Channel) {
        viewModelScope.launch {
            val nextActive = !channel.activeFlag
            repository.setChannelActive(channel.id, nextActive)
                .onSuccess {
                    _statusMessage.value = if (nextActive) "چینل فعال کر دیا گیا" else "چینل معطل کر دیا گیا"
                }
                .onFailure { _statusMessage.value = unwrapFirestoreErrorMessage(it, "چینل اپ ڈیٹ نہ ہو سکا") }
        }
    }

    fun toggleVideoStatus(video: VideoItem) {
        viewModelScope.launch {
            val nextStatus = if (video.status == "ACTIVE") "REMOVED" else "ACTIVE"
            repository.setVideoModerationStatus(video.id, nextStatus)
                .onSuccess {
                    _statusMessage.value = if (nextStatus == "REMOVED") "ویڈیو ہٹا دی گئی" else "ویڈیو بحال کر دی گئی"
                }
                .onFailure { _statusMessage.value = unwrapFirestoreErrorMessage(it, "ویڈیو اپ ڈیٹ نہ ہو سکی") }
        }
    }

    fun deleteVideoPermanently(videoId: String) {
        viewModelScope.launch {
            repository.deleteVideoCompletely(videoId)
                .onSuccess { _statusMessage.value = "ویڈیو مستقل طور پر حذف کر دی گئی" }
                .onFailure { _statusMessage.value = unwrapFirestoreErrorMessage(it, "حذف کرنے میں مسئلہ") }
        }
    }

    fun resolveReport(report: ContentReport, status: String, removeContent: Boolean) {
        viewModelScope.launch {
            repository.resolveReport(report, status, removeContent)
                .onSuccess { _statusMessage.value = "رپورٹ پراسیس ہو گئی" }
                .onFailure { _statusMessage.value = unwrapFirestoreErrorMessage(it, "رپورٹ اپ ڈیٹ نہ ہو سکی") }
        }
    }

    fun grantAdmin(user: UserProfile) {
        viewModelScope.launch {
            repository.grantAdminRole(user.userId, user.email)
                .onSuccess { _statusMessage.value = "${user.displayName} کو ایڈمن بنا دیا گیا" }
                .onFailure { _statusMessage.value = unwrapFirestoreErrorMessage(it, "ایڈمن رول نہ دیا جا سکا") }
        }
    }
}
