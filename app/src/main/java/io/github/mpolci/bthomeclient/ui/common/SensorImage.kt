package io.github.mpolci.bthomeclient.ui.common

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SensorImage(
    uriString: String?,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val context = LocalContext.current
    val targetPixels = with(LocalDensity.current) { size.roundToPx().coerceIn(1, 512) }
    val image = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uriString, targetPixels) {
        value = if (uriString == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = generateSequence(1) { it * 2 }.takeWhile { it * 2 <= maxOf(bounds.outWidth, bounds.outHeight) / targetPixels }.lastOrNull() ?: 1
                context.contentResolver.openInputStream(uri).use { stream ->
                    stream?.let { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.size(size).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).testTag("sensor-image"),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image.value
        if (bitmap == null) {
            Text("No image", modifier = Modifier.testTag("sensor-image-placeholder"))
        } else {
            Image(bitmap, contentDescription = "Sensor image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
