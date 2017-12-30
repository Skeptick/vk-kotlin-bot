package routes

import kotlinx.coroutines.experimental.delay
import tk.skeptick.bot.ApplicationContext
import tk.skeptick.bot.Chat
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
                val peerId = it.message.peerId
                val quiz = quizzes[peerId]
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
            val peerId = it.message.peerId
            val quiz = quizzes[peerId]
            if (quiz != null && quiz.isActive) {
                val (question, answer, lettersOpenNum, alreadyAnswered) = quiz
                if (alreadyAnswered) {
                    val response = with(StringBuilder()) {
                        append("В этом чате уже запущена викторина.\n")
                        append("Подождите, скоро будет новый вопрос.")
                    }

                    it.message.respondWithForward(response)
                } else {
                    val hint = makeHint(answer, lettersOpenNum)
                    val response = with(StringBuilder()) {
                        append("В этом чате уже запущена викторина.\n")
                        append("Текущий вопрос: $question\n")
                        append("Текущая подсказка: $hint")
                    }

                    it.message.respondWithForward(response)
                }
            } else sendNewQuiz(peerId)
        }

    }

}

private suspend fun ApplicationContext.sendNewQuiz(peerId: Int) {
    val oldQuiz = quizzes[peerId]
    if (oldQuiz != null && oldQuiz.isActive && !oldQuiz.alreadyAnswered) return

    val newQuiz = makeQuiz()
    val messageId = respond(peerId, newQuiz.question) ?: return

    quizzes.put(peerId, newQuiz)
    sendQuizHint(peerId, messageId, newQuiz)
}

private suspend fun ApplicationContext.sendQuizHint(peerId: Int, questionMessageId: Int, quiz: Quiz) {
    val maxHint = 5
    var currentHint = 0
    while (currentHint < maxHint) {
        delay(30, TimeUnit.SECONDS)
        if (quiz.alreadyAnswered || !quiz.isActive) return

        if (currentHint < maxHint - 1 && quiz.answer.lastIndex > currentHint) {
            quiz.lettersOpenNum += 1
            val hint = makeHint(quiz.answer, quiz.lettersOpenNum)
            respond(peerId, "Подсказка: $hint", questionMessageId)
        } else {
            val response = with(StringBuilder()) {
                append("Никто не дал правильного ответа.\n")
                append("Правильный ответ: ${quiz.answer}")
            }

            respond(peerId, response, questionMessageId)
            quiz.alreadyAnswered = true
            break
        }

        currentHint++
    }

    delay(5, TimeUnit.SECONDS)
    if (quiz.isActive) sendNewQuiz(peerId)
}

suspend fun ApplicationContext.interceptQuizAnswer(message: Chat) {
    if (!quizzes.containsKey(message.chatId)) return

    val quiz = quizzes[message.peerId] ?: return
    if (quiz.isActive && !quiz.alreadyAnswered) {
        if (message.text.startsWith(quiz.answer, true)) {
            quiz.alreadyAnswered = true
            message.respondWithForward("Поздравляю! Вы правы!")

            delay(5, TimeUnit.SECONDS)
            if (quiz.isActive) sendNewQuiz(message.peerId)
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