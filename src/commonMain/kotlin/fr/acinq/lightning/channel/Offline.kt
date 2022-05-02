package fr.acinq.lightning.channel

import fr.acinq.bitcoin.BlockHeader
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.Feature
import fr.acinq.lightning.blockchain.*
import fr.acinq.lightning.blockchain.fee.OnChainFeerates
import fr.acinq.lightning.wire.ChannelReestablish
import fr.acinq.lightning.wire.Error

data class Offline(val state: ChannelStateWithCommitments) : ChannelState() {
    override val staticParams: StaticParams get() = state.staticParams
    override val currentTip: Pair<Int, BlockHeader> get() = state.currentTip
    override val currentOnChainFeerates: OnChainFeerates get() = state.currentOnChainFeerates

    override fun processInternal(event: ChannelEvent): Pair<ChannelState, List<ChannelAction>> {
        logger.warning { "c:${state.channelId} offline processing ${event::class}" }
        return when {
            event is ChannelEvent.Connected -> {
                when {
                    state is WaitForRemotePublishFutureCommitment -> {
                        // they already proved that we have an outdated commitment
                        // there isn't much to do except asking them again to publish their current commitment by sending an error
                        val exc = PleasePublishYourCommitment(state.channelId)
                        val error = Error(state.channelId, exc.message)
                        val nextState = state.updateCommitments(state.commitments.updateFeatures(event.localInit, event.remoteInit))
                        Pair(nextState, listOf(ChannelAction.Message.Send(error)))
                    }
                    staticParams.nodeParams.features.hasFeature(Feature.ChannelBackupClient) -> {
                        // We wait for them to go first, which lets us restore from the latest backup if we've lost data.
                        logger.info { "c:${state.channelId} syncing ${state::class}, waiting fo their channelReestablish message" }
                        val nextState = state.updateCommitments(state.commitments.updateFeatures(event.localInit, event.remoteInit))
                        Pair(Syncing(nextState, true), listOf())
                    }
                    else -> {
                        val yourLastPerCommitmentSecret = state.commitments.remotePerCommitmentSecrets.lastIndex?.let { state.commitments.remotePerCommitmentSecrets.getHash(it) } ?: ByteVector32.Zeroes
                        val myCurrentPerCommitmentPoint = keyManager.commitmentPoint(state.commitments.localParams.channelKeys.shaSeed, state.commitments.localCommit.index)

                        val channelReestablish = ChannelReestablish(
                            channelId = state.channelId,
                            nextLocalCommitmentNumber = state.commitments.localCommit.index + 1,
                            nextRemoteRevocationNumber = state.commitments.remoteCommit.index,
                            yourLastCommitmentSecret = PrivateKey(yourLastPerCommitmentSecret),
                            myCurrentPerCommitmentPoint = myCurrentPerCommitmentPoint
                        ).withChannelData(state.commitments.remoteChannelData)
                        logger.info { "c:${state.channelId} syncing ${state::class}" }
                        val nextState = state.updateCommitments(state.commitments.updateFeatures(event.localInit, event.remoteInit))
                        Pair(Syncing(nextState, false), listOf(ChannelAction.Message.Send(channelReestablish)))
                    }
                }
            }
            event is ChannelEvent.WatchReceived -> {
                val watch = event.watch
                when {
                    watch is WatchEventSpent -> when {
                        state is Negotiating && state.closingTxProposed.flatten().any { it.unsignedTx.tx.txid == watch.tx.txid } -> {
                            logger.info { "c:${state.channelId} closing tx published: closingTxId=${watch.tx.txid}" }
                            val closingTx = state.getMutualClosePublished(watch.tx)
                            val nextState = Closing(
                                staticParams,
                                currentTip,
                                currentOnChainFeerates,
                                state.commitments,
                                fundingTx = null,
                                waitingSinceBlock = currentBlockHeight.toLong(),
                                mutualCloseProposed = state.closingTxProposed.flatten().map { it.unsignedTx },
                                mutualClosePublished = listOf(closingTx)
                            )
                            val actions = listOf(
                                ChannelAction.Storage.StoreState(nextState),
                                ChannelAction.Blockchain.PublishTx(watch.tx),
                                ChannelAction.Blockchain.SendWatch(WatchConfirmed(state.channelId, watch.tx, staticParams.nodeParams.minDepthBlocks.toLong(), BITCOIN_TX_CONFIRMED(watch.tx)))
                            )
                            Pair(nextState, actions)
                        }
                        watch.tx.txid == state.commitments.remoteCommit.txid -> state.handleRemoteSpentCurrent(watch.tx)
                        watch.tx.txid == state.commitments.remoteNextCommitInfo.left?.nextRemoteCommit?.txid -> state.handleRemoteSpentNext(watch.tx)
                        state is WaitForRemotePublishFutureCommitment -> state.handleRemoteSpentFuture(watch.tx)
                        else -> state.handleRemoteSpentOther(watch.tx)
                    }
                    watch is WatchEventConfirmed && (watch.event is BITCOIN_FUNDING_DEPTHOK || watch.event is BITCOIN_FUNDING_DEEPLYBURIED) -> {
                        // just ignore this, we will put a new watch when we reconnect, and we'll be notified again
                        Pair(this, listOf())
                    }
                    else -> unhandled(event)
                }
            }
            event is ChannelEvent.GetFundingTxResponse && state is WaitForFundingConfirmed && event.getTxResponse.txid == state.commitments.commitInput.outPoint.txid -> handleGetFundingTx(
                event.getTxResponse,
                state.waitingSinceBlock,
                state.fundingTx
            )
            event is ChannelEvent.CheckHtlcTimeout -> {
                val (newState, actions) = state.checkHtlcTimeout()
                when (newState) {
                    is Closing -> Pair(newState, actions)
                    is Closed -> Pair(newState, actions)
                    else -> Pair(Offline(newState as ChannelStateWithCommitments), actions)
                }
            }
            event is ChannelEvent.NewBlock -> {
                val (newState, actions) = state.process(event)
                when (newState) {
                    is Closing -> Pair(newState, actions)
                    is Closed -> Pair(newState, actions)
                    else -> Pair(Offline(newState as ChannelStateWithCommitments), actions)
                }
            }
            event is ChannelEvent.ExecuteCommand && event.command is CMD_FORCECLOSE -> {
                val (newState, actions) = state.process(event)
                when (newState) {
                    // NB: it doesn't make sense to try to send outgoing messages if we're offline.
                    is Closing -> Pair(newState, actions.filterNot { it is ChannelAction.Message.Send })
                    is Closed -> Pair(newState, actions.filterNot { it is ChannelAction.Message.Send })
                    else -> Pair(Offline(newState as ChannelStateWithCommitments), actions)
                }
            }
            else -> unhandled(event)
        }
    }

    override fun handleLocalError(event: ChannelEvent, t: Throwable): Pair<ChannelState, List<ChannelAction>> {
        logger.error(t) { "c:${state.channelId} error on event ${event::class} in state ${this::class}" }
        return Pair(this, listOf(ChannelAction.ProcessLocalError(t, event)))
    }
}
