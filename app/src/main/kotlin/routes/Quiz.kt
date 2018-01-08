package routes

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import tk.skeptick.bot.ApplicationContext
import tk.skeptick.bot.Chat
import tk.skeptick.bot.TypedMessageRoute
import java.util.concurrent.TimeUnit

fun TypedMessageRoute<Chat>.quiz() {
    onMessage("викторина") {
        onMessage("стоп") {
            intercept {
                val peerId = it.message.peerId
                val messageId = it.message.messageId
                quizAllocator.send(QuizEvent.Stop(this, peerId, messageId))
            }
        }

        intercept {
            val peerId = it.message.peerId
            val messageId = it.message.messageId
            quizAllocator.send(QuizEvent.Start(this, peerId, messageId))
        }
    }
}

suspend fun ApplicationContext.interceptQuizAnswer(message: Chat) {
    val peerId = message.peerId
    val messageId = message.messageId
    val text = message.text
    quizAllocator.send(QuizEvent.Answer(this, peerId, messageId, text))
}

private typealias Context = ApplicationContext
private sealed class QuizEvent(val context: Context, val peerId: Int) {
    class Start(context: Context, peerId: Int, val messageId: Int) : QuizEvent(context, peerId)
    class Stop(context: Context, peerId: Int, val messageId: Int) : QuizEvent(context, peerId)
    class HintTime(context: Context, peerId: Int) : QuizEvent(context, peerId)
    class ContinueTime(context: Context, peerId: Int) : QuizEvent(context, peerId)
    class Answer(context: Context, peerId: Int, val messageId: Int, val text: String) : QuizEvent(context, peerId)
}

private val questions by lazy {
    Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("quiz.txt")
            .bufferedReader()
            .lineSequence()
            .toList()
}

private val quizAllocator: SendChannel<QuizEvent> = actor {
    val quizzes = mutableMapOf<Int, SendChannel<QuizEvent>>()
    for (event in channel) {
        quizzes[event.peerId]?.send(event)
                ?: quizChannel()
                .also { quizzes.put(event.peerId, it) }
                .also { it.send(event) }
    }
}

private fun quizChannel(): SendChannel<QuizEvent> = actor {
    var question = ""
    var answer = ""
    var lettersOpenNum = 0
    var questionMessageId = 0
    var isRunning = false
    var isAnswered = false
    var waitHintJob: Job? = null
    var waitContinueJob: Job? = null

    fun startQuiz() {
        makeQuiz().also { question = it.first; answer = it.second }
        isRunning = true
        isAnswered = false
        lettersOpenNum = 0
    }

    fun stopQuiz() = runBlocking {
        isRunning = false
        isAnswered = false
        waitHintJob?.cancelAndJoin()
        waitContinueJob?.cancelAndJoin()
    }

    for (event in channel) {
        when (event) {

            is QuizEvent.Start -> when {
                isRunning && isAnswered ->
                    event.sendAlreadyRunningOnPause()
                isRunning ->
                    makeHint(answer, lettersOpenNum)
                            .also { event.sendAlreadyRunning(question, it) }
                else ->
                    startQuiz().let { event.sendNewQuestion(question) }
                            ?.also { questionMessageId = it }
                            ?.also { waitHintJob = waitHint(event.context, event.peerId) }
                            ?: stopQuiz()
            }

            is QuizEvent.Stop -> when {
                isRunning && isAnswered ->
                    stopQuiz().also { event.sendQuizStoppedOnPause() }
                isRunning ->
                    stopQuiz().also { event.sendQuizStopped() }
                else ->
                    event.sendQuizNotRunning()
            }

            is QuizEvent.HintTime -> when {
                isRunning && !isAnswered && lettersOpenNum++ < 5 && answer.length > 5 ->
                    event.sendHint(questionMessageId, makeHint(answer, lettersOpenNum))
                            .also { waitHintJob = waitHint(event.context, event.peerId) }
                isRunning && !isAnswered ->
                    event.sendNoOneAnswered(questionMessageId, answer)
                            .also { isAnswered = true }
                            .also { waitContinueJob = waitContinue(event.context, event.peerId) }
            }

            is QuizEvent.ContinueTime -> when {
                isRunning ->
                    startQuiz().let { event.sendNewQuestion(question) }
                            ?.also { questionMessageId = it }
                            ?.also { waitHintJob = waitHint(event.context, event.peerId) }
                            ?: stopQuiz()
            }

            is QuizEvent.Answer -> when {
                isRunning && !isAnswered && event.text.startsWith(answer, true) ->
                    event.sendCongratulation()
                            ?.also { isAnswered = true }
                            ?.also { waitContinueJob = waitContinue(event.context, event.peerId) }
            }
        }
    }
}

private suspend fun waitHint(context: Context, peerId: Int) = launch {
    delay(30, TimeUnit.SECONDS)
    quizAllocator.send(QuizEvent.HintTime(context, peerId))
}

private suspend fun waitContinue(context: Context, peerId: Int) = launch {
    delay(5, TimeUnit.SECONDS)
    quizAllocator.send(QuizEvent.ContinueTime(context, peerId))
}

private suspend fun QuizEvent.Start.sendAlreadyRunning(question: String, hint: String) =
        with(StringBuilder()) {
            append("В этом чате уже запущена викторина.\n")
            append("Текущий вопрос: $question\n")
            append("Текущая подсказка: $hint")
            context.respond(peerId, this, messageId)
        }

private suspend fun QuizEvent.Start.sendAlreadyRunningOnPause() =
        with(StringBuilder()) {
            append("В этом чате уже запущена викторина.\n")
            append("Подождите, скоро будет новый вопрос.")
            context.respond(peerId, this, messageId)
        }

private suspend fun QuizEvent.Start.sendNewQuestion(question: String) =
        context.respond(peerId, question)

private suspend fun QuizEvent.Stop.sendQuizNotRunning() =
        context.respond(peerId, "В вашем чате не запущена викторина.", messageId)

private suspend fun QuizEvent.Stop.sendQuizStopped() =
        context.respond(peerId, "Викторина остановлена. Ответа не будет.", messageId)

private suspend fun QuizEvent.Stop.sendQuizStoppedOnPause() =
        context.respond(peerId, "Викторина остановлена.", messageId)

private suspend fun QuizEvent.HintTime.sendHint(questionMessageId: Int, hint: String) =
        context.respond(peerId, "Подсказка: $hint", questionMessageId)

private suspend fun QuizEvent.ContinueTime.sendNewQuestion(question: String) =
        context.respond(peerId, question)

private suspend fun QuizEvent.HintTime.sendNoOneAnswered(questionMessageId: Int, answer: String) =
        with(StringBuilder()) {
            append("Никто не дал правильного ответа.\n")
            append("Правильный ответ: $answer")
            context.respond(peerId, this, questionMessageId)
        }

private suspend fun QuizEvent.Answer.sendCongratulation() =
        context.respond(peerId, "Поздравляю! Вы правы!", messageId)


private fun makeQuiz(): Pair<String, String> { // <Question, Answer>
    val randomLine = (Math.random() * questions.lastIndex).toInt()
    val (question, answer) = questions[randomLine].split('|')
    return question to answer
}

private fun makeHint(answer: String, lettersOpenNum: Int): String =
        answer.mapIndexed { i, c ->
            when {
                i < lettersOpenNum -> c
                c.isWhitespace() -> c
                else -> '٭'
            }
        }.joinToString(" ")