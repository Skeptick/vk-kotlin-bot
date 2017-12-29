package tk.skeptick.bot

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpGet

internal fun getLongPollRequest(key: String, server: String, ts: Long): Request {
    return "https://$server".httpGet(listOf(
            "ts" to ts, "key" to key, "act" to "a_check",
            "wait" to 25, "mode" to 34, "version" to 2
    )).timeout(90000).timeoutRead(90000)
}

internal suspend fun BotApplication.getLongPollServerLoop(): LongPollServerResponse {
    var response: LongPollServerResponse? = null
    while (response == null) response = api.getLongPollServer()
    if (pts == 0L) {
        this.pts = pts
        ptsUpdatedHandler?.let { it(pts) }
    }

    return response
}

internal suspend fun BotApplication.handleLongPollHistory(ts: Long) {
    historyHandler?.let { handler ->
        while (true) {
            val response = api.getLongPollHistory(ts, pts)
            if (response != null) {
                if (response.newPts == pts) return
                if (response.newPts != null) {
                    pts = response.newPts
                    ptsUpdatedHandler?.let { it(response.newPts) }
                }

                val messages = response.messages.items
                val logInfo = StringBuilder()
                if (messages.isNotEmpty())
                    logInfo.append("Received ${messages.size} messages, ")
                            .append("from ${messages.first().id} ")
                            .append("to ${messages.last().id}")
                else
                    logInfo.append("Received 0 messages")

                log.info(logInfo.toString())
                handler(messages)
            } else {
                log.error("Couldn't get LongPolling history")
                return
            }
        }
    }
}