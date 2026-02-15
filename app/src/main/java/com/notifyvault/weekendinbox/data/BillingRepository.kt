package com.notifyvault.weekendinbox.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PRO_PRODUCT_ID = "notifyvault_pro"

class BillingRepository(
    context: Context,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _state = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = _state

    fun connect() {
        if (billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshProducts()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(message = "Billing disconnected")
            }
        })
    }

    fun refreshProducts() {
        if (!billingClient.isReady) return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRO_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        billingClient.queryProductDetailsAsync(params) { _, products ->
            _state.value = _state.value.copy(productDetails = products.firstOrNull())
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = _state.value.productDetails ?: return
        if (details.oneTimePurchaseOfferDetails == null) return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    fun restorePurchases() {
        refreshPurchases()
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            processPurchases(purchases)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            processPurchases(purchases ?: emptyList())
        } else {
            _state.value = _state.value.copy(message = result.debugMessage)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        scope.launch(Dispatchers.IO) {
            val pro = purchases.any { it.products.contains(PRO_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            prefs.setPro(pro)
            purchases.filter { !it.isAcknowledged && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { purchase ->
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    ) {}
                }
            _state.value = _state.value.copy(isPro = pro)
        }
    }
}

data class BillingState(
    val isPro: Boolean = false,
    val productDetails: ProductDetails? = null,
    val message: String? = null
)
