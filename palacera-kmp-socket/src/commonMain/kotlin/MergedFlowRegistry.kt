import dispatcher.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

class MergedFlowRegistry<T>(
    private val dispatcher: Dispatcher
) {
    private val _registeredFlows: MutableStateFlow<List<Flow<T>>> = MutableStateFlow(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val registeredFlows = _registeredFlows.flatMapLatest { flows ->
        merge(*flows.toTypedArray())
    }.shareIn(
        CoroutineScope(dispatcher.io),
        SharingStarted.WhileSubscribed()
    )

    fun register(flow: Flow<T>) =
        _registeredFlows.update { it + flow }

    fun unregister(flow: Flow<T>) =
        _registeredFlows.update { it - flow }
}
