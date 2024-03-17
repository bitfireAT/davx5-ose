/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
import kotlinx.coroutines.launch
import java.net.URI

abstract class AccountsDrawerHandler {

    open class CloseDrawerHandler {
        open fun closeDrawer() {}
    }

    val localCloseDrawerHandler = compositionLocalOf {
        CloseDrawerHandler()
    }


    @Composable
    abstract fun MenuEntries(
        snackbarHostState: SnackbarHostState
    )


    @Composable
    fun AccountsDrawer(
        snackbarHostState: SnackbarHostState,
        onCloseDrawer: () -> Unit
    ) {
        Column(Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
            Header()

            val closeDrawerHandler = object : CloseDrawerHandler() {
                override fun closeDrawer() {
                    onCloseDrawer()
                }
            }
            CompositionLocalProvider(localCloseDrawerHandler provides closeDrawerHandler) {
                MenuEntries(snackbarHostState)
            }
        }
    }


    // menu section composables

    @Composable
    open fun ImportantEntries(
        snackbarHostState: SnackbarHostState
    ) {
        val context = LocalContext.current
        val isBeta =
            LocalInspectionMode.current ||
            BuildConfig.VERSION_NAME.contains("-alpha") ||
            BuildConfig.VERSION_NAME.contains("-beta") ||
            BuildConfig.VERSION_NAME.contains("-rc")
        val scope = rememberCoroutineScope()

        MenuEntry(
            icon = Icons.Default.Info,
            title = stringResource(R.string.navigation_drawer_about),
            onClick = {
                context.startActivity(Intent(context, AboutActivity::class.java))
            }
        )

        if (isBeta)
            MenuEntry(
                icon = Icons.Default.Feedback,
                title = stringResource(R.string.navigation_drawer_beta_feedback),
                onClick = {
                    onBetaFeedback(
                        context,
                        onShowSnackbar = { text: String, actionLabel: String, action: () -> Unit ->
                            scope.launch {
                                if (snackbarHostState.showSnackbar(text, actionLabel) == SnackbarResult.ActionPerformed)
                                    action()
                            }
                        }
                    )
                }
            )

        MenuEntry(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.navigation_drawer_settings),
            onClick = {
                context.startActivity(Intent(context, AppSettingsActivity::class.java))
            }
        )
    }

    @Composable
    fun Tools() {
        val context = LocalContext.current

        MenuHeading(R.string.navigation_drawer_tools)
        MenuEntry(
            icon = Icons.Default.Storage,
            title = stringResource(R.string.webdav_mounts_title),
            onClick = {
                context.startActivity(Intent(context, WebdavMountsActivity::class.java))
            }
        )
    }


    // overridable actions

    open fun onBetaFeedback(
        context: Context,
        onShowSnackbar: (message: String, actionLabel: String, action: () -> Unit) -> Unit
    ) {
        val mailto = URI(
            "mailto", "play@bitfire.at?subject=${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} feedback (${BuildConfig.VERSION_CODE})", null
        )
        val intent = Intent(Intent.ACTION_SENDTO, mailto.toString().toUri())
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }


    // building blocks

    @Composable
    fun Header() {
        Column(
            Modifier
            .background(Color.DarkGray)
            .fillMaxWidth()
            .padding(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                .background(
                    color = MaterialTheme.colors.primary,
                    shape = RoundedCornerShape(16.dp)
                )
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    stringResource(R.string.app_name),
                    tint = Color.White,
                    modifier = Modifier
                        .scale(1.2f)
                        .height(56.dp)
                        .width(56.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.body1
            )
            Text(
                stringResource(R.string.navigation_drawer_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.body2
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    @Composable
    fun MenuHeading(text: String) {
        Divider(Modifier.padding(vertical = 8.dp))

        Text(
            text,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(8.dp)
        )
    }

    @Composable
    fun MenuHeading(@StringRes text: Int) = MenuHeading(stringResource(text))

    @Composable
    fun MenuEntry(
        icon: Painter,
        title: String,
        onClick: () -> Unit
    ) {
        val closeHandler = localCloseDrawerHandler.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = {
                    onClick()
                    closeHandler.closeDrawer()
                })
                .padding(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            )
        }
    }

    @Composable
    fun MenuEntry(
        icon: ImageVector,
        title: String,
        onClick: () -> Unit
    ) {
        MenuEntry(
            icon = rememberVectorPainter(icon),
            title = title,
            onClick = onClick
        )
    }

}