package fr.acinq.lightning.payment

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.*
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.utils.currentTimestampSeconds
import kotlin.test.Test
import fr.acinq.lightning.payment.legacy.PaymentRequest as LegacyPaymentRequest
import fr.acinq.lightning.payment.legacy.Feature as LegacyFeature
import fr.acinq.lightning.payment.legacy.Features as LegacyFeatures
import fr.acinq.lightning.payment.legacy.FeatureSupport as LegacyFeatureSupport

class PaymentRequestMigrationTestsCommon {
    val privateKey = PrivateKey.fromHex("0101010101010101010101010101010101010101010101010101010101010101")
    @Test
    fun `migrate old payment requests`() {
        val legacyPr = LegacyPaymentRequest.create(
            Block.RegtestGenesisBlock.hash.value,
            null, //MilliSatoshi(5000),
            randomBytes32(),
            privateKey,
            "test",
            CltvExpiryDelta(42),
            LegacyFeatures(LegacyFeature.VariableLengthOnion to LegacyFeatureSupport.Optional, LegacyFeature.PaymentSecret to LegacyFeatureSupport.Optional),
            randomBytes32(),
            0,
            listOf(),
            currentTimestampSeconds()
        )

        val encoded = legacyPr.write()
        val decoded = PaymentRequest.read(encoded).get()
        val reencoded = decoded.write()
        val redecoded = PaymentRequest.read(reencoded).get()
        println(redecoded)
    }
}