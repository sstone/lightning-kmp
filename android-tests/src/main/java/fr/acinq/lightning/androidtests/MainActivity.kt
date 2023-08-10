package fr.acinq.lightning.androidtests

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import kotlinx.coroutines.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class MainActivity : AppCompatActivity() {
    val loggerFactory = LoggerFactory.default
    val logger = loggerFactory.newLogger(MainActivity::class)

    class MyTcpBuilder(val builder: TcpSocket.Builder) : TcpSocket.Builder {
        var socket: TcpSocket? = null
        override suspend fun connect(host: String, port: Int, tls: TcpSocket.TLS, loggerFactory: LoggerFactory): TcpSocket {
            socket = builder.connect(host, port, tls, loggerFactory)
            return socket!!
        }
    }

    val myTcpBuilder = MyTcpBuilder(TcpSocket.Builder())
    val client = ElectrumClient(myTcpBuilder, MainScope(), loggerFactory)
    val exceptionHandler = CoroutineExceptionHandler { _, e -> logger.error(e) { "exception handler caught error" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_socket).setOnClickListener {
            MainScope().launch((Dispatchers.IO)) {
                SocketCrash.connectAndSend(exceptionHandler, ByteArray(10 * 1024))
            }
        }

        findViewById<Button>(R.id.btn_electrum_connect).setOnClickListener {
            client.connect(ServerAddress("electrum.acinq.co", 50002, TcpSocket.TLS.UNSAFE_CERTIFICATES))
        }

        findViewById<Button>(R.id.btn_electrum_test).setOnClickListener {
            val uiCtx = this
            MainScope().launch(Dispatchers.IO) {
                val tip = client.startHeaderSubscription().header
                withContext(Dispatchers.Main) {
                    Toast.makeText(uiCtx, "tip is $tip", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btn_electrum_kill).setOnClickListener {
            myTcpBuilder.socket?.close()
        }
    }
}