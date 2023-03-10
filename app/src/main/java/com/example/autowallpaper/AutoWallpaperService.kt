package com.example.autowallpaper

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.example.autowallpaper.helper.GlobalApplication
import com.example.autowallpaper.helper.PrefHelper
import com.example.autowallpaper.helper.getImagePathList
import java.io.File
import java.util.*

object WallpaperData {
    private const val KEY_TIME_INTERVAL = "timeInterval"
    private const val KEY_IMAGE_FOLDER_PATH = "imageFolderPath"
    private const val KEY_CURRENT_INDEX = "currentIndex"
    private const val KEY_RANDOM_CHANGE = "randomChange"

    var timeInterval = 0
    var imageFolderPath = ""
    var isRandom = false
        set(value) {
            field = value
            prefHelper.put(KEY_RANDOM_CHANGE, value)
        }
    var curIndex = 0
    var nextIndex = 0
    private var imagePaths = listOf<String>()
    private val prefHelper by lazy { PrefHelper() }

    fun load() {
        timeInterval = prefHelper.getInt(KEY_TIME_INTERVAL, defValue = 1)
        imageFolderPath = prefHelper.getString(KEY_IMAGE_FOLDER_PATH)
        isRandom = prefHelper.getBoolean(KEY_RANDOM_CHANGE)
        nextIndex = prefHelper.getInt(KEY_CURRENT_INDEX)
        checkFolder()
        refreshNextIndex()
    }

    fun refreshNextIndex() {
        if (imageSize == 0) return
        curIndex = nextIndex
        nextIndex = if (isRandom) {
            (imagePaths.indices).random()
        } else {
            if (nextIndex < imagePaths.size - 1) nextIndex + 1 else 0
        }
        prefHelper.put(KEY_CURRENT_INDEX, curIndex)
    }

    fun checkFolder(): String? {
        if (imagePaths.isNotEmpty() && nextImagePath != null) return null
        imagePaths = File(imageFolderPath).getImagePathList()
        return if (imagePaths.isEmpty()) "文件夹没有图片" else null
    }

    fun update(imageFolderPath: String, timeInterval: Int) {
        if (this.imageFolderPath != imageFolderPath) {
            this.imageFolderPath = imageFolderPath
            prefHelper.put(KEY_IMAGE_FOLDER_PATH, imageFolderPath)
            imagePaths = listOf()
            curIndex = 0
            nextIndex = 0
        }
        if (this.timeInterval != timeInterval) {
            this.timeInterval = timeInterval
            prefHelper.put(KEY_TIME_INTERVAL, timeInterval)
        }
    }

    val nextImagePath: String?
        get() = imagePaths.getOrNull(nextIndex)

    val lockCurIndex: Int
        get() = imageSize - curIndex - 1
    val lockNextIndex: Int
        get() = imageSize - nextIndex - 1

    val curImagePath: String?
        get() = imagePaths.getOrNull(curIndex)

    val lockCurImagePath: String?
        get() = imagePaths.getOrNull(lockCurIndex)

    val lockNextImagePath: String?
        get() = imagePaths.getOrNull(lockNextIndex)

    val imageSize
        get() = imagePaths.size
}

class AutoWallpaperService : Service() {

    companion object {

        private val TAG = AutoWallpaperService::class.java.simpleName

        private const val KEY_CURRENT_IMAGE_INDEX = "currentImageIndex"

        private const val SECOND = 1000L
        private const val MINUTE = 60 * SECOND
    }


    private val handler by lazy { Handler() }
    private val runnable: Runnable by lazy {
        Runnable {
            changeWallpaper()
        }
    }

    private fun changeWallpaper() {
        val date = Calendar.getInstance()
        val hour = date.get(Calendar.HOUR_OF_DAY)
        if (hour < 8) {
            //                date.set(Calendar.HOUR_OF_DAY, 8)
            //                date.set(Calendar.MINUTE, 0)
            //                date.set(Calendar.SECOND, 0)
            //                val delayTime = date.timeInMillis - System.currentTimeMillis()
            handler.postDelayed(runnable, WallpaperData.timeInterval * MINUTE)
            return
        }
        WallpaperData.checkFolder()?.let {
            errorListener?.invoke(it)
            return
        }
        val imagePath = WallpaperData.nextImagePath ?: return
        val lockImagePath = WallpaperData.lockNextImagePath ?: return
        WallpaperData.refreshNextIndex()
        setWallpaper(imagePath, isSystem = true)
        setWallpaper(lockImagePath, isSystem = false)
        wallpaperChangeListener?.invoke()
        handler.postDelayed(runnable, WallpaperData.timeInterval * MINUTE)
    }

    private var wallpaperChangeListener: (() -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null

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
            val flag = if (isSystem) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK
            wpManager.setBitmap(
                bitmap,
                null,
                true,
                flag
            )
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

    private val CHANNEL_ID = "autoWallpaper"
    private val CHANNEL_NAME = "自动更换壁纸"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification =
            Notification.Builder(applicationContext, CHANNEL_ID).build()
        startForeground(1, notification)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return ImageBinder()
    }

    internal inner class ImageBinder : Binder() {

        fun setChangeListener(listener: () -> Unit) {
            wallpaperChangeListener = listener
        }

        fun setErrorListener(listener: (String) -> Unit) {
            errorListener = listener
        }

        fun startChangeWallpaper(
            imageFolderPath: String,
            timeInterval: Int
        ) {
            WallpaperData.update(imageFolderPath, timeInterval)
            handler.removeCallbacks(runnable)
            handler.post(runnable)
        }

        fun forceChange() {
            changeWallpaper()
        }
    }
}
