package tk.skeptick.bot

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpUpload
import kotlinx.coroutines.experimental.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.ListLikeSerializer
import kotlinx.serialization.list
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private object Methods {

    object Messages {
        private const val it = "messages."
        const val send = it + "send"
        const val getById = it + "getById"
        const val getChatUsers = it + "getChatUsers"
        const val getLongPollServer = it + "getLongPollServer"
        const val getLongPollHistory = it + "getLongPollHistory"
        const val getHistory = it + "getHistory"
        const val removeChatUser = it + "removeChatUser"
        const val addChatUser = it + "addChatUser"
    }

    object Photos {
        private const val it = "photos."
        const val getMessagesUploadServer = it + "getMessagesUploadServer"
        const val saveMessagesPhoto = it + "saveMessagesPhoto"
    }

    object Friends {
        private const val it = "friends."
        const val add = it + "add"
        const val getRequests = it + "getRequests"
    }

    object Wall {
        private const val it = "wall."
        const val search = it + "search"
    }

    object Utils {
        private const val it = "utils."
        const val resolveScreenName = it + "resolveScreenName"
    }

}

class VkApi(private val context: ApplicationContext, accessToken: String) {

    private val defaultHost = "https://api.vk.com/method"
    private val defaultVersion = "5.69"

    init {
        FuelManager.instance.basePath = defaultHost
        FuelManager.instance.baseParams = listOf(
                "access_token" to accessToken,
                "v" to defaultVersion
        )
    }

    @Suppress("unused")
    suspend fun sendMessage(
            peerId: Int,
            message: String? = null,
            attachments: List<MessageAttachment>? = null,
            forwardedMessages: IntArray? = null,
            stickerId: Int? = null): Int? {

        val preparedForwards = forwardedMessages?.joinToString(",")
        val preparedAttachments = attachments?.joinToString(",") {
            with(StringBuilder()) {
                append(it.typeAttachment)
                append(it.ownerId)
                append('_').append(it.id)
                if (it.accessKey != null) {
                    append('_').append(it.accessKey)
                }
            }.toString()
        }

        val serializer = IntSerializer
        return Methods.Messages.send.httpPost(listOf(
                "peer_id" to peerId,
                "message" to message,
                "attachment" to preparedAttachments,
                "forward_messages" to preparedForwards,
                "sticker_id" to stickerId
        )).execRequest(serializer)
    }

    @Suppress("unused")
    suspend fun getMessagesById(vararg ids: Int): List<Message>? {
        val messageIds = ids.joinToString(",")
        val serializer = ListResponse.serializer(Message.serializer())
        return Methods.Messages.getById.httpGet(listOf(
                "message_ids" to messageIds
        )).execRequest(serializer)?.items
    }

    @Suppress("unused")
    suspend fun getChatUsers(chatId: Int): List<UserProfile>? {
        val serializer = UserProfile.serializer().list
        return Methods.Messages.getChatUsers.httpGet(listOf(
                "chat_id" to chatId,
                "fields" to "nickname,screen_name,sex,bdate,city,online",
                "name_case" to "nom"
        )).execRequest(serializer)
    }

    @Suppress("unused")
    suspend fun uploadMessagesPhoto(file: File): List<Photo>? {
        val server = getMessagesPhotoUploadServer() ?: return null
        val result = server.uploadUrl.httpUpload()
                .source { _, _ -> file }
                .name { "photo" }
                .responseString()

        val (string, error) = result.third
        return if (string == null) {
            context.log.error("HTTP request error. Message: ${error!!.localizedMessage}").let { null }
        } else {
            val uploadSerializer = PhotoUploadResponse.serializer()
            val uploadResponse = context.jsonParser.parse(uploadSerializer, string)
            val saveSerializer = Photo.serializer().list
            Methods.Photos.saveMessagesPhoto.httpPost(listOf(
                    "photo" to uploadResponse.photo,
                    "server" to uploadResponse.server,
                    "hash" to uploadResponse.hash
            )).execRequest(saveSerializer)
        }
    }

    @Suppress("unused")
    suspend fun getMessagesHistory(peerId: Int, offset: Int): List<Message>? {
        val serializer = ListResponse.serializer(Message.serializer())
        return Methods.Messages.getHistory.httpGet(listOf(
                "peer_id" to peerId,
                "offset" to offset,
                "count" to 200,
                "rev" to 1
        )).execRequest(serializer)?.items
    }

