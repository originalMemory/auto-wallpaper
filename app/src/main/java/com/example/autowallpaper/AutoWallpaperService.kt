package com.example.autowallpaper

import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.example.autowallpaper.helper.getImagePathList
import java.io.File


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
            val imagePath = imagePaths[currentImageIndex]
            log("index: $currentImageIndex\timageName:${imagePath.substringAfterLast('/')}")
            setWallpaper(imagePath)
            wallpaperChangeListener?.invoke(currentImageIndex, imagePath)

            currentImageIndex = if (currentImageIndex < imagePaths.size - 1) currentImageIndex + 1 else 0
            handler.postDelayed(runnable, timeInterval * MINUTE)
        }
    }
    private var wallpaperChangeListener: ((Int, String) -> Unit)? = null

    private fun log(info: String) {
        Log.i(TAG, info)
    }

    private fun setWallpaper(imagePath: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            val wpManager = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // On Android N and above use the new API to set both the general system wallpaper and
                // the lock-screen-specific wallpaper
                wpManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM
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
