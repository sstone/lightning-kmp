package fr.acinq.lightning.androidtests

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class SocketInstrumentedTest {
    val exceptionHandler = CoroutineExceptionHandler { _, e -> Log.e(null, "exception handler caught $e") }

    @Test
    fun exceptionHandleAndSmallBuffer() {
        runTest {
            // exception handler + small buffer, no exception caught in connectAndSend()
            val job = MainScope().launch(Dispatchers.IO) {
                val result = SocketCrash.connectAndSend(exceptionHandler, ByteArray(10))
                assertTrue(result)
            }
            job.join()
        }
    }
    @Test
    fun exceptionHandleAndLargeBuffer() {
        runTest {
            // exception handler + large buffer, exception caught in connectAndSend()
            val job = MainScope().launch(Dispatchers.IO) {
                val result = SocketCrash.connectAndSend(exceptionHandler, ByteArray(10 * 1024))
                assertFalse(result)
            }
            job.join()
        }
    }

    @Test
    fun noExceptionHandleAndSmallBuffer() {
        runTest {
            // no exception handler !!, small buffer, exception caught in connectAndSend()
            val job = launch(Dispatchers.IO) {
                val result = SocketCrash.connectAndSend(null, ByteArray(10))
                println(result)
                assertTrue(result)
            }
            job.join()
        }
    }

    @Test
    fun noExceptionHandleAndLargeBuffer() {
        runTest {
            // no exception handler !!, small buffer, exception caught in connectAndSend()
            val job = launch(Dispatchers.IO) {
                val result = SocketCrash.connectAndSend(null, ByteArray(10 * 1024))
                println(result)
                assertTrue(result)
            }
            job.join()
        }
    }
}