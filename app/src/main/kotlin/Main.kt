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
import java.util.*

val appProperties by lazy { loadProperties() }

fun main(args: Array<String>) = runBlocking {
    initDatabase()

    val bot = BotApplication(appProperties.getProperty("accessToken"))
    bot.anyMessage { chat() }
    bot.onPtsUpdated { savePts(it) }
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
        }
    }
}

/* Сохраняем каждый пятидесятый PTS,
 чтоб не тревожить БД попусту */
private var ptsCounter = -1
private fun savePts(pts: Long) {
    if (ptsCounter++ == -1) Settings.savePts(pts)
    else if (ptsCounter == 50) {
        Settings.savePts(pts)
        ptsCounter = 0
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