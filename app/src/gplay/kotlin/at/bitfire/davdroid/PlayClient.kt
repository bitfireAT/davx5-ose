package at.bitfire.davdroid

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryPurchasesAsync
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.logging.Level
import java.util.logging.Logger

class PlayClient @AssistedInject constructor(
    @Assisted val activity: Activity,
    val logger: Logger
) : Closeable,
    PurchasesUpdatedListener,
    BillingClientStateListener,
    ProductDetailsResponseListener,
    PurchasesResponseListener
{

    @AssistedFactory
    interface Factory {
        fun create(activity: Activity): PlayClient
    }

    val context = activity.applicationContext

    /**
     * The product details; IE title, description, price, etc.
     */
    val productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())

    /**
     * The purchases that have been made.
     */
    val purchases = MutableStateFlow<List<Purchase>>(emptyList())

    /**
     * Error message to display to the user
     */
    val errorMessage = MutableStateFlow<String?>(null)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()
    private var connectionTriesCount: Int = 0

    /**
     * Set up the billing client and connect when the activity is created
     * Product details and purchases are loaded from play store app cache, but this will give a more responsive user
     * experience, when buying a product
     */
    init {
        if (!billingClient.isReady) {
            logger.fine("Start connection...")
            billingClient.startConnection(this)
        }
    }

    /**
     * Stop the billing client connection when the activity is destroyed
     */
    override fun close() {
        if (!billingClient.isReady) {
            logger.fine("Closing connection...")
            billingClient.endConnection()
        }
    }

    /**
     * Continue if connected, and handle failure if connecting was unsuccessful
     */
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        logger.warning("onBillingSetupFinished: $responseCode $debugMessage")
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                logger.fine("ready")

                // Purchases are stored locally by gplay app
                queryPurchases()

                // Only request product details if not found already
                if (productDetailsList.value.isEmpty()) {
                    logger.fine("No products loaded yet, requesting")
                    queryProductDetailsAsync()
                }
            }

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
                errorMessage.value = context.getString(R.string.network_problems)
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                errorMessage.value = context.getString(R.string.billing_unavailable)
        }
    }

    /**
     * Retry starting the billingClient a few times
     */
    override fun onBillingServiceDisconnected() {
        connectionTriesCount++
        val maxTries = BILLINGCLIENT_CONNECTION_MAX_RETRIES
        logger.warning("Connecting to BillingService failed. Retrying $connectionTriesCount/$maxTries times")
        if (connectionTriesCount > maxTries) {
            logger.warning("Failed to connect to BillingService. Given up on re-trying")
            return
        }

        // Try to restart the connection on the next request
        billingClient.startConnection(this)
    }

    /**
     * Ask google servers for product details to display (ie. id, price, description, etc)
     * This is an asynchronous call that will receive a result in [onProductDetailsResponse].
     */
    private fun queryProductDetailsAsync() {
        logger.fine("queryProductDetailsAsync")
        val productList = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build() }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        billingClient.queryProductDetailsAsync(params, this)
    }

    /**
     * Receives the result from [queryProductDetailsAsync].
     *
     * Post product details to [productDetailsList]. This allows other parts
     * of the app to show product detail information and make purchases.
     */
    override fun onProductDetailsResponse(billingResult: BillingResult, productDetails: MutableList<ProductDetails>) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            if (productDetails.size == productIds.size) {
                logger.log(Level.FINE, "BillingClient: Got product details!", productDetails)
            } else
                logger.warning("Oh no! Expected ${productIds.size}, but got ${productDetails.size} product details from server.")
            productDetailsList.value = productDetails
        } else
            logger.warning("Failed to query for product details:\n $responseCode $debugMessage")
    }

    fun purchaseProduct(badge: Badge) {
        if (!billingClient.isReady) {
            logger.warning("BillingClient is not ready")
            return
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(badge.productDetails)
                .build()
        )).build()
        val billingResult = billingClient.launchBillingFlow(activity, params)
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        logger.fine("BillingResponse $responseCode $debugMessage")
    }

    /**
     * Query Google Play Billing for existing purchases.
     *
     * New purchases will be provided to the PurchasesUpdatedListener.
     * You still need to check the Google Play Billing API to know when purchase tokens are removed.
     */
    internal fun queryPurchases() {
        if (!billingClient.isReady) {
            logger.warning("BillingClient is not ready")
            return
        }
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build(), this)
    }

    /**
     * Callback from the billing library when [queryPurchasesAsync] is called.
     */
    override fun onQueryPurchasesResponse(billingResult: BillingResult, purchasesList: MutableList<Purchase>) =
        processPurchases(purchasesList)

    /**
     * Called by the Billing Library when new purchases are detected.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        logger.fine("$responseCode $debugMessage")
        when (responseCode) {
            BillingClient.BillingResponseCode.OK ->
                if (!purchases.isNullOrEmpty()) processPurchases(purchases)
            BillingClient.BillingResponseCode.USER_CANCELED ->
                logger.info("User canceled the purchase")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                logger.info("The user already owns this item")
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                logger.warning("Google Play does not recognize the application configuration." +
                    "Do the product IDs match and is the APK in use signed with release keys?")
        }
    }

    /**
     * Process purchases
     */
    private fun processPurchases(purchasesList: MutableList<Purchase>) {
        val initialCount = purchasesList.size
        if (purchasesList == purchases.value) {
            logger.fine("Purchase list has not changed")
            return
        }

        // Handle purchases
        logPurchaseStatus(purchasesList)
        runBlocking {
            for (purchase in purchasesList) {
                logger.info("Handling purchase with state: ${purchase.purchaseState}")
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        logger.info("Acknowledging purchase")
                        acknowledgePurchase(purchase) { ackPurchaseResult ->
                            val responseCode = ackPurchaseResult.responseCode
                            val debugMessage = ackPurchaseResult.debugMessage
                            if (responseCode != BillingClient.BillingResponseCode.OK) {
                                logger.warning("Acknowledging Purchase failed!")
                                logger.warning("AcknowledgePurchaseResult: $responseCode $debugMessage")
                                purchasesList.remove(purchase)
                            }
                        }
                    }
                } else {
                    // purchase pending or in undefined state (ie. refunded)
                    purchasesList.remove(purchase)
                }
//                // DANGER: consumes product! use for revoking a test purchase
//                if (BuildConfig.BUILD_TYPE == "debug")
//                    consumePurchase(purchase) { result ->
//                        when (result.billingResult.responseCode) {
//                            BillingClient.BillingResponseCode.OK ->
//                                logger.info("Successfully consumed item with purchase token: '${result.purchaseToken}'")
//                            BillingClient.BillingResponseCode.ITEM_NOT_OWNED ->
//                                logger.info("Failed to consume item with purchase token: '${result.purchaseToken}'. Item not owned")
//                            else ->
//                                logger.info("Failed to consume item with purchase token: '${result.purchaseToken}'. BillingResult: $result")
//                        }
//                    }
            }
        }

        logAcknowledgementStatus(purchasesList)

        if (purchasesList.size != initialCount)
            throw BillingException("Some purchase could not be acknowledged")

        // Update list
        logger.info("New purchases: $purchasesList")
        purchasesList.addAll(purchases.value)
        purchases.value = purchasesList

    }

//    /**
//     * Consumes a purchased item, so it will be available for purchasing again.
//     * Used for testing - don't remove.
//     */
//    @Suppress("unused")
//    private suspend fun consumePurchase(purchase: Purchase, runAfter: (billingResult: ConsumeResult) -> Unit) {
//        logger.info("Trying to consume purchase with token: ${purchase.purchaseToken}")
//        val consumeParams = ConsumeParams.newBuilder()
//            .setPurchaseToken(purchase.purchaseToken)
//            .build()
//        val response = withContext(Dispatchers.IO) {
//            billingClient.consumePurchase(consumeParams)
//        }
//        runAfter(response)
//    }

    /**
     * Requests acknowledgement of a purchase and lets the passed function handle the request result
     */
    private suspend fun acknowledgePurchase(purchase: Purchase, runAfter: (billingResult: BillingResult) -> Unit) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
        val response = withContext(Dispatchers.IO) {
            billingClient.acknowledgePurchase(acknowledgePurchaseParams.build())
        }
        // Handle acknowledgement response in passed function
        runAfter(response)
    }

    /**
     * Log the number of purchases that are acknowledge and not acknowledged.
     */
    private fun logAcknowledgementStatus(purchasesList: List<Purchase>) {
        var ackYes = 0
        var ackNo = 0
        for (purchase in purchasesList)
            if (purchase.isAcknowledged) ackYes++ else ackNo++
        logger.info("logAcknowledgementStatus: acknowledged=$ackYes unacknowledged=$ackNo")
    }

    /**
     * Log the number of purchases that are acknowledge and not acknowledged.
     */
    private fun logPurchaseStatus(purchasesList: List<Purchase>) {
        var undefined = 0
        var purchased = 0
        var pending = 0
        for (purchase in purchasesList)
            when (purchase.purchaseState) {
                Purchase.PurchaseState.UNSPECIFIED_STATE -> undefined++
                Purchase.PurchaseState.PURCHASED -> purchased++
                Purchase.PurchaseState.PENDING -> pending++
            }
        logger.info("logPurchaseStatus: purchased=$purchased pending=$pending undefined=$undefined")
    }

    fun resetErrors() { errorMessage.value = null }

    /**
     * A Badge type object
     * @param productDetails
     * @param yearBought
     * @param count - amount of badge items of this badge type
     */
    data class Badge(val productDetails: ProductDetails, var yearBought: String?, val count: Int) {
        val name = productDetails.name
        val description = productDetails.description
        val price = productDetails.oneTimePurchaseOfferDetails?.formattedPrice
        val purchased = yearBought != null
    }

    companion object {
        const val BILLINGCLIENT_CONNECTION_MAX_RETRIES = 4

        val BADGE_ICONS = mapOf(
            "helping_hands.2022" to R.drawable.ic_badge_life_buoy,
            "a_coffee_for_you.2022" to R.drawable.ic_badge_coffee,
            "loyal_foss_backer.2022" to R.drawable.ic_badge_medal,
            "part_of_the_journey.2022" to R.drawable.ic_badge_sailboat,
            "9th_anniversary.2022" to R.drawable.ic_badge_ninth_anniversary,
            "1up_extralife.2023" to R.drawable.ic_badge_1up_extralife,
            "energy_booster.2023" to R.drawable.ic_badge_energy_booster,
            "davx5_decade" to R.drawable.ic_badge_davx5_decade,
            "push_development" to R.drawable.ic_badge_offline_bolt,
            "davx5_cocktail" to R.drawable.ic_badge_local_bar,
            "11th_anniversary" to R.drawable.ic_badge_cupcake
        )
        val productIds = BADGE_ICONS.keys.toList()
    }

}

class BillingException(msg: String) : Exception(msg)