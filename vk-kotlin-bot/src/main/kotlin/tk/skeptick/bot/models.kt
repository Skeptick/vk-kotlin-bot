package tk.skeptick.bot

import kotlinx.serialization.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

internal val documentTypes = enumValues<DocumentType>().map { it.id to it }.toMap()
internal val serviceActTypes = enumValues<ServiceActType>().map { it.act to it }.toMap()
internal val attachmentsTypes = enumValues<AttachmentType>().map { it.type to it }.toMap()
internal val typesAttachment = enumValues<AttachmentType>().map { it to it.type }.toMap()
internal val wallPostTypes = enumValues<WallPostTypes>().map { it.type to it }.toMap()

enum class ServiceActType(val act: String) {
    NONE(""),
    UNKNOWN("unknown"),
    CHAT_CREATE("chat_create"),
    CHAT_TITLE_UPDATE("chat_title_update"),
    CHAT_PHOTO_UPDATE("chat_photo_update"),
    CHAT_PHOTO_REMOVE("chat_photo_remove"),
    CHAT_INVITE_USER("chat_invite_user"),
    CHAT_KICK_USER("chat_kick_user")
}

enum class AttachmentType(val type: String) {
    PHOTO("photo"),
    VIDEO("video"),
    AUDIO("audio"),
    DOC("doc"),
    WALL("wall"),
    STICKER("sticker"),
    LINK("link"),
    MONEY("money"),
    GIFT("gift")
}

enum class DocumentType(val id: Int) {
    TEXT(1),
    ARCHIVE(2),
    GIF(3),
    IMAGE(4),
    AUDIO(5),
    VIDEO(6),
    EBOOK(7),
    UNKNOWN(8)
}

enum class WallPostTypes(val type: String) {
    POST("post"),
    COPY("copy"),
    REPLY("reply"),
    POSTPONE("postpone"),
    SUGGEST("suggest")
}

@Serializable
internal data class ResponseWrapper<out T>(
        @Optional @SerialName("error") val error: Error? = null,
        @Optional @SerialName("response") val response: T? = null)

@Serializable
internal data class Error(
        @SerialName("error_code") val errorCode: Int,
        @SerialName("error_msg") val errorMessage: String)

@Serializable
internal data class LongPollServerResponse(
        @SerialName("key") val key: String,
        @SerialName("server") val server: String,
        @SerialName("ts") val ts: Long,
        @SerialName("pts") val pts: Long)

@Serializable
internal data class LongPollResponse(
        @Optional @SerialName("failed") val failed: Int? = null,
        @Optional @SerialName("ts") val ts: Long = 0,
        @Optional @SerialName("pts") val pts: Long = 0)

@Serializable
internal data class LongPollHistoryResponse internal constructor(
        @SerialName("messages") val messages: ListResponse<Message>,
        @Optional @SerialName("new_pts") val newPts: Long? = null)


@Serializable
data class Message internal constructor(
        @SerialName("id") val id: Int,
        @SerialName("user_id") val userId: Int,
        @SerialName("date") val date: Int,
        @SerialName("read_state") private val readState: Int,
        @SerialName("out") private val out: Int,
        @SerialName("body") val body: String,
        @Optional @SerialName("title") val title: String? = null,
        @Optional @SerialName("attachments") val attachments: List<Attachment> = emptyList(),
        @Optional @SerialName("fwd_messages") val forwardedMessages: List<ForwardMessage> = emptyList(),
        @Optional @SerialName("emoji") private val emoji: Int = 0,
        @Optional @SerialName("important") private val important: Int = 0,
        @Optional @SerialName("deleted") private val deleted: Int = 0,
        @Optional @SerialName("chat_id") val chatId: Int? = null,
        @Optional @SerialName("chat_active") val chatActive: List<Int>? = null,
        @Optional @SerialName("users_count") val chatUsersCount: Int? = null,
        @Optional @SerialName("admin_id") val chatAdminId: Int? = null,
        @Optional @SerialName("action") private val action: String? = null,
        @Optional @SerialName("action_mid") val actionMid: Int? = null,
        @Optional @SerialName("action_email") val actionEmail: String? = null,
        @Optional @SerialName("action_text") val actionText: String? = null,
        @Optional @SerialName("photo_50") val chatPhoto50: String? = null,
        @Optional @SerialName("photo_100") val chatPhoto100: String? = null,
        @Optional @SerialName("photo_200") val chatPhoto200: String? = null) {

    @Transient val isRead: Boolean get() = readState == 1
    @Transient val isOutbox: Boolean get() = out == 1
    @Transient val isNotOutbox: Boolean get() = !isOutbox
    @Transient val hasEmoji: Boolean get() = emoji == 1
    @Transient val isImportant: Boolean get() = important == 1
    @Transient val isDeleted: Boolean get() = deleted == 1
    @Transient val isFromChat: Boolean get() = chatId != null
    @Transient val isServiceAct: Boolean get() = action != null

    @Transient val serviceActType: ServiceActType get() =
        if (action != null) serviceActTypes[action] ?: ServiceActType.UNKNOWN
        else ServiceActType.NONE

    @Transient val maxChatPhoto: String? get() =
        chatPhoto200 ?: chatPhoto100 ?: chatPhoto50

}

@Serializable
data class ForwardMessage internal constructor(
        @SerialName("user_id") val userId: Int,
        @SerialName("date") val date: Int,
        @SerialName("body") val body: String,
        @Optional @SerialName("attachments") val attachments: List<Attachment> = emptyList(),
        @Optional @SerialName("fwd_messages") val forwardedMessages: List<ForwardMessage> = emptyList()
)

@Serializable
data class Attachment internal constructor(
        @SerialName("type") private val type: String,
        @Optional @SerialName("photo") val photo: Photo? = null,
        @Optional @SerialName("video") val video: Video? = null,
        @Optional @SerialName("audio") val audio: Audio? = null,
        @Optional @SerialName("doc") val document: Document? = null,
        // TODO wall
        @Optional @SerialName("sticker") val sticker: Sticker? = null) {

    @Transient val attachmentType: AttachmentType? = attachmentsTypes[type]

}

interface MessageAttachment {
    val id: Int
    val ownerId: Int
    val accessKey: String? get() = null
    val typeAttachment: String
}

@Serializable
data class Photo internal constructor(
        @SerialName("id") override val id: Int,
        @SerialName("album_id") val albumId: Int,
        @SerialName("owner_id") override val ownerId: Int,
        @Optional @SerialName("user_id") val userId: Int? = null,
        @SerialName("text") val description: String,
        @SerialName("date") val date: Int,
        @SerialName("photo_75") val photo75: String,
        @Optional @SerialName("photo_130") val photo130: String? = null,
        @Optional @SerialName("photo_604") val photo604: String? = null,
        @Optional @SerialName("photo_807") val photo807: String? = null,
        @Optional @SerialName("photo_1280") val photo1280: String? = null,
        @Optional @SerialName("photo_2560") val photo2560: String? = null,
        @Optional @SerialName("height") val height: Int? = null,
        @Optional @SerialName("width") val width: Int? = null,
        @Optional @SerialName("access_key") override val accessKey: String? = null
) : MessageAttachment {

    @Transient override val typeAttachment get() = typesAttachment[AttachmentType.PHOTO]!!
    @Transient val isInCommunity get() = userId == 100
    @Transient val maxPhoto: String get() =
        photo2560 ?: photo1280 ?: photo807 ?: photo604 ?: photo130 ?: photo75

}

@Serializable
data class Video internal constructor(
        @SerialName("id") override val id: Int,
        @SerialName("owner_id") override val ownerId: Int,
        @SerialName("title") val title: String,
        @SerialName("description") val description: String,
        @SerialName("duration") val duration: Int, // sec
        @SerialName("date") val date: Int,
        @SerialName("views") val viewsCount: Int,
        @SerialName("comments") val commentsCount: Int,
        @Optional @SerialName("platform") val platform: String? = null,
        @Optional @SerialName("adding_date") val addingDate: Int? = null,
        @Optional @SerialName("can_edit") private val edit: Int = 0,
        @Optional @SerialName("can_add") private val add: Int = 0,
        @Optional @SerialName("is_private") private val private: Int = 0,
        @Optional @SerialName("processing") private val processing: Int = 0,
        @Optional @SerialName("live") private val live: Int = 0,
        @Optional @SerialName("access_key") override val accessKey: String? = null
) : MessageAttachment {

    @Transient override val typeAttachment get() = typesAttachment[AttachmentType.VIDEO]!!
    @Transient val canEdit: Boolean get() = edit == 1
    @Transient val canAdd: Boolean get() = add == 1
    @Transient val isPrivate: Boolean get() = private == 1
    @Transient val isInProcessing: Boolean get() = processing == 1
    @Transient val isLive: Boolean get() = live == 1

}

@Serializable
data class Audio internal constructor(
        @SerialName("id") override val id: Int,
        @SerialName("owner_id") override val ownerId: Int,
        @SerialName("date") val date: Int,
        @SerialName("artist") val artist: String,
        @SerialName("title") val title: String,
        @SerialName("duration") val duration: Int, // sec
        @SerialName("url") val url: String,
        @Optional @SerialName("genre_id") val genreId: Int? = null,
        @Optional @SerialName("lyrics_id") val lyricsId: Int? = null,
        @Optional @SerialName("album_id") val albumId: Int? = null,
        @Optional @SerialName("no_search") private val search: Int = 0,
        @Optional @SerialName("is_hq") private val isHq: Boolean = false
) : MessageAttachment {

    @Transient override val typeAttachment get() = typesAttachment[AttachmentType.AUDIO]!!
    @Transient val isNoSearch: Boolean get() = search == 1

}

@Serializable
data class Document internal constructor(
        @SerialName("id") override val id: Int,
        @SerialName("owner_id") override val ownerId: Int,
        @SerialName("date") val date: Int,
        @SerialName("title") val title: String,
        @SerialName("size") val size: Int, // byte
        @SerialName("ext") val ext: String,
        @SerialName("url") val url: String,
        @SerialName("type") private val type: Int,
        @Optional @SerialName("preview") val preview: DocumentPreview? = null,
        @Optional @SerialName("access_key") override val accessKey: String? = null
) : MessageAttachment {

    @Transient override val typeAttachment get() = typesAttachment[AttachmentType.DOC]!!
    @Transient val documentType: DocumentType
        get() = documentTypes[type] ?: DocumentType.UNKNOWN

}

@Serializable
data class DocumentPreview(
        @Optional @SerialName("photo") val photo: PhotoDocumentPreview? = null,
        @Optional @SerialName("graffiti") val graffiti: GraffitiDocumentPreview? = null,
        @Optional @SerialName("audio_msg") val audioMessage: AudioMessageDocumentPreview? = null)

@Serializable
data class PhotoDocumentPreview(
        @SerialName("sizes") val sizes: List<PhotoSizeDocumentPreview>)

@Serializable
data class PhotoSizeDocumentPreview(
        @SerialName("src") val src: String,
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int,
        @SerialName("type") val type: Char)

@Serializable
data class GraffitiDocumentPreview internal constructor(
        @SerialName("src") val src: String,
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int)

@Serializable
data class AudioMessageDocumentPreview internal constructor(
        @SerialName("duration") val duration: Int, // sec
        @SerialName("waveform") val waveform: List<Int>,
        @SerialName("link_ogg") val linkOgg: String,
        @SerialName("link_mp3") val linkMp3: String)

@Serializable
data class WallPost internal constructor(
        @SerialName("id") val id: Int,
        @SerialName("to_id") val toId: Int,
        @SerialName("from_id") val fromId: Int,
        @SerialName("date") val date: Int,
        @SerialName("text") val text: String,
        @SerialName("comments") val comments: WallPostComments,
        @SerialName("likes") val likes: WallPostLikes,
        @SerialName("reposts") val reposts: WallPostReposts,
        @SerialName("views") val views: WallPostViews,
        @SerialName("post_type") private val type: String,
        // TODO attachments
        @Optional @SerialName("reply_owner_id") val replyOwnerId: Int? = null,
        @Optional @SerialName("reply_post_id") val replyPostId: Int? = null,
        @Optional @SerialName("friends_only") private val friendsOnly: Int = 0,
        @Optional @SerialName("signer_id") val signerId: Int? = null,
        @Optional @SerialName("copy_history") val copyHistory: List<WallPost>? = null,
        @Optional @SerialName("can_pin") private val canPin: Int = 0,
        @Optional @SerialName("can_delete") private val canDelete: Int = 0,
        @Optional @SerialName("can_edit") private val canEdit: Int = 0,
        @Optional @SerialName("is_pinned") private val pinned: Int = 0,
        @Optional @SerialName("marked_as_ads") private val markedAsAds: Int = 0) {

    @Transient val isFriendsOnly: Boolean get() = friendsOnly == 1
    @Transient val iCanPinIt: Boolean get() = canPin == 1
    @Transient val iCanDeleteIt: Boolean get() = canDelete == 1
    @Transient val iCanEditIt: Boolean get() = canEdit == 1
    @Transient val isPinned: Boolean get() = pinned == 1
    @Transient val isMarkedAsAds: Boolean get() = markedAsAds == 1
    @Transient val postType: WallPostTypes get() = wallPostTypes[type]!!

}

@Serializable
data class WallPostComments internal constructor(
        @SerialName("count") val count: Int,
        @SerialName("can_post") private val canPost: Int = 0,
        @SerialName("groups_can_post") val groupsCanCommentIt: Boolean = false) {

    @Transient val iCanCommentIt: Boolean get() = canPost == 1

}

@Serializable
data class WallPostLikes internal constructor(
        @SerialName("count") val count: Int,
        @SerialName("user_likes") private val userLikes: Int = 0,
        @SerialName("can_like") private val canLike: Int = 0,
        @SerialName("can_publish") private val canPublish: Int = 0) {

    @Transient val iLikedIt: Boolean get() = userLikes == 1
    @Transient val iCanLikeIt: Boolean get() = canLike == 1
    @Transient val iCanPublishIt: Boolean get() = canPublish == 1

}

@Serializable
data class WallPostReposts internal constructor(
        @SerialName("count") val count: Int,
        @SerialName("user_reposted") private val userReposted: Int = 0) {

    @Transient val iRepostedIt: Boolean get() = userReposted == 1

}

@Serializable
data class WallPostViews internal constructor(
        @SerialName("count") val count: Int)

@Serializable
data class ListResponse<out T> internal constructor(
        @SerialName("count") val count: Int,
        @SerialName("items") val items: List<T>)

@Serializable
data class UserProfile internal constructor(
        @SerialName("id") val id: Int,
        @SerialName("first_name") val firstName: String,
        @SerialName("last_name") val lastName: String,
        @Optional @SerialName("deactivated") val deactivated: String? = null,
        @Optional @SerialName("hidden") private val hidden: Int = 0) {

    @Transient val isDeactivated: Boolean = deactivated != null
    @Transient val isHidden: Boolean = hidden == 1

}

@Serializable
data class Sticker internal constructor(
        @SerialName("id") val id: Int,
        @SerialName("product_id") val productId: Int,
        @SerialName("photo_64") val photo64: String,
        @SerialName("photo_128") val photo128: String,
        @SerialName("photo_256") val photo256: String,
        @SerialName("photo_352") val photo352: String,
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int)

@Serializable
data class MessagesPhotoUploadServer internal constructor(
        @SerialName("upload_url") val uploadUrl: String,
        @Optional @SerialName("aid") val albumId: Int? = null,
        @Optional @SerialName("mid") val currentUserId: Int? = null,
        @Optional @SerialName("gid") val groupId: Int? = null)

@Serializable
data class PhotoUploadResponse internal constructor(
        @SerialName("server") val server: Int,
        @SerialName("photo") val photo: String,
        @SerialName("hash") val hash: String)