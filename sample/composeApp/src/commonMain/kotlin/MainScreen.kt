import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class MainScreen : Screen {
    @Composable
    override fun Content() {

        val viewModel = rememberScreenModel { MainViewModel() }
        val state = viewModel.mainFlow.collectAsState()

        Text(state.value.toString())
    }
}

sealed interface MainState {
    data object Init: MainState
    data object Loading: MainState
    data class Success(val message: String): MainState
    data class Error(val message: String): MainState
}

class MainViewModel(
    private val dispatcher: CoroutineDispatcher = PlatformDispatcher.io,
    private val connect: Connect = Connect()
): ScreenModel {

    val mainFlow = connect.mainFlow.stateIn(screenModelScope, SharingStarted.WhileSubscribed(), MainState.Init)

    init {
        screenModelScope.launch(PlatformDispatcher.io) {
            connect.connect()
        }
    }
}

class Connect {

    private val _mainFlow: MutableStateFlow<List<StateFlow<MainState>>> = MutableStateFlow(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val mainFlow = _mainFlow.flatMapLatest { flows ->
        merge(*flows.toTypedArray())
    }

    private fun MutableStateFlow<List<StateFlow<MainState>>>.addFlow(flow: StateFlow<MainState>) =
        update { it + flow }

    suspend fun connect() {
        val adapter = Adapter()
        _mainFlow.addFlow(adapter.mainFlow)
        adapter.connect()
    }
}

class Adapter {

    private val _mainFlow: MutableStateFlow<MainState> = MutableStateFlow(MainState.Init)
    val mainFlow = _mainFlow.asStateFlow()

    suspend fun connect() {
        _mainFlow.value = MainState.Loading
        establishConnection()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun establishConnection() = suspendCancellableCoroutine { continuation ->
        CoroutineScope(PlatformDispatcher.io).launch {
            try {
                delay(2.seconds) // Simulate connection logic
                _mainFlow.value = MainState.Success("hello")
                if (!continuation.isCancelled) {
                    continuation.resume(Unit) {
                        cancel()
                    }
                }
            } catch (e: Exception) {
                if (!continuation.isCancelled) {
                    _mainFlow.value = MainState.Error("An error occured")
                    continuation.resumeWithException(e)
                }
            }
        }
    }
}
