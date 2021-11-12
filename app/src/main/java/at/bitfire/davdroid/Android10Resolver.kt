package at.bitfire.davdroid

import android.net.DnsResolver
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.xbill.DNS.Message
import org.xbill.DNS.Resolver
import org.xbill.DNS.ResolverListener
import org.xbill.DNS.TSIG
import java.util.*
import java.util.concurrent.CompletableFuture

@RequiresApi(Build.VERSION_CODES.Q)
class Android10Resolver: Resolver {

    private val executor = Dispatchers.IO.asExecutor()
    private val resolver = DnsResolver.getInstance()


    override fun send(query: Message): Message {
        val future = CompletableFuture<Message>()

        resolver.rawQuery(null, query.toWire(), DnsResolver.FLAG_EMPTY, executor, null, object: DnsResolver.Callback<ByteArray> {
            override fun onAnswer(rawAnswer: ByteArray, rcode: Int) {
                future.complete(Message((rawAnswer)))
            }

            override fun onError(error: DnsResolver.DnsException) {
                future.completeExceptionally(error)
            }
        })

        return future.get()
    }

    override fun sendAsync(query: Message, listener: ResolverListener): Any {
        val id = UUID.randomUUID()

        resolver.rawQuery(null, query.toWire(), DnsResolver.FLAG_EMPTY, executor, null, object: DnsResolver.Callback<ByteArray> {
            override fun onAnswer(rawAnswer: ByteArray, rcode: Int) {
                listener.receiveMessage(id, Message(rawAnswer))
            }

            override fun onError(error: DnsResolver.DnsException) {
                listener.handleException(id, error)
            }
        })

        return id
    }


    override fun setPort(port: Int) {
        // not applicable
    }

    override fun setTCP(flag: Boolean) {
        // not applicable
    }

    override fun setIgnoreTruncation(flag: Boolean) {
        // not applicable
    }

    override fun setEDNS(level: Int) {
        // not applicable
    }

    override fun setEDNS(level: Int, payloadSize: Int, flags: Int, options: MutableList<Any?>?) {
        // not applicable
    }

    override fun setTSIGKey(key: TSIG?) {
        // not applicable
    }

    override fun setTimeout(secs: Int, msecs: Int) {
        // not applicable
    }

    override fun setTimeout(secs: Int) {
        // not applicable
    }

}