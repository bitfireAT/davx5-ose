/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Assistant(
    nextLabel: String? = null,
    nextEnabled: Boolean = true,
    onNext: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .weight(1f)) {
            content()
        }

        BottomAppBar(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            actions = {
                if (nextLabel != null)
                    Button(
                        enabled = nextEnabled,
                        onClick = onNext,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .wrapContentSize(Alignment.CenterEnd)
                    ) {
                        Text(nextLabel)
                    }
            }
        )
    }
}

@Composable
@Preview
fun Assistant_Preview() {
    Assistant(nextLabel = "Next") {
        Text("Some Content")
    }
}