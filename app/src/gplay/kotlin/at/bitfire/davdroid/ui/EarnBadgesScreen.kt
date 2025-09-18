/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.PlayClient
import at.bitfire.davdroid.PlayClient.Badge
import at.bitfire.davdroid.PlayClient.Companion.BADGE_ICONS
import at.bitfire.davdroid.R
import kotlin.collections.forEach


@Composable
fun EarnBadgesScreen(
    playClient: PlayClient,
    onStartRating: () -> Unit = {},
    onNavUp: () -> Unit = {},
    model: EarnBadgesModel = hiltViewModel(
        creationCallback = { factory: EarnBadgesModel.Factory ->
            factory.create(playClient)
        }
    )
) {
    val availableBadges by model.availableBadges.collectAsStateWithLifecycle()
    val boughtBadges by model.boughtBadges.collectAsStateWithLifecycle()
    val errorMessage by model.message.collectAsStateWithLifecycle()

    EarnBadges(
        availableBadges = availableBadges,
        boughtBadges = boughtBadges,
        message = errorMessage,
        onBuyBadge = model::buyBadge,
        onResetMessage = model::onResetMessage,
        onStartRating = onStartRating,
        onNavUp = onNavUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarnBadges(
    availableBadges: List<Badge>,
    boughtBadges: List<Badge>,
    message: String?,
    onBuyBadge: (badge: Badge) -> Unit = {},
    onResetMessage: () -> Unit = {},
    onStartRating: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    // Show snackbar when some message needs to be displayed
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message != null) {
        message?.let {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
            onResetMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavUp) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            stringResource(R.string.navigate_up)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.earn_badges),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onStartRating) {
                        Icon(Icons.Default.StarRate, stringResource(R.string.nav_rate_us))
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (boughtBadges.isNotEmpty()) {
                TextHeading(
                    pluralStringResource(
                        R.plurals.you_earned_badges,
                        boughtBadges.size,
                        boughtBadges.size
                    )
                )
                LazyVerticalGrid(
                    modifier = Modifier.heightIn(max = 1000.dp),
                    columns = GridCells.Adaptive(minSize = 60.dp)
                ) {
                    items(boughtBadges.size) { index ->
                        BoughtBadgeListItem(boughtBadges[index])
                    }
                }
            }

            TextHeading(stringResource(R.string.available_badges))
            if (availableBadges.isEmpty())
                TextBody(stringResource(R.string.available_badges_empty))
            availableBadges.forEach { badge ->
                BuyBadgeListItem(badge, onBuyBadge)
            }

            TextHeading(stringResource(R.string.what_are_badges_title))
            TextBody(stringResource(R.string.what_are_badges_body))

            TextHeading(stringResource(R.string.why_badges_title))
            TextBody(stringResource(R.string.why_badges_body))
        }
    }
}

@Composable
fun BoughtBadgeListItem(badge: Badge) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            modifier = Modifier
                .fillMaxSize(),
            onClick = { /* could start an animation */ }
        ) {
            Card(
                modifier = Modifier
                    .aspectRatio(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White,
                ),
            ) {
                val (icon, tint) = BADGE_ICONS[badge.productDetails.productId]!!
                Icon(
                    imageVector = icon,
                    contentDescription = badge.productDetails.productId,
                    tint = tint,
                    modifier = Modifier
                        .size(65.dp)
                        .padding(3.dp)
                )
            }

        }
        Text(badge.yearBought ?: "?", fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun BuyBadgeListItem(
    badge: Badge,
    onBuyBadge: (badge: Badge) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            val (icon, tint) = BADGE_ICONS[badge.productDetails.productId]!!
            Icon(
                imageVector = icon,
                contentDescription = badge.productDetails.productId,
                tint = tint,
                modifier = Modifier.size(30.dp)
            )
            Column(
                modifier = Modifier
                    .weight(3f, true)
                    .padding(horizontal = 16.dp)
            ) {
                Text(badge.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(badge.description.replace("\n", ""), fontSize = 12.sp, lineHeight = 14.sp)
            }
            Button(
                onClick = { onBuyBadge(badge) },
                enabled = !badge.purchased
            ) {
                Icon(
                    imageVector = if (!badge.purchased) Icons.Default.Star else Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                if (!badge.purchased)
                    Text(badge.price ?: stringResource(R.string.button_buy_badge_free))
                else
                    Text(stringResource(R.string.button_buy_badge_bought))
            }
        }
    }
}

@Composable
fun TextHeading(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineSmall,
    modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
)

@Composable
fun TextBody(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    modifier = Modifier.padding(bottom = 16.dp)
)