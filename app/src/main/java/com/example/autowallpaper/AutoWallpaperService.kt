package com.example.autowallpaper

import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.example.autowallpaper.helper.Const.KEY_CURRENT_IMAGE_INDEX
import com.example.autowallpaper.helper.Const.KEY_IMAGE_FOLDER_PATH
import com.example.autowallpaper.helper.Const.KEY_TIME_INTERVAL
import com.example.autowallpaper.helper.Const.MINUTE
import com.example.autowallpaper.helper.PrefHelper
import java.io.File

class AutoWallpaperService : Service() {

    companion object {

        private val TAG = AutoWallpaperService::class.java.simpleName
    }

    private var timeInterval = 0
    private var imageFolderPath = ""
    private var currentImageIndex = 0
    private var imagePaths = listOf<String>()

    private val prefHelper by lazy { PrefHelper() }
    private val handler by lazy { Handler() }
    private val runnable: Runnable by lazy {
        Runnable {
            val imagePath = imagePaths[currentImageIndex]
            log("index: $currentImageIndex\timageName:${imagePath.substringAfterLast('/')}")
            setWallpaper(imagePath)
            currentImageIndex = if (currentImageIndex < imagePaths.size - 1) currentImageIndex + 1 else 0
            handler.postDelayed(runnable, timeInterval * MINUTE)
        }
    }

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

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        log("startId: $startId")
        timeInterval = intent.getIntExtra(KEY_TIME_INTERVAL, 0)
        val imageFolderPath = intent.getStringExtra(KEY_IMAGE_FOLDER_PATH) ?: ""
        if (imageFolderPath != this.imageFolderPath) {
            this.imageFolderPath = imageFolderPath
            currentImageIndex = 0
            imagePaths = File(imageFolderPath).listFiles()?.map { it.absolutePath } ?: listOf()
        }
        handler.removeCallbacks(runnable)
        handler.post(runnable)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        prefHelper.put(KEY_CURRENT_IMAGE_INDEX, currentImageIndex)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
