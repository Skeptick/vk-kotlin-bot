package tk.skeptick.bot

import com.github.kittinunf.fuel.httpDownload
import java.io.File

@Suppress("unused")
suspend fun downloadFile(url: String): File? {
    val file = createTempFile("${System.currentTimeMillis()}")
    val (bytes) = url.httpDownload().destination { _, _ -> file }.response().third
    return if (bytes != null) file
    else null
}