package com.example.autowallpaper

import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.example.autowallpaper.helper.GlobalApplication
import com.example.autowallpaper.helper.getImagePathList
import java.io.File
import java.util.*


class AutoWallpaperService : Service() {

    companion object {

        private val TAG = AutoWallpaperService::class.java.simpleName

        private const val SECOND = 1000L
        private const val MINUTE = 60 * SECOND
    }

    private var timeInterval = 0
    private var imageFolderPath = ""
    private var currentImageIndex = 0
    private var imagePaths = listOf<String>()

    private val handler by lazy { Handler() }
    private val runnable: Runnable by lazy {
        Runnable {
            val date = Calendar.getInstance()
            val hour = date.get(Calendar.HOUR_OF_DAY)
            if (hour < 8) {
                date.set(Calendar.HOUR_OF_DAY, 8)
                date.set(Calendar.MINUTE, 0)
                date.set(Calendar.SECOND, 0)
                val delayTime = date.timeInMillis - System.currentTimeMillis()
                handler.postDelayed(runnable, delayTime)
                return@Runnable
            }
            val imagePath = imagePaths[currentImageIndex]
            log("index: $currentImageIndex\timageName:${imagePath.substringAfterLast('/')}")
            setWallpaper(imagePath, true)
            setWallpaper(imagePaths[imagePaths.size - currentImageIndex - 1], false)
            wallpaperChangeListener?.invoke(currentImageIndex, imagePath)

            currentImageIndex = if (currentImageIndex < imagePaths.size - 1) currentImageIndex + 1 else 0
            handler.postDelayed(runnable, timeInterval * MINUTE)
        }
    }
    private var wallpaperChangeListener: ((Int, String) -> Unit)? = null

    private fun log(info: String) {
        Log.i(TAG, info)
    }

    private fun setWallpaper(imagePath: String, isSystem: Boolean) {
        try {
            var bitmap = BitmapFactory.decodeFile(imagePath)
            if (!isSystem) {
                log("oldWidth: ${bitmap.width}\toldHeight:${bitmap.height}")
                val displayMetrics = GlobalApplication.instance.resources.displayMetrics
                val screenScale = displayMetrics.widthPixels / displayMetrics.heightPixels.toFloat()
                val newWidth = (bitmap.height * screenScale).toInt()
                // 只裁剪
                when {
                    // 裁剪宽度
                    newWidth < bitmap.width -> {
                        val startX = (bitmap.width - newWidth) / 2
                        bitmap = Bitmap.createBitmap(bitmap, startX, 0, newWidth, bitmap.height)
                        log("newWidth: $newWidth\tnewHeight:${bitmap.height}")
                    }
                    // 裁剪高度
                    newWidth > bitmap.width -> {
                        val newHeight = (bitmap.width / screenScale).toInt()
                        val startY = (bitmap.height - newHeight) / 2
                        bitmap = Bitmap.createBitmap(bitmap, 0, startY, bitmap.width, newHeight)
                        log("newWidth: ${bitmap.width}\tnewHeight:$newHeight")
                    }
                    else -> {
                        log("newWidth: ${bitmap.width}\tnewHeight:${bitmap.height}")
                    }
                }
            }
            val wpManager = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // On Android N and above use the new API to set both the general system wallpaper and
                // the lock-screen-specific wallpaper
                val flag = if (isSystem) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK
                wpManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    flag
                )
            } else {
                wpManager.setBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e("wallpaper", e.message.toString())
        }
    }

    override fun onCreate() {
        log("create")
        super.onCreate()
    }

    override fun onDestroy() {
        log("destroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return ImageBinder()
    }

    internal inner class ImageBinder : Binder() {

        var listener: ((Int, String) -> Unit)? = null
            set(value) {
                field = value
                wallpaperChangeListener = value
            }

        fun startChangeWallpaper(imageFolderPath: String, timeInterval: Int, currentImageIndex: Int) {
            this@AutoWallpaperService.timeInterval = timeInterval
            this@AutoWallpaperService.imageFolderPath = imageFolderPath
            this@AutoWallpaperService.currentImageIndex = currentImageIndex
            imagePaths = File(imageFolderPath).getImagePathList()
            handler.removeCallbacks(runnable)
            handler.post(runnable)
        }
    }
}
