package tk.skeptick.bot

import tk.skeptick.bot.SenderType.*

data class RoutePath(val passedPath: String, val restOfMessage: String)
data class Call<out E>(val route: RoutePath, val message: E)

abstract class MessageRoute<E : MessageEvent>(
        open val context: ApplicationContext,
        open val parent: MessageRoute<*>?,
        open val senderType: SenderType?,
        open val onlyIncoming: Boolean,
        open val phrases: List<String>) {

    private var handler: (suspend ApplicationContext.(Call<E>) -> Unit)? = null
    private var interceptor: (suspend ApplicationContext.(Call<E>) -> Unit)? = null

    internal val children: MutableSet<MessageRoute<*>> = mutableSetOf()

    @Suppress("unchecked_cast")
    suspend internal fun pass(message: E, routePath: RoutePath) {
        handler?.let { context.it(Call(routePath, message)) }

        val iterator = children.iterator()
        while (iterator.hasNext()) {
            val messageRoute = iterator.next()
            val overlapPhrase = messageRoute.phrases.find {
                routePath.restOfMessage.startsWith(it, true)
            } ?: String()

            if (messageRoute.phrases.isNotEmpty() && overlapPhrase.isBlank()) continue
            if (messageRoute.onlyIncoming && message.isOutbox) continue
            if (messageRoute.senderType != null && messageRoute.senderType != message.senderType) continue

            var restOfMessage = routePath.restOfMessage.substring(overlapPhrase.length)
            if (overlapPhrase.isNotBlank()) {
                if (restOfMessage.isNotEmpty() && restOfMessage[0].isLetterOrDigit()) continue
                else restOfMessage = restOfMessage.dropWhile { !it.isLetterOrDigit() }
            }

            val newRoute = routePath.passedPath
                    .let { if (it.isNotBlank()) it + " " else it }
                    .let { it + routePath.restOfMessage.substring(0..overlapPhrase.lastIndex) }
                    .let { RoutePath(it, restOfMessage) }

            if (messageRoute.senderType != null) {
                when (message.senderType) {
                    USER -> (messageRoute as MessageRoute<User>).pass(message as User, newRoute)
                    COMMUNITY -> (messageRoute as MessageRoute<Community>).pass(message as Community, newRoute)
                    CHAT -> (messageRoute as MessageRoute<Chat>).pass(message as Chat, newRoute)
                }
            } else (messageRoute as MessageRoute<MessageEvent>).pass(message as MessageEvent, newRoute)

            return
        }

        interceptor?.let { context.it(Call(routePath, message)) }
    }

    @Suppress("unused")
    fun handle(block: suspend ApplicationContext.(Call<E>) -> Unit) {
        if (handler != null)
            throw HandlerOverrideException("You can set only one handler")
        else handler = block
    }

    @Suppress("unused")
    fun intercept(block: suspend ApplicationContext.(Call<E>) -> Unit) {
        if (interceptor != null)
            throw HandlerOverrideException("You can set only one interceptor")
        else interceptor = block
    }

}

@ContextDsl
@Suppress("unused")
class TypedMessageRoute<E : MessageEvent>(
        context: ApplicationContext,
        parent: MessageRoute<*>?,
        senderType: SenderType,
        onlyIncoming: Boolean,
        phrases: List<String>
) : MessageRoute<E>(context, parent, senderType, onlyIncoming, phrases) {

    @Suppress("unused")
    fun onMessage(vararg words: String, block: TypedMessageRoute<E>.() -> Unit) {
        val phrases = preparePhrases(words)
        checkIntersectPhrases(phrases)
        TypedMessageRoute<E>(context, this, senderType!!, onlyIncoming, phrases)
                .also { children.add(it) }.block()
    }

    @Suppress("unused")
    fun onIncomingMessage(vararg words: String, block: TypedMessageRoute<E>.() -> Unit) {
        val phrases = preparePhrases(words)
        checkIntersectPhrases(phrases)

        if (onlyIncoming) {
            val path = getFullPath() + phrases.toString()
            context.log.warn("<Only Incoming> flag already installed for path: $path")
        }

        TypedMessageRoute<E>(context, this, senderType!!, true, phrases)
                .also { children.add(it) }.block()
    }
}

@ContextDsl
@Suppress("unused")
class DefaultMessageRoute(
        context: ApplicationContext,
        parent: MessageRoute<*>?,
        onlyIncoming: Boolean,
        phrases: List<String>
) : MessageRoute<MessageEvent>(context, parent, null, onlyIncoming, phrases) {

    @Suppress("unused")
    fun onMessage(vararg words: String, block: DefaultMessageRoute.() -> Unit) {
        val phrases = preparePhrases(words)
        checkIntersectPhrases(phrases)
        DefaultMessageRoute(context, this, onlyIncoming, phrases)
                .also { children.add(it) }.block()
    }

    @Suppress("unused")
    fun onIncomingMessage(vararg words: String, block: DefaultMessageRoute.() -> Unit) {
        val phrases = preparePhrases(words)
        checkIntersectPhrases(phrases)

        if (onlyIncoming) {
            val path = getFullPath() + phrases.toString()
            context.log.warn("<Only Incoming> flag already installed for path: $path")
        }

        DefaultMessageRoute(context, this, true, phrases)
                .also { children.add(it) }.block()
    }

    @Suppress("unused")
    inline fun <reified T : MessageEvent> onMessageFrom(
            vararg words: String,
            noinline block: TypedMessageRoute<T>.() -> Unit) {

        val phrases = preparePhrases(words)
        checkIntersectPhrases(phrases)

        val senderType = when (T::class) {
            User::class -> USER
            Chat::class -> CHAT
            Community::class -> COMMUNITY
            else -> throw IllegalArgumentException("Unknown sender type class")
        }

        TypedMessageRoute<T>(context, this, senderType, onlyIncoming, phrases)
                .also { internalChildren.add(it) }.block()
    }

    @PublishedApi
    internal val internalChildren: MutableSet<MessageRoute<*>> get() = children
}

fun <E : MessageEvent> MessageRoute<E>.preparePhrases(words: Array<out String>): List<String> {
    val phrases = words.toList().map(String::trim)

    if (phrases.find(String::isEmpty) != null)
        throw IllegalArgumentException("Empty phrase are not allowed")

    phrases.forEachIndexed { i, s ->
        if (s != words[i]) context.log.warn("Using space at the end of phrase: ${getFullPath()}[${words[i]}]")
    }

    return phrases.sortedByDescending { it.length }
}

fun <E : MessageEvent> MessageRoute<E>.checkIntersectPhrases(phrases: List<String>) {
    val intersectingWords = children.flatMap { it.phrases }.intersect(phrases)
    if (intersectingWords.isNotEmpty())
        throw HandlerOverrideException("Found handler on same route with same phrase: $intersectingWords")
}

fun <E : MessageEvent> MessageRoute<E>.getFullPath(): String {
    var path = String()
    var parent: MessageRoute<*>? = this
    while (parent != null) {
        if (parent.phrases.isNotEmpty())
            path = "${parent.phrases} -> " + path

        parent = parent.parent
    }

    return path
}