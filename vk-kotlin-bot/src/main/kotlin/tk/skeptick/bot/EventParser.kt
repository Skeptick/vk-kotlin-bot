package tk.skeptick.bot

import tk.skeptick.bot.SenderType.*
import tk.skeptick.bot.ServiceActType.*
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.json.JSON
import kotlinx.serialization.map
import kotlin.math.absoluteValue

internal object EventParser {

    private val bracesRegex = Regex("[{}]")
    private val eventTypes = enumValues<EventType>().map { it.id to it }.toMap()

    private fun getSenderType(peerId: Int): SenderType = when {
        peerId > 2000000000 -> CHAT
        peerId < 0 -> COMMUNITY
        else -> USER
    }

    private fun getSenderId(peerId: Int, senderType: SenderType): Int = when(senderType) {
        CHAT -> peerId - 2000000000
        COMMUNITY -> peerId.absoluteValue
        USER -> peerId
    }

    private fun parseAttachments(event: String): Map<String, String> =
            if (event.contains(bracesRegex)) {
                val attachments = event.substring(event.indexOf('{')..event.indexOf('}'))
                JSON.parse((StringSerializer to StringSerializer).map, attachments)
            } else emptyMap()

    private fun parseMediaAttachments(attachments: Map<String, String>): List<AttachmentType> =
            attachments.mapNotNull { (key, value) ->
                if (key.startsWith("attach") && key.endsWith("type")) attachmentsTypes[value]
                else null
            }

    private fun hasForwardedMessages(attachments: Map<String, String>): Boolean =
            attachments.containsKey("fwd")

    private fun getServiceActType(attachments: Map<String, String>): ServiceActType =
            if (attachments.containsKey("source_act")) serviceActTypes[attachments["source_act"]] ?: UNKNOWN
            else NONE

    private fun getFrom(attachments: Map<String, String>): Int =
            attachments["from"]?.toInt() ?: 0

    private fun getChatTitle(attachments: Map<String, String>): String =
            attachments["source_text"] ?: String()

    private fun getOldChatTitle(attachments: Map<String, String>): String =
            attachments["source_old_text"] ?: String()

    private fun getMidUserId(attachments: Map<String, String>): Int =
            attachments["source_mid"]?.toInt() ?: 0

    private fun parseEvent(event: String): Event? {
        val fields = event.substringBeforeLast('{').split(',').takeIf { it.size >= 6 } ?: return null
        fields[0].toInt().let(eventTypes::get).takeIf { it == EventType.NEW_MESSAGE } ?: return null

        val (_, messageId, flags, peerId, timestamp) = fields.take(5).map(String::toInt)
        val text = event.substringAfter(",\"").substringBeforeLast("\",{")

        val senderType = getSenderType(peerId)
        val senderId = getSenderId(peerId, senderType)

        val attachments = parseAttachments(event.substringAfter(text))
        val mediaAttachments = parseMediaAttachments(attachments)
        val hasForwardedMessages = hasForwardedMessages(attachments)

        val actType = getServiceActType(attachments).takeIf { it != UNKNOWN } ?: return null

        val fromId = when (senderType) {
            CHAT -> getFrom(attachments)
            else -> 0
        }

        val messageEvent = MessageEventImpl(flags, messageId, peerId, timestamp, text)
        return when (senderType) {
            COMMUNITY -> CommunityMessageEvent(messageEvent, senderId, mediaAttachments, hasForwardedMessages)
            USER -> UserMessageEvent(messageEvent, senderId, mediaAttachments, hasForwardedMessages)
            CHAT -> when (actType) {
                NONE -> ChatMessageEvent(messageEvent, senderId, fromId, mediaAttachments, hasForwardedMessages)
                CHAT_CREATE -> ChatCreateEvent(messageEvent, senderId, fromId, getChatTitle(attachments))
                CHAT_TITLE_UPDATE -> ChatTitleUpdateEvent(messageEvent, senderId, fromId, getOldChatTitle(attachments), getChatTitle(attachments))
                CHAT_PHOTO_UPDATE -> ChatPhotoUpdateEvent(messageEvent, senderId, fromId)
                CHAT_PHOTO_REMOVE -> ChatPhotoRemoveEvent(messageEvent, senderId, fromId)
                CHAT_INVITE_USER -> ChatInviteUserEvent(messageEvent, senderId, fromId,  getMidUserId(attachments))
                CHAT_KICK_USER -> ChatKickUserEvent(messageEvent, senderId, fromId,  getMidUserId(attachments))
                UNKNOWN -> null
            }
        }
    }

    private fun String.ejectArray() = this.substringAfter('[').substringBeforeLast(']')

    fun parse(json: String): List<Event> {
        return json.ejectArray()
                .takeIf(String::isNotEmpty)
                ?.ejectArray()
                ?.split("],[")
                ?.mapNotNull { parseEvent(it) }
                ?: emptyList()
    }

}