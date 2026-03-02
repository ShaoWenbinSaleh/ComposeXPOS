package com.cofopt.orderingmachine

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.cofopt.orderingmachine.network.SyncedMenuImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ImageCache {
    internal val cache = ConcurrentHashMap<String, ImageBitmap>()
    internal val cacheByDrawableName = ConcurrentHashMap<String, ImageBitmap>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val preloadStartedDirs = ConcurrentHashMap<String, Boolean>()
    private const val RASTER_MAX_DIMENSION = 1024

    /**
     * 预加载指定目录下的所有图片
     */
    suspend fun preloadImages(context: Context, directory: String = "images/menu") {
        withContext(Dispatchers.IO) {
            try {
                val prefix = directory
                    .removePrefix("images/")
                    .trim('/')
                    .lowercase()
                    .replace(Regex("[^a-z0-9_]+"), "_")
                    .replace(Regex("_+"), "_")
                    .trim('_')

                val fields = R.drawable::class.java.fields
                var loadedCount = 0
                for (f in fields) {
                    val name = f.name
                    if (name == prefix || name.startsWith("${prefix}_")) {
                        if (!cacheByDrawableName.containsKey(name)) {
                            val resId = runCatching { f.getInt(null) }.getOrNull() ?: 0
                            if (resId != 0) {
                                decodeRasterDrawableResId(context, resId)?.let { bmp ->
                                    cacheByDrawableName[name] = bmp
                                    loadedCount++
                                    // Add small delay every 10 images to prevent memory pressure
                                    if (loadedCount % 10 == 0) {
                                        kotlinx.coroutines.delay(50)
                                    }
                                }
                            }
                        }
                    }
                }
                android.util.Log.d("ImageCache", "Preloaded $loadedCount images from $directory")
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("ImageCache", "OOM during preload of $directory", e)
                System.gc()
            } catch (e: Exception) {
                android.util.Log.e("ImageCache", "Error during preload of $directory", e)
            }
        }
    }

    fun startPreload(context: Context, directory: String = "images/menu") {
        if (preloadStartedDirs.putIfAbsent(directory, true) != null) return
        val appContext = context.applicationContext
        scope.launch {
            preloadImages(appContext, directory)
        }
    }

    suspend fun loadImage(context: Context, assetPath: String): ImageBitmap? {
        cache[assetPath]?.let { return it }

        val isSyncedMenuImage = assetPath.startsWith("menu_sync/")
        val drawableName = assetPathToDrawableName(assetPath)
        if (!isSyncedMenuImage) {
            cacheByDrawableName[drawableName]?.let {
                cache[assetPath] = it
                return it
            }
        }

        inFlight[assetPath]?.let { existing ->
            return runCatching { existing.await() }.getOrNull()
        }

        val appContext = context.applicationContext
        val created = scope.async {
            try {
                loadImageInternal(appContext, assetPath)
            } catch (_: Exception) {
                null
            }
        }

        val active = inFlight.putIfAbsent(assetPath, created) ?: created
        if (active !== created) {
            created.cancel()
        }

        return try {
            active.await()
        } finally {
            inFlight.remove(assetPath, active)
        }
    }

    suspend fun loadImageSync(context: Context, assetPath: String): ImageBitmap? {
        cache[assetPath]?.let { return it }

        val isSyncedMenuImage = assetPath.startsWith("menu_sync/")
        val drawableName = assetPathToDrawableName(assetPath)
        if (!isSyncedMenuImage) {
            cacheByDrawableName[drawableName]?.let {
                cache[assetPath] = it
                return it
            }
        }
        return try {
            loadImageInternal(context, assetPath)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadImageInternal(context: Context, assetPath: String): ImageBitmap? {
        cache[assetPath]?.let { return it }
        if (assetPath.startsWith("menu_sync/")) {
            val bitmap = decodeSyncedMenuImage(context, assetPath)
            if (bitmap != null) {
                cache[assetPath] = bitmap
            }
            return bitmap
        }
        val drawableName = assetPathToDrawableName(assetPath)
        cacheByDrawableName[drawableName]?.let {
            cache[assetPath] = it
            return it
        }
        val bitmap = decodeRasterDrawable(context, assetPath)

        if (bitmap != null) {
            cache[assetPath] = bitmap
            cacheByDrawableName[drawableName] = bitmap
        }
        return bitmap
    }

    private fun decodeSyncedMenuImage(context: Context, relativePath: String): ImageBitmap? {
        val cleanPath = relativePath.substringBefore('?').substringBefore('#')
        val file = SyncedMenuImageStore.resolveFile(context, cleanPath)
        if (!file.exists()) return null

        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            val w = boundsOptions.outWidth
            val h = boundsOptions.outHeight
            if (w <= 0 || h <= 0) return null

            var sampleSize = 1
            while (maxOf(w, h) / sampleSize > RASTER_MAX_DIMENSION) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                inSampleSize = sampleSize
                inJustDecodeBounds = false
                inDither = false
                inScaled = false
            }
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    @DrawableRes
    fun drawableResIdForPath(context: Context, assetPath: String): Int {
        val name = assetPathToDrawableName(assetPath)
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    internal fun assetPathToDrawableName(assetPath: String): String {
        val noQuery = assetPath.substringBefore('?').substringBefore('#')
        val withoutPrefix = noQuery.removePrefix("images/")
        val noExt = withoutPrefix.substringBeforeLast('.', withoutPrefix)
        val normalized = noExt
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (normalized.firstOrNull()?.isDigit() == true) {
            "img_$normalized"
        } else {
            normalized
        }
    }

    private fun decodeRasterDrawable(context: Context, assetPath: String): ImageBitmap? {
        val resId = drawableResIdForPath(context, assetPath)
        if (resId == 0) return null

        return decodeRasterDrawableResId(context, resId)
    }

    private fun decodeRasterDrawableResId(context: Context, @DrawableRes resId: Int): ImageBitmap? {
        if (resId == 0) return null

        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, resId, boundsOptions)

            val w = boundsOptions.outWidth
            val h = boundsOptions.outHeight
            if (w <= 0 || h <= 0) return null

            var sampleSize = 1
            var maxDim = maxOf(w, h)
            while (maxDim / sampleSize > RASTER_MAX_DIMENSION) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                // Use RGB_565 instead of ARGB_8888 to reduce memory by 50%
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                inSampleSize = sampleSize
                inJustDecodeBounds = false
                inDither = false
                inScaled = false
            }
            BitmapFactory.decodeResource(context.resources, resId, options)?.asImageBitmap()
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageCache", "OOM while decoding resId=$resId", e)
            System.gc() // Suggest garbage collection
            null
        } catch (e: Exception) {
            android.util.Log.e("ImageCache", "Error decoding resId=$resId", e)
            null
        }
    }

    fun clear() {
        cache.clear()
        cacheByDrawableName.clear()
    }
}

@Composable
actual fun CachedAssetImage(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    
    // 同步加载，使用 remember 缓存
    var bitmap by remember(assetPath) {
        val drawableName = ImageCache.assetPathToDrawableName(assetPath)
        mutableStateOf(ImageCache.cache[assetPath] ?: ImageCache.cacheByDrawableName[drawableName])
    }
    LaunchedEffect(assetPath) {
        if (bitmap == null) {
            bitmap = ImageCache.loadImage(context, assetPath)
        }
    }
    
    val b = bitmap
    val alpha by animateFloatAsState(
        targetValue = if (b != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "assetImageAlpha"
    )

    Box(modifier = modifier) {
        val placeholderAlpha = 1f - alpha
        if (placeholderAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(placeholderAlpha)
                    .background(Color(0xFFE0E0E0))
            )
        }
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize().alpha(alpha),
                contentScale = contentScale
            )
        }
    }
}

@Composable
fun AnimatedAssetImage(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    //TODO: AnimatedAssetImage
    CachedAssetImage(
        assetPath = assetPath,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
