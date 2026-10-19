package com.dreamct.tingfeng.utilities

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.dreamct.tingfeng.service.TingConfig
import java.lang.ref.WeakReference
import java.lang.Runnable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Loads and displays the app-owned wallpaper behind the whole UI (no card-local sampling). */
object AppWallpaperApplier {

    private const val MAX_BITMAP_EDGE = 1600
    private const val MAX_BITMAP_PIXELS = 1_600_000L

    private data class AssetKey(
        val uri: String,
        val width: Int,
        val height: Int
    )

    private data class WallpaperAsset(
        val key: AssetKey,
        val image: Bitmap
    )

    @Volatile
    private var activeAsset: WallpaperAsset? = null

    @Volatile
    private var activeHost: WeakReference<View>? = null

    private var pendingKey: AssetKey? = null
    private val pendingCallbacks = mutableListOf<Runnable>()
    private var pendingActivity: WeakReference<Activity>? = null
    private var pendingHost: WeakReference<View>? = null

    private val generation = AtomicInteger()
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Applies the selected app wallpaper, or restores the plain theme background when disabled.
     * Image decoding happens off the UI thread. The wallpaper is drawn over an opaque
     * theme-color base (窗口不透明，任何启动/恢复瞬间都有主题背景色兜底).
     */
    @JvmStatic
    @JvmOverloads
    fun applyConfigured(activity: Activity, onApplied: Runnable? = null) {
        val host = wallpaperHost(activity) ?: return
        val uriText = TingConfig.appWallpaperUri(activity)
        if (!TingConfig.appWallpaperEnabled(activity) || uriText.isNullOrBlank()) {
            generation.incrementAndGet()
            pendingKey = null
            pendingCallbacks.clear()
            pendingActivity = null
            pendingHost = null
            activeAsset = null
            activeHost = null
            host.background = ColorDrawable(themeBackground(activity))
            onApplied?.run()
            return
        }

        val appBaseColor = themeBackground(activity)
        val dimensionsReady: () -> Unit = dimensionsReady@{
            // A posted layout callback may outlive a source change made in Settings.
            if (!TingConfig.appWallpaperEnabled(activity) ||
                TingConfig.appWallpaperUri(activity) != uriText
            ) return@dimensionsReady

            val width = host.width.coerceAtLeast(1)
            val height = host.height.coerceAtLeast(1)
            val scale = bitmapScale(width, height)
            val bitmapWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val bitmapHeight = (height * scale).roundToInt().coerceAtLeast(1)
            val key = AssetKey(uriText, bitmapWidth, bitmapHeight)
            val cached = activeAsset?.takeIf { it.key == key }
            if (cached != null) {
                generation.incrementAndGet()
                pendingKey = null
                pendingCallbacks.clear()
                pendingActivity = null
                pendingHost = null
                activeHost = WeakReference(host)
                host.background = WallpaperImageDrawable(cached.image, appBaseColor, TingConfig.wallpaperAlpha(activity))
                onApplied?.run()
            } else if (pendingKey == key) {
                pendingActivity = WeakReference(activity)
                pendingHost = WeakReference(host)
                if (onApplied != null) pendingCallbacks += onApplied
            } else {
                val requestedGeneration = generation.incrementAndGet()
                pendingKey = key
                pendingCallbacks.clear()
                pendingActivity = WeakReference(activity)
                pendingHost = WeakReference(host)
                if (onApplied != null) pendingCallbacks += onApplied
                // Hide the system wallpaper immediately while the selected image is decoded.
                activeAsset = null
                activeHost = null
                host.background = ColorDrawable(appBaseColor)
                worker.execute {
                    val loaded = runCatching {
                        loadAsset(activity.applicationContext, Uri.parse(uriText), key, appBaseColor)
                    }.getOrNull()
                    mainHandler.post {
                        val targetActivity = pendingActivity?.get()
                        val targetHost = pendingHost?.get()
                        if (requestedGeneration != generation.get()) return@post
                        if (targetActivity == null || targetActivity.isFinishing ||
                            targetActivity.isDestroyed || targetHost == null
                        ) {
                            pendingKey = null
                            pendingCallbacks.clear()
                            pendingActivity = null
                            pendingHost = null
                            return@post
                        }
                        pendingKey = null
                        val callbacks = pendingCallbacks.toList()
                        pendingCallbacks.clear()
                        pendingActivity = null
                        pendingHost = null
                        if (loaded == null) {
                            activeAsset = null
                            activeHost = null
                            TingConfig.setAppWallpaperEnabled(targetActivity, false)
                            targetHost.background = ColorDrawable(themeBackground(targetActivity))
                        } else {
                            activeAsset = loaded
                            activeHost = WeakReference(targetHost)
                            targetHost.background = WallpaperImageDrawable(
                                loaded.image,
                                themeBackground(targetActivity),
                                TingConfig.wallpaperAlpha(targetActivity)
                            )
                        }
                        callbacks.forEach { it.run() }
                    }
                }
            }
        }

        if (host.width > 0 && host.height > 0) dimensionsReady()
        else host.post { dimensionsReady() }
    }

    /** Loads a small thumbnail instead of letting ImageView decode the full-size source. */
    fun loadPreview(activity: Activity, uri: Uri, imageView: ImageView) {
        val resolver = activity.applicationContext.contentResolver
        val expectedTag = uri.toString()
        imageView.tag = expectedTag
        imageView.setPadding(0, 0, 0, 0)
        imageView.imageTintList = null
        worker.execute {
            val thumbnail = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream)
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                resolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream)
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }.getOrNull()
            mainHandler.post {
                if (imageView.tag == expectedTag && thumbnail != null) {
                    imageView.setImageBitmap(thumbnail)
                }
            }
        }
    }

    private fun wallpaperHost(activity: Activity): ViewGroup? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return content.getChildAt(0) as? ViewGroup
    }

    private fun loadAsset(
        context: Context,
        uri: Uri,
        key: AssetKey,
        baseColor: Int
    ): WallpaperAsset {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Cannot open selected wallpaper" }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported wallpaper image" }

        val targetAspect = key.width.toFloat() / key.height
        val sourceAspect = bounds.outWidth.toFloat() / bounds.outHeight
        val crop = if (sourceAspect > targetAspect) {
            val cropWidth = (bounds.outHeight * targetAspect).roundToInt().coerceIn(1, bounds.outWidth)
            val left = (bounds.outWidth - cropWidth) / 2
            Rect(left, 0, left + cropWidth, bounds.outHeight)
        } else {
            val cropHeight = (bounds.outWidth / targetAspect).roundToInt().coerceIn(1, bounds.outHeight)
            val top = (bounds.outHeight - cropHeight) / 2
            Rect(0, top, bounds.outWidth, top + cropHeight)
        }
        val decoded = decodeCroppedWallpaper(context, uri, crop, key)

        val fitted = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
        Canvas(fitted).apply {
            drawColor(baseColor)
            val sourceRect = RectF(0f, 0f, decoded.width.toFloat(), decoded.height.toFloat())
            val targetRect = RectF(0f, 0f, key.width.toFloat(), key.height.toFloat())
            val scale = max(targetRect.width() / sourceRect.width(), targetRect.height() / sourceRect.height())
            val drawWidth = sourceRect.width() * scale
            val drawHeight = sourceRect.height() * scale
            val destination = RectF(
                (key.width - drawWidth) / 2f,
                (key.height - drawHeight) / 2f,
                (key.width + drawWidth) / 2f,
                (key.height + drawHeight) / 2f
            )
            drawBitmap(decoded, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (decoded !== fitted && !decoded.isRecycled) decoded.recycle()
        return WallpaperAsset(key, fitted)
    }

    private fun decodeCroppedWallpaper(
        context: Context,
        uri: Uri,
        crop: Rect,
        key: AssetKey
    ): Bitmap {
        val regionBitmap = runCatching {
            context.contentResolver.openInputStream(uri).use { stream ->
                val decoder = requireNotNull(
                    BitmapRegionDecoder.newInstance(
                        requireNotNull(stream) { "Cannot open selected wallpaper" },
                        false
                    )
                ) { "Cannot decode selected wallpaper" }
                try {
                    var sample = 1
                    while (crop.width() / (sample * 2) >= key.width &&
                        crop.height() / (sample * 2) >= key.height
                    ) sample *= 2
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    decoder.decodeRegion(crop, options)
                } finally {
                    decoder.recycle()
                }
            }
        }.getOrNull()
        if (regionBitmap != null) return regionBitmap

        // Fallback for image providers/formats that do not support region decoding.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Cannot open selected wallpaper" }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        var sample = 1
        val maxTargetEdge = max(key.width, key.height)
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) > maxTargetEdge * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Cannot open selected wallpaper" }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Unsupported wallpaper image")
    }

    /** 清晰壁纸相对屏幕的缩放比（≤1，受边长/像素数上限约束） */
    private fun bitmapScale(width: Int, height: Int): Float = min(
        1f,
        min(MAX_BITMAP_EDGE.toFloat() / max(width, height),
            kotlin.math.sqrt(MAX_BITMAP_PIXELS.toFloat() / (width.toFloat() * height)))
    )

    private fun themeBackground(context: Context): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            value.data
        } else Color.BLACK
    }

    private class WallpaperImageDrawable(
        private val bitmap: Bitmap,
        private val baseColor: Int,
        private val opacity: Float
    ) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            canvas.drawColor(baseColor)
            paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
            canvas.drawBitmap(bitmap, null, bounds, paint)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
