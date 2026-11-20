package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val suspendedFlag: Boolean = false,
    val suspendedReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Channel(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val handle: String = "",
    val description: String = "",
    val avatarData: String = "",
    val subscriberCount: Long = 0L,
    val videoCount: Long = 0L,
    val activeFlag: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subscription(
    @DocumentId val id: String = "",
    val subscriberId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val channelAvatar: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoItem(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val channelAvatar: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "تفریح",
    val shortFlag: Boolean = false,
    val durationSeconds: Long = 0L,
    val storageType: String = "CHUNKED",
    val chunkCount: Long = 0L,
    val videoUrl: String = "",
    val thumbnailData: String = "",
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val dislikeCount: Long = 0L,
    val commentCount: Long = 0L,
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoReaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val reactionType: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommentItem(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val parentCommentId: String = "",
    val text: String = "",
    val likeCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserVideoEntry(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val title: String = "",
    val thumbnailData: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val shortFlag: Boolean = false,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val recordedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = "NEW_VIDEO",
    val title: String = "",
    val message: String = "",
    val videoId: String = "",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ContentReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "VIDEO",
    val targetId: String = "",
    val targetTitle: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlatformConfig(
    val maxShortDurationSeconds: Long = 60L,
    val maxNormalDurationSeconds: Long = 180L,
    val maxUploadSizeMb: Long = 12L,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminRole(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val grantedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

object PakTubeCategories {
    val allCategories = listOf(
        "تعلیم",
        "ٹیکنالوجی",
        "تفریح",
        "کھیل",
        "نعت و کلام",
        "کھانا",
        "وی لاگ",
        "خبریں"
    )

    val reportReasons = listOf(
        "نامناسب یا غیر اخلاقی مواد",
        "غلط معلومات یا افواہ",
        "اسپام یا دھوکہ دہی",
        "نفرت انگیز بیان",
        "کاپی رائٹ خلاف ورزی"
    )
}
