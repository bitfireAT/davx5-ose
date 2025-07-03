/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.AndroidEvent
import at.bitfire.synctools.storage.calendar.UnknownProperty
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.property.Url
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.use

/**
 * Store event URLs as URL (extended property) instead of unknown property. At the same time,
 * convert legacy unknown properties to the current format.
 */
class AccountSettingsMigration12 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { provider ->
                // Attention: CalendarProvider does NOT limit the results of the ExtendedProperties query
                // to the given account! So all extended properties will be processed number-of-accounts times.
                val extUri = CalendarContract.ExtendedProperties.CONTENT_URI.asSyncAdapter(account)

                provider.query(
                    extUri, arrayOf(
                        CalendarContract.ExtendedProperties._ID,     // idx 0
                        CalendarContract.ExtendedProperties.NAME,    // idx 1
                        CalendarContract.ExtendedProperties.VALUE    // idx 2
                    ), null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val rawValue = cursor.getString(2)

                        val uri by lazy {
                            ContentUris.withAppendedId(CalendarContract.ExtendedProperties.CONTENT_URI, id).asSyncAdapter(account)
                        }

                        when (cursor.getString(1)) {
                            UnknownProperty.CONTENT_ITEM_TYPE -> {
                                // unknown property; check whether it's a URL
                                try {
                                    val property = UnknownProperty.fromJsonString(rawValue)
                                    if (property is Url) {  // rewrite to MIMETYPE_URL
                                        val newValues = contentValuesOf(
                                            CalendarContract.ExtendedProperties.NAME to AndroidEvent.EXTNAME_URL,
                                            CalendarContract.ExtendedProperties.VALUE to property.value
                                        )
                                        provider.update(uri, newValues, null, null)
                                    }
                                } catch (e: Exception) {
                                    logger.log(
                                        Level.WARNING,
                                        "Couldn't rewrite URL from unknown property to ${AndroidEvent.EXTNAME_URL}",
                                        e
                                    )
                                }
                            }

                            "unknown-property" -> {
                                // unknown property (deprecated format); convert to current format
                                try {
                                    val stream = ByteArrayInputStream(Base64.decode(rawValue, Base64.NO_WRAP))
                                    ObjectInputStream(stream).use {
                                        (it.readObject() as? Property)?.let { property ->
                                            // rewrite to current format
                                            val newValues = contentValuesOf(
                                                CalendarContract.ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                                                CalendarContract.ExtendedProperties.VALUE to UnknownProperty.toJsonString(property)
                                            )
                                            provider.update(uri, newValues, null, null)
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.log(Level.WARNING, "Couldn't rewrite deprecated unknown property to current format", e)
                                }
                            }

                            "unknown-property.v2" -> {
                                // unknown property (deprecated MIME type); rewrite to current MIME type
                                val newValues = contentValuesOf(CalendarContract.ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE)
                                provider.update(uri, newValues, null, null)
                            }
                        }
                    }
                }
            }
        }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(12)
        abstract fun provide(impl: AccountSettingsMigration12): AccountSettingsMigration
    }

}