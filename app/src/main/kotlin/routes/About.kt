package routes

import tk.skeptick.bot.Chat
import tk.skeptick.bot.TypedMessageRoute

fun TypedMessageRoute<Chat>.about() {
    onMessage("о себе", "инфо") {
        intercept {
            it.message.respondWithForward("Ты не робот, а я - да.")
        }
    }
}