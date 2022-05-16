/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level

class EarnBadgesActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var binding: ActivityEarnBadgesBinding
    val model by viewModels<Model>()

    companion object {

        private const val SKU_HELPING_HANDS = "helping_hands.2022"
        private const val SKU_A_COFFEE_FOR_YOU = "a_coffee_for_you.2022"
        private const val SKU_LOYAL_FOSS_BACKER = "loyal_foss_backer.2022"
        private const val SKU_PART_OF_THE_JOURNEY = "part_of_the_journey.2022"

        private val SKU_BADGES = mapOf(
            SKU_HELPING_HANDS to R.drawable.ic_badge_life_buoy,
            SKU_A_COFFEE_FOR_YOU to R.drawable.ic_badge_coffee,
            SKU_LOYAL_FOSS_BACKER to R.drawable.ic_badge_medal,
            SKU_PART_OF_THE_JOURNEY to R.drawable.ic_badge_sailboat
        )
        private val SKU_BADGES_ANIMATIONS = mapOf(
            SKU_HELPING_HANDS to R.anim.spin,
            SKU_A_COFFEE_FOR_YOU to R.anim.lift,
            SKU_LOYAL_FOSS_BACKER to R.anim.pulsate,
            SKU_PART_OF_THE_JOURNEY to R.anim.rock
        )
        val SKUS = SKU_BADGES.keys.toList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEarnBadgesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bought badges adapter
        val boughtSkusAdapter = BoughtBadgesAdapter()
        binding.boughtBadgesList.apply {
            layoutManager = GridLayoutManager(context, 5)
            adapter = boughtSkusAdapter
        }

        // Buy badges adapter
        val badgesAdapter = BuyBadgeAdapter(model, this)
        binding.buyBadgesList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = badgesAdapter
        }

        // Observe bought skus and purchases
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
                Logger.log.log(Level.INFO, "BoughtSkusAdapter: Adding bought SKUs", boughtBadges)
                boughtSkusAdapter.update(boughtBadges)
                binding.boughtBadgesList.scheduleLayoutAnimation() // triggers badge drop-in animation
            }
        }

        // Observe skus available to buy
        model.badges.observe(this) { badges ->
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
                        "\n${it.skuDetails.sku}, ${it.count}, ${it.yearBought}"
                    }
                )
                badgesAdapter.update(badges)

            }
        }

    }

    override fun onResume() {
        super.onResume()
        // ensures that all purchases are successfully processed
        model.store.queryPurchases()
    }

    /**
     * Populates and updates RecyclerView showing buy-able skus (badges)
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
            val boughtSkuItemBinding = BoughtBadgeItemBinding.inflate(
                LayoutInflater.from(viewGroup.context),
                viewGroup,
                false
            )
            return ViewHolder(boughtSkuItemBinding)
        }

        // Replace the contents of a view
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val badge = badges[position]

            // Animation
            val iconImageView = viewHolder.boughtBadgeItemBinding.icon
            iconImageView.setOnClickListener {
                AnimationUtils.loadAnimation(iconImageView.context, SKU_BADGES_ANIMATIONS[badge.skuDetails.sku]!!).also { animation ->
                    iconImageView.startAnimation(animation)
                }
            }

            // Data bindings
            viewHolder.boughtBadgeItemBinding.apply {
                info.text = badge.yearBought
                icon.setBackgroundResource(SKU_BADGES[badge.skuDetails.sku]!!)
            }
        }
        override fun getItemCount() = badges.size
    }

    /**
     * Populates and updates RecyclerView showing buy-able skus (badges)
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

                // Badge icon - in dark grey
                val badgeDrawable: Drawable = AppCompatResources.getDrawable(activity, SKU_BADGES[badge.skuDetails.sku]!!)!!
                icon.setImageDrawable(badgeDrawable)
                icon.setColorFilter(ContextCompat.getColor(icon.context, R.color.grey800), PorterDuff.Mode.SRC_IN)

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
     * A Badge type object containing
     * - skuDetails
     * - purchase that bought this sku
     * - amount of badge items of this badge type
     */
    data class Badge(val skuDetails: SkuDetails, var yearBought: String?, val count: Int) {
        private val jsonSku = JSONObject(skuDetails.originalJson)

        val name: String = jsonSku.getString("name")
        val description = skuDetails.description
        val price = skuDetails.price
        val purchased:Boolean = yearBought != null
    }


    class Model(app: Application) : AndroidViewModel(app) {

        internal val store = PlayClient(app)

        val purchases = store.purchases
        val skus = store.skus

        /**
         * List of badges available to buy
         */
        val badges = object: MediatorLiveData<List<Badge>>() {
            var nullableSkus: List<SkuDetails>? = null
            var nullablePurchases: List<Purchase>? = null
            init {
                addSource(skus) { newSkus ->
                    nullableSkus = newSkus
                    createBadges()
                }
                addSource(purchases) { newPurchases ->
                    nullablePurchases = newPurchases
                    createBadges()
                }
            }
            private fun createBadges() {

                // Null checks
                val nonNullSkus = nullableSkus ?: return
                val nonNullPurchases = nullablePurchases ?: return

                Logger.log.info("Creating new list of badges from SKUs and purchases")
                Logger.log.info("SKUs: ${nonNullSkus.map {"\n" + it.sku}}")
                Logger.log.info("Purchases: ${nonNullPurchases.map { "\nPurchase: ${it.skus}"}}")

                // Create badges from old livedata value if available
                val oldBadgesList: List<Badge> = this.value ?: mutableListOf()
                val badges: MutableMap<String, Badge> = oldBadgesList.associateBy { it.skuDetails.sku }.toMutableMap()

                // for each sku from play store
                for (skuDetails in nonNullSkus) {
                    // If the sku/badge has been bought in one of the purchases, find the year and amount
                    var yearBought: String? = null
                    var count = 0
                    for (purchase in nonNullPurchases) {
                        if (purchase.skus.contains(skuDetails.sku)) {
                            yearBought = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(purchase.purchaseTime))
                            count = purchase.quantity
                        }
                    }

                    // Create and add/update the badge
                    val badge = Badge(skuDetails, yearBought, count)
                    Logger.log.info("Created badge: $badge")
                    badges[skuDetails.sku] = badge
                }

                // Post the (changed) badges
                postValue(badges.values.toList())
            }
        }

        /**
         * Bought badges
         */
        val boughtBadges = object: MediatorLiveData<MutableList<Badge>>() {
            var nullableBadges: List<Badge>? = null
            init {
                addSource(badges) { newBadges ->
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
                    for (i in 1 until badge.count) { // runs if count > 1
                        badgeDuplicates.add(badge)
                    }
                }
                badges.addAll(badgeDuplicates)

                // Post it
                postValue(badges)
            }
        }

        fun buyBadge(activity: Activity, badge: Badge) {
            store.launchBillingFlow(
                activity,
                store.getBillingParams(badge.skuDetails)
            )
        }

        override fun onCleared() {
            store.close()
        }

    }


    class PlayClient(
        val context: Context
    ) : Closeable, PurchasesUpdatedListener, BillingClientStateListener,
        SkuDetailsResponseListener, PurchasesResponseListener {

        /**
         * SkuDetails and purchases as LiveData: observers will be notified of changes when posting/setting values
         */
        val skus = MutableLiveData<List<SkuDetails>>()
        val purchases = MutableLiveData<List<Purchase>>()

        private val billingClient: BillingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        private var connectionTriesCount: Int = 0

        /**
         * Set up the billing client and connect when the activity is created
         * SKUs and purchases are loaded from cache, but this will give a more responsive user
         * experience, when buying a SKU
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
         * Do nothing, when the billing client is ready
         */
        override fun onBillingSetupFinished(billingResult: BillingResult) {
            val responseCode = billingResult.responseCode
            val debugMessage = billingResult.debugMessage
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                Logger.log.fine("ready")

                // Purchases are stored locally by gplay app
                queryPurchases()

                // Only request SKUs if not found already
                if (skus.value.isNullOrEmpty()) {
                    Logger.log.fine("No skus loaded yet, requesting")
                    querySkusAsync()
                }

            } else {
                Logger.log.warning("onBillingSetupFinished: $responseCode $debugMessage")
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
         * Ask google servers for SKU details to display (ie. SKU-id, price, description, etc)
         * This is an asynchronous call that will receive a result in [onSkuDetailsResponse].
         */
        private fun querySkusAsync() {
            Logger.log.fine("querySkuDetailsAsync")
            billingClient.querySkuDetailsAsync(
                SkuDetailsParams.newBuilder()
                .setType(BillingClient.SkuType.INAPP) // In App Purchases
                .setSkusList(SKUS)
                .build(), this)
        }

        /**
         * Receives the result from [querySkusAsync].
         *
         * Store the SkuDetails and post them in to [skus]. This allows other parts
         * of the app to use the [SkuDetails] to show SKU information and make purchases.
         */
        override fun onSkuDetailsResponse(billingResult: BillingResult, skuDetailsList: MutableList<SkuDetails>?) {
            val responseCode = billingResult.responseCode
            val debugMessage = billingResult.debugMessage
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                if (skuDetailsList != null && skuDetailsList.size == SKUS.size) {
                    Logger.log.log(Level.FINE, "BillingClient: Got sku details!", skuDetailsList)
                    skus.postValue(skuDetailsList)
                } else {
                    Logger.log.warning("Oh no! Expected ${SKUS.size}, but found ${skuDetailsList?.size} SkuDetails.")
                }
            } else {
                Logger.log.warning("Failed to query for sku details:\n $responseCode $debugMessage")
            }
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
            }
            billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, this) // onQueryPurchasesResponse receives
        }

        /**
         * Callback from the billing library when queryPurchasesAsync is called.
         */
        override fun onQueryPurchasesResponse(
            billingResult: BillingResult, purchasesList: MutableList<Purchase>) {
            processPurchases(purchasesList)
        }

        /**
         * Called by the Billing Library when new purchases are detected.
         */
        override fun onPurchasesUpdated(
            billingResult: BillingResult,
            purchases: MutableList<Purchase>?) {
            val responseCode = billingResult.responseCode
            val debugMessage = billingResult.debugMessage
            Logger.log.fine("$responseCode $debugMessage")
            when (responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    if (!purchases.isNullOrEmpty()) {
                        processPurchases(purchases)
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    Logger.log.info("User canceled the purchase")
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    Logger.log.info("The user already owns this item")
                }
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                    Logger.log.warning("Google Play does not recognize the application configuration." +
                            "Do the SKU product IDs match and is the APK in use signed with release keys?")
                }
            }
        }

        /**
         * Process purchases
         */
        private fun processPurchases(purchasesList: MutableList<Purchase>) {
            val initialCount = purchasesList.size
            // Check if purchases changed
            if (purchasesList == purchases.value) {
                // Lists have same elements in same order
                Logger.log.fine("Purchase list has not changed")
                return
            }

            // Acknowledge purchases
            runBlocking {
                Logger.log.info("Acknowledging purchases")
                for (purchase in purchasesList) {
                    handlePurchase(purchase) { ackPurchaseResult ->
                        val responseCode = ackPurchaseResult.responseCode
                        val debugMessage = ackPurchaseResult.debugMessage
                        if (responseCode != BillingClient.BillingResponseCode.OK) {
                            Logger.log.warning("Acknowledging Purchase failed!")
                            Logger.log.warning("AcknowledgePurchaseResult: $responseCode $debugMessage")
                            purchasesList.remove(purchase)
                        }
                    }
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
         * Log the number of purchases that are acknowledge and not acknowledged.
         */
        private fun logAcknowledgementStatus(purchasesList: List<Purchase>) {
            var ack_yes = 0
            var ack_no = 0
            for (purchase in purchasesList) {
                if (purchase.isAcknowledged) {
                    ack_yes++
                } else {
                    ack_no++
                }
            }
            Logger.log.info("logAcknowledgementStatus: acknowledged=$ack_yes unacknowledged=$ack_no")
        }

        /**
         * Requests acknowledgement of a purchase and lets the passed function handle the request result
         */
        private suspend fun handlePurchase(purchase: Purchase, runAfter: (billingResult: BillingResult) -> Unit) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED // pending purchases (ie. cash-payment) are not supported
                || purchase.isAcknowledged) {
                return
            }

            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
            val response = withContext(Dispatchers.IO) {
                billingClient.acknowledgePurchase(acknowledgePurchaseParams.build())
            }

            // Handle acknowledgement response in passed function
            runAfter(response)
        }

        /**
         * Launches the billing flow
         */
        fun launchBillingFlow(activity: Activity, params: BillingFlowParams) {
            if (!billingClient.isReady) {
                Logger.log.warning("BillingClient is not ready")
            }
            val billingResult = billingClient.launchBillingFlow(activity, params)
            val responseCode = billingResult.responseCode
            val debugMessage = billingResult.debugMessage
            Logger.log.fine("BillingResponse $responseCode $debugMessage")
        }

        fun getBillingParams(skuDetails: SkuDetails): BillingFlowParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build()
    }

    class BillingException(msg: String) : Exception(msg)

}