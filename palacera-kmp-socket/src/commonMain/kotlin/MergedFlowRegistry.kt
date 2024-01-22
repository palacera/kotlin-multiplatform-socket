import dispatcher.Dispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class MergedFlowRegistry<T>(
    private val dispatcher: Dispatcher,
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = job + dispatcher.io

    private val flowChannel = Channel<Pair<Flow<T>, Boolean>>(Channel.UNLIMITED)
    private val jobs = atomic<Map<Int, Job>>(mapOf())

    private val Flow<T>.id get() = hashCode()

    val registeredFlows: Flow<T> = channelFlow {
        for (pair in flowChannel) {
            ensureActive()

            try {
                val (flow, isRegistering) = pair

                if (isRegistering) {
                    launch(flow, this)
                } else {
                    cancel(flow)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // TODO handle error
            }
        }
    }.catch {
        // TODO handle error
    }.shareIn(
        this,
        SharingStarted.WhileSubscribed(),
    )

    fun register(flow: Flow<T>) {
        launch { flowChannel.send(Pair(flow, true)) }
    }

    fun unregister(flow: Flow<T>) {
        launch { flowChannel.send(Pair(flow, false)) }
    }

    private fun launch(flow: Flow<T>, scope: ProducerScope<T>) {
        val flowJob = launch(dispatcher.io) {
            flow.catch {
                // TODO handle error
            }.collect { scope.send(it) }
        }
        jobs.update { it + (flow.id to flowJob) }
    }

    private fun cancel(flow: Flow<T>) {
        jobs.value[flow.id]?.cancel()
        jobs.update { it - flow.id }
    }

    private fun cancelAllJobs() {
        jobs.value.forEach { (_, job) ->
            job.cancel()
        }
    }

    fun dispose() {
        cancelAllJobs()
        job.cancel()
        flowChannel.close()
    }
}
