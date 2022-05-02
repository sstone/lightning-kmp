package fr.acinq.lightning.channel

import fr.acinq.bitcoin.*
import fr.acinq.lightning.*
import fr.acinq.lightning.blockchain.*
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.blockchain.fee.OnChainFeerates
import fr.acinq.lightning.channel.Channel.ANNOUNCEMENTS_MINCONF
import fr.acinq.lightning.channel.Channel.FUNDING_TIMEOUT_FUNDEE_BLOCK
import fr.acinq.lightning.channel.Channel.MAX_NEGOTIATION_ITERATIONS
import fr.acinq.lightning.channel.Channel.handleSync
import fr.acinq.lightning.channel.Helpers.Closing.extractPreimages
import fr.acinq.lightning.channel.Helpers.Closing.onChainOutgoingHtlcs
import fr.acinq.lightning.channel.Helpers.Closing.overriddenOutgoingHtlcs
import fr.acinq.lightning.channel.Helpers.Closing.timedOutHtlcs
import fr.acinq.lightning.crypto.KeyManager
import fr.acinq.lightning.crypto.ShaChain
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.router.Announcements
import fr.acinq.lightning.serialization.Serialization
import fr.acinq.lightning.transactions.CommitmentSpec
import fr.acinq.lightning.transactions.Scripts
import fr.acinq.lightning.transactions.Transactions
import fr.acinq.lightning.transactions.Transactions.TransactionWithInputInfo.ClosingTx
import fr.acinq.lightning.transactions.outgoings
import fr.acinq.lightning.utils.*
import fr.acinq.lightning.wire.*
import org.kodein.log.Logger

/**
 * Channel is closed i.t its funding tx has been spent and the spending transactions have been confirmed, it can be forgotten
 */
data class Closed(val state: Closing) : ChannelStateWithCommitments() {
    override val staticParams: StaticParams get() = state.staticParams
    override val currentTip: Pair<Int, BlockHeader> get() = state.currentTip
    override val currentOnChainFeerates: OnChainFeerates get() = state.currentOnChainFeerates
    override val commitments: Commitments get() = state.commitments

    override fun updateCommitments(input: Commitments): ChannelStateWithCommitments {
        return this.copy(state = state.updateCommitments(input) as Closing)
    }

    override fun processInternal(event: ChannelEvent): Pair<ChannelState, List<ChannelAction>> {
        return Pair(this, listOf())
    }

    override fun handleLocalError(event: ChannelEvent, t: Throwable): Pair<ChannelState, List<ChannelAction>> {
        logger.error(t) { "c:$channelId error on event ${event::class} in state ${this::class}" }
        return Pair(this, listOf())
    }
}
