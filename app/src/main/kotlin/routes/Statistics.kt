package routes

import dao.*
import equalsLeastOne
import isNumber
import org.joda.time.DateTime
import tk.skeptick.bot.ApplicationContext
import tk.skeptick.bot.Chat
import tk.skeptick.bot.Message
import tk.skeptick.bot.TypedMessageRoute
import kotlin.math.absoluteValue

fun TypedMessageRoute<Chat>.statistics() {

    onMessage("стата", "стату", "статистика", "статистику", "статка", "статку") {

        intercept {
            val restOfMessage = it.route.restOfMessage
            if (restOfMessage.isNotBlank()) {
                val words = restOfMessage.split(' ').filter { it.isNotBlank() }

                if (words.size == 2 && words[0].isNumber() && words[1].equalsLeastOne("день", "дней", "дня")) {

                    val days = words[0].toInt()
                    val result = getStatisticsForAll(it.message.chatId, days) ?: return@intercept
                    it.message.respondWithForward(result)

                } else if (words.size == 2) {

                    val username = words[0] + ' ' + words[1]
                    val result = getStatisticsForUser(it.message.chatId, username) ?: return@intercept
                    it.message.respondWithForward(result)

                } else if (words.size == 4 && words[2].isNumber() && words[3].equalsLeastOne("день", "дней", "дня")) {

                    val days = words[2].toInt()
                    val username = words[0] + ' ' + words[1]
                    val result = getStatisticsForUser(it.message.chatId, username, days) ?: return@intercept
                    it.message.respondWithForward(result)

                } else {

                    val response = with(StringBuilder()) {
                        append("Некорректное обращение к команде.\n")
                        append("Синтаксис: статистика [Имя Фамилия] [{кол-во} дней]\n\n")
                        append("Примеры:\n")
                        append("статистика 3 дня\n")
                        append("статистика Вася Пупкин\n")
                        append("статистика Вася Пупкин 5 дней")
                    }

                    it.message.respondWithForward(response)

                }
            } else {

                val result = getStatisticsForAll(it.message.chatId) ?: return@intercept
                it.message.respondWithForward(result)

            }
        }

    }

}

private suspend fun ApplicationContext.getStatisticsForAll(
        chatId: Int,
        days: Int? = null
): String? {

    if (days != null && days == 0) return "Количество дней должно быть больше нуля."

    val now = DateTime.now()
    val minDate = days?.let { now.minusDays(it - 1).round() }

    val users = api.getChatUsers(chatId) ?: return null
    val countMessagesForUsers = MessagesHistory.getMessagesCountForUsersByChat(chatId, minDate)
    val firstMessageDate = MessagesHistory.getFirstMessageDateByChat(chatId, minDate) ?: return null

    val presentUsersCounter = users.mapNotNull { user ->
        countMessagesForUsers[user.id]?.let {
            (user.firstName + ' ' + user.lastName) to it
        }
    }

    val absentUsers = users.mapNotNull { user ->
        if (countMessagesForUsers[user.id] == null)
            user.firstName + ' ' + user.lastName
        else null
    }

    val result = StringBuilder()
    val dayBefore = firstMessageDate.round().diffInDays(now) + 1
    result.append("Статистика за $dayBefore ${getDeclensionDays(dayBefore)}.\n\n")
    result.append("Кол-во сообщений / символов:\n")

    presentUsersCounter
            .sortedByDescending { it.second.messageCount }
            .forEachIndexed { i, pair ->
                result.append("${i + 1}. ${pair.first}")
                result.append(" — ")
                result.append("${pair.second.messageCount} / ")
                result.append("${pair.second.charCount}\n") }

    if (days == null) {
        val lastMessagesForUsers = MessagesHistory.getLastMessagesForUsersByChat(chatId)
        val presentUsersMessageDate = users
                .mapNotNull { user ->
                    lastMessagesForUsers[user.id]?.let {
                        (user.firstName + ' ' + user.lastName) to it
                    }
                }
                .filter { now.diffInDays(it.second) > 0 }
                .sortedBy { it.second }

        if (presentUsersMessageDate.isNotEmpty()) {
            result.append('\n')
            result.append("Не писали дольше суток:\n")
            presentUsersMessageDate.forEach {
                result.append(it.first)
                result.append(" — ")
                result.append("${now.diffInString(it.second)}\n")
            }
        }
    }

    if (absentUsers.isNotEmpty()) {
        result.append('\n')
        result.append("Нет данных за указанный период:\n")
        absentUsers.sortedBy { it }
                .joinToString("\n")
                .let(result::append)
    }

    return result.toString()
}

private suspend fun ApplicationContext.getStatisticsForUser(
        chatId: Int,
        username: String,
        days: Int? = null
): String? {

    if (days != null && days == 0) return "Количество дней должно быть больше нуля."

    val now = DateTime.now()
    val roundNow = now.round()
    val minDate = days?.let { now.minusDays(it - 1).round() }

    val users = api.getChatUsers(chatId) ?: return null
    val user = users.find { (it.firstName + ' ' + it.lastName).equals(username, true) }
            ?: return "Не удалось найти указанного пользователя."

    val messagesByDate = MessagesHistory
            .getMessagesCountForUserByChatAndUserId(chatId, user.id, minDate)
            .toList()

    if (messagesByDate.isEmpty() && days == null)
        return "Ещё нет статистики для указанного пользователя."
    else if (messagesByDate.isEmpty())
        return "Нет статистики для этого пользователя за указанный период."

    val lastMessageDate = MessagesHistory
            .getLastMessageForUserByChatAndUserId(chatId, user.id)
            ?: return null

    val correctUsername = user.firstName + ' ' + user.lastName
    val dayBefore = messagesByDate.first().first.diffInDays(now) + 1
    val result = with(StringBuilder()) {
        append("Статистика пользователя $correctUsername (id${user.id}) ")
        append("за $dayBefore ${getDeclensionDays(dayBefore)}.\n")
        append("Последнее сообщение: ${now.diffInString(lastMessageDate)} назад.\n\n")
        append("Кол-во сообщений / символов:\n")
    }

    fun addDateCounter(pair: Pair<DateTime, UserStat>) = with(result) {
        append("${pair.first.dateString()} — ")
        result.append("${pair.second.messageCount} / ")
        result.append("${pair.second.charCount}\n")
    }

    fun addEmptyCounter(date: DateTime) =
            result.append("${date.dateString()} — 0 / 0\n")

    var prevDay: DateTime? = null
    messagesByDate.takeLast(30).forEach {
        if (prevDay == null || it.first.diffInDays(prevDay!!) == 1) {
            prevDay = it.first
            addDateCounter(it)
        } else {
            while (prevDay != it.first) {
                prevDay = prevDay?.plusDays(1)
                if (prevDay == it.first) addDateCounter(it)
                else addEmptyCounter(prevDay!!)
            }
        }
    }

    while (prevDay != roundNow) {
        prevDay = prevDay?.plusDays(1)
        addEmptyCounter(prevDay!!)
    }

    val allMessages = messagesByDate.sumBy { it.second.messageCount }
    val allChars = messagesByDate.sumBy { it.second.charCount }
    with(result) {
        append("\nВсего за $dayBefore ")
        append("${getDeclensionDays(dayBefore)}: ")
        append("$allMessages / $allChars\n")
    }

    return result.toString()
}

fun saveMessagesHistory(messages: List<Message>) {
    if (messages.isNotEmpty()) {
        MessagesHistory.addAll(messages)
    }
}

fun saveChatEvent(event: Chat) {
    event.let {
        MessagesHistory.add(
                messageId = it.messageId,
                chatId = it.chatId,
                userId = it.userId,
                text = it.text,
                date = it.timestamp)
    }
}

private fun getDeclensionDays(num: Int): String {
    return when {
        num == 1 -> "день"
        num == 2 || num == 3 || num == 4 -> "дня"
        num > 20 && num % 10 == 1 -> "день"
        num > 20 && num % 10 == 2 || num % 10 == 3 || num % 10 == 4 -> "дня"
        else -> "дней"
    }
}

private fun DateTime.round(): DateTime =
        withTime(0, 0, 0, 0)

private fun DateTime.dateString(): String =
        "$dayOfMonth.$monthOfYear.$year"

private fun DateTime.diffInDays(other: DateTime): Int =
        ((millis - other.millis).toDouble() / 1000 / 60 / 60 / 24).toInt().absoluteValue

private fun DateTime.diffInString(other: DateTime): String {
    val result = StringBuilder()
    val minutes = ((millis - other.millis).toDouble() / 1000 / 60).toInt()
    result.append("${minutes / 1440} д. ")
    result.append("${(minutes / 60) % 24} ч. ")
    result.append("${minutes % 60} мин.")
    return result.toString()
}