/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.LinkedList
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

@HiltViewModel
class AboutModel @Inject constructor(
    @ApplicationContext val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
): ViewModel() {

    data class LanguageTranslators(
        val language: String,
        val translators: Set<String>
    )

    val transifexTranslators: Flow<List<LanguageTranslators>> = flow {
        val translators = loadTransifexTranslators()
        emit(translators)
    }

    val weblateTranslators: Flow<List<LanguageTranslators>> = flow {
        val translators = loadWeblateTranslators()
        emit(translators)
    }

    @VisibleForTesting
    suspend fun loadWeblateTranslators(): List<LanguageTranslators> = withContext(ioDispatcher) {
        try {
            context.resources.assets.open("weblate-translators.json").use { stream ->
                val jsonTranslations = JSONArray(stream.readBytes().decodeToString())
                val result = LinkedList<LanguageTranslators>()
                for (i in 0 until jsonTranslations.length()) {
                    val jsonObject = jsonTranslations.getJSONObject(i)
                    val language = jsonObject.keys().takeIf { it.hasNext() }?.next() ?: continue
                    val jsonTranslators = jsonObject.getJSONArray(language)
                    val translators = (0 until jsonTranslators.length()).mapNotNull { idx ->
                        val obj = jsonTranslators.getJSONObject(idx)
                        val fullName = obj.getString("full_name")
                        val username = obj.getString("username")

                        if (username == EXCLUDED_USER)
                            return@mapNotNull null

                        "$fullName (@$username)"
                    }.toSet()

                    result += LanguageTranslators(language, translators)
                }

                sortTranslators(result)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't load Weblate translators", e)
            emptyList()
        }
    }

    private suspend fun loadTransifexTranslators(): List<LanguageTranslators> = withContext(ioDispatcher) {
        try {
            context.resources.assets.open("transifex-translators.json").use { stream ->
                val jsonTranslations = JSONObject(stream.readBytes().decodeToString())
                val result = LinkedList<LanguageTranslators>()
                for (langCode in jsonTranslations.keys()) {
                    val jsonTranslators = jsonTranslations.getJSONArray(langCode)
                    val translators = Array<String>(jsonTranslators.length()) { idx ->
                        jsonTranslators.getString(idx)
                    }

                    val langTag = langCode.replace('_', '-')
                    val language = Locale.forLanguageTag(langTag).displayName
                    result += LanguageTranslators(language, translators.toSet())
                }

                sortTranslators(result)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't load Transifex translators", e)
            emptyList()
        }
    }

    /**
     * Sorts the list of language translators by localized language name and returns it.
     *
     * @param translators List of language translators to sort
     * @return Sorted list of language translators
     */
    private fun sortTranslators(translators: LinkedList<LanguageTranslators>): List<LanguageTranslators> {
        // sort translations by localized language name
        val collator = Collator.getInstance()
        translators.sortWith { o1, o2 ->
            collator.compare(o1.language, o2.language)
        }
        return translators
    }


    companion object {
        // Ricki did the migration from Weblate to Transifex, so the user is shown as
        // contributor for many languages. Should be filtered out.
        private const val EXCLUDED_USER = "rfc2822"
    }

}