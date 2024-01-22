package api.ping

import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import socket.api.ping.PingEvent
import socket.api.ping.PingEventFlow
import socket.api.ping.PingState

@OptIn(ExperimentalCoroutinesApi::class)
class PingEventFlowTest: CoroutineScope {

    private lateinit var pingEventFlow: PingEventFlow
    private lateinit var job: Job

    private var actualState: PingState? = null

    private var actualException: Exception? = null

    private val maxUnconfirmedPingsBeforeThrowing = 3

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    @BeforeTest
    fun setup() {
        job = Job()
        Dispatchers.setMain(Dispatchers.Unconfined)

        pingEventFlow = PingEventFlow(maxUnconfirmedPingsBeforeThrowing)
        launch {
            pingEventFlow.observePingState(
                lambda = { actualState = it }
            )
        }
    }

    @AfterTest
    fun tearDown() {
        job.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun whenNoEventsPostedThenStateIsSent() = runTest {
        advanceUntilIdle()

        assertNull(actualException)
        assertEquals(PingState.Sent, actualState)
    }

    @Test
    fun whenOneSendEventPostedThenStateIsSent() = runTest {
        pingEventFlow.post(PingEvent.Send)
        advanceUntilIdle()

        assertNull(actualException)
        assertEquals(PingState.Sent, actualState)
    }

    @Test
    fun whenTwoSendEventPostedThenStateIsSent() = runTest {
        pingEventFlow.post(PingEvent.Receive)
        repeat(2) {
            pingEventFlow.post(PingEvent.Send)
        }
        advanceUntilIdle()

        assertNull(actualException)
        assertEquals(PingState.Sent, actualState)
    }

    @Test
    fun whenOneReceiveEventPostedThenStateIsConfirmed() = runTest {
        pingEventFlow.post(PingEvent.Receive)
        advanceUntilIdle()

        assertNull(actualException)
        assertEquals(PingState.Confirmed, actualState)
    }

    @Test
    fun whenMultipleReceiveEventsPostedThenStateIsConfirmed() = runTest {
        repeat(3) {
            pingEventFlow.post(PingEvent.Receive)
        }
        advanceUntilIdle()

        assertNull(actualException)
        assertEquals(PingState.Confirmed, actualState)
    }

    @Test
    fun whenReceivedEventPostedAfterSendEventThenStateIsConfirmed() = runTest {
        pingEventFlow.post(PingEvent.Send)
        pingEventFlow.post(PingEvent.Receive)
        advanceUntilIdle()

        assertNull(actualException)
        assertEquals(PingState.Confirmed, actualState)
    }

    @Test
    fun whenSendEventPostedAfterReceiveEventThenStateIsConfirmed() = runTest {
        pingEventFlow.post(PingEvent.Receive)
        pingEventFlow.post(PingEvent.Send)
        advanceUntilIdle()

        assertNull(actualException)
        assertEquals(PingState.Confirmed, actualState)
    }

    @Test
    fun whenSendEventsPostedSurpassMaxThenThrowException()  {
        val exception = assertFailsWith(Exception::class) {
            runTest {
                val job = launch {
                    pingEventFlow.observePingState {}
                }
                repeat(maxUnconfirmedPingsBeforeThrowing) {
                    pingEventFlow.post(PingEvent.Send)
                }
                advanceUntilIdle()
                job.cancelAndJoin()
            }
        }

        val expectedMessage = "Sent $maxUnconfirmedPingsBeforeThrowing ping requests without response."
        assertEquals(expectedMessage, exception.message)
    }
}
