package fr.acinq.lightning.message

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.crypto.RouteBlinding
import fr.acinq.lightning.crypto.sphinx.Sphinx
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.wire.MessageOnion
import fr.acinq.lightning.wire.OnionMessage
import fr.acinq.lightning.wire.RelayInfo

class Postman(val privateKey: PrivateKey) {
    fun processOnionMessage(msg: OnionMessage) {
        val blindedPrivateKey = RouteBlinding.derivePrivateKey(privateKey, msg.blindingKey)
        when (val decrypted = Sphinx.peel(blindedPrivateKey, ByteVector.empty, msg.onionRoutingPacket, msg.onionRoutingPacket.payload.size())) {
            is Either.Right -> try {
                val message = MessageOnion.read(decrypted.value.payload.toByteArray())
                val (decryptedPayload, nextBlinding) = RouteBlinding.decryptPayload(privateKey, msg.blindingKey, message.encryptedData)
                val relayInfo = RelayInfo.read(decryptedPayload.toByteArray())
                if (decrypted.value.isLastPacket) {
                    relayInfo.pathId // TODO
                } else if (!decrypted.value.isLastPacket && relayInfo.nextnodeId == privateKey.publicKey()) {
                    // We may add ourselves to the route several times at the end to hide the real length of the route.
                    processOnionMessage(OnionMessage(relayInfo.nextBlindingOverride ?: nextBlinding, decrypted.value.nextPacket))
                }
            } catch (_: Throwable) {
                // Ignore bad messages
            }
        }
    }
}