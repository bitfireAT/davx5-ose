/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityEarnBadgesBinding
import at.bitfire.davdroid.databinding.BoughtBadgeItemBinding
import at.bitfire.davdroid.databinding.BuyBadgeItemBinding
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.SettingsManager
import com.android.billingclient.api.*
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class EarnBadgesActivity : AppCompatActivity(), LifecycleOwner {

    companion object {
        internal const val LAST_REVIEW_PROMPT = "lastReviewPrompt"

        /** Time between rating interval prompts in milliseconds */
        private const val RATING_INTERVAL = 2*7*24*60*60*1000 // Two weeks

        private const val HELPING_HANDS = "helping_hands.2022"
        private const val A_COFFEE_FOR_YOU = "a_coffee_for_you.2022"
        private const val LOYAL_FOSS_BACKER = "loyal_foss_backer.2022"
        private const val PART_OF_THE_JOURNEY = "part_of_the_journey.2022"
        private const val NINTH_ANNIVERSARY = "9th_anniversary.2022"

        private val BADGES = mapOf(
            HELPING_HANDS to R.drawable.ic_badge_life_buoy,
            A_COFFEE_FOR_YOU to R.drawable.ic_badge_coffee,
            LOYAL_FOSS_BACKER to R.drawable.ic_badge_medal,
            PART_OF_THE_JOURNEY to R.drawable.ic_badge_sailboat,
            NINTH_ANNIVERSARY to R.drawable.ic_badge_ninth_anniversary
        )
        private val BADGES_ANIMATIONS = mapOf(
            HELPING_HANDS to R.anim.spin,
            A_COFFEE_FOR_YOU to R.anim.lift,
            LOYAL_FOSS_BACKER to R.anim.pulsate,
            PART_OF_THE_JOURNEY to R.anim.rock,
            NINTH_ANNIVERSARY to R.anim.drop_in
        )
        val PRODUCT_IDS = BADGES.keys.toList()

        /**
         * Determines whether we should show a rating prompt to the user depending on whether
         * - the RATING_INTERVAL has passed once after first installation, or
         * - the last rating prompt is older than RATING_INTERVAL
         *
         * If the return value is `true`, also updates the `LAST_REVIEW_PROMPT` setting to the current time
         * so that the next call won't be `true` again for the time specified in `RATING_INTERVAL`.
         */
        fun shouldShowRatingRequest(context: Context, settings: SettingsManager): Boolean {
            val now = currentTime()
            val firstInstall = installTime(context)
            val lastPrompt = settings.getLongOrNull(LAST_REVIEW_PROMPT) ?: now
            val shouldShowRatingRequest = (now > firstInstall + RATING_INTERVAL) && (now > lastPrompt + RATING_INTERVAL)
            Logger.log.info("now=$now, firstInstall=$firstInstall, lastPrompt=$lastPrompt, shouldShowRatingRequest=$shouldShowRatingRequest")
            if (shouldShowRatingRequest)
                settings.putLong(LAST_REVIEW_PROMPT, now)
            return shouldShowRatingRequest
        }

        fun currentTime() = System.currentTimeMillis()
        fun installTime(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }

    @Inject lateinit var settingsManager: SettingsManager

    private lateinit var binding: ActivityEarnBadgesBinding
    val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEarnBadgesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show rating API dialog one week after the app has been installed
        if (shouldShowRatingRequest(this, settingsManager))
            showRatingRequest(ReviewManagerFactory.create(this))

        // Bought badges adapter
        val boughtProductsAdapter = BoughtBadgesAdapter()
        binding.boughtBadgesList.apply {
            layoutManager = GridLayoutManager(context, 5)
            adapter = boughtProductsAdapter
        }

        // Buy badges adapter
        val badgesAdapter = BuyBadgeAdapter(model, this)
        binding.buyBadgesList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = badgesAdapter
        }

        // Observe bought products and purchases
        model.boughtBadges.observe(this) { boughtBadges ->
            if (boughtBadges.isEmpty()) {
                binding.boughtBadgesList.visibility = View.GONE
                binding.boughtBadgesTitle.visibility = View.GONE
            } else {
                binding.boughtBadgesList.visibility = View.VISIBLE
                binding.boughtBadgesTitle.visibility = View.VISIBLE
                val count = model.boughtBadges.value!!.size
                binding.boughtBadgesTitle.text = resources.getQuantityString(R.plurals.you_earned_badges, count, count)

                // Update view
                Logger.log.log(Level.INFO, "Adding bought products", boughtBadges)
                boughtProductsAdapter.update(boughtBadges)
                binding.boughtBadgesList.scheduleLayoutAnimation() // triggers badge drop-in animation
            }
        }

        // Observe products available to buy
        model.availableBadges.observe(this) { badges ->
            if (badges.isEmpty()) {
                binding.buyBadgesList.visibility = View.GONE
                binding.availableBadgesEmpty.visibility = View.VISIBLE
            } else {
                binding.buyBadgesList.visibility = View.VISIBLE
                binding.availableBadgesEmpty.visibility = View.GONE
                
                // Update view
                Logger.log.log(Level.INFO,
                    "BuyBadgesAdapter: Adding badges",
                    badges.map{
                        "\n${it.productDetails}, ${it.count}, ${it.yearBought}"
                    }
                )
                badgesAdapter.update(badges)
            }
        }

        // Show Snack bar when something goes wrong
        model.errorMessage.observe(this) { errorMessage ->
            if (errorMessage != null) {
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                model.errorMessage.value = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ensures that all purchases are successfully processed
        model.playClient.queryPurchases()
    }

    /**
     * Starts the in-app review API to trigger the review request
     * Once the user has rated the app, it will still trigger, but won't show up anymore.
     */
    fun showRatingRequest(manager: ReviewManager) {
        // Try prompting for review/rating
        manager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
            Logger.log.log(Level.INFO, "Launching app rating flow")
            manager.launchReviewFlow(this, reviewInfo)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_earn_badges, menu)
        return true
    }

    fun startRating(item: MenuItem) {
        if (!UiUtils.launchUri(this, Uri.parse("market://details?id=$packageName"))) {
            // no store installed, open Google Play website instead
            UiUtils.launchUri(
                this,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
        }
    }

    /**
     * Populates and updates RecyclerView showing buy-able products (badges)
     */
    class BoughtBadgesAdapter: RecyclerView.Adapter<BoughtBadgesAdapter.ViewHolder>() {

        private var badges: MutableList<Badge> = mutableListOf()

        class ViewHolder(val boughtBadgeItemBinding: BoughtBadgeItemBinding) :
            RecyclerView.ViewHolder(boughtBadgeItemBinding.root)

        @SuppressLint("NotifyDataSetChanged")
        fun update(badge: List<Badge>) {
            badges.clear()
            badges.addAll(badge)
            notifyDataSetChanged()
        }

        // Create new buy badge views
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val boughtProductItemBinding = BoughtBadgeItemBinding.inflate(
                LayoutInflater.from(viewGroup.context),
                viewGroup,
                false
            )
            return ViewHolder(boughtProductItemBinding)
        }

        // Replace the contents of a view
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val badge = badges[position]

            // Animation
            val iconImageView = viewHolder.boughtBadgeItemBinding.icon
            iconImageView.setOnClickListener {
                AnimationUtils.loadAnimation(iconImageView.context, BADGES_ANIMATIONS[badge.productDetails.productId]!!).also { animation ->
                    iconImageView.startAnimation(animation)
                }
            }

            // Data bindings
            viewHolder.boughtBadgeItemBinding.apply {
                info.text = badge.yearBought
                icon.setBackgroundResource(BADGES[badge.productDetails.productId]!!)
            }
        }
        override fun getItemCount() = badges.size
    }

    /**
     * Populates and updates RecyclerView showing buy-able products (badges)
     */
    class BuyBadgeAdapter(
        private var model: Model,
        private var activity: Activity
        ): RecyclerView.Adapter<BuyBadgeAdapter.ViewHolder>() {

        private var badges: MutableList<Badge> = mutableListOf()

        class ViewHolder(val buyBadgeItemBinding: BuyBadgeItemBinding) :
            RecyclerView.ViewHolder(buyBadgeItemBinding.root)

        @SuppressLint("NotifyDataSetChanged")
        fun update(badges: List<Badge>) {
            this.badges.clear()
            this.badges.addAll(badges)
            notifyDataSetChanged()
        }

        // Create new buy badge views
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val buyBadgeItemBinding = BuyBadgeItemBinding.inflate(
                LayoutInflater.from(viewGroup.context),
                viewGroup,
                false)
            return ViewHolder(buyBadgeItemBinding)
        }

        // Bind view
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val badge = badges[position]

            // Buy a badge when clicking the buyBadge button
            viewHolder.buyBadgeItemBinding.button.setOnClickListener {
                Logger.log.info("Trying to buy a badge...")
                model.buyBadge(activity, badge)
            }

            // Data bindings
            viewHolder.buyBadgeItemBinding.apply {
                title.text = badge.name
                description.text = badge.description
                button.text = badge.price

                // Badge icon
                val badgeDrawable: Drawable = AppCompatResources.getDrawable(activity, BADGES[badge.productDetails.productId]!!)!!
                icon.setImageDrawable(badgeDrawable)

                // buy button
                button.isEnabled = !badge.purchased
                if (badge.purchased) {
                    button.setBackgroundColor(ContextCompat.getColor(activity, R.color.grey500))
                    button.text = button.context.getString(R.string.button_buy_badge_bought)
                    val heart = AppCompatResources.getDrawable(activity, R.drawable.ic_heart)
                    button.setCompoundDrawablesWithIntrinsicBounds(heart, null, null, null)
                }
            }
        }

        override fun getItemCount() = badges.size
    }


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
        val purchased:Boolean = yearBought != null
    }


    class Model(app: Application) : AndroidViewModel(app) {

        internal val playClient = PlayClient(app)

        val errorMessage = playClient.errorMessage

        /**
         * List of badges available to buy
         */
        val availableBadges = object: MediatorLiveData<List<Badge>>() {
            var nullableProductDetails: List<ProductDetails>? = null
            var nullablePurchases: List<Purchase>? = null
            init {
                addSource(playClient.productDetailsList) { newProducts ->
                    nullableProductDetails = newProducts
                    createBadges()
                }
                addSource(playClient.purchases) { newPurchases ->
                    nullablePurchases = newPurchases
                    createBadges()
                }
            }
            private fun createBadges() {

                // Null checks
                val nonNullProductDetails = nullableProductDetails ?: return
                val nonNullPurchases = nullablePurchases ?: return

                Logger.log.info("Creating new list of badges from product details and purchases")
                Logger.log.info("Product IDs: ${nonNullProductDetails.map {"\n" + it.productId}}")
                Logger.log.info("Purchases: ${nonNullPurchases.map { "\nPurchase: ${it.products}"}}")

                // Create badges from old livedata value if available
                val oldBadgesList: List<Badge> = this.value ?: mutableListOf()
                val badgesList: MutableMap<String, Badge> = oldBadgesList.associateBy { it.productDetails.productId }.toMutableMap()

                // for each product from play store
                for (productDetails in nonNullProductDetails) {
                    // If the product/badge has been bought in one of the purchases, find the year and amount
                    var yearBought: String? = null
                    var count = 0
                    for (purchase in nonNullPurchases) {
                        if (purchase.products.contains(productDetails.productId)) {
                            yearBought = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(purchase.purchaseTime))
                            count = purchase.quantity
                        }
                    }

                    // Create and add/update the badge
                    val badge = Badge(productDetails, yearBought, count)
                    Logger.log.info("Created badge: $badge")
                    badgesList[productDetails.productId] = badge
                }

                // Post the (changed) badges
                postValue(badgesList.values.toList())
            }
        }

        /**
         * Bought badges
         */
        val boughtBadges = object: MediatorLiveData<MutableList<Badge>>() {
            var nullableBadges: List<Badge>? = null
            init {
                addSource(availableBadges) { newBadges ->
                    nullableBadges = newBadges
                    findBoughtBadges()
                }
            }

            private fun findBoughtBadges() {
                val nonNullBadges = nullableBadges ?: return

                Logger.log.info("Finding bought badges")

                // Filter for the bought badges
                val filteredBadges = nonNullBadges.filter { badge ->
                    badge.purchased
                }
                val badges = filteredBadges.toMutableList() // will create a copy to operate on
                val badgeDuplicates = mutableListOf<Badge>()

                // Add extra duplicates of a badge, if needed - ie. for coffees
                for (badge in badges) {
                    for (i in 1 until badge.count)  // runs if count > 1
                        badgeDuplicates.add(badge)
                }
                badges.addAll(badgeDuplicates)

                // Post it
                postValue(badges)
            }
        }

        fun buyBadge(activity: Activity, badge: Badge) = playClient.purchaseProduct(activity, badge)

        override fun onCleared() = playClient.close()

    }

    class PlayClient(
        val context: Context
    ) : Closeable, PurchasesUpdatedListener, BillingClientStateListener,
        ProductDetailsResponseListener, PurchasesResponseListener {

        /**
         * ProductDetails and purchases as LiveData: observers will be notified of changes when posting/setting values
         */
        val productDetailsList = MutableLiveData<List<ProductDetails>>()
        val purchases = MutableLiveData<List<Purchase>>()

        val errorMessage = MutableLiveData<String>()

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
                Logger.log.fine("Start connection...")
                billingClient.startConnection(this)
            }
        }

        /**
         * Stop the billing client connection when the activity is destroyed
         */
        override fun close() {
            if (!billingClient.isReady) {
                Logger.log.fine("Closing connection...")
                billingClient.endConnection()
            }
        }

        /**
         * Continue if connected, and handle failure if connecting was unsuccessful
         */
        override fun onBillingSetupFinished(billingResult: BillingResult) {
            val responseCode = billingResult.responseCode
            val debugMessage = billingResult.debugMessage
            Logger.log.warning("onBillingSetupFinished: $responseCode $debugMessage")
            when (responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    Logger.log.fine("ready")

                    // Purchases are stored locally by gplay app
                    queryPurchases()

                    // Only request product details if not found already
                    if (productDetailsList.value.isNullOrEmpty()) {
                        Logger.log.fine("No products loaded yet, requesting")
                        queryProductDetailsAsync()
                    }
                }

                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingClient.BillingResponseCode.SERVICE_TIMEOUT ->
                    errorMessage.postValue(context.getString(R.string.network_problems))
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                    errorMessage.postValue(context.getString(R.string.billing_unavailable))
            }
        }

        /**
         * Retry starting the billingClient a few times
         */
        override fun onBillingServiceDisconnected() {
            connectionTriesCount++
            val maxTries = Constants.BILLINGCLIENT_CONNECTION_MAX_RETRIES
            Logger.log.warning("Connecting to BillingService failed. Retrying $connectionTriesCount/$maxTries times")
            if (connectionTriesCount > maxTries) {
                Logger.log.warning("Failed to connect to BillingService. Given up on re-trying")
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
            Logger.log.fine("queryProductDetailsAsync")
            val productList = PRODUCT_IDS.map {
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
                if (productDetails.size == PRODUCT_IDS.size) {
                    Logger.log.log(Level.FINE, "BillingClient: Got product details!", productDetails)
                } else
                    Logger.log.warning("Oh no! Expected ${PRODUCT_IDS.size}, but got ${productDetails.size} product details from server.")
                productDetailsList.postValue(productDetails)
            } else
                Logger.log.warning("Failed to query for product details:\n $responseCode $debugMessage")
        }

        fun purchaseProduct(activity: Activity, badge: Badge) {
            if (!billingClient.isReady) {
                Logger.log.warning("BillingClient is not ready")
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
            Logger.log.fine("BillingResponse $responseCode $debugMessage")
        }

        /**
         * Query Google Play Billing for existing purchases.
         *
         * New purchases will be provided to the PurchasesUpdatedListener.
         * You still need to check the Google Play Billing API to know when purchase tokens are removed.
         */
        internal fun queryPurchases() {
            if (!billingClient.isReady) {
                Logger.log.warning("BillingClient is not ready")
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
            Logger.log.fine("$responseCode $debugMessage")
            when (responseCode) {
                BillingClient.BillingResponseCode.OK ->
                    if (!purchases.isNullOrEmpty()) processPurchases(purchases)
                BillingClient.BillingResponseCode.USER_CANCELED ->
                    Logger.log.info("User canceled the purchase")
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                    Logger.log.info("The user already owns this item")
                BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                    Logger.log.warning("Google Play does not recognize the application configuration." +
                            "Do the product IDs match and is the APK in use signed with release keys?")
            }
        }

        /**
         * Process purchases
         */
        private fun processPurchases(purchasesList: MutableList<Purchase>) {
            val initialCount = purchasesList.size
            if (purchasesList == purchases.value) {
                Logger.log.fine("Purchase list has not changed")
                return
            }

            // Handle purchases
            logPurchaseStatus(purchasesList)
            runBlocking {
                for (purchase in purchasesList) {
                    Logger.log.info("Handling purchase with state: ${purchase.purchaseState}")
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (!purchase.isAcknowledged) {
                            Logger.log.info("Acknowledging purchase")
                            acknowledgePurchase(purchase) { ackPurchaseResult ->
                                val responseCode = ackPurchaseResult.responseCode
                                val debugMessage = ackPurchaseResult.debugMessage
                                if (responseCode != BillingClient.BillingResponseCode.OK) {
                                    Logger.log.warning("Acknowledging Purchase failed!")
                                    Logger.log.warning("AcknowledgePurchaseResult: $responseCode $debugMessage")
                                    purchasesList.remove(purchase)
                                }
                            }
                        }
                    } else {
                        // purchase pending or in undefined state (ie. refunded)
                        purchasesList.remove(purchase)
                    }
                    // DANGER: consumes product! use for revoking a test purchase
                    /*if (BuildConfig.BUILD_TYPE == "debug")
                        consumePurchase(purchase) { result ->
                            when (result.billingResult.responseCode) {
                                BillingClient.BillingResponseCode.OK ->
                                    Logger.log.info("Successfully consumed item with purchase token: '${result.purchaseToken}'")
                                BillingClient.BillingResponseCode.ITEM_NOT_OWNED ->
                                    Logger.log.info("Failed to consume item with purchase token: '${result.purchaseToken}'. Item not owned")
                                else ->
                                    Logger.log.info("Failed to consume item with purchase token: '${result.purchaseToken}'. BillingResult: $result")
                            }
                            }*/
                }
            }

            logAcknowledgementStatus(purchasesList)

            if (purchasesList.size != initialCount)
                throw BillingException("Some purchase could not be acknowledged")

            // Update list
            Logger.log.info("posting ${purchasesList.size} purchases")
            purchases.value?.let { purchasesList.addAll(it) }
            purchases.postValue(purchasesList)

        }

        /**
         * Consumes a purchased item, so it will be available for purchasing again.
         * Used for testing - don't remove.
         */
        private suspend fun consumePurchase(purchase: Purchase, runAfter: (billingResult: ConsumeResult) -> Unit) {
            Logger.log.info("Trying to consume purchase with token: ${purchase.purchaseToken}")
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val response = withContext(Dispatchers.IO) {
                billingClient.consumePurchase(consumeParams)
            }
            runAfter(response)
        }

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
            Logger.log.info("logAcknowledgementStatus: acknowledged=$ackYes unacknowledged=$ackNo")
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
            Logger.log.info("logPurchaseStatus: purchased=$purchased pending=$pending undefined=$undefined")
        }

    }

    class BillingException(msg: String) : Exception(msg)

}