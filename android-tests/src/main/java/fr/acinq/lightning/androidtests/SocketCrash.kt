package fr.acinq.lightning.androidtests

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class SocketCrash {
    companion object {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }
        }

        /**
         * Opens a ssl connection, wait a bit, close it and THEN send a buffer
         * @param exceptionHandler optional exception handler
         * @param buffer data to send
         * @return true if this method completed without errors, false if an exception was caught
         */
        suspend fun connectAndSend(exceptionHandler: CoroutineExceptionHandler?, buffer: ByteArray) : Boolean {
            val ctx = exceptionHandler?.let { Dispatchers.IO + it } ?: Dispatchers.IO
            val socket = aSocket(ActorSelectorManager(Dispatchers.IO))
                .tcp()
                .connect("www.google.com", 443)
                .tls(coroutineContext =  ctx, trustManager = trustManager, randomAlgorithm = "SHA1PRNG")

            val output = socket.openWriteChannel(autoFlush = true)

            delay(100)

            socket.dispose()

            try {
                output.writeFully(buffer)
                output.flush()
                Log.i(null, "wrote ${buffer.size} bytes")
            } catch (t: Throwable) {
                Log.e(null, "try/catch caught $t")
                return false
            }
            return true
        }
    }
}