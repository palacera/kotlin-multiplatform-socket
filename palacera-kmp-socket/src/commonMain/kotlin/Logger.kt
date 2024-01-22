import co.touchlab.kermit.Logger
import co.touchlab.kermit.NoTagFormatter
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import kotlin.jvm.JvmInline
import socket.api.connection.messageOrDefault

private val log = Logger(
    loggerConfigInit(platformLogWriter(NoTagFormatter)),
    "kmpsocketc"
)

@JvmInline
value class Tag(val value: String)
fun tag(vararg tag: String): Tag = Tag(tag.joinToString(separator = ":"))
fun Tag.append(vararg tag: String): Tag = tag(value, *tag)

suspend fun i(tag: Tag, message: suspend () -> Any?) {
    log.i(
        messageString = message().toString(),
        throwable = null,
        tag = tag.value.ifBlank { log.tag }
    )
}

suspend fun d(tag: Tag, message: suspend () -> Any?) {
    log.d(
        messageString = message().toString(),
        throwable = null,
        tag = tag.value.ifBlank { log.tag }
    )
}

fun logd(tag: String, append: String = "", message: () -> Any?) {
    log.d(
        messageString = append + message(),
        throwable = null,
        tag = tag.ifBlank { log.tag }
    )
}

fun logw(tag: String, append: String = "", message: () -> Any?) {
    log.w(
        messageString = append + message(),
        throwable = null,
        tag = tag.ifBlank { log.tag }
    )
}

fun loge(tag: String, exception: Exception? = null, append: String = "", message: () -> Any?) {
    log.e(
        messageString = append + (message() ?: exception?.messageOrDefault ?: "An error occurred"),
        throwable = exception,
        tag = tag.ifBlank { log.tag }
    )
}

suspend fun e(tag: Tag, exception: Exception? = null, message: (suspend () -> String?)? = { "" }) {
    log.e(
        messageString = message?.invoke() ?: exception?.message ?: "An error occurred",
        throwable = exception,
        tag = tag.value.ifBlank { log.tag }
    )
}
