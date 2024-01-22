import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface Mvi<State, Event, SideEffect> {
    val state: SharedFlow<State>
    val event: Flow<Event>
    val sideEffect: Flow<SideEffect>

    suspend fun postEvent(event: Event)

    suspend fun emitState(state: State)

    fun CoroutineScope.emitSideEffect(effect: SideEffect)
}

class MVIDelegate<State, Event, SideEffect> internal constructor(
    initialState: State,
) : Mvi<State, Event, SideEffect> {

    private val _state = MutableSharedFlow<State>()
    override val state: SharedFlow<State> = _state.asSharedFlow().apply { distinctUntilChanged() }

    private val _event = MutableSharedFlow<Event>()
    override val event: Flow<Event> = _event.asSharedFlow()

    private val _sideEffect by lazy { Channel<SideEffect>() }
    override val sideEffect: Flow<SideEffect> by lazy { _sideEffect.receiveAsFlow() }

    override suspend fun postEvent(event: Event) {
        _event.emit(event)
    }

    override suspend fun emitState(state: State) {
        _state.emit(state)
    }

    override fun CoroutineScope.emitSideEffect(effect: SideEffect) {
        launch { _sideEffect.send(effect) }
    }
}

fun <State, Event, SideEffect> mvi(
    initialState: State,
): Mvi<State, Event, SideEffect> = MVIDelegate(initialState)
