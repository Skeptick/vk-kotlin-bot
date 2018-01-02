package routes

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.cancelAndJoin
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import routes.QuizAction.*
import tk.skeptick.bot.ApplicationContext
import tk.skeptick.bot.Chat
import tk.skeptick.bot.TypedMessageRoute
import java.util.concurrent.TimeUnit

typealias Context = ApplicationContext

sealed class QuizAction(open val peerId: Int) {

    class StartQuiz(
            val context: Context,
            override val peerId: Int,
            private val messageId: Int
    ) : QuizAction(peerId) {

        suspend fun sendAlreadyStarted() {
            val response = with(StringBuilder()) {
                append("В этом чате уже запущена викторина.\n")
                append("Подождите, скоро будет новый вопрос.")
            }

            context.respond(peerId, response, messageId)
        }

        suspend fun sendOldQuestion(question: String, answer: String, hintCount: Int) {
            val hint = makeHint(answer, hintCount)
            val response = with(StringBuilder()) {
                append("В этом чате уже запущена викторина.\n")
                append("Текущий вопрос: $question\n")
                append("Текущая подсказка: $hint")
            }

            context.respond(peerId, response, messageId)
        }

        suspend fun sendNewQuestion(question: String): Int {
            return context.respond(peerId, question) ?: 0
        }

    }

    class StopQuiz(
            val context: Context,
            override val peerId: Int,
            private val messageId: Int
    ) : QuizAction(peerId) {

        suspend fun sendNotRunning() {
            context.respond(peerId, "В вашем чате не запущена викторина.", messageId)
        }

        suspend fun sendStoppedOnPause() {
            context.respond(peerId, "Викторина остановлена.", messageId)
        }

        suspend fun sendStopped() {
            context.respond(peerId, "Викторина остановлена. Ответа не будет.", messageId)
        }

    }

    class ContinueQuiz(
            val context: Context,
            override val peerId: Int
    ) : QuizAction(peerId) {

        suspend fun sendNewQuestion(question: String): Int {
            return context.respond(peerId, question) ?: 0
        }

    }

    class SendHint(
            val context: Context,
            override val peerId: Int
    ) : QuizAction(peerId) {

        suspend fun sendHint(questionMessage: Int, answer: String, hintCount: Int) {
            val hint = makeHint(answer, hintCount)
            context.respond(peerId, "Подсказка: $hint", questionMessage)
        }

        suspend fun sendLastHint(questionMessage: Int, answer: String) {
            val response = with(StringBuilder()) {
                append("Никто не дал правильного ответа.\n")
                append("Правильный ответ: $answer")
            }

            context.respond(peerId, response, questionMessage)
        }

    }

    class CheckAnswer(
            val context: Context,
            override val peerId: Int,
            private val messageId: Int,
            val text: String
    ) : QuizAction(peerId) {

        suspend fun sendSuccessful() {
            context.respond(peerId, "Поздравляю! Вы правы!", messageId)
        }

    }

    class RemoveQuiz(override val peerId: Int) : QuizAction(peerId)

}

private val quizManager = actor<QuizAction> {
    val quizzes = mutableMapOf<Int, SendChannel<QuizAction>>()
    for (action in channel) {
        when (action) {
            is RemoveQuiz -> {
                quizzes[action.peerId]?.close()
                quizzes.remove(action.peerId)
            }

            is StartQuiz, is StopQuiz -> {
                quizzes[action.peerId]?.send(action)
                        ?: quizActor()
                        .apply { quizzes.put(action.peerId, this) }
                        .also { it.send(action) }
            }

            else -> quizzes[action.peerId]?.send(action)
        }
    }
}

private fun quizActor(): SendChannel<QuizAction> = actor {
    var question = String()
    var answer = String()
    var hintCount = 0

    var isRunning = false
    var isAnswered = false

    var awaitHint: Job? = null
    var awaitContinue: Job? = null

    var questionMessage = 0

    fun updateQuiz() {
        val quiz = makeQuiz()
        question = quiz.first
        answer = quiz.second
        isRunning = true
        isAnswered = false
        hintCount = 0
    }

    for (action in channel) {
        when (action) {
            is StartQuiz -> {
                when {
                    isRunning && isAnswered -> action.sendAlreadyStarted()
                    isRunning -> action.sendOldQuestion(question, answer, hintCount)
                    else -> {
                        updateQuiz()
                        questionMessage = action.sendNewQuestion(question)
                        when (questionMessage) {
                            0 -> quizManager.send(RemoveQuiz(action.peerId))
                            else -> awaitHint = awaitHint(action.context, action.peerId)
                        }
                    }
                }
            }

            is StopQuiz -> {
                when {
                    !isRunning -> action.sendNotRunning()
                    isAnswered -> action.sendStoppedOnPause()
                    else -> action.sendStopped()
                }

                awaitContinue?.cancelAndJoin()
                awaitHint?.cancelAndJoin()
                quizManager.send(RemoveQuiz(action.peerId))
            }

            is ContinueQuiz -> {
                updateQuiz()
                questionMessage = action.sendNewQuestion(question)
                when (questionMessage) {
                    0 -> quizManager.send(RemoveQuiz(action.peerId))
                    else -> awaitHint = awaitHint(action.context, action.peerId)
                }
            }

            is SendHint -> {
                when {
                    isRunning && !isAnswered && hintCount < 4 && answer.lastIndex > hintCount -> {
                        hintCount++
                        action.sendHint(questionMessage, answer, hintCount)
                        awaitHint = awaitHint(action.context, action.peerId)
                    }
                    isRunning && !isAnswered -> {
                        isAnswered = true
                        action.sendLastHint(questionMessage, answer)
                        awaitContinue = awaitContinue(action.context, action.peerId)
                    }
                }
            }

            is CheckAnswer -> {
                if (isRunning && !isAnswered && action.text.startsWith(answer, true)) {
                    isAnswered = true
                    action.sendSuccessful()
                    awaitContinue = awaitContinue(action.context, action.peerId)
                }
            }
        }
    }
}

private fun awaitHint(context: Context, peerId: Int): Job = launch {
    delay(30, TimeUnit.SECONDS)
    quizManager.send(SendHint(
            context = context,
            peerId = peerId
    ))
}

private fun awaitContinue(context: Context, peerId: Int): Job = launch {
    delay(5, TimeUnit.SECONDS)
    quizManager.send(ContinueQuiz(
            context = context,
            peerId = peerId
    ))
}

fun TypedMessageRoute<Chat>.quiz() {

    onMessage("викторина") {
        onMessage("стоп") {
            intercept {
                quizManager.send(StopQuiz(
                        context = this,
                        peerId = it.message.peerId,
                        messageId = it.message.messageId
                ))
            }
        }

        intercept {
            quizManager.send(StartQuiz(
                    context = this,
                    peerId = it.message.peerId,
                    messageId = it.message.messageId
            ))
        }
    }

}

suspend fun ApplicationContext.interceptQuizAnswer(message: Chat) {
    quizManager.send(CheckAnswer(
            context = this,
            peerId = message.peerId,
            text = message.text,
            messageId = message.messageId
    ))
}

private val questions = Thread.currentThread().contextClassLoader
        .getResourceAsStream("quiz.txt")
        .bufferedReader()
        .lineSequence()
        .toList()

private fun makeQuiz(): Pair<String, String> {
    val randomLine = (Math.random() * questions.lastIndex).toInt()
    val line = questions[randomLine]
    val (question, answer) = line.split('|')
    return question to answer
}

private fun makeHint(answer: String, hintCount: Int): String =
        answer.mapIndexed { i, c ->
            when {
                i < hintCount -> c
                c.isWhitespace() -> c
                else -> '٭'
            }
        }.joinToString(" ")