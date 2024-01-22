package socket.api

import AtomicSet
import PlatformDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SocketMessageReceiptManager {

    private val awaitingReceipt by lazy {
        AtomicSet<SocketMessageReceipt>()
    }

    private lateinit var awaitReceiptsJob: Job

    suspend fun awaitReceipts(block: suspend (SocketMessageReceiptManager) -> Unit): Job {
        if (!::awaitReceiptsJob.isInitialized) {
            awaitReceiptsJob = CoroutineScope(PlatformDispatcher.io).launch { block(this@SocketMessageReceiptManager) }
        }

        return awaitReceiptsJob
    }

    fun getConfirmedReceipt(content: ByteArray): SocketMessageReceipt? = awaitingReceipt.set
        .firstOrNull { it.isConfirmed(content) }

    suspend fun add(messageReceipt: SocketMessageReceipt) {
        awaitingReceipt.add(messageReceipt)
    }

    fun remove(messageReceipt: SocketMessageReceipt) {
        awaitingReceipt.remove(messageReceipt)
    }

    fun clear() {
        awaitingReceipt.clear()
    }

    fun cancel() {
        if (::awaitReceiptsJob.isInitialized) {
            awaitReceiptsJob.cancel()
        }
    }
}
