package fr.acinq.lightning.channel

import fr.acinq.bitcoin.BlockHeader
import fr.acinq.bitcoin.Transaction
import fr.acinq.lightning.blockchain.BITCOIN_FUNDING_SPENT
import fr.acinq.lightning.blockchain.WatchEventSpent
import fr.acinq.lightning.blockchain.fee.OnChainFeerates
import fr.acinq.lightning.wire.ChannelReestablish
import fr.acinq.lightning.wire.Error

data class WaitForRemotePublishFutureCommitment(
    override val staticParams: StaticParams,
    override val currentTip: Pair<Int, BlockHeader>,
    override val currentOnChainFeerates: OnChainFeerates,
    override val commitments: Commitments,
    val remoteChannelReestablish: ChannelReestablish
) : ChannelStateWithCommitments() {
    override fun updateCommitments(input: Commitments): ChannelStateWithCommitments = this.copy(commitments = input)

    override fun processInternal(event: ChannelEvent): Pair<ChannelState, List<ChannelAction>> {
        return when {
            event is ChannelEvent.WatchReceived && event.watch is WatchEventSpent && event.watch.event is BITCOIN_FUNDING_SPENT -> handleRemoteSpentFuture(event.watch.tx)
            event is ChannelEvent.Disconnected -> Pair(Offline(this), listOf())
            else -> unhandled(event)
        }
    }

    override fun handleLocalError(event: ChannelEvent, t: Throwable): Pair<ChannelState, List<ChannelAction>> {
        logger.error(t) { "c:${commitments.channelId} error on event ${event::class} in state ${this::class}" }
        val error = Error(channelId, t.message)
        return Pair(Aborted(staticParams, currentTip, currentOnChainFeerates), listOf(ChannelAction.Message.Send(error)))
    }

    internal fun handleRemoteSpentFuture(tx: Transaction): Pair<ChannelState, List<ChannelAction>> {
        logger.warning { "c:${commitments.channelId} they published their future commit (because we asked them to) in txid=${tx.txid}" }
        val remoteCommitPublished = Helpers.Closing.claimRemoteCommitMainOutput(
            keyManager,
            commitments,
            tx,
            currentOnChainFeerates.claimMainFeerate
        )
        val nextState = Closing(
            staticParams = staticParams,
            currentTip = currentTip,
            commitments = commitments,
            currentOnChainFeerates = currentOnChainFeerates,
            fundingTx = null,
            waitingSinceBlock = currentBlockHeight.toLong(),
            futureRemoteCommitPublished = remoteCommitPublished
        )
        val actions = mutableListOf<ChannelAction>(ChannelAction.Storage.StoreState(nextState))
        actions.addAll(remoteCommitPublished.doPublish(channelId, staticParams.nodeParams.minDepthBlocks.toLong()))
        return Pair(nextState, actions)
    }
}