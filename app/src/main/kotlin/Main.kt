import dao.*
import routes.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import tk.skeptick.bot.BotApplication
import tk.skeptick.bot.Chat
import tk.skeptick.bot.DefaultMessageRoute
import tk.skeptick.bot.User
import java.util.*

val appProperties by lazy { loadProperties() }

fun main(args: Array<String>) = runBlocking {
    initDatabase()

    val bot = BotApplication(appProperties.getProperty("accessToken"))
    bot.anyMessage { chat() }
    bot.onPtsUpdated { Settings.savePts(it) }
    bot.onHistoryLoaded(Settings.getPts()) {
        it.filter { !it.isServiceAct && it.isFromChat }
                .let { saveMessagesHistory(it) }
    }

    bot.run()
}

private fun DefaultMessageRoute.chat() {
    onMessageFrom<Chat> {
        handle { saveChatEvent(it.message) }
        intercept { interceptQuizAnswer(it.message) }
        onIncomingMessage("бет", "бетховен") {
            quiz()
            about()
            help()
            statistics()
            addFriendChat()
        }
    }

    onMessageFrom<User> {
        onIncomingMessage {
            addFriendUser()
        }
    }
}

private fun initDatabase() {
    val config = HikariConfig("/hikari.properties")
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
    transaction { SchemaUtils.create(MessagesHistory, Settings) }
}

private fun loadProperties(): Properties {
    val properties = Properties()
    Thread.currentThread().contextClassLoader
            .getResourceAsStream("application.properties")
            .apply { properties.load(this) }

    return properties
}