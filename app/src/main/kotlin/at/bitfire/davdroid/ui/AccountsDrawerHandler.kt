/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.about.AboutActivity
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
import kotlinx.coroutines.launch
import java.net.URI

val LocalCloseDrawerHandler = compositionLocalOf {
    AccountsDrawerHandler.CloseDrawerHandler()
}

abstract class AccountsDrawerHandler {

    open class CloseDrawerHandler {
        open fun closeDrawer() {}
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
        Column(modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
            BrandingHeader()

            val closeDrawerHandler = object : CloseDrawerHandler() {
                override fun closeDrawer() {
                    onCloseDrawer()
                }
            }
            CompositionLocalProvider(LocalCloseDrawerHandler provides closeDrawerHandler) {
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

}


// generic building blocks

@Composable
fun MenuHeading(text: String) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(8.dp)
    )
}

@Composable
fun MenuHeading(@StringRes text: Int) = MenuHeading(stringResource(text))

@Composable
@Preview
fun MenuHeading_Preview() {
    MenuHeading("Tools")
}

@Composable
fun MenuEntry(
    icon: Painter,
    title: String,
    onClick: () -> Unit
) {
    val closeHandler = LocalCloseDrawerHandler.current
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = title) },
        label = { Text(title, style = MaterialTheme.typography.labelLarge) },
        selected = false,
        shape = RectangleShape,
        onClick = {
            onClick()
            closeHandler.closeDrawer()
        }
    )
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

@Composable
@Preview
fun MenuEntry_Preview() {
    MenuEntry(
        icon = Icons.Default.Info,
        title = "About",
        onClick = {}
    )
}


// specific blocks

@Composable
fun BrandingHeader() {
    Column(
        Modifier
            .statusBarsPadding()
            .background(Color.DarkGray)
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.background(
                color = M3ColorScheme.primaryLight,
                shape = RoundedCornerShape(16.dp)
            )
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground),
                stringResource(R.string.app_name),
                tint = Color.White,
                modifier = Modifier
                    .scale(1.2f)
                    .size(64.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.app_name),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            stringResource(R.string.navigation_drawer_subtitle),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
@Preview
fun BrandingHeader_Preview_Light() {
    AppTheme(darkTheme = false) {
        BrandingHeader()
    }
}

@Composable
@Preview
fun BrandingHeader_Preview_Dark() {
    AppTheme(darkTheme = true) {
        BrandingHeader()
    }
}