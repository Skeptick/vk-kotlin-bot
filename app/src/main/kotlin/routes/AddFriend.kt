package routes

import tk.skeptick.bot.ApplicationContext
import tk.skeptick.bot.TypedMessageRoute
import tk.skeptick.bot.Chat
import tk.skeptick.bot.User

fun TypedMessageRoute<Chat>.addFriendChat() {
    onMessage("добавь") {
        intercept {
            val userId = retrieveUserId(it.route.restOfMessage, it.message.userId)
            if (userId != 0) addFriend(userId)
                    ?.let { result -> it.message.respondWithForward(result) }
                    ?: it.message.respondWithForward("Произошла ошибка.")
            else it.message.respondWithForward("Укажите корректный id пользователя.")
        }
    }
}

fun TypedMessageRoute<User>.addFriendUser() {
    onMessage("добавь") {
        intercept {
            val userId = retrieveUserId(it.route.restOfMessage, it.message.userId)
            if (userId != 0) addFriend(userId)
                    ?.let { result -> it.message.respondWithForward(result) }
                    ?: it.message.respondWithForward("Произошла ошибка.")
            else it.message.respondWithForward("Укажите корректный id пользователя.")
        }
    }
}

private suspend fun ApplicationContext.addFriend(userId: Int): String? {
    val usersRequested = mutableListOf<Int>()

    while (true) {
        val requests = api.getFriendsRequests(offset = usersRequested.size)
                ?.apply { usersRequested.addAll(this) }
                ?: return null

        if (requests.size < 1000) break
    }

    if (!usersRequested.contains(userId))
        return "Сперва отправь мне запрос в друзья."

    return api.addFriend(userId)?.let { "Заявка в друзья одобрена." }
}

private fun retrieveUserId(restOfMessage: String, fromUserId: Int): Int {
    return if (restOfMessage.startsWith("меня")) fromUserId
    else restOfMessage
            .substringAfter("id")
            .takeWhile { it.isDigit() }
            .takeIf { it.isNotEmpty() }
            ?.toInt()
            ?: 0
}