package at.bitfire.davdroid.ui.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.ui.AppTheme

@Composable
fun ButtonWithIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.tertiary,
    onClick: () -> Unit
) {
    Surface(
        color = color,
        contentColor = contentColorFor(backgroundColor = color),
        modifier = modifier
            .size(size)
            .aspectRatio(1f),
        onClick = onClick,
        shape = CircleShape
    ) {
        AnimatedContent(
            targetState = icon,
            label = "Button Icon"
        ) {
            Icon(
                imageVector = it,
                contentDescription = contentDescription,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Preview
@Composable
fun ButtonWithIcon_Preview() {
    AppTheme {
        ButtonWithIcon(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null
        ) { }
    }
}
