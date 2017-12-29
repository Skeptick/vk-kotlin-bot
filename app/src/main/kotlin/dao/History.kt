package dao

import charLengthSum
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import tk.skeptick.bot.Message

data class UserStat(val messagesCount: Int, val charCount: Int)

data class HistoryMessage(
        val messageId: Int,
        val chatId: Int,
        val userId: Int,
        val text: String,
        val charCount: Int,
        val date: DateTime)

private fun ResultRow.toHistoryMessage(): HistoryMessage {
    return HistoryMessage(
            messageId = this[MessagesHistory.messageId],
            chatId = this[MessagesHistory.chatId],
            userId = this[MessagesHistory.userId],
            text = this[MessagesHistory.text],
            charCount = this[MessagesHistory.text].length,
            date = this[MessagesHistory.date])
}

object MessagesHistory : Table("messages_history") {
    val messageId = integer("message_id").primaryKey().autoIncrement()
    val chatId = integer("chat_id").index()
    val userId = integer("user_id").index()
    val text = text("text")
    val date = datetime("date").index()
}

fun MessagesHistory.add(messageId: Int, chatId: Int, userId: Int, text: String, date: Int) {
    transaction {
        insertIgnore {
            it[this.messageId] = messageId
            it[this.chatId] = chatId
            it[this.userId] = userId
            it[this.text] = text
            it[this.date] = DateTime(date * 1000.toLong())
        }
    }
}

fun MessagesHistory.addAll(messages: List<Message>) {
    transaction {
        batchInsert(messages, true) {
            this[messageId] = it.id
            this[chatId] = it.chatId!!
            this[userId] = it.userId
            this[text] = it.body
            this[date] = DateTime(it.date * 1000.toLong())
        }
    }
}

fun MessagesHistory.getMessagesCountForUsersByChat(chatId: Int, datetime: DateTime? = null): Map<Int, UserStat> {
    return transaction {
        if (datetime != null) {
            slice(MessagesHistory.userId, MessagesHistory.userId.count(), MessagesHistory.text.charLengthSum())
                    .select { (MessagesHistory.chatId eq chatId) and
                            (MessagesHistory.date greaterEq datetime) }
                    .groupBy(MessagesHistory.userId)
                    .associate {
                        it[MessagesHistory.userId] to UserStat(
                                messagesCount = it[MessagesHistory.userId.count()],
                                charCount = it[MessagesHistory.text.charLengthSum()])
                    }
        } else {
            slice(MessagesHistory.userId, MessagesHistory.userId.count(), MessagesHistory.text.charLengthSum())
                    .select { MessagesHistory.chatId eq chatId }
                    .groupBy(MessagesHistory.userId)
                    .associate {
                        it[MessagesHistory.userId] to UserStat(
                                messagesCount = it[MessagesHistory.userId.count()],
                                charCount = it[MessagesHistory.text.charLengthSum()])
                    }
        }
    }
}

fun MessagesHistory.getLastMessagesForUsersByChat(chatId: Int, datetime: DateTime? = null): Map<Int, DateTime?> {
    return transaction {
        if (datetime != null) {
            slice(MessagesHistory.userId, MessagesHistory.date.max())
                    .select { (MessagesHistory.date greaterEq datetime) and (MessagesHistory.chatId eq chatId) }
                    .groupBy(MessagesHistory.userId)
                    .associate { it[MessagesHistory.userId] to it[MessagesHistory.date.max()] }
        } else {
            slice(MessagesHistory.userId, MessagesHistory.date.max())
                    .select { MessagesHistory.chatId eq chatId }
                    .groupBy(MessagesHistory.userId)
                    .associate { it[MessagesHistory.userId] to it[MessagesHistory.date.max()] }
        }
    }
}

fun MessagesHistory.getUserMessages(chatId: Int, userId: Int, datetime: DateTime? = null): List<HistoryMessage> {
    return transaction {
        if (datetime != null) {
            select { (MessagesHistory.date greaterEq datetime) and
                    (MessagesHistory.userId eq userId) and
                    (MessagesHistory.chatId eq chatId) }
                    .orderBy(MessagesHistory.date)
                    .map(ResultRow::toHistoryMessage)
        } else {
            select { (MessagesHistory.userId eq userId) and
                    (MessagesHistory.chatId eq chatId) }
                    .orderBy(MessagesHistory.date)
                    .map(ResultRow::toHistoryMessage)
        }
    }
}

fun MessagesHistory.getFirstMessageDateByChat(chatId: Int, datetime: DateTime? = null): DateTime? {
    return if (datetime != null) {
        transaction {
            slice(MessagesHistory.date.min())
                    .select { (MessagesHistory.date greaterEq datetime) and
                                (MessagesHistory.chatId eq chatId) }
                    .firstOrNull()
                    ?.let { it[MessagesHistory.date.min()] }
        }
    } else {
        transaction {
            slice(MessagesHistory.date.min())
                    .select { MessagesHistory.chatId eq chatId }
                    .firstOrNull()
                    ?.let { it[MessagesHistory.date.min()] }
        }
    }
}