    @Suppress("unused")
    suspend fun getFriendsRequests(
            offset: Int = 0,
            withViewed: Boolean = true,
            out: Boolean = false): List<Int>? {

        val serializer = ListResponse.serializer(IntSerializer)
        return Methods.Friends.getRequests.httpGet(listOf(
                "offset" to offset,
                "count" to 1000,
                "need_viewed" to if (withViewed) 1 else 0,
                "out" to if (out) 1 else 0
        )).execRequest(serializer)?.items
    }

    @Suppress("unused")
    suspend fun addFriend(userId: Int, declineRequest: Boolean = false): Int? {
        val serializer = IntSerializer
        return Methods.Friends.add.httpGet(listOf(
                "user_id" to userId,
                "follow" to if (declineRequest) 1 else 0
        )).execRequest(serializer)
    }

    @Suppress("unused")
    suspend fun wallSearch(ownerId: Int, offset: Int, text: String): ListResponse<WallPost>? {
        val serializer = ListResponse.serializer(WallPost.serializer())
        return Methods.Wall.search.httpGet(listOf(
                "owner_id" to ownerId,
                "query" to text,
                "offset" to offset,
                "count" to 100,
                "extended" to 1
        )).execRequest(serializer)
    }

    @Suppress("unused")
    suspend fun resolveScreenName(screenName: String): ResolveScreenNameResponse? {
        val serializer = ResolveScreenNameResponse.serializer()
        return Methods.Utils.resolveScreenName.httpGet(listOf(
                "screen_name" to screenName
        )).execRequest(serializer)
    }

    @Suppress("unused")
    suspend fun addChatUser(chatId: Int, userId: Int): Boolean {
        val serializer = IntSerializer
        return Methods.Messages.addChatUser.httpGet(listOf(
                "chat_id" to chatId,
                "user_id" to userId
        )).execRequest(serializer) == 1 // may return "true" even if not added
    }

    @Suppress("unused")
    suspend fun removeChatUser(chatId: Int, userId: Int): Boolean {
        val serializer = IntSerializer
        return Methods.Messages.removeChatUser.httpGet(listOf(
                "chat_id" to chatId,
                "user_id" to userId
        )).execRequest(serializer) == 1
    }

    suspend internal fun getLongPollServer(): LongPollServerResponse? {
        val serializer = LongPollServerResponse.serializer()
        return Methods.Messages.getLongPollServer.httpGet(listOf(
                "lp_version" to 2,
                "need_pts" to 1
        )).execRequest(serializer)
    }

    suspend internal fun getLongPollHistory(ts: Long, pts: Long): LongPollHistoryResponse? {
        val serializer = LongPollHistoryResponse.serializer()
        return Methods.Messages.getLongPollHistory.httpGet(listOf(
                "ts" to ts,
                "pts" to pts,
                "fields" to "",
                "events_limit" to 100000,
                "msgs_limit" to 100000,
                "lp_version" to 2
        )).execRequest(serializer)
    }

    suspend private fun getMessagesPhotoUploadServer(): MessagesPhotoUploadServer? {
        val serializer = MessagesPhotoUploadServer.serializer()
        return Methods.Photos.getMessagesUploadServer.httpGet().execRequest(serializer)
    }

    suspend private fun <T> Request.execRequest(serializer: KSerializer<T>): T? {
        repeat(5) {
            context.log.debug("Starting request: ${path.substringBefore('?')}")
            val (string, fuelError) = responseString().third
            if (string == null)
                context.log.error("HTTP request error. Message: ${fuelError!!.localizedMessage}")
            else {
                val jsonObject = JSONObject(string)

                val optResponse = jsonObject.opt("response")
                val response = if (optResponse != null && optResponse !is JSONArray)
                    context.jsonParser.parse(serializer, optResponse.toString())
                else if (optResponse is JSONArray && serializer is ListLikeSerializer<*, *, *>)
                    context.jsonParser.parse(serializer, optResponse.toString())
                else null

                val error = jsonObject.optJSONObject("error")?.toString()
                        ?.let { context.jsonParser.parse(Error.serializer(), it) }

                if (error != null) with(StringBuilder()) {
                    append("VK request error. ")
                    append("Code: ${error.errorCode}. ")
                    append("Message: ${error.errorMessage}")
                    context.log.error(toString())
                }

                return response
            }

            context.log.info("Repeating request after 1 second")
            delay(1, TimeUnit.SECONDS)
        }

        return null
    }

}