package at.bitfire.davdroid.ui

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.Collator
import java.util.LinkedList
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.collections.iterator

@HiltViewModel
class AboutModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val logger: Logger
): ViewModel() {

    data class Translation(
        val language: String,
        val translators: Set<String>
    )

    val translations = MutableLiveData<List<Translation>>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadTranslations()
        }
    }

    private fun loadTranslations() {
        try {
            context.resources.assets.open("translators.json").use { stream ->
                val jsonTranslations = JSONObject(stream.readBytes().decodeToString())
                val result = LinkedList<Translation>()
                for (langCode in jsonTranslations.keys()) {
                    val jsonTranslators = jsonTranslations.getJSONArray(langCode)
                    val translators = Array<String>(jsonTranslators.length()) { idx ->
                        jsonTranslators.getString(idx)
                    }

                    val langTag = langCode.replace('_', '-')
                    val language = Locale.forLanguageTag(langTag).displayName
                    result += Translation(language, translators.toSet())
                }

                // sort translations by localized language name
                val collator = Collator.getInstance()
                result.sortWith { o1, o2 ->
                    collator.compare(o1.language, o2.language)
                }

                translations.postValue(result)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't load translators", e)
        }
    }

}
