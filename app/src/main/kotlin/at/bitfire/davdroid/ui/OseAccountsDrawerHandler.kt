/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.COMMUNITY_URL
import at.bitfire.davdroid.Constants.FEDIVERSE_URL
import at.bitfire.davdroid.Constants.MANUAL_URL
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import javax.inject.Inject

open class OseAccountsDrawerHandler @Inject constructor(): AccountsDrawerHandler() {

    companion object {
        const val WEB_CONTEXT = "AccountsDrawerHandler"
    }

    @Composable
    override fun MenuEntries(
        snackbarHostState: SnackbarHostState
    ) {
        val uriHandler = LocalUriHandler.current

        // Most important entries
        ImportantEntries(snackbarHostState)

        // News
        MenuHeading(R.string.navigation_drawer_news_updates)
        MenuEntry(
            icon = painterResource(R.drawable.mastodon),
            title = Constants.FEDIVERSE_HANDLE,
            onClick = {
                uriHandler.openUri(FEDIVERSE_URL.toString())
            }
        )

        // Tools
        Tools()

        // Support the project
        MenuHeading(R.string.navigation_drawer_support_project)
        Contribute(onContribute = {
            uriHandler.openUri(
                Constants.HOMEPAGE_URL.buildUpon()
                    .appendPath(Constants.HOMEPAGE_PATH_OPEN_SOURCE)
                    .withStatParams(WEB_CONTEXT)
                    .build().toString()
            )
        })
        MenuEntry(
            icon = Icons.Default.Forum,
            title = stringResource(R.string.navigation_drawer_community),
            onClick = {
                uriHandler.openUri(COMMUNITY_URL.toString())
            }
        )


        // External links
        MenuHeading(R.string.navigation_drawer_external_links)
        MenuEntry(
            icon = Icons.Default.Home,
            title = stringResource(R.string.navigation_drawer_website),
            onClick = {
                uriHandler.openUri(Constants.HOMEPAGE_URL
                    .buildUpon()
                    .withStatParams(WEB_CONTEXT)
                    .build().toString())
            }
        )
        MenuEntry(
            icon = Icons.Default.Info,
            title = stringResource(R.string.navigation_drawer_manual),
            onClick = {
                uriHandler.openUri(MANUAL_URL.toString())
            }
        )
        MenuEntry(
            icon = Icons.AutoMirrored.Default.HelpCenter,
            title = stringResource(R.string.navigation_drawer_faq),
            onClick = {
                uriHandler.openUri(
                    Constants.HOMEPAGE_URL.buildUpon()
                        .appendPath(Constants.HOMEPAGE_PATH_FAQ)
                        .withStatParams(WEB_CONTEXT)
                        .build().toString()
                )
            }
        )
        MenuEntry(
            icon = Icons.Default.CloudOff,
            title = stringResource(R.string.navigation_drawer_privacy_policy),
            onClick = {
                uriHandler.openUri(
                    Constants.HOMEPAGE_URL.buildUpon()
                        .appendPath(Constants.HOMEPAGE_PATH_PRIVACY)
                        .withStatParams(WEB_CONTEXT)
                        .build().toString()
                )
            }
        )
    }

    @Composable
    @Preview
    fun MenuEntries_Standard_Preview() {
        Column {
            MenuEntries(SnackbarHostState())
        }
    }


    @Composable
    open fun Contribute(onContribute: () -> Unit) {
        MenuEntry(
            icon = Icons.Default.VolunteerActivism,
            title = stringResource(R.string.navigation_drawer_contribute),
            onClick = onContribute
        )
    }

}