package fr.acinq.eclair.db

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.CltvExpiry
import fr.acinq.eclair.channel.ChannelStateWithCommitments

interface ChannelsDb {
    suspend fun addOrUpdateChannel(state: ChannelStateWithCommitments)

    suspend fun removeChannel(channelId: ByteVector32)

    suspend fun listLocalChannels(): List<ChannelStateWithCommitments>

    suspend fun addHtlcInfo(channelId: ByteVector32, commitmentNumber: Long, paymentHash: ByteVector32, cltvExpiry: CltvExpiry)

    suspend fun listHtlcInfos(channelId: ByteVector32, commitmentNumber: Long): List<Pair<ByteVector32, CltvExpiry>>

    fun close()
}