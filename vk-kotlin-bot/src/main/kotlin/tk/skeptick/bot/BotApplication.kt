package tk.skeptick.bot

import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.sendBlocking

class BotApplication(accessToken: String) : ApplicationContext(accessToken) {

    private var chatCreateEventHandler: (suspend ApplicationContext.(ChatCreateEvent) -> Unit)? = null
    private var chatTitleUpdateEventHandler: (suspend ApplicationContext.(ChatTitleUpdateEvent) -> Unit)? = null
    private var chatPhotoUpdateEventHandler: (suspend ApplicationContext.(ChatPhotoUpdateEvent) -> Unit)? = null
    private var chatPhotoRemoveEventHandler: (suspend ApplicationContext.(ChatPhotoRemoveEvent) -> Unit)? = null
    private var chatInviteUserEventHandler: (suspend ApplicationContext.(ChatInviteUserEvent) -> Unit)? = null
    private var chatKickUserEventHandler: (suspend ApplicationContext.(ChatKickUserEvent) -> Unit)? = null

    private var messageHandler: DefaultMessageRoute? = null

    internal var pts: Long = 0
    internal var historyHandler: (ApplicationContext.(List<Message>) -> Unit)? = null
    internal var ptsUpdatedHandler: (ApplicationContext.(Long) -> Unit)? = null

    private val eventAllocator: SendChannel<Event> = actor {
        val peers = mutableMapOf<Int, SendChannel<MessageEvent>>()
        for (event in channel) {
            if (event is MessageEvent) {
                peers[event.peerId]?.send(event)
                        ?: peerChannel()
                        .also { peers.put(event.peerId, it) }
                        .also { it.send(event) }
            }
        }
    }

    private fun peerChannel(): SendChannel<MessageEvent> = actor {
        for (event in channel) {
            try { handleMessageEvent(event) }
            catch (e: Throwable) { log.error("Event handling error: ${e.printStackTrace()}") }
        }
    }

    suspend private fun handleMessageEvent(event: MessageEvent) {
        when (event.actType) {
            ServiceActType.CHAT_CREATE -> chatCreateEventHandler?.let { it(event as ChatCreateEvent) }
            ServiceActType.CHAT_TITLE_UPDATE -> chatTitleUpdateEventHandler?.let { it(event as ChatTitleUpdateEvent) }
            ServiceActType.CHAT_PHOTO_UPDATE -> chatPhotoUpdateEventHandler?.let { it(event as ChatPhotoUpdateEvent) }
            ServiceActType.CHAT_PHOTO_REMOVE -> chatPhotoRemoveEventHandler?.let { it(event as ChatPhotoRemoveEvent) }
            ServiceActType.CHAT_INVITE_USER -> chatInviteUserEventHandler?.let { it(event as ChatInviteUserEvent) }
            ServiceActType.CHAT_KICK_USER -> chatKickUserEventHandler?.let { it(event as ChatKickUserEvent) }
            ServiceActType.NONE -> messageHandler?.pass(event, RoutePath(String(), event.text))
            ServiceActType.UNKNOWN -> Unit
        }
    }

    @Suppress("unused")
    fun onChatCreate(block: suspend ApplicationContext.(ChatCreateEvent) -> Unit) {
        if (chatCreateEventHandler != null)
            throw HandlerOverrideException("Chat create event already handled")

        chatCreateEventHandler = block
    }

    @Suppress("unused")
    fun onChatTitleUpdate(block: suspend ApplicationContext.(ChatTitleUpdateEvent) -> Unit) {
        if (chatTitleUpdateEventHandler != null)
            throw HandlerOverrideException("Chat title update event already handled")

        chatTitleUpdateEventHandler = block
    }

    @Suppress("unused")
    fun onChatPhotoUpdate(block: suspend ApplicationContext.(ChatPhotoUpdateEvent) -> Unit) {
        if (chatPhotoUpdateEventHandler != null)
            throw HandlerOverrideException("Chat photo update event already handled")

        chatPhotoUpdateEventHandler = block
    }

    @Suppress("unused")
    fun onChatPhotoRemove(block: suspend ApplicationContext.(ChatPhotoRemoveEvent) -> Unit) {
        if (chatPhotoRemoveEventHandler != null)
            throw HandlerOverrideException("Chat photo remove event already handled")

        chatPhotoRemoveEventHandler = block
    }

    @Suppress("unused")
    fun onChatInviteUser(block: suspend ApplicationContext.(ChatInviteUserEvent) -> Unit) {
        if (chatInviteUserEventHandler != null)
            throw HandlerOverrideException("Chat invite user event already handled")

        chatInviteUserEventHandler = block
    }

    @Suppress("unused")
    fun onChatKickUser(block: suspend ApplicationContext.(ChatKickUserEvent) -> Unit) {
        if (chatKickUserEventHandler != null)
            throw HandlerOverrideException("Chat kick user event already handled")

        chatKickUserEventHandler = block
    }

    @Suppress("unused")
    fun onHistoryLoaded(pts: Long = 0, block: ApplicationContext.(List<Message>) -> Unit) {
        if (historyHandler != null)
            throw HandlerOverrideException("History already handled")

        this.pts = pts
        historyHandler = block
    }

    @Suppress("unused")
    fun onPtsUpdated(block: ApplicationContext.(Long) -> Unit) {
        if (ptsUpdatedHandler != null)
            throw HandlerOverrideException("Pts update already handled")

        ptsUpdatedHandler = block
    }

    @Suppress("unused")
    fun anyMessage(block: DefaultMessageRoute.() -> Unit) {
        if (messageHandler != null)
            throw HandlerOverrideException()

        messageHandler = DefaultMessageRoute(this, null, false, emptyList())
        messageHandler?.block()
    }

    @Suppress("unused")
    suspend fun run() {
        var (key, server, ts) = getLongPollServerLoop()
        handleLongPollHistory(ts)

        while (true) {
            log.debug("LongPolling request execution")
            val response = getLongPollRequest(key, server, ts).responseString()
            val responseString = response.third.component1() ?: continue
            val result = jsonParser.parse<LongPollResponse>(responseString)

            if (result.failed != null) {
                log.error("LongPolling request error. Code: ${result.failed}")
                when (result.failed) {
                    1 -> ts = result.ts
                    2 -> key = getLongPollServerLoop().key
                    3 -> getLongPollServerLoop().also {
                        key = it.key
                        ts = it.ts
                        handleLongPollHistory(ts)
                    }
                }
                continue
            }

            ts = result.ts
            result.pts.takeIf { it != 0L }
                    ?.also { pts = it }
                    ?.also { ptsUpdatedHandler?.let { it(pts) } }

            EventParser.parse(responseString)
                    .also { log.info("Received ${it.size} events") }
                    .onEach { eventAllocator.sendBlocking(it) }
        }
    }

}