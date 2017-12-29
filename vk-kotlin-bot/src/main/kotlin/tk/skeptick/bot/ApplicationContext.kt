package tk.skeptick.bot

import kotlinx.serialization.json.JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class ApplicationContext(accessToken: String) {

    val log: Logger = LoggerFactory.getLogger("Bot")
    val api: VkApi by lazy { VkApi(this, accessToken) }
    val jsonParser: JSON = JSON.nonstrict

    @Suppress("unused")
    suspend fun respond(peerId: Int, message: CharSequence, vararg forwardMessages: Int): Int? {
        return api.sendMessage(
                peerId = peerId,
                message = message.toString(),
                forwardedMessages = forwardMessages)
    }

    @Suppress("unused")
    suspend fun MessageEvent.respond(message: CharSequence): Int? {
        return respond(peerId, message)
    }

    @Suppress("unused")
    suspend fun MessageEvent.respondWithForward(message: CharSequence): Int? {
        return if (actType != ServiceActType.NONE) null
        else respond(peerId, message, messageId)
    }

    @Suppress("unused")
    suspend fun Message.respond(message: CharSequence): Int? {
        return respond(peerId, message)
    }

    @Suppress("unused")
    suspend fun Message.respondWithForward(message: CharSequence): Int? {
        return respond(peerId, message, id)
    }

    private val Message.peerId: Int
        get() = when {
            chatId != null -> chatId + 2000000000
            else -> userId
        }
}
