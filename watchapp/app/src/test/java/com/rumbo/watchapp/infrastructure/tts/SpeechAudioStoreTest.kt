package com.rumbo.watchapp.infrastructure.tts

import com.rumbo.watchapp.domain.error.QuotaExhaustedError
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val DOWNLOAD_MS = 3_000L

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechAudioStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val requested = mutableListOf<String>()
    private var failure: Exception? = null
    private var now = 0L

    private fun TestScope.store(): SpeechAudioStore {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return SpeechAudioStore(
            folder.root,
            download = { text ->
                requested += text
                delay(DOWNLOAD_MS)
                failure?.let { throw it }
                text.toByteArray()
            },
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            clock = { now },
            io = dispatcher,
        )
    }

    @Test
    fun anAudioIsAskedOnceAndThenComesFromTheFile() = runTest {
        val store = store()

        val first = store.get("Uno")
        val second = store.get("Uno")

        assertEquals(first, second)
        assertArrayEquals("Uno".toByteArray(), first.readBytes())
        assertEquals(listOf("Uno"), requested)
    }

    @Test
    fun aPreparedAudioIsNotAskedAgain() = runTest {
        val store = store()

        store.prepare("Uno")
        store.get("Uno")

        assertEquals(listOf("Uno"), requested)
    }

    @Test
    fun sayingAPhraseThatIsStillPreparingWaitsForItInsteadOfAskingTwice() = runTest {
        val store = store()
        val preparing = launch { store.prepare("Gira ahora a la derecha.") }
        runCurrent()

        val file = store.get("Gira ahora a la derecha.")
        preparing.join()

        assertEquals(listOf("Gira ahora a la derecha."), requested)
        assertTrue(file.length() > 0)
    }

    @Test
    fun aPhraseCutOffStillLeavesItsAudioReady() = runTest {
        val store = store()
        val speaking = launch { store.get("Uno") }
        runCurrent()

        speaking.cancelAndJoin()
        testScheduler.advanceTimeBy(DOWNLOAD_MS + 1)
        store.get("Uno")

        assertEquals(listOf("Uno"), requested)
    }

    @Test
    fun preparingStopsAtTwoRequestsPerMinute() = runTest {
        val store = store()

        listOf("Uno", "Dos", "Tres").forEach { store.prepare(it) }

        assertEquals(listOf("Uno", "Dos"), requested)
    }

    @Test
    fun whatHasToBeSaidIsAskedEvenIfPreparingHasNoRoomLeft() = runTest {
        val store = store()
        store.prepare("Uno")
        store.prepare("Dos")

        store.get("Gira ahora a la derecha.")

        assertEquals(listOf("Uno", "Dos", "Gira ahora a la derecha."), requested)
    }

    @Test
    fun whatIsSaidAlsoCountsForPreparing() = runTest {
        val store = store()
        store.get("Uno")
        store.get("Dos")

        store.prepare("Tres")

        assertEquals(listOf("Uno", "Dos"), requested)
    }

    @Test
    fun preparingHasRoomAgainAfterAMinute() = runTest {
        val store = store()
        store.prepare("Uno")
        store.prepare("Dos")

        now = REQUEST_WINDOW_MS
        store.prepare("Tres")

        assertEquals(listOf("Uno", "Dos", "Tres"), requested)
    }

    @Test
    fun whatIsAlreadySavedUsesNoRoom() = runTest {
        val store = store()
        store.get("Uno")

        repeat(5) { store.prepare("Uno") }
        store.prepare("Dos")

        assertEquals(listOf("Uno", "Dos"), requested)
    }

    @Test
    fun aRejectionReachesWhoAskedAndTheNextTryAsksAgain() = runTest {
        val store = store()
        failure = QuotaExhaustedError("Límite por minuto.", retryAfterMs = 27_000)

        val rejected = async { runCatching { store.get("Uno") }.exceptionOrNull() }
        assertTrue(rejected.await() is QuotaExhaustedError)

        failure = null
        assertTrue(store.get("Uno").length() > 0)
        assertEquals(listOf("Uno", "Uno"), requested)
    }

    @Test
    fun aRejectionDoesNotStopOtherDownloads() = runTest {
        val store = store()
        failure = IllegalStateException("sin audio")
        runCatching { store.get("Uno") }
        failure = null

        assertTrue(store.get("Dos").length() > 0)
    }

    @Test
    fun aRejectionLeavesNoFileBehind() = runTest {
        val store = store()
        failure = QuotaExhaustedError("Límite por minuto.")

        runCatching { store.get("Uno") }

        assertEquals(emptyList<String>(), folder.root.list()!!.toList())
    }

    @Test
    fun oldAudiosAndHalfWrittenOnesAreDeleted() = runTest {
        val old = File(folder.root, "v1-viejo.wav").apply { writeText("x") }
        val partial = File(folder.root, "${speechCacheFileName("Uno")}$SPEECH_PARTIAL_SUFFIX").apply { writeText("x") }
        val store = store()

        store.get("Dos")

        assertFalse(old.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun aFailedSaveIsAnError() = runTest {
        val blocked = File(folder.root, "ocupado").apply { writeText("no es carpeta") }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = SpeechAudioStore(
            blocked,
            download = { it.toByteArray() },
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            clock = { now },
            io = dispatcher,
        )

        assertTrue(runCatching { store.get("Uno") }.isFailure)
    }
}
