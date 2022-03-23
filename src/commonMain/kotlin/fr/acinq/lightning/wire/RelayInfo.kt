package fr.acinq.lightning.wire

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.io.ByteArrayInput
import fr.acinq.bitcoin.io.ByteArrayOutput
import fr.acinq.bitcoin.io.Input
import fr.acinq.bitcoin.io.Output
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@OptIn(ExperimentalUnsignedTypes::class)
@Serializable
sealed class RelayInfoTlv : Tlv {
    /** Id of the next node. */
    @Serializable
    data class OutgoingNodeId(@Contextual val nodeId: PublicKey) : RelayInfoTlv() {
        override val tag: Long get() = OutgoingNodeId.tag
        override fun write(out: Output) = LightningCodecs.writeBytes(nodeId.value, out)

        companion object : TlvValueReader<OutgoingNodeId> {
            const val tag: Long = 4
            override fun read(input: Input): OutgoingNodeId =
                OutgoingNodeId(PublicKey(LightningCodecs.bytes(input, 33)))
        }
    }

    /**
     * The final recipient may store some data in the encrypted payload for itself to avoid storing it locally.
     * It can for example put a payment_hash to verify that the route is used for the correct invoice.
     * It should use that field to detect when blinded routes are used outside of their intended use (malicious probing)
     * and react accordingly (ignore the message or send an error depending on the use-case).
     */
    @Serializable
    data class PathId(@Contextual val data: ByteVector) : RelayInfoTlv() {
        override val tag: Long get() = PathId.tag
        override fun write(out: Output) = LightningCodecs.writeBytes(data, out)

        companion object : TlvValueReader<PathId> {
            const val tag: Long = 6
            override fun read(input: Input): PathId =
                PathId(ByteVector(LightningCodecs.bytes(input, input.availableBytes)))
        }
    }

    /** Blinding override for the rest of the route. */
    @Serializable
    data class NextBlinding(@Contextual val blinding: PublicKey) : RelayInfoTlv() {
        override val tag: Long get() = NextBlinding.tag
        override fun write(out: Output) = LightningCodecs.writeBytes(blinding.value, out)

        companion object : TlvValueReader<NextBlinding> {
            const val tag: Long = 8
            override fun read(input: Input): NextBlinding =
                NextBlinding(PublicKey(LightningCodecs.bytes(input, 33)))
        }
    }
}

data class RelayInfo(val records: TlvStream<RelayInfoTlv>) {
    val nextnodeId = records.get<RelayInfoTlv.OutgoingNodeId>()?.nodeId
    val pathId = records.get<RelayInfoTlv.PathId>()?.data
    val nextBlindingOverride = records.get<RelayInfoTlv.NextBlinding>()?.blinding

    fun write(out: Output) = tlvSerializer.write(records, out)

    fun write(): ByteArray {
        val out = ByteArrayOutput()
        write(out)
        return out.toByteArray()
    }

    companion object {
        val tlvSerializer = TlvStreamSerializer(
            true, @Suppress("UNCHECKED_CAST") mapOf(
                RelayInfoTlv.OutgoingNodeId.tag to RelayInfoTlv.OutgoingNodeId.Companion as TlvValueReader<RelayInfoTlv>,
                RelayInfoTlv.PathId.tag to RelayInfoTlv.PathId.Companion as TlvValueReader<RelayInfoTlv>,
                RelayInfoTlv.NextBlinding.tag to RelayInfoTlv.NextBlinding.Companion as TlvValueReader<RelayInfoTlv>,
            )
        )

        fun read(input: Input): RelayInfo = RelayInfo(tlvSerializer.read(input))

        fun read(bytes: ByteArray): RelayInfo = read(ByteArrayInput(bytes))
    }
}