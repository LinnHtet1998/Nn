package com.example.core.payment

import com.example.domain.model.Company
import com.example.domain.model.PaymentMethodConfig
import com.example.domain.model.PaymentMethodProviderType
import com.example.domain.model.SubscriptionPlan

/**
 * Clean abstraction layer enabling configurable manual transfer slips today
 * and automated payment gateway webhooks (KBZPay Direct / WavePay / 2C2P / Stripe)
 * without refactoring billing workflows.
 */
data class PaymentCheckoutSession(
    val sessionId: String,
    val providerType: PaymentMethodProviderType,
    val amountMmk: Long,
    val requiresManualProof: Boolean,
    val checkoutUrl: String? = null,
)

interface PaymentGatewayAdapter {
    val providerType: PaymentMethodProviderType

    suspend fun createCheckoutSession(
        company: Company,
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
    ): PaymentCheckoutSession
}

class ManualSlipPaymentAdapter : PaymentGatewayAdapter {
    override val providerType: PaymentMethodProviderType = PaymentMethodProviderType.MANUAL_TRANSFER

    override suspend fun createCheckoutSession(
        company: Company,
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
    ): PaymentCheckoutSession = PaymentCheckoutSession(
        sessionId = "manual_${company.id}_${plan.id}",
        providerType = PaymentMethodProviderType.MANUAL_TRANSFER,
        amountMmk = plan.effectivePriceMmk(),
        requiresManualProof = true,
        checkoutUrl = null,
    )
}

class GatewayWebhookAdapter : PaymentGatewayAdapter {
    override val providerType: PaymentMethodProviderType = PaymentMethodProviderType.GATEWAY_WEBHOOK

    override suspend fun createCheckoutSession(
        company: Company,
        plan: SubscriptionPlan,
        method: PaymentMethodConfig,
    ): PaymentCheckoutSession {
        // Future gateway token / deep-link session generated via Cloud Function endpoint.
        return PaymentCheckoutSession(
            sessionId = "gw_${company.id}_${plan.id}",
            providerType = PaymentMethodProviderType.GATEWAY_WEBHOOK,
            amountMmk = plan.effectivePriceMmk(),
            requiresManualProof = true,
            checkoutUrl = method.gatewayEndpoint,
        )
    }
}

object PaymentGatewayRegistry {
    private val adapters: Map<PaymentMethodProviderType, PaymentGatewayAdapter> = listOf(
        ManualSlipPaymentAdapter(),
        GatewayWebhookAdapter(),
    ).associateBy { it.providerType }

    fun adapterFor(method: PaymentMethodConfig): PaymentGatewayAdapter =
        adapters[PaymentMethodProviderType.fromKey(method.providerType)]
            ?: ManualSlipPaymentAdapter()
}
