package com.zvpn.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.zvpn.domain.billing.BillingConfig
import com.zvpn.domain.billing.UserTier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val TAG = "BillingManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _userTier = MutableStateFlow(UserTier.Free)
    val userTier: StateFlow<UserTier> = _userTier.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    // Rewarded access expiry
    private val _rewardedUntil = MutableStateFlow(0L)
    val rewardedUntil: StateFlow<Long> = _rewardedUntil.asStateFlow()

    private lateinit var billingClient: BillingClient

    fun init() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { queryProducts(); queryActivePurchases() }
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected")
            }
        })
    }

    private suspend fun queryProducts() {
        val productIds = listOf(
            BillingConfig.PRODUCT_PREMIUM_WEEKLY,
            BillingConfig.PRODUCT_PREMIUM_MONTHLY,
            BillingConfig.PRODUCT_PREMIUM_YEARLY,
        ).map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build() }

        val params = QueryProductDetailsParams.newBuilder().setProductList(productIds).build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _products.value = result.productDetailsList ?: emptyList()
        }
    }

    private suspend fun queryActivePurchases() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        val activePurchase = result.purchasesList.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (activePurchase != null) {
            val productId = activePurchase.products.firstOrNull() ?: ""
            _userTier.value = when {
                productId == BillingConfig.PRODUCT_PREMIUM_YEARLY && productId.contains("plus") -> UserTier.PremiumPlus
                else -> UserTier.Premium
            }
            // Acknowledge if not already
            if (!activePurchase.isAcknowledged) {
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(activePurchase.purchaseToken).build()
                ) {}
            }
        }
    }

    fun launchBilling(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build())
            ).build()
        _isPurchasing.value = true
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _isPurchasing.value = false
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch {
                purchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        _userTier.value = UserTier.Premium
                        if (!purchase.isAcknowledged) {
                            billingClient.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                            ) {}
                        }
                    }
                }
            }
        }
    }

    /** Grant rewarded access for N minutes after verified ad view */
    fun grantAdReward(minutes: Int = BillingConfig.AD_REWARD_MINUTES) {
        _rewardedUntil.value = System.currentTimeMillis() + minutes * 60_000L
        if (_userTier.value == UserTier.Free) {
            _userTier.value = UserTier.AdRewarded
        }
    }

    /** Called every minute to check if rewarded access expired */
    fun checkRewardExpiry() {
        if (_userTier.value == UserTier.AdRewarded && System.currentTimeMillis() > _rewardedUntil.value) {
            _userTier.value = UserTier.Free
        }
    }

    fun destroy() {
        scope.cancel()
        if (::billingClient.isInitialized && billingClient.isReady) billingClient.endConnection()
    }
}
