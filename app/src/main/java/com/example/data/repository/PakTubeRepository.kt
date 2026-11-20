package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.media.MediaProcessor
import com.example.data.model.AdminRole
import com.example.data.model.Channel
import com.example.data.model.CommentItem
import com.example.data.model.ContentReport
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformConfig
import com.example.data.model.Subscription
import com.example.data.model.UserProfile
import com.example.data.model.UserVideoEntry
import com.example.data.model.VideoItem
import com.example.data.model.VideoReaction
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PakTubeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("صارف کا گوگل اکاؤنٹ سے سائن ان ہونا ضروری ہے")

    private fun <T> Flow<T>.guardFirestoreStream(
        operationType: OperationType,
        path: String,
        uid: String?
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (uid == null || auth.currentUser?.uid == uid)) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- AUTH & USER PROFILE ---

    suspend fun ensureUserProfile(): Result<Unit> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("لاگ ان نہیں ہے"))
        val uid = currentUser.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val safeName = (currentUser.displayName?.takeIf { it.isNotBlank() } ?: "PakTube User").take(80)
            val safeEmail = (currentUser.email?.takeIf { it.isNotBlank() } ?: "$uid@paktube.app").take(200)
            val safePhoto = (currentUser.photoUrl?.toString() ?: "").take(290_000)

            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "suspendedFlag" to false,
                    "suspendedReason" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "photoUrl" to safePhoto,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val activeUid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .guardFirestoreStream(OperationType.GET, path, activeUid)
        )
    }

    fun observeIsAdmin(uid: String): Flow<Boolean> = flow {
        val activeUid = requireUserId()
        val currentEmail = auth.currentUser?.email
        val emailVerified = auth.currentUser?.isEmailVerified == true
        val bootstrapped = currentEmail == BOOTSTRAPPED_ADMIN_EMAIL && emailVerified
        val path = "admins/$uid"
        emitAll(
            db.collection("admins").document(uid)
                .snapshots()
                .map { snap -> snap.exists() || bootstrapped }
                .catch { error ->
                    if (bootstrapped) {
                        emit(true)
                    } else {
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        if (auth.currentUser?.uid == activeUid) {
                            handleFirestoreError(error, OperationType.GET, path)
                        }
                        emit(false)
                    }
                }
        )
    }

    // --- PLATFORM CONFIG ---

    fun observePlatformConfig(): Flow<PlatformConfig> = flow {
        val path = "platform_config/settings"
        emitAll(
            db.collection("platform_config").document("settings")
                .snapshots()
                .map { snap ->
                    snap.toObject(PlatformConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PlatformConfig()
                }
                .guardFirestoreStream(OperationType.GET, path, null)
        )
    }

    suspend fun updatePlatformConfig(
        maxShortSeconds: Long,
        maxNormalSeconds: Long,
        maxUploadMb: Long
    ): Result<Unit> {
        val path = "platform_config/settings"
        return try {
            val uid = requireUserId()
            require(maxShortSeconds in 10..180) { "شارٹ ویڈیو کا دورانیہ 10 سے 180 سیکنڈ کے درمیان ہونا چاہیے" }
            require(maxNormalSeconds in 30..600) { "نارمل ویڈیو کا دورانیہ 30 سے 600 سیکنڈ کے درمیان ہونا چاہیے" }
            require(maxUploadMb in 1..25) { "زیادہ سے زیادہ فائل سائز 1 سے 25 MB کے درمیان ہونا چاہیے" }

            db.collection("platform_config").document("settings").set(
                mapOf(
                    "maxShortDurationSeconds" to maxShortSeconds,
                    "maxNormalDurationSeconds" to maxNormalSeconds,
                    "maxUploadSizeMb" to maxUploadMb,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- CHANNELS & SUBSCRIPTIONS ---

    fun observeActiveChannels(): Flow<List<Channel>> = flow {
        val path = "channels"
        emitAll(
            db.collection("channels")
                .whereEqualTo("activeFlag", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.subscriberCount }
                }
                .guardFirestoreStream(OperationType.LIST, path, null)
        )
    }

    fun observeChannel(channelId: String): Flow<Channel?> = flow {
        val path = "channels/$channelId"
        emitAll(
            db.collection("channels").document(channelId)
                .snapshots()
                .map { snap -> snap.toObject(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .guardFirestoreStream(OperationType.GET, path, null)
        )
    }

    suspend fun createOrUpdateChannel(
        name: String,
        handle: String,
        description: String,
        avatarData: String
    ): Result<Unit> {
        val cleanName = name.trim()
        val cleanHandle = handle.trim().let { if (it.startsWith("@")) it else "@$it" }
        val cleanDesc = description.trim()
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("لاگ ان ضروری ہے"))
        val path = "channels/$uid"
        return try {
            requireUserId()
            require(cleanName.length in 2..60) { "چینل کا نام 2 سے 60 حروف کا ہونا چاہیے" }
            require(cleanHandle.length in 2..40) { "چینل ہینڈل 2 سے 40 حروف کا ہونا چاہیے" }
            require(cleanDesc.length <= 500) { "تفصیل 500 حروف سے زیادہ نہیں ہو سکتی" }

            val ref = db.collection("channels").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "ownerId" to uid,
                        "name" to cleanName,
                        "handle" to cleanHandle,
                        "description" to cleanDesc,
                        "avatarData" to avatarData.take(290_000),
                        "subscriberCount" to 0L,
                        "videoCount" to 0L,
                        "activeFlag" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "name" to cleanName,
                        "handle" to cleanHandle,
                        "description" to cleanDesc,
                        "avatarData" to avatarData.take(290_000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeMySubscriptions(uid: String): Flow<List<Subscription>> = flow {
        val activeUid = requireUserId()
        val path = "subscriptions"
        emitAll(
            db.collection("subscriptions")
                .whereEqualTo("subscriberId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Subscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .guardFirestoreStream(OperationType.LIST, path, activeUid)
        )
    }

    suspend fun toggleSubscription(channel: Channel): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("لاگ ان ضروری ہے"))
        if (channel.id == uid) {
            return Result.failure(IllegalArgumentException("آپ اپنے ہی چینل کو سبسکرائب نہیں کر سکتے"))
        }
        val subId = "${uid}_${channel.id}"
        val subRef = db.collection("subscriptions").document(subId)
        val channelRef = db.collection("channels").document(channel.id)
        return try {
            requireUserId()
            val isNowSubscribed = db.runTransaction { tx ->
                val subSnap = tx.get(subRef)
                val chanSnap = tx.get(channelRef)
                val currentSubs = chanSnap.getLong("subscriberCount") ?: 0L
                if (subSnap.exists()) {
                    tx.delete(subRef)
                    tx.update(
                        channelRef,
                        mapOf(
                            "subscriberCount" to (currentSubs - 1L).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    false
                } else {
                    tx.set(
                        subRef,
                        mapOf(
                            "subscriberId" to uid,
                            "channelId" to channel.id,
                            "channelName" to channel.name.take(60),
                            "channelAvatar" to channel.avatarData.take(290_000),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.update(
                        channelRef,
                        mapOf(
                            "subscriberCount" to (currentSubs + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    true
                }
            }.await()

            if (isNowSubscribed) {
                val senderName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "صارف"
                sendNotificationInternal(
                    recipientId = channel.ownerId,
                    senderId = uid,
                    senderName = senderName,
                    type = "SUBSCRIBE",
                    title = "نیا سبسکرائبر",
                    message = "$senderName نے آپ کے چینل ${channel.name} کو سبسکرائب کیا",
                    videoId = ""
                )
            }
            Result.success(isNowSubscribed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, subRef.path)
            Result.failure(e)
        }
    }

    // --- VIDEOS & SHORTS ---

    fun observeActiveVideos(): Flow<List<VideoItem>> = flow {
        val path = "videos"
        emitAll(
            db.collection("videos")
                .whereEqualTo("status", "ACTIVE")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, path, null)
        )
    }

    fun observeMyVideos(uid: String): Flow<List<VideoItem>> = flow {
        val activeUid = requireUserId()
        val path = "videos"
        emitAll(
            db.collection("videos")
                .whereEqualTo("ownerId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "ACTIVE" }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, path, activeUid)
        )
    }

    fun observeVideo(videoId: String): Flow<VideoItem?> = flow {
        val path = "videos/$videoId"
        emitAll(
            db.collection("videos").document(videoId)
                .snapshots()
                .map { snap -> snap.toObject(VideoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .guardFirestoreStream(OperationType.GET, path, null)
        )
    }

    suspend fun uploadVideo(
        context: Context,
        channel: Channel,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        videoUri: Uri?,
        remoteVideoUrl: String,
        thumbnailUri: Uri?,
        durationSecondsOverride: Long,
        platformConfig: PlatformConfig,
        onProgress: (Float) -> Unit
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("لاگ ان ضروری ہے"))
        val videoRef = db.collection("videos").document()
        val videoId = videoRef.id
        return try {
            requireUserId()
            val cleanTitle = title.trim()
            val cleanDesc = description.trim()
            require(cleanTitle.length in 2..120) { "عنوان 2 سے 120 حروف کا ہونا چاہیے" }
            require(cleanDesc.length <= 1000) { "تفصیل 1000 حروف سے کم ہونی چاہیے" }

            val maxDuration = if (isShort) {
                platformConfig.maxShortDurationSeconds
            } else {
                platformConfig.maxNormalDurationSeconds
            }

            var finalDuration = durationSecondsOverride.coerceAtLeast(1L)
            var finalThumb = ""
            var chunkCount = 0L
            var storageType = "URL"
            var cleanRemoteUrl = remoteVideoUrl.trim()

            if (videoUri != null) {
                onProgress(0.08f)
                val info = MediaProcessor.extractVideoInfo(context, videoUri)
                finalDuration = info.durationSeconds
                if (finalDuration > maxDuration) {
                    val kind = if (isShort) "شارٹ" else "ویڈیو"
                    throw IllegalArgumentException("یہ $kind $finalDuration سیکنڈ کی ہے۔ ایڈمن کی مقرر کردہ حد $maxDuration سیکنڈ ہے (طویل ویڈیوز کی اجازت نہیں)")
                }
                val maxBytes = platformConfig.maxUploadSizeMb * 1024L * 1024L
                if (info.sizeBytes > maxBytes) {
                    throw IllegalArgumentException("فائل کا سائز ${platformConfig.maxUploadSizeMb} MB کی حد سے زیادہ ہے")
                }

                finalThumb = if (thumbnailUri != null) {
                    MediaProcessor.compressImageUriToDataUri(context, thumbnailUri).ifBlank { info.autoThumbnailDataUri }
                } else {
                    info.autoThumbnailDataUri
                }

                onProgress(0.18f)
                val chunks = MediaProcessor.readVideoIntoBase64Chunks(context, videoUri, maxBytes)
                storageType = "CHUNKED"
                chunkCount = chunks.size.toLong()
                cleanRemoteUrl = ""

                chunks.forEachIndexed { idx, chunkB64 ->
                    videoRef.collection("chunks").document(idx.toString()).set(
                        mapOf(
                            "ownerId" to uid,
                            "index" to idx.toLong(),
                            "data" to chunkB64
                        )
                    ).await()
                    val pct = 0.20f + 0.70f * ((idx + 1).toFloat() / chunks.size.toFloat())
                    onProgress(pct)
                }
            } else {
                require(cleanRemoteUrl.startsWith("https://")) { "ویڈیو فائل منتخب کریں یا درست HTTPS لنک درج کریں" }
                require(finalDuration <= maxDuration) {
                    "زیادہ سے زیادہ دورانیہ $maxDuration سیکنڈ ہے (طویل ویڈیوز کی اجازت نہیں)"
                }
                if (thumbnailUri != null) {
                    finalThumb = MediaProcessor.compressImageUriToDataUri(context, thumbnailUri)
                }
                onProgress(0.7f)
            }

            val payload = mapOf(
                "ownerId" to uid,
                "channelId" to channel.id,
                "channelName" to channel.name.take(60),
                "channelAvatar" to channel.avatarData.take(290_000),
                "title" to cleanTitle,
                "description" to cleanDesc,
                "category" to category,
                "shortFlag" to isShort,
                "durationSeconds" to finalDuration,
                "storageType" to storageType,
                "chunkCount" to chunkCount,
                "videoUrl" to cleanRemoteUrl.take(1000),
                "thumbnailData" to finalThumb.take(390_000),
                "viewCount" to 0L,
                "likeCount" to 0L,
                "dislikeCount" to 0L,
                "commentCount" to 0L,
                "status" to "ACTIVE",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            videoRef.set(payload).await()

            val chanRef = db.collection("channels").document(channel.id)
            db.runTransaction { tx ->
                val snap = tx.get(chanRef)
                val count = snap.getLong("videoCount") ?: 0L
                tx.update(
                    chanRef,
                    mapOf(
                        "videoCount" to (count + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            // Notify subscribers about new upload
            val subs = db.collection("subscriptions")
                .whereEqualTo("channelId", channel.id)
                .get()
                .await()
            for (subDoc in subs.documents) {
                val subUid = subDoc.getString("subscriberId") ?: continue
                if (subUid != uid) {
                    sendNotificationInternal(
                        recipientId = subUid,
                        senderId = uid,
                        senderName = channel.name,
                        type = "NEW_VIDEO",
                        title = "${channel.name} کی نئی ویڈیو",
                        message = cleanTitle,
                        videoId = videoId
                    )
                }
            }

            onProgress(1f)
            Result.success(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, videoRef.path)
            Result.failure(e)
        }
    }

    suspend fun resolvePlayableVideoUri(context: Context, video: VideoItem): Result<Uri> {
        return try {
            if (video.storageType == "URL" && video.videoUrl.isNotBlank()) {
                return Result.success(Uri.parse(video.videoUrl))
            }
            val cached = MediaProcessor.getCachedVideoFile(context, video.id)
            if (cached.exists() && cached.length() > 0L) {
                return Result.success(Uri.fromFile(cached))
            }
            val snap = db.collection("videos").document(video.id)
                .collection("chunks")
                .get()
                .await()
            val ordered = snap.documents
                .mapNotNull { doc ->
                    val idx = doc.getLong("index") ?: return@mapNotNull null
                    val data = doc.getString("data") ?: return@mapNotNull null
                    idx to data
                }
                .sortedBy { it.first }
                .map { it.second }
            if (ordered.isEmpty()) {
                return Result.failure(IllegalStateException("ویڈیو کا مواد دستیاب نہیں ہے"))
            }
            val uri = MediaProcessor.assembleChunksToCache(context, video.id, ordered)
            Result.success(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "videos/${video.id}/chunks")
            Result.failure(e)
        }
    }

    suspend fun incrementViewAndRecordHistory(video: VideoItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val videoRef = db.collection("videos").document(video.id)
        val histRef = db.collection("users").document(uid).collection("watchHistory").document(video.id)
        return try {
            requireUserId()
            db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                val views = vSnap.getLong("viewCount") ?: 0L
                tx.update(
                    videoRef,
                    mapOf(
                        "viewCount" to (views + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            histRef.set(
                mapOf(
                    "videoId" to video.id,
                    "title" to video.title.take(120),
                    "thumbnailData" to video.thumbnailData.take(390_000),
                    "channelId" to video.channelId,
                    "channelName" to video.channelName.take(60),
                    "shortFlag" to video.shortFlag,
                    "durationSeconds" to video.durationSeconds,
                    "recordedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, videoRef.path)
            Result.failure(e)
        }
    }

    // --- SINGLE ACTIVE REACTION (LIKE / DISLIKE) ---

    fun observeMyReaction(videoId: String, uid: String): Flow<VideoReaction?> = flow {
        val activeUid = requireUserId()
        val path = "videos/$videoId/reactions/$uid"
        emitAll(
            db.collection("videos").document(videoId)
                .collection("reactions").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(VideoReaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .guardFirestoreStream(OperationType.GET, path, activeUid)
        )
    }

    suspend fun toggleReaction(video: VideoItem, reactionType: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("لاگ ان ضروری ہے"))
        require(reactionType == "LIKE" || reactionType == "DISLIKE")
        val videoRef = db.collection("videos").document(video.id)
        val reactRef = videoRef.collection("reactions").document(uid)
        val likedIndexRef = db.collection("users").document(uid).collection("likedVideos").document(video.id)
        return try {
            requireUserId()
            val becameLike = db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                val rSnap = tx.get(reactRef)
                var likes = vSnap.getLong("likeCount") ?: 0L
                var dislikes = vSnap.getLong("dislikeCount") ?: 0L
                val existing = rSnap.getString("reactionType")

                var nowLiked = false
                if (existing == reactionType) {
                    // Toggle off
                    tx.delete(reactRef)
                    if (reactionType == "LIKE") likes = (likes - 1L).coerceAtLeast(0L)
                    else dislikes = (dislikes - 1L).coerceAtLeast(0L)
                } else {
                    if (existing == "LIKE") likes = (likes - 1L).coerceAtLeast(0L)
                    if (existing == "DISLIKE") dislikes = (dislikes - 1L).coerceAtLeast(0L)
                    if (reactionType == "LIKE") {
                        likes += 1L
                        nowLiked = true
                    } else {
                        dislikes += 1L
                    }
                    tx.set(
                        reactRef,
                        mapOf(
                            "userId" to uid,
                            "reactionType" to reactionType,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.update(
                    videoRef,
                    mapOf(
                        "likeCount" to likes,
                        "dislikeCount" to dislikes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nowLiked
            }.await()

            if (becameLike) {
                likedIndexRef.set(
                    mapOf(
                        "videoId" to video.id,
                        "title" to video.title.take(120),
                        "thumbnailData" to video.thumbnailData.take(390_000),
                        "channelId" to video.channelId,
                        "channelName" to video.channelName.take(60),
                        "shortFlag" to video.shortFlag,
                        "durationSeconds" to video.durationSeconds,
                        "recordedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (video.ownerId != uid) {
                    val senderName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "صارف"
                    sendNotificationInternal(
                        recipientId = video.ownerId,
                        senderId = uid,
                        senderName = senderName,
                        type = "LIKE",
                        title = "آپ کی ویڈیو کو پسند کیا گیا",
                        message = "$senderName نے \"${video.title}\" کو لائک کیا",
                        videoId = video.id
                    )
                }
            } else {
                runCatching { likedIndexRef.delete().await() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reactRef.path)
            Result.failure(e)
        }
    }

    // --- COMMENTS & REPLIES ---

    fun observeComments(videoId: String): Flow<List<CommentItem>> = flow {
        val path = "videos/$videoId/comments"
        emitAll(
            db.collection("videos").document(videoId).collection("comments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CommentItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, path, null)
        )
    }

    suspend fun postComment(
        video: VideoItem,
        text: String,
        parentComment: CommentItem? = null
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("لاگ ان ضروری ہے"))
        val uid = user.uid
        val cleanText = text.trim()
        val videoRef = db.collection("videos").document(video.id)
        val commentRef = videoRef.collection("comments").document()
        return try {
            requireUserId()
            require(cleanText.length in 1..500) { "تبصرہ 1 سے 500 حروف کا ہونا چاہیے" }
            val authorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "صارف").take(80)
            val authorAvatar = (user.photoUrl?.toString() ?: "").take(290_000)

            commentRef.set(
                mapOf(
                    "videoId" to video.id,
                    "authorId" to uid,
                    "authorName" to authorName,
                    "authorAvatar" to authorAvatar,
                    "parentCommentId" to (parentComment?.id ?: ""),
                    "text" to cleanText,
                    "likeCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                val count = vSnap.getLong("commentCount") ?: 0L
                tx.update(
                    videoRef,
                    mapOf(
                        "commentCount" to (count + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            if (parentComment != null && parentComment.authorId != uid) {
                sendNotificationInternal(
                    recipientId = parentComment.authorId,
                    senderId = uid,
                    senderName = authorName,
                    type = "REPLY",
                    title = "تبصرے پر نیا جواب",
                    message = "$authorName نے جواب دیا: $cleanText".take(300),
                    videoId = video.id
                )
            } else if (video.ownerId != uid) {
                sendNotificationInternal(
                    recipientId = video.ownerId,
                    senderId = uid,
                    senderName = authorName,
                    type = "COMMENT",
                    title = "آپ کی ویڈیو پر نیا تبصرہ",
                    message = "$authorName: $cleanText".take(300),
                    videoId = video.id
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(videoId: String, commentId: String): Result<Unit> {
        val commentRef = db.collection("videos").document(videoId).collection("comments").document(commentId)
        val videoRef = db.collection("videos").document(videoId)
        return try {
            requireUserId()
            commentRef.delete().await()
            db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                val count = vSnap.getLong("commentCount") ?: 0L
                tx.update(
                    videoRef,
                    mapOf(
                        "commentCount" to (count - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commentRef.path)
            Result.failure(e)
        }
    }

    // --- USER LIBRARY: SAVED, LIKED, HISTORY, NOTIFICATIONS ---

    fun observeSavedVideos(uid: String): Flow<List<UserVideoEntry>> = flow {
        val activeUid = requireUserId()
        val path = "users/$uid/savedVideos"
        emitAll(
            db.collection("users").document(uid).collection("savedVideos")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserVideoEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.recordedAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, path, activeUid)
        )
    }

    fun observeWatchHistory(uid: String): Flow<List<UserVideoEntry>> = flow {
        val activeUid = requireUserId()
        val path = "users/$uid/watchHistory"
        emitAll(
            db.collection("users").document(uid).collection("watchHistory")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserVideoEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.recordedAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, path, activeUid)
        )
    }

    fun observeLikedVideos(uid: String): Flow<List<UserVideoEntry>> = flow {
        val activeUid = requireUserId()
        val path = "users/$uid/likedVideos"
        emitAll(
            db.collection("users").document(uid).collection("likedVideos")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserVideoEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.recordedAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, path, activeUid)
        )
    }

    suspend fun toggleSaveVideo(video: VideoItem): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("لاگ ان ضروری ہے"))
        val ref = db.collection("users").document(uid).collection("savedVideos").document(video.id)
        return try {
            requireUserId()
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.delete().await()
                Result.success(false)
            } else {
                ref.set(
                    mapOf(
                        "videoId" to video.id,
                        "title" to video.title.take(120),
                        "thumbnailData" to video.thumbnailData.take(390_000),
                        "channelId" to video.channelId,
                        "channelName" to video.channelName.take(60),
                        "shortFlag" to video.shortFlag,
                        "durationSeconds" to video.durationSeconds,
                        "recordedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> = flow {
        val activeUid = requireUserId()
        val path = "users/$uid/notifications"
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, path, activeUid)
        )
    }

    suspend fun markNotificationRead(uid: String, notifId: String): Result<Unit> {
        val ref = db.collection("users").document(uid).collection("notifications").document(notifId)
        return try {
            requireUserId()
            ref.update("readFlag", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(uid: String): Result<Unit> {
        val path = "users/$uid/notifications"
        return try {
            requireUserId()
            val snap = db.collection("users").document(uid).collection("notifications")
                .whereEqualTo("readFlag", false)
                .get()
                .await()
            val batch = db.batch()
            for (doc in snap.documents) {
                batch.update(doc.reference, "readFlag", true)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun sendNotificationInternal(
        recipientId: String,
        senderId: String,
        senderName: String,
        type: String,
        title: String,
        message: String,
        videoId: String
    ) {
        if (recipientId.isBlank() || senderId.isBlank()) return
        val notifRef = db.collection("users").document(recipientId).collection("notifications").document()
        runCatching {
            notifRef.set(
                mapOf(
                    "recipientId" to recipientId,
                    "senderId" to senderId,
                    "senderName" to senderName.take(80),
                    "type" to type,
                    "title" to title.take(100),
                    "message" to message.take(300),
                    "videoId" to videoId.take(128),
                    "readFlag" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, notifRef.path)
        }
    }

    // --- REPORTING & ADMIN MODERATION ---

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        targetTitle: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("لاگ ان ضروری ہے"))
        val ref = db.collection("reports").document()
        return try {
            requireUserId()
            require(reason.trim().length >= 2) { "رپورٹ کی وجہ منتخب کریں" }
            ref.set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "targetTitle" to targetTitle.take(150).ifBlank { targetId },
                    "reason" to reason.trim().take(100),
                    "details" to details.trim().take(500),
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeAllUsersAdmin(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap -> snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .guardFirestoreStream(OperationType.LIST, "users", uid)
        )
    }

    fun observeAllChannelsAdmin(): Flow<List<Channel>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("channels")
                .snapshots()
                .map { snap -> snap.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .guardFirestoreStream(OperationType.LIST, "channels", uid)
        )
    }

    fun observeAllVideosAdmin(): Flow<List<VideoItem>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("videos")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, "videos", uid)
        )
    }

    fun observeAllReportsAdmin(): Flow<List<ContentReport>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("reports")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ContentReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(OperationType.LIST, "reports", uid)
        )
    }

    suspend fun setUserSuspended(targetUid: String, suspended: Boolean, reason: String): Result<Unit> {
        val ref = db.collection("users").document(targetUid)
        return try {
            val adminUid = requireUserId()
            ref.update(
                mapOf(
                    "suspendedFlag" to suspended,
                    "suspendedReason" to if (suspended) reason.trim().take(300) else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            sendNotificationInternal(
                recipientId = targetUid,
                senderId = adminUid,
                senderName = "PakTube Admin",
                type = "ADMIN_NOTICE",
                title = if (suspended) "اکاؤنٹ معطل کر دیا گیا" else "اکاؤنٹ بحال کر دیا گیا",
                message = if (suspended) reason.ifBlank { "پالیسی کی خلاف ورزی پر اکاؤنٹ معطل ہوا" } else "آپ کا اکاؤنٹ دوبارہ فعال کر دیا گیا ہے",
                videoId = ""
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun setChannelActive(channelId: String, active: Boolean): Result<Unit> {
        val ref = db.collection("channels").document(channelId)
        return try {
            requireUserId()
            ref.update(
                mapOf(
                    "activeFlag" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun setVideoModerationStatus(videoId: String, status: String): Result<Unit> {
        val ref = db.collection("videos").document(videoId)
        return try {
            requireUserId()
            ref.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteVideoCompletely(videoId: String): Result<Unit> {
        val ref = db.collection("videos").document(videoId)
        return try {
            requireUserId()
            val chunks = ref.collection("chunks").get().await()
            for (c in chunks.documents) {
                c.reference.delete().await()
            }
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun resolveReport(
        report: ContentReport,
        newStatus: String,
        removeTargetContent: Boolean
    ): Result<Unit> {
        val ref = db.collection("reports").document(report.id)
        return try {
            requireUserId()
            if (removeTargetContent) {
                when (report.targetType) {
                    "VIDEO" -> setVideoModerationStatus(report.targetId, "REMOVED")
                    "CHANNEL" -> setChannelActive(report.targetId, false)
                }
            }
            ref.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun grantAdminRole(targetUid: String, targetEmail: String): Result<Unit> {
        val ref = db.collection("admins").document(targetUid)
        return try {
            val adminUid = requireUserId()
            ref.set(
                mapOf(
                    "userId" to targetUid,
                    "email" to targetEmail.trim().take(200),
                    "grantedBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "gousghulam50@gmail.com"
    }
}
