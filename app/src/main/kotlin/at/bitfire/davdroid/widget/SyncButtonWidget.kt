package at.bitfire.davdroid.widget

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.ui.AccountsActivity
import at.bitfire.davdroid.ui.onPrimaryGreen
import at.bitfire.davdroid.ui.primaryGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                .background(primaryGreen)
                .cornerRadius(16.dp)
                .padding(4.dp)
                .clickable {
                    requestSync(context)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_sync),
                contentDescription = context.getString(R.string.accounts_sync_all),
                modifier = GlanceModifier
                    .padding(vertical = 8.dp, horizontal = 8.dp)
                    .size(32.dp),
                colorFilter = ColorFilter.tint(ColorProvider(onPrimaryGreen))
            )
            Text(
                text = context.getString(R.string.accounts_sync_all_short),
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(end = 8.dp),
                style = TextDefaults.defaultTextStyle.copy(
                    color = ColorProvider(onPrimaryGreen),
                    fontSize = 16.sp
                )
            )
        }
    }

    private fun requestSync(context: Context) = CoroutineScope(Dispatchers.IO).launch {
        val accountType = context.getString(R.string.account_type)
        val accounts = AccountManager.get(context).getAccountsByType(accountType)
        for (account in accounts) {
            SyncWorker.enqueueAllAuthorities(context, account)
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.sync_started, Toast.LENGTH_SHORT).show()
        }
    }
}
