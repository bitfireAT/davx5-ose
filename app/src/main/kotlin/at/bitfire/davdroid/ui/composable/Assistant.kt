package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
        Column(Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
            .weight(1f)) {
            content()
        }

        Surface(Modifier
            .fillMaxWidth()
            .imePadding()) {
            if (nextLabel != null)
                TextButton(
                    enabled = nextEnabled,
                    onClick = onNext,
                    modifier = Modifier
                        .wrapContentSize(Alignment.CenterEnd)
                ) {
                    Text(nextLabel.uppercase())
                }
        }
    }
}

@Composable
@Preview
fun Assistant_Preview() {
    Assistant(nextLabel = "Next") {
        Text("Some Content")
    }
}