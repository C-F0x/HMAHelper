package org.cf0x.hma.helper.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.cf0x.hma.helper.R

/**
 * App icon with the app-manager flip interaction: when [flipSelected] is true
 * the icon rotates 180° into a checkmark badge (theme accent). Shared by the
 * app manager, scope settings and preset screens to avoid triplicated
 * icon-loading / flip code.
 */
@Composable
fun AppIconWithFlip(
    packageName: String,
    flipSelected: Boolean,
    size: Dp = 36.dp
) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(packageName) {
        iconBitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()?.toBitmap(48, 48, null)
    }

    val rotation by animateFloatAsState(
        targetValue = if (flipSelected) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "flipRotation"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center
    ) {
        // Front face (visible when rotation < 90°)
        if (rotation < 90f) {
            val icon = iconBitmap
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(size)
                )
            } else {
                Surface(
                    modifier = Modifier.size(size),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(size * 0.5f)
                        )
                    }
                }
            }
        }
        // Back face (visible when rotation >= 90°)
        if (rotation >= 90f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer { rotationY = -rotation },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(size),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.desc_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(size * 0.6f)
                        )
                    }
                }
            }
        }
    }
}
