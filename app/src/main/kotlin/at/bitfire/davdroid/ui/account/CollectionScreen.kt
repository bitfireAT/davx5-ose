/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import dagger.hilt.android.EntryPointAccessors

@Composable
fun CollectionScreen(
    collectionId: Long,
    onFinish: () -> Unit,
    onNavUp: () -> Unit
) {
    val context = LocalContext.current as Activity
    val entryPoint = EntryPointAccessors.fromActivity(context, CollectionActivity.CollectionEntryPoint::class.java)
    val model = viewModel<CollectionScreenModel>(
        factory = CollectionScreenModel.factoryFromCollection(entryPoint.collectionModelAssistedFactory(), collectionId)
    )

    val collectionOrNull by model.collection.collectAsStateWithLifecycle(null)
    if (model.invalid) {
        onFinish()
        return
    }

    val collection = collectionOrNull ?: return
    CollectionScreen(
        title = collection.title(),
        onDelete = model::delete,
        onNavUp = onNavUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    title: String,
    onDelete: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                        }
                    },
                    title = { Text("Collection details TODO") },
                    actions = {
                        IconButton(onClick = onDelete) {
                            // TODO CONFIRMATION!
                            Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_collection))
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                Text(title)
            }
        }
    }
}

@Composable
@Preview
fun CollectionScreenPreview() {
    CollectionScreen(
        title = "Some Calendar"
    )
}