/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.LifecycleCoroutineScope
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.ui.icons.Badge1UpExtralife
import at.bitfire.davdroid.ui.icons.BadgeCoffee
import at.bitfire.davdroid.ui.icons.BadgeCupcake
import at.bitfire.davdroid.ui.icons.BadgeDavx5Decade
import at.bitfire.davdroid.ui.icons.BadgeEnergyBooster
import at.bitfire.davdroid.ui.icons.BadgeLifeBuoy
import at.bitfire.davdroid.ui.icons.BadgeLocalBar
import at.bitfire.davdroid.ui.icons.BadgeMedal
import at.bitfire.davdroid.ui.icons.BadgeNinthAnniversary
import at.bitfire.davdroid.ui.icons.BadgeOfflineBolt
import at.bitfire.davdroid.ui.icons.BadgeSailboat
import at.bitfire.davdroid.ui.icons.BadgesIcons
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.logging.Logger

class PlayClient @AssistedInject constructor(
    @Assisted val activity: Activity,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
    @Assisted val lifecycleScope: LifecycleCoroutineScope,
    val logger: Logger
) : Closeable,
    PurchasesUpdatedListener,
    BillingClientStateListener,
    PurchasesResponseListener
{

    @AssistedFactory
    interface Factory {
        fun create(activity: Activity, lifecycleScope: LifecycleCoroutineScope): PlayClient
    }

    /**
     * The product details; IE title, description, price, etc.
     */
    val productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())

    /**
     * The purchases that have been made.
     */
    val purchases = MutableStateFlow<List<Purchase>>(emptyList())

    /**
     * Short message to display to the user
     */
    val message = MutableStateFlow<String?>(null)

    val purchaseSuccessful = MutableStateFlow(false)

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection() // Less SERVICE_DISCONNECTED responses (still need to handle them)
        .build()
    private var connectionTriesCount: Int = 0

    /**
     * Set up the billing client and connect when the activity is created.
     * Product details and purchases are loaded from play store app cache,
     * but this will give a more responsive user experience, when buying a product.
     */
    init {
        if (!billingClient.isReady) {
            logger.fine("Start connection...")
            billingClient.startConnection(this)
        }
    }

    fun resetMessage() { message.value = null }

    fun resetPurchaseSuccessful() { purchaseSuccessful.value = false }

    /**
     * Query the product details and purchases
     */
    fun queryProductsAndPurchases() {
        // Make sure billing client is available
        if (!billingClient.isReady) {
            logger.warning("BillingClient is not ready")
            message.value = activity.getString(R.string.billing_unavailable)
            return
        }

        // Only request product details if not found already
        if (productDetailsList.value.isEmpty()) {
            logger.fine("No products loaded yet, requesting")
            // Query product details
            queryProductDetails()
        }

        // Query purchases
        // Purchases are stored locally by gplay app
        // Result is received in [onQueryPurchasesResponse]
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build(), this)
    }

    /**
     * Start the purchase flow for a product
     */
    fun purchaseProduct(badge: Badge) {
        // Make sure billing client is available
        if (!billingClient.isReady) {
            logger.warning("BillingClient is not ready")
            message.value = activity.getString(R.string.billing_unavailable)
            return
        }

        // Build and send purchase request
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(badge.productDetails)
                .build()
        )).build()
        val billingResult = billingClient.launchBillingFlow(activity, params)

        // Check purchase was successful
        if (!billingResultOk(billingResult)) {
            logBillingResult("launchBillingFlow", billingResult)
            message.value = activity.getString(R.string.purchase_failed)
        }
    }

    /**
     * Stop the billing client connection (ie. when the activity is destroyed)
     */
    override fun close() {
        logger.fine("Closing connection...")
        billingClient.endConnection()
    }

    /**
     * Continue if connected
     */
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        logBillingResult("onBillingSetupFinished", billingResult)
        if (billingResultOk(billingResult)) {
            logger.fine("Play client ready")
            queryProductsAndPurchases()
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
     */
    private fun queryProductDetails() = lifecycleScope.launch(ioDispatcher) {
        // Build request and query product details
        val productList = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        val productDetailsResult = billingClient.queryProductDetails(params)

        // Handle billing result for request
        val billingResult = productDetailsResult.billingResult
        logBillingResult("onProductDetailsResponse", billingResult)
        if (!billingResultOk(billingResult)) {
            logger.warning("Failed to retrieve product details")
            return@launch
        }

        // Check amount of products received is correct
        val productDetails = productDetailsResult.productDetailsList
        if (productDetails?.size != productIds.size) {
            logger.warning("Missing products. Expected ${productIds.size}, but got ${productDetails?.size} product details from server.")
            return@launch
        }

        // Save product details to be shown on screen
        logger.fine("Got product details!\n$productDetails")
        productDetailsList.emit(productDetails)
    }

    /**
     * Callback from the billing library when [queryPurchasesAsync] is called.
     */
    override fun onQueryPurchasesResponse(billingResult: BillingResult, purchasesList: MutableList<Purchase>) {
        logBillingResult("onQueryPurchasesResponse", billingResult)
        if (billingResultOk(billingResult)) {
            logger.fine("Received purchases list")
            processPurchases(purchasesList)
        }
    }

    /**
     * Called by the Billing Library when new purchases are detected.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        logBillingResult("onPurchasesUpdated", billingResult)
        if (billingResultOk(billingResult) && !purchases.isNullOrEmpty()){
            logger.fine("Received updated purchases list")
            processPurchases(purchases)
        }
    }

    /**
     * Process purchases
     */
    private fun processPurchases(purchasesList: MutableList<Purchase>) {
        // Return early if purchases list has not changed
        if (purchasesList == purchases.value)
            return

        // Handle purchases
        logPurchaseStatus(purchasesList)
        for (purchase in purchasesList) {
            logger.info("Handling purchase with state: ${purchase.purchaseState}")

            // Verify purchase state
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                // purchase pending or in undefined state (ie. refunded or consumed)
                purchasesList.remove(purchase)
                continue
            }

            // Check acknowledgement
            if (!purchase.isAcknowledged) {
                // Don't entitle user to purchase yet remove from purchases list for now
                purchasesList.remove(purchase)

                // Try to acknowledge purchase
                acknowledgePurchase(purchase)
            }
        }
        logAcknowledgementStatus(purchasesList)

        // Update list
        val mergedPurchases = (purchases.value + purchasesList).distinctBy {
            it.purchaseToken
        }
        logger.info("Purchases: $mergedPurchases")
        purchases.value = mergedPurchases
    }

    /**
     * Requests acknowledgement of a purchase
     */
    private fun acknowledgePurchase(purchase: Purchase) = lifecycleScope.launch(ioDispatcher)  {
        logger.info("Acknowledging purchase")
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        val billingResult = billingClient.acknowledgePurchase(params)
        logBillingResult("acknowledgePurchase", billingResult)

        // Check billing result
        if (!billingResultOk(billingResult)) {
            logger.warning("Acknowledging Purchase failed!")

            // Notify user about failure
            message.value = activity.getString(R.string.purchase_acknowledgement_failed)
            return@launch
        }

        // Billing result OK! Acknowledgement successful
        // Now entitle user to purchase (Add to purchases list)
        val purchasesList = purchases.value.toMutableList()
        purchasesList.add(purchase)
        purchases.emit(purchasesList)

        // Notify user about success
        message.value = activity.getString(R.string.purchase_acknowledgement_successful)
        purchaseSuccessful.value = true
    }
    
    /**
     * Checks if the billing result response code is ok. Logs and may set error message if not.
     */
    private fun billingResultOk(result: BillingResult): Boolean {
        when (result.responseCode) {
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.SERVICE_UNAVAILABLE ->
                message.value = activity.getString(R.string.network_problems)
            BillingResponseCode.BILLING_UNAVAILABLE ->
                message.value = activity.getString(R.string.billing_unavailable)
            BillingResponseCode.USER_CANCELED ->
                logger.info("User canceled the purchase")
            BillingResponseCode.ITEM_ALREADY_OWNED ->
                logger.info("The user already owns this item")
            BillingResponseCode.DEVELOPER_ERROR ->
                logger.warning("Google Play does not recognize the application configuration." +
                        "Do the product IDs match and is the APK in use signed with release keys?")
        }
        return result.responseCode == BillingResponseCode.OK
    }

//    /**
//     * DANGER: Use only for testing!
//     * Consumes a purchased item, so it will be available for purchasing again.
//     * Used only for revoking a test purchase.
//     */
//    @Suppress("unused")
//    private fun consumePurchase(purchase: Purchase) {
//        if (BuildConfig.BUILD_TYPE != "debug")
//            return
//
//        logger.info("Trying to consume purchase with token: ${purchase.purchaseToken}")
//        val consumeParams = ConsumeParams.newBuilder()
//            .setPurchaseToken(purchase.purchaseToken)
//            .build()
//        lifecycleScope.launch(ioDispatcher) {
//            val consumeResult = billingClient.consumePurchase(consumeParams)
//            when (consumeResult.billingResult.responseCode) {
//                BillingResponseCode.OK ->
//                    logger.info("Successfully consumed item with purchase token: '${consumeResult.purchaseToken}'")
//                BillingResponseCode.ITEM_NOT_OWNED ->
//                    logger.info("Failed to consume item with purchase token: '${consumeResult.purchaseToken}'. Item not owned")
//                else ->
//                    logger.info("Failed to consume item with purchase token: '${consumeResult.purchaseToken}'. BillingResult: ${consumeResult.billingResult}")
//            }
//        }
//    }


    // logging helpers

    /**
     * Log billing result the same way each time
     */
    private fun logBillingResult(source: String, result: BillingResult) {
        logger.fine("$source: responseCode=${result.responseCode}, message=${result.debugMessage}")
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
        logger.info("Purchases status: purchased=$purchased pending=$pending undefined=$undefined")
    }

    /**
     * Support badge product
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
            "helping_hands.2022" to (BadgesIcons.BadgeLifeBuoy to Color(0xFFFF6A00)),
            "a_coffee_for_you.2022" to (BadgesIcons.BadgeCoffee to Color(0xFF352B1B) ),
            "loyal_foss_backer.2022" to (BadgesIcons.BadgeMedal to Color(0xFFFFC200)),
            "part_of_the_journey.2022" to (BadgesIcons.BadgeSailboat to Color(0xFF083D77)),
            "9th_anniversary.2022" to (BadgesIcons.BadgeNinthAnniversary to Color(0xFFFA8072)),
            "1up_extralife.2023" to (BadgesIcons.Badge1UpExtralife to Color(0xFFD32F2F)),
            "energy_booster.2023" to (BadgesIcons.BadgeEnergyBooster to Color(0xFFFDD835)),
            "davx5_decade" to (BadgesIcons.BadgeDavx5Decade to Color(0xFF43A047)),
            "push_development" to (BadgesIcons.BadgeOfflineBolt to Color(0xFFFDD835)),
            "davx5_cocktail" to (BadgesIcons.BadgeLocalBar to Color(0xFF29CC00)),
            "11th_anniversary" to (BadgesIcons.BadgeCupcake to Color(0xFFF679E5)),
        )
        val productIds = BADGE_ICONS.keys.toList()
    }

}