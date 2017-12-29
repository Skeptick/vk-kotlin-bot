package routes

import dao.*
import equalsLeastOne
import isNumber
import org.joda.time.DateTime
import tk.skeptick.bot.ApplicationContext
import tk.skeptick.bot.Chat
import tk.skeptick.bot.Message
import tk.skeptick.bot.TypedMessageRoute
import kotlin.math.roundToInt

fun TypedMessageRoute<Chat>.statistics() {

    onMessage("стата", "стату", "статистика", "статистику", "статка", "статку") {

        intercept {
            val restOfMessage = it.route.restOfMessage
            if (restOfMessage.isNotBlank()) {
                val words = restOfMessage.split(' ').filter { it.isNotBlank() }

                if (words.size == 2 && words[0].isNumber() && words[1].equalsLeastOne("день", "дней", "дня")) {

                    val days = words[0].toInt()
                    val result = getStatisticsForAll(it.message, days) ?: return@intercept
                    it.message.respondWithForward(result)

                } else if (words.size == 2) {

                    val username = words[0] + ' ' + words[1]
                    val result = getStatisticsForUser(it.message, username) ?: return@intercept
                    it.message.respondWithForward(result)

                } else if (words.size == 4 && words[2].isNumber() && words[3].equalsLeastOne("день", "дней", "дня")) {

                    val days = words[2].toInt()
                    val username = words[0] + ' ' + words[1]
                    val result = getStatisticsForUser(it.message, username, days) ?: return@intercept
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

                val result = getStatisticsForAll(it.message) ?: return@intercept
                it.message.respondWithForward(result)

            }
        }

    }

}

private suspend fun ApplicationContext.getStatisticsForAll(
        message: Chat,
        days: Int? = null
): String? {

    if (days != null && days == 0) return "Количество дней должно быть больше нуля."

    val now = DateTime.now()
    val minDate = days?.let { now.minusDays(it)
            .let { DateTime(it.year, it.monthOfYear, it.dayOfMonth, 0, 0) }
    }

    val chatId = message.chatId
    val users = api.getChatUsers(chatId) ?: return null
    val countMessagesForUsers = MessagesHistory.getMessagesCountForUsersByChat(chatId, minDate)
    val firstMessageDate = MessagesHistory.getFirstMessageDateByChat(chatId, minDate) ?: return null

    val presentUsersCounter = users.mapNotNull { user ->
        countMessagesForUsers[user.id]?.let {
            (user.firstName + ' ' + user.lastName) to it }
    }

    val absentUsers = users.mapNotNull { user ->
        if (countMessagesForUsers[user.id] == null)
            user.firstName + ' ' + user.lastName
        else null
    }

    val result = StringBuilder()
    val daysCount = now.diffInDaysRound(firstMessageDate)
    result.append("Статистика за $daysCount ${getDeclensionDays(daysCount)}.\n\n")
    result.append("Кол-во сообщений / символов:\n")

    presentUsersCounter
            .sortedByDescending { it.second.messagesCount }
            .forEachIndexed { i, pair ->
                result.append("${i + 1}. ${pair.first}")
                result.append(" — ")
                result.append("${pair.second.messagesCount} / ")
                result.append("${pair.second.charCount}\n") }

    if (days == null) {
        val lastMessagesForUsers = MessagesHistory.getLastMessagesForUsersByChat(chatId)
        val presentUsersMessageDate = users
                .mapNotNull { user ->
                    lastMessagesForUsers[user.id]?.let { (user.firstName + ' ' + user.lastName) to it } }
                .filter { now.diffInDays(it.second) > 0 }
                .sortedBy { it.second }

        if (presentUsersMessageDate.isNotEmpty()) {
            result.append('\n')
            result.append("Не писали дольше суток:\n")
            presentUsersMessageDate.forEachIndexed { i, pair ->
                result.append(pair.first)
                result.append(" — ")
                result.append("${now.diffInString(pair.second)}\n") }
        }
    }

    if (absentUsers.isNotEmpty()) {
        result.append('\n')
        result.append("Нет данных за указанный период:\n")
        absentUsers.sortedBy { it }
                .forEach { result.append("$it\n") }
    }

    return result.toString()
}

private suspend fun ApplicationContext.getStatisticsForUser(
        message: Chat,
        username: String,
        days: Int? = null
): String? {

    if (days != null && days == 0) return "Количество дней должно быть больше нуля."

    val now = org.joda.time.DateTime.now()
    val minDate = days?.let { now.minusDays(it - 1)
            .let { DateTime(it.year, it.monthOfYear, it.dayOfMonth, 0, 0) } }

    val users = api.getChatUsers(message.chatId) ?: return null
    val user = users.find { (it.firstName + ' ' + it.lastName).equals(username, true) }
            ?: return "Не удалось найти указанного пользователя."

    val username = user.firstName + ' ' + user.lastName
    val userMessages = MessagesHistory.getUserMessages(message.chatId, user.id, minDate)
    if (userMessages.isEmpty()) return "Ещё нет статистики для указанного пользователя"

    val result = StringBuilder()
    result.append("Статистика пользователя $username (id${user.id}).\n")
    result.append("Последнее сообщение: ${now.diffInString(userMessages.last().date)} назад\n\n")

    val messagesByDays = userMessages
            .groupBy { it.date.datestamp() }.toList()
            .mapIndexed { i, pair ->
                i to UserStat(pair.second.size, pair.second.sumBy { it.charCount }) }

    result.append("Кол-во сообщений / символов:\n")
    val allMessages = userMessages.size
    val allChars = userMessages.sumBy { it.charCount }

    if (days == null)
        result.append("За всё время: $allMessages / $allChars\n")

    if (days != null)
        result.append("За ${messagesByDays.size} ${getDeclensionDays(messagesByDays.size)}: " +
                "$allMessages / $allChars\n")

    var days = messagesByDays.size
    while (days > 0) {
        if (days > 5) days /= 2
        else days -= 1
        if (days != 0) {
            val messages = messagesByDays.takeLast(days).sumBy { it.second.messagesCount }
            val chars = messagesByDays.takeLast(days).sumBy { it.second.charCount }
            result.append("За $days ${getDeclensionDays(days)}: $messages / $chars\n")
        }
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

private fun DateTime.diffInDaysRound(other: DateTime): Int =
        ((millis - other.millis).toDouble() / 1000 / 60 / 60 / 24).roundToInt()

private fun DateTime.diffInDays(other: DateTime): Int =
        ((millis - other.millis).toDouble() / 1000 / 60 / 60 / 24).toInt()

private fun DateTime.diffInString(other: DateTime): String {
    val result = StringBuilder()
    val minutes = ((millis - other.millis).toDouble() / 1000 / 60).toInt()
    result.append("${minutes / 1440} д. ")
    result.append("${(minutes / 60) % 24} ч. ")
    result.append("${minutes % 60} мин.")
    return result.toString()
}

private fun DateTime.datestamp(): Int {
    return year * 10000 + monthOfYear * 100 + dayOfMonth
}