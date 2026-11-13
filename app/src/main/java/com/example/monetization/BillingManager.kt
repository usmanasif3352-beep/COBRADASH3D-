package com.example.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BillingUiState {
    data object Disconnected : BillingUiState
    data object Ready : BillingUiState
    data class PurchasePendingVerification(val productId: String, val purchaseToken: String) : BillingUiState
    data class Message(val text: String) : BillingUiState
}

class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val processedTokens = mutableSetOf<String>()

    private val _billingState = MutableStateFlow<BillingUiState>(BillingUiState.Disconnected)
    val billingState: StateFlow<BillingUiState> = _billingState.asStateFlow()

    private val _catalogDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val catalogDetails: StateFlow<Map<String, ProductDetails>> = _catalogDetails.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            queryProductsAndRestore()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingState.value = BillingUiState.Ready
                    queryProductsAndRestore()
                } else {
                    _billingState.value = BillingUiState.Message(
                        "Google Play Billing unavailable (${result.debugMessage.ifBlank { "code ${result.responseCode}" }})"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingState.value = BillingUiState.Disconnected
            }
        })
    }

    fun queryProductsAndRestore() {
        if (!billingClient.isReady) return
        val inAppIds = listOf(
            "cobradash_no_ads",
            "cobradash_battle_pass_s1",
            "cobradash_coins_500",
            "cobradash_gems_120",
            "cobradash_bundle_dragon"
        )
        val products = inAppIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _catalogDetails.value = productDetailsList.associateBy { it.productId }
            }
        }

        val restoreParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(restoreParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        if (!billingClient.isReady) {
            _billingState.value = BillingUiState.Message(
                "Play Store Billing is not connected on this device/emulator. Configure products in Play Console to test real purchases."
            )
            return
        }
        val details = _catalogDetails.value[productId]
        if (details == null) {
            _billingState.value = BillingUiState.Message(
                "Product '$productId' is not configured in Google Play Console yet."
            )
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.value = BillingUiState.Message("Purchase cancelled.")
            }
            else -> {
                _billingState.value = BillingUiState.Message(
                    "Billing error (${billingResult.responseCode}): ${billingResult.debugMessage}"
                )
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!processedTokens.add(purchase.purchaseToken)) return

        val productId = purchase.products.firstOrNull().orEmpty()
        // Never trust client-only purchase results for currency/entitlements;
        // acknowledge on Play and submit token to backend server verification endpoint.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams) {
                _billingState.value = BillingUiState.PurchasePendingVerification(
                    productId = productId,
                    purchaseToken = purchase.purchaseToken
                )
            }
        } else {
            _billingState.value = BillingUiState.PurchasePendingVerification(
                productId = productId,
                purchaseToken = purchase.purchaseToken
            )
        }
    }

    fun clearMessage() {
        if (_billingState.value is BillingUiState.Message) {
            _billingState.value = if (billingClient.isReady) BillingUiState.Ready else BillingUiState.Disconnected
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
