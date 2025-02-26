/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.davdroid.ui.M3ColorScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A widget with a "Sync all" button.
 */
class LabeledSyncButtonWidget : GlanceAppWidget() {

    // Hilt over @AndroidEntryPoint is not available for widgets
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncButtonWidgetEntryPoint {
        fun model(): Model
    }


    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // initial data
        val entryPoint = EntryPointAccessors.fromApplication<SyncButtonWidgetEntryPoint>(context)
        val model = entryPoint.model()

        // will be called when the widget is updated
        provideContent {
            WidgetContent(model)
        }
    }

    @Composable
    private fun WidgetContent(model: Model) {
        val context = LocalContext.current

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(M3ColorScheme.primaryLight))
                .cornerRadius(16.dp)
                .padding(4.dp)
                .clickable {
                    model.requestSync()
                    Toast.makeText(context, R.string.sync_started, Toast.LENGTH_SHORT).show()
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


    class Model @Inject constructor(
        private val accountRepository: AccountRepository,
        @ApplicationContext val context: Context,
        private val syncWorkerManager: SyncWorkerManager
    ): ViewModel() {

        fun requestSync() = viewModelScope.launch(Dispatchers.Default) {
            for (account in accountRepository.getAll())
                syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
        }

    }

}