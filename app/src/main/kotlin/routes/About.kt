package routes

import tk.skeptick.bot.Chat
import tk.skeptick.bot.TypedMessageRoute

fun TypedMessageRoute<Chat>.about() {
    onMessage("о себе", "инфо") {
        intercept {
            val response = StringBuilder()
            response.append("Ты не робот, а я - да.\n\n")
            response.append("Исходный код бот и библиотека для создания своего на языке Kotlin:\n")
            response.append("https://github.com/Skeptick/vk-kotlin-bot")
            it.message.respondWithForward(response)
        }
    }
}