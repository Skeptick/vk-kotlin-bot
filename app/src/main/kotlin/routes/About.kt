package routes

import tk.skeptick.bot.Chat
import tk.skeptick.bot.TypedMessageRoute

fun TypedMessageRoute<Chat>.about() {
    onMessage("о себе", "инфо") {
        intercept {
            val response = with(StringBuilder()) {
                append("Ты не робот, а я - да.\n\n")
                append("Исходный код бота и библиотека для создания своего на языке Kotlin:\n")
                append("https://github.com/Skeptick/vk-kotlin-bot")
            }

            it.message.respondWithForward(response)
        }
    }
}