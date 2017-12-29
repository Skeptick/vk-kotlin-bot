package tk.skeptick.bot

enum class EventType(val id: Int) {
    MESSAGE_FLAGS_REPLACE(1),
    MESSAGE_FLAGS_INSTALL(2),
    MESSAGE_FLAGS_RESET(3),
    NEW_MESSAGE(4),
    EDIT_MESSAGE(5),
    READ_ALL_INCOMING(6),
    READ_ALL_OUTGOING(7),
    FRIEND_IS_ONLINE(8),
    FRIEND_IS_OFFLINE(9),
    DIALOG_FLAGS_RESET(10),
    DIALOG_FLAGS_REPLACE(11),
    DIALOG_FLAGS_INSTALL(12),
    DELETING_MESSAGES(13),
    RESTORING_MESSAGES(14),
    CONVERSATION_PARAMETER_CHANGED(51),
    USER_IS_TYPING_IN_DIALOG(61),
    USER_IS_TYPING_IN_CONVERSATION(62),
    USER_COMPLETED_CALL(70),
    COUNTER_UPDATE(80),
    NOTIFICATION_SETTINGS_CHANGED(114)
}

enum class SenderType {
    USER,
    CHAT,
    COMMUNITY
}

enum class MessageFlag(val mask: Int) {
    UNREAD(1),
    OUTBOX(2),
    REPLIED(4),
    IMPORTANT(8),
    CHAT(16),
    FRIENDS(32),
    SPAM(64),
    DELETED(128),
    FIXED(256),
    MEDIA(512),
    HIDDEN(65536)
}

interface Event {
    val eventType: EventType
}

interface MessageEvent : Event {
    val flags: Int
    val messageId: Int
    val peerId: Int
    val timestamp: Int
    val text: String

    override val eventType get() = EventType.NEW_MESSAGE
    val actType: ServiceActType get() = ServiceActType.NONE
    val senderType: SenderType get() = SenderType.USER

    val isOutbox get() = flags and MessageFlag.OUTBOX.mask != 0
}

fun MessageEvent.isNotOutbox(): Boolean = !isOutbox

data class MessageEventImpl internal constructor(
        override val flags: Int,
        override val messageId: Int,
        override val peerId: Int,
        override val timestamp: Int,
        override val text: String
) : MessageEvent

data class CommunityMessageEvent internal constructor(
        private val common: MessageEvent,
        val communityId: Int,
        val attachments: List<AttachmentType>,
        val hasForwardedMessages: Boolean
) : MessageEvent by common {
    override val actType: ServiceActType = ServiceActType.NONE
    override val senderType: SenderType = SenderType.COMMUNITY
}

data class ChatMessageEvent internal constructor(
        private val common: MessageEvent,
        val chatId: Int,
        val userId: Int,
        val attachments: List<AttachmentType>,
        val hasForwardedMessages: Boolean
) : MessageEvent by common {
    override val actType: ServiceActType = ServiceActType.NONE
    override val senderType: SenderType = SenderType.CHAT
}

data class UserMessageEvent internal constructor(
        private val common: MessageEvent,
        val userId: Int,
        val attachments: List<AttachmentType>,
        val hasForwardedMessages: Boolean
) : MessageEvent by common {
    override val actType: ServiceActType = ServiceActType.NONE
    override val senderType: SenderType = SenderType.USER
    val isFromFriend: Boolean = common.flags and MessageFlag.FRIENDS.mask != 0
}

data class ChatCreateEvent internal constructor(
        private val common: MessageEvent,
        val chatId: Int,
        val userId: Int,
        val title: String
) : MessageEvent by common{
    override val actType: ServiceActType = ServiceActType.CHAT_CREATE
    override val senderType: SenderType = SenderType.CHAT
}

data class ChatTitleUpdateEvent internal constructor(
        private val common: MessageEvent,
        val chatId: Int,
        val userId: Int,
        val oldTitle: String,
        val newTitle: String
) : MessageEvent by common{
    override val actType: ServiceActType = ServiceActType.CHAT_TITLE_UPDATE
    override val senderType: SenderType = SenderType.CHAT
}

data class ChatPhotoUpdateEvent internal constructor(
        private val common: MessageEvent,
        val chatId: Int,
        val userId: Int
) : MessageEvent by common{
    override val actType: ServiceActType = ServiceActType.CHAT_PHOTO_UPDATE
    override val senderType: SenderType = SenderType.CHAT
}

data class ChatPhotoRemoveEvent internal constructor(
        private val common: MessageEvent,
        val chatId: Int,
        val userId: Int
) : MessageEvent by common{
    override val actType: ServiceActType = ServiceActType.CHAT_PHOTO_REMOVE
    override val senderType: SenderType = SenderType.CHAT
}

data class ChatInviteUserEvent internal constructor(
        private val common: MessageEvent,
        val chatId: Int,
        val userId: Int,
        val inviteeUserId: Int
) : MessageEvent by common{
    override val actType: ServiceActType = ServiceActType.CHAT_INVITE_USER
    override val senderType: SenderType = SenderType.CHAT
}

data class ChatKickUserEvent internal constructor(
        private val common: MessageEvent,
        val chatId: Int,
        val userId: Int,
        val kickedUserId: Int
) : MessageEvent by common{
    override val actType: ServiceActType = ServiceActType.CHAT_KICK_USER
    override val senderType: SenderType = SenderType.CHAT
}

typealias Chat = ChatMessageEvent
typealias User = UserMessageEvent
typealias Community = CommunityMessageEvent