import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function

class CharLengthSum<out T>(val expr: Expression<T>, _columnType: IColumnType): Function<Int>() {
    override val columnType: IColumnType = _columnType
    override fun toSQL(queryBuilder: QueryBuilder): String = "SUM(CHAR_LENGTH(${expr.toSQL(queryBuilder)}))"
}

fun Column<String>.charLengthSum() = CharLengthSum(this, this.columnType)


fun String.isNumber(): Boolean {
    return all { it.isDigit() }
}

fun String.equalsLeastOne(vararg suffix: String): Boolean {
    suffix.forEach { if (equals(it, true)) return true }
    return false
}