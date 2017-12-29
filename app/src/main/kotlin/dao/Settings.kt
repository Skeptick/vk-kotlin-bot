package dao

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object Settings : Table("settings") {
    val key = varchar("key", 64).primaryKey()
    val value = varchar("value", 64)
}

fun Settings.getPts(): Long {
    return transaction {
        slice(value).select { key eq "pts" }
                .firstOrNull()
                ?.let { it[value] }
                ?.toLong()
                ?: 0L
    }
}

fun Settings.savePts(pts: Long) {
    transaction {
        if (getPts() != 0L) {
            update({ key eq "pts" }) {
                it[value] = pts.toString()
            }
        } else {
            insert {
                it[key] = "pts"
                it[value] = pts.toString()
            }.generatedKey
        }
    }
}