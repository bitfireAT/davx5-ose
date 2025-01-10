/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.PlayClient
import at.bitfire.davdroid.PlayClient.Badge
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Logger

@HiltViewModel(assistedFactory = EarnBadgesModel.Factory::class)
class EarnBadgesModel @AssistedInject constructor(
    @ApplicationContext val context: Context,
    private val logger: Logger,
    @Assisted val playClient: PlayClient
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(playClient: PlayClient): EarnBadgesModel
    }

    init {
        // Load the current state of bought badges
        playClient.queryPurchases()
    }

    val errorMessage = playClient.errorMessage

    /**
     * List of badges available to buy
     */
    val availableBadges = combine(
        playClient.productDetailsList,
        playClient.purchases
    ) { productDetails, purchases ->

        logger.info("Creating new list of badges from product details and purchases")
        logger.info("Product IDs: ${productDetails.map {"\n" + it.productId}}")
        logger.info("Purchases: ${purchases.map { "\nPurchase: ${it.products}"}}")

        // Create badges
        productDetails.map { productDetails ->
            // If the product/badge has been bought in one of the purchases, find the year and amount
            var yearBought: String? = null
            var count = 0
            for (purchase in purchases)
                if (purchase.products.contains(productDetails.productId)) {
                    yearBought = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(purchase.purchaseTime))
                    count = purchase.quantity
                }
            Badge(productDetails, yearBought, count)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Bought badges
     */
    val boughtBadges = availableBadges.map { allBadges ->
        logger.info("Finding bought badges")

        // Filter for the bought badges
        val boughtBadges = allBadges.filter { badge ->
            badge.purchased
        }

        // Create duplicates for the ones that have been bought multiple times
        boughtBadges.toMutableList().apply {
            addAll(flatMap { badge -> List(badge.count - 1) { badge } })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun buyBadge(badge: Badge) = playClient.purchaseProduct(badge)

    fun onResetErrors() = playClient.resetErrors()

    override fun onCleared() = playClient.close()

}