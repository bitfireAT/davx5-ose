package at.bitfire.davdroid.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebcalSubscription
import at.bitfire.davdroid.util.DavUtils
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

@AndroidEntryPoint
class WebcalSubscriptionsActivity: ComponentActivity() {

    val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = null)
                                }             
                            },
                            title = {
                                Text(stringResource(R.string.webcal_subscriptions))
                            }
                        )
                    }
                ) { paddingValues ->
                    Column(Modifier.padding(paddingValues)) {
                        model.subscriptions.observeAsState().value?.forEach { subscription ->
                            SubscriptionItem(subscription)
                        }
                    }
                }
            }
        }
    }

    
    @Composable
    fun SubscriptionItem(subscription: WebcalSubscription) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
            Row {
                Box(Modifier
                    .background(Color(subscription.color))
                    .align(Alignment.CenterVertically)
                    .height(16.dp)
                    .width(16.dp))
                Text(
                    subscription.displayName ?: DavUtils.lastSegmentOfUrl(subscription.url),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            SelectionContainer {
                Text(
                    subscription.url.toString(),
                    fontFamily = FontFamily.Monospace
                )
            }

            if (subscription.collectionId != null)
                Row {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null)
                    Text("Account Name", modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 8.dp))
                }
        }
    }

    @Composable
    @Preview
    fun SubscriptionItem_Sample() {
        SubscriptionItem(WebcalSubscription(
            id = 0,
            collectionId = 1,
            calendarId = null,
            "https://example.com".toHttpUrl(),
            displayName = "Sample Calendar",
            color = Constants.DAVDROID_GREEN_RGBA
        ))
    }


    @HiltViewModel
    class Model @Inject constructor(
        db: AppDatabase
    ): ViewModel() {

        val subscriptions = db.webcalSubscriptionDao().getAllLive()

    }

}