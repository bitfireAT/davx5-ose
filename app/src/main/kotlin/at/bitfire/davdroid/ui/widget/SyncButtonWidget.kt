/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import android.accounts.AccountManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import androidx.glance.unit.ColorProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker
import at.bitfire.davdroid.ui.M3ColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncButtonWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(M3ColorScheme.primaryLight))
                .cornerRadius(16.dp)
                .padding(4.dp)
                .clickable {
                    requestSync(context)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onPrimary = ColorProvider(M3ColorScheme.onPrimaryLight)
            Image(
                provider = ImageProvider(R.drawable.ic_sync),
                contentDescription = context.getString(R.string.widget_sync_all_accounts),
                modifier = GlanceModifier
                    .padding(vertical = 8.dp, horizontal = 8.dp)
                    .size(32.dp),
                colorFilter = ColorFilter.tint(onPrimary)
            )
            Text(
                text = context.getString(R.string.widget_sync_all),
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(end = 8.dp),
                style = TextDefaults.defaultTextStyle.copy(
                    color = onPrimary,
                    fontSize = 16.sp
                )
            )
        }
    }

    private fun requestSync(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            val accountType = context.getString(R.string.account_type)
            val accounts = AccountManager.get(context).getAccountsByType(accountType)
            for (account in accounts) {
                OneTimeSyncWorker.enqueueAllAuthorities(context, account, manual = true)
            }
        }

        Toast.makeText(context, R.string.sync_started, Toast.LENGTH_SHORT).show()
    }

}