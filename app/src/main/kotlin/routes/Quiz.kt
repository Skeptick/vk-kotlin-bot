package routes

import chatIdtoPeerId
import kotlinx.coroutines.experimental.delay
import tk.skeptick.bot.ApplicationContext
import tk.skeptick.bot.Chat
import tk.skeptick.bot.Message
import tk.skeptick.bot.TypedMessageRoute
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private data class Quiz(
        val question: String,
        val answer: String,
        var lettersOpenNum: Int,
        var alreadyAnswered: Boolean = false,
        var isActive: Boolean = true)

private val quizzes = ConcurrentHashMap<Int, Quiz>()
private val questions = Thread.currentThread().contextClassLoader
        .getResourceAsStream("quiz.txt")
        .bufferedReader()
        .lineSequence()
        .toList()

fun TypedMessageRoute<Chat>.quiz() {

    onMessage("викторина") {

        onMessage("стоп") {
            intercept {
                val chatId = it.message.chatId
                val quiz = quizzes[chatId]
                if (quiz == null || !quiz.isActive) {
                    it.message.respondWithForward("В вашем чате не запущена викторина.")
                } else {
                    quiz.isActive = false
                    if (quiz.alreadyAnswered) it.message.respondWithForward("Викторина остановлена.")
                    else it.message.respondWithForward("Викторина остановлена. Ответа не будет.")
                }
            }
        }

        intercept {
            val chatId = it.message.chatId
            val quiz = quizzes[chatId]
            if (quiz != null && quiz.isActive) {
                val (question, answer, lettersOpenNum, alreadyAnswered) = quiz
                if (alreadyAnswered) {
                    val response = StringBuilder()
                    response.append("В этом чате уже запущена викторина.\n")
                    response.append("Подождите, скоро будет новый вопрос.")
                    it.message.respondWithForward(response)
                } else {
                    val hint = makeHint(answer, lettersOpenNum)
                    val response = StringBuilder()
                    response.append("В этом чате уже запущена викторина.\n")
                    response.append("Текущий вопрос: $question\n")
                    response.append("Текущая подсказка: $hint")
                    it.message.respondWithForward(response)
                }
            } else sendNewQuiz(chatId)
        }

    }

}

private suspend fun ApplicationContext.sendNewQuiz(chatId: Int) {
    val oldQuiz = quizzes[chatId]
    if (oldQuiz != null && oldQuiz.isActive && !oldQuiz.alreadyAnswered) return

    val newQuiz = makeQuiz()
    val messageId = respond(chatIdtoPeerId(chatId), newQuiz.question) ?: return
    val message = api.getMessagesById(messageId)?.first() ?: return

    quizzes.put(chatId, newQuiz)
    sendQuizHint(newQuiz, message)
}

private suspend fun ApplicationContext.sendQuizHint(quiz: Quiz, message: Message) {
    val maxTry = 5
    var currentTry = 0
    while (currentTry < maxTry) {
        delay(30, TimeUnit.SECONDS)
        if (quiz.alreadyAnswered || !quiz.isActive) return

        if (currentTry < 4 && quiz.answer.lastIndex > currentTry) {
            quiz.lettersOpenNum += 1
            val hint = makeHint(quiz.answer, quiz.lettersOpenNum)
            message.respondWithForward("Подсказка: $hint")
        } else {
            val response = StringBuilder()
            response.append("Никто не дал правильного ответа.\n")
            response.append("Правильный ответ: ${quiz.answer}")
            message.respondWithForward(response)
            quiz.alreadyAnswered = true
            break
        }

        currentTry++
    }

    delay(5, TimeUnit.SECONDS)
    if (quiz.isActive) sendNewQuiz(message.chatId!!)
}

suspend fun ApplicationContext.interceptQuizAnswer(message: Chat) {
    if (!quizzes.containsKey(message.chatId)) return

    val quiz = quizzes[message.chatId] ?: return
    if (quiz.isActive && !quiz.alreadyAnswered) {
        if (message.text.startsWith(quiz.answer, true)) {
            quiz.alreadyAnswered = true
            message.respondWithForward("Поздравляю! Вы правы!")

            delay(5, TimeUnit.SECONDS)
            if (quiz.isActive) sendNewQuiz(message.chatId)
        }
    }
}

private fun makeQuiz(): Quiz {
    val randomLine = (Math.random() * questions.lastIndex).toInt()
    val line = questions[randomLine]

    val (question, answer) = line.split('|')
    return Quiz(question, answer.trim(), 0)
}

private fun makeHint(answer: String, lettersOpenNum: Int): String =
        answer.mapIndexed { i, c ->
            when {
                i < lettersOpenNum -> c
                c.isWhitespace() -> c
                else -> '٭'
            }
        }.joinToString(" ")