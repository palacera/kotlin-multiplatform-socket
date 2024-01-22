package api.ping

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import socket.api.ping.MaxUnconfirmedPingsException
import socket.api.ping.PingEvent
import socket.api.ping.PingFlow
import socket.api.ping.PingState

@OptIn(ExperimentalCoroutinesApi::class)
class PingEventFlowTest {

    private lateinit var testScope: TestScope
    private lateinit var pingFlow: PingFlow
    private lateinit var actual: MutableList<PingState>

    @BeforeTest
    fun setup() {
        testScope = TestScope()

        actual = mutableListOf()

        pingFlow = PingFlow(
            maxConsecutiveBeforeWaitingState = 3,
            maxUnconfirmedPingsBeforeError = 4,
        )
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    private fun startObservingAndCollecting(results: MutableList<PingState>): Pair<Job, Job> {
        val collectJob = testScope.launch {
            pingFlow.pingStateFlow.toList(results)
        }
        val observeJob = testScope.launch {
            pingFlow.observePingState()
        }
        testScope.advanceUntilIdle()
        return Pair(observeJob, collectJob)
    }

    private fun cancelJobs(vararg jobs: Job) {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun whenNoEventsPostedThenNoState() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        advanceUntilIdle()

        val expected = mutableListOf<PingState>()

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenOneSendEventPostedThenStateIsSent() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        pingFlow.post(PingEvent.Send)
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Sent,
        )

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenLessThanMaxConsecutiveSendEventPostedThenStateIsSent() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        pingFlow.post(PingEvent.Receive)
        repeat(pingFlow.maxConsecutiveBeforeWaitingState - 1) {
            pingFlow.post(PingEvent.Send)
        }
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Confirmed,
            PingState.Sent,
            PingState.Sent,
        )

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenEqualsMaxConsecutiveSendEventPostedThenStateIsSent() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        pingFlow.post(PingEvent.Receive)
        repeat(pingFlow.maxConsecutiveBeforeWaitingState) {
            pingFlow.post(PingEvent.Send)
        }
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Confirmed,
            PingState.Sent,
            PingState.Sent,
            PingState.Waiting,
        )

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenOneReceiveEventPostedThenStateIsConfirmed() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        pingFlow.post(PingEvent.Receive)
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Confirmed,
        )

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenMultipleReceiveEventsPostedThenStateIsConfirmed() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        repeat(3) {
            pingFlow.post(PingEvent.Receive)
        }
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Confirmed,
            PingState.Confirmed,
            PingState.Confirmed,
        )

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenReceivedEventPostedAfterSendEventThenStateIsConfirmed() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        pingFlow.post(PingEvent.Send)
        pingFlow.post(PingEvent.Receive)
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Sent,
            PingState.Confirmed,
        )

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenSendEventPostedAfterReceiveEventThenStateIsSent() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        pingFlow.post(PingEvent.Receive)
        pingFlow.post(PingEvent.Send)
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Confirmed,
            PingState.Sent,
        )

        assertEquals(expected, actual)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenErrorEventPostedThenStateIsError() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        pingFlow.post(PingEvent.Receive)
        pingFlow.post(PingEvent.Send)
        pingFlow.post(PingEvent.Error(Exception("An error occurred.")))
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Confirmed,
            PingState.Sent,
        )

        assertEquals(expected, actual.dropLast(1))

        val expectedState = actual.last()
        assertTrue { expectedState is PingState.Error }

        val expectedException = (expectedState as PingState.Error).exception
        val expectedExceptionMessage = "An error occurred."
        assertEquals(expectedExceptionMessage, expectedException.message)

        cancelJobs(observeJob, collectJob)
    }

    @Test
    fun whenSendEventsPostedSurpassMaxThenStateIsError() = testScope.runTest {
        val (observeJob, collectJob) = startObservingAndCollecting(actual)

        repeat(pingFlow.maxUnconfirmedPingsBeforeError) {
            pingFlow.post(PingEvent.Send)
        }
        advanceUntilIdle()

        val expected = mutableListOf<PingState>(
            PingState.Sent,
            PingState.Sent,
            PingState.Waiting,
        )

        assertEquals(expected, actual.dropLast(1))

        val expectedState = actual.last()
        assertTrue { expectedState is PingState.Error }

        val expectedException = (expectedState as PingState.Error).exception
        assertTrue { expectedException is MaxUnconfirmedPingsException }

        val expectedExceptionMessage = "Sent ${pingFlow.maxUnconfirmedPingsBeforeError} ping requests without a response."
        assertEquals(expectedExceptionMessage, expectedException.message)

        cancelJobs(observeJob, collectJob)
    }

}


//    @Test
//    fun exampleExceptionTest()  {
//        val exception = assertFailsWith(Exception::class) {
//            runTest {
//                val job = launch {
//                    pingEventFlow.observePingState {}
//                }
//                repeat(pingEventFlow.maxUnconfirmedPingsBeforeError) {
//                    pingEventFlow.post(PingEvent.Send)
//                }
//                advanceUntilIdle()
//                job.cancelAndJoin()
//            }
//        }
//
//        val expectedMessage = "Sent ${pingEventFlow.maxUnconfirmedPingsBeforeError} ping requests without response."
//        assertEquals(expectedMessage, exception.message)
//    }
