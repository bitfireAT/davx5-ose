/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.about

import android.content.Context
import android.icu.text.ListFormatter
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.text.Collator
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalSerializationApi::class)
class AboutModel @Inject constructor(
    @ApplicationContext val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
): ViewModel() {

    @Serializable
    data class WeblateTranslator(
        val email: String,
        val username: String,
        val full_name: String
    )

    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        packageInfo.longVersionCode
    else
        packageInfo.versionCode.toLong()
    val versionName = packageInfo.versionName ?: versionCode.toString()

    private val collator = Collator.getInstance()
    private val json = Json { ignoreUnknownKeys = true }

    val transifexTranslators = flow {
        val translators = loadTransifexTranslators().sortedWith(collator)
        emit(formatList(translators))
    }.flowOn(ioDispatcher)

    val weblateTranslators = flow {
        val translators = loadWeblateTranslators().sortedWith(collator)
        emit(formatList(translators))
    }.flowOn(ioDispatcher)


    @VisibleForTesting
    internal fun loadTransifexTranslators(): Set<String> =
        try {
            context.resources.assets.open("transifex-translators.json").use { stream ->
                // format:   {"de_AT":["userA","userB"], ...}
                val langTranslators = json.decodeFromStream<Map<String, List<String>>>(stream)

                // ignore languages and put user names into set
                langTranslators.values.flatten().toSet()
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't load Transifex translators", e)
            emptySet()
        }

    @VisibleForTesting
    internal fun loadWeblateTranslators(): Set<String> =
        try {
            context.resources.assets.open("weblate-translators.json").use { stream ->
                val langTranslators = json.decodeFromStream<List<Map<String, List<WeblateTranslator>>>>(stream)
                langTranslators.map { languageAndTranslators ->
                    languageAndTranslators.values.map { translators ->
                        // Ricki did the migration from Weblate to Transifex, so the user is shown as
                        // contributor for many languages. Should be filtered out.
                        translators
                            .filter { it.email != "hirner@bitfire.at" }
                            .map { it.username }
                    }.flatten()
                }.flatten().toSet()
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't load Weblate translators", e)
            emptySet()
        }


    // helper to format lists in a localized way

    private val listFormatter: ListFormatter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ListFormatter.getInstance()
    } else
        null

    private fun formatList(list: List<String>): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && listFormatter != null)
            listFormatter.format(list)
        else
            list.joinToString(", ")

}