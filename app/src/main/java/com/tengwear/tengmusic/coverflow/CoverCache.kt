package com.tengwear.tengmusic.coverflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tengwear.tengmusic.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 专辑封面位图缓存（专辑流专用）。
 *
 * 根治「循环一圈有封面一圈黑白 / 边缘闪卡」：
 * 此前用 Coil AsyncImage 按需异步加载 —— LazyRow 无 key 按位置复用 + 回环大跳转
 * 会把可见项的 model 瞬间换成另一张专辑的封面，异步请求首帧必然先显示占位背景
 * （灰底音符），表现为闪灰/黑白卡片。透明度怎么锁都没用，因为这是加载中间态。
 *
 * 现在改为：进入专辑流时把所有封面解码进 LruCache（每张缩到约 320px，几十张专辑
 * 只占十几 MB），渲染时直接取同一张 ImageBitmap 同步绘制 —— 无异步、无中间态，
 * 克隆区与真实区、回环前后共享同一张位图，任何滑动/回环都不可能闪卡。
 */
object CoverCache {

    /** 解码目标尺寸（专辑流里封面最大约 200px，320 已超采样，肉眼无损） */
    private const val TARGET_PX = 320

    /** 缓存上限 24MB（按位图字节计），足够几十张专辑 */
    private val lru = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bmp = value.asAndroidBitmap()
            return bmp.byteCount / 1024
        }
    }

    /** 缓存键：自定义封面用 uri，普通专辑用 albumId */
    fun keyOf(album: Album): String =
        if (album.customCoverUri != null) "u:${album.customCoverUri}" else "a:${album.albumId}"

    /** 同步取缓存；未命中返回 null（UI 显示占位并触发后台解码） */
    fun get(album: Album): ImageBitmap? = lru.get(keyOf(album))

    fun put(album: Album, bmp: ImageBitmap) {
        lru.put(keyOf(album), bmp)
    }

    /** 解码一张封面（IO 线程）；无封面 / uri 失效时返回 null */
    suspend fun decode(context: Context, album: Album): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                // 先只读尺寸，算采样率，避免大图整张解码浪费内存
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(album.albumArtUri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                var w = bounds.outWidth
                if (w <= 0) return@withContext null
                while (w / (sample * 2) >= TARGET_PX) sample *= 2
                val bmp: Bitmap = resolver.openInputStream(album.albumArtUri)?.use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                } ?: return@withContext null
                bmp.asImageBitmap()
            } catch (t: Throwable) {
                null
            }
        }

    /**
     * 预解码全部专辑封面（进入专辑流时后台跑一次）。
     * 4 路并行：串行解几十张要 1~3 秒，期间滑动到未命中的卡片会闪占位图；
     * 并行填缓存大幅缩短这个窗口。未命中缓存的才解，二次进入几乎瞬时完成。
     */
    suspend fun preloadAll(context: Context, albums: List<Album>) =
        withContext(Dispatchers.IO) {
            val pending = albums.filter { get(it) == null }
            pending.chunked(4).forEach { batch ->
                coroutineScope {
                    batch.map { a ->
                        async { decode(context, a)?.let { put(a, it) } }
                    }.awaitAll()
                }
            }
        }
}
