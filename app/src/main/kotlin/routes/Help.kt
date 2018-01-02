package routes

import tk.skeptick.bot.Chat
import tk.skeptick.bot.TypedMessageRoute

fun TypedMessageRoute<Chat>.help() {
    onMessage("помощь", "команды") {
        intercept {
            val response = with(StringBuilder()) {
                append("Я откликаюсь на: Бет, Бетховен\n\n")
                append("Доступные команды:\n")
                append("• статистика [Имя Фамилия] [{кол-во} дней]\n")
                append("• викторина [стоп]\n")
                append("• добавь {меня} или {id} - одобрение заявки в друзья")
            }

            it.message.respondWithForward(response)
        }
    }
}