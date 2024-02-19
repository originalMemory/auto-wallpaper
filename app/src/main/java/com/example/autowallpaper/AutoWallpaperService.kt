package com.example.autowallpaper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.autowallpaper.helper.GlobalApplication
import com.example.autowallpaper.helper.ImageInfo
import com.example.autowallpaper.helper.PrefHelper
import com.example.autowallpaper.helper.getImageInfoList
import java.io.File

object WallpaperData {
    private const val KEY_TIME_INTERVAL = "timeInterval"
    private const val KEY_IMAGE_FOLDER_PATH = "imageFolderPath"
    private const val KEY_CURRENT_INDEX = "currentIndex"
    private const val KEY_RANDOM_CHANGE = "randomChange"
    private const val KEY_CHANGE_LOCK = "changeLock"
    private const val KEY_RESIZE_SYSTEM = "resizeSystem"
    private const val KEY_TYPE_FILTER = "typeFilter"
    private const val KEY_LEVEL_FILTER = "levelFilter"

    var timeInterval = 0
    var imageFolderPath = ""
    var isRandom = false
        set(value) {
            field = value
            prefHelper.put(KEY_RANDOM_CHANGE, value)
        }
    var isChangeLock = false
        set(value) {
            field = value
            prefHelper.put(KEY_CHANGE_LOCK, value)
        }
    var isResizeSystem = false
        set(value) {
            field = value
            prefHelper.put(KEY_RESIZE_SYSTEM, value)
        }
    var typeFilter: List<Int> = emptyList()
        set(value) {
            field = value
            prefHelper.put(KEY_TYPE_FILTER, value.joinToString(","))
        }
    var levelFilter: List<Int> = emptyList()
        set(value) {
            field = value
            prefHelper.put(KEY_LEVEL_FILTER, value.joinToString(","))
        }

    private var curIndex = 0
    var nextIndex = 0
    private var totalImages = listOf<ImageInfo>()
    private var filteredImages = listOf<ImageInfo>()
    private val prefHelper by lazy { PrefHelper() }

    fun load() {
        timeInterval = prefHelper.getInt(KEY_TIME_INTERVAL, defValue = 10)
        imageFolderPath = prefHelper.getString(KEY_IMAGE_FOLDER_PATH)
        isRandom = prefHelper.getBoolean(KEY_RANDOM_CHANGE)
        isChangeLock = prefHelper.getBoolean(KEY_CHANGE_LOCK)
        isResizeSystem = prefHelper.getBoolean(KEY_RESIZE_SYSTEM)
        typeFilter =
            prefHelper.getString(KEY_TYPE_FILTER).split(",").mapNotNull { it.toIntOrNull() }
        levelFilter =
            prefHelper.getString(KEY_LEVEL_FILTER).split(",").mapNotNull { it.toIntOrNull() }
        nextIndex = prefHelper.getInt(KEY_CURRENT_INDEX)
        checkFolder()
    }

    fun refreshNextIndex() {
        if (imageSize == 0) return
        curIndex = nextIndex
        nextIndex = if (isRandom) {
            (filteredImages.indices).random()
        } else {
            if (nextIndex < filteredImages.size - 1) nextIndex + 1 else 0
        }
        prefHelper.put(KEY_CURRENT_INDEX, curIndex)
    }

    private fun checkFolder() {
        totalImages = File(imageFolderPath).getImageInfoList()
        updateFilter()
    }

    private fun updateFilter() {
        filteredImages = totalImages.filter {
            (typeFilter.isEmpty() || it.type in typeFilter) &&
                (levelFilter.isEmpty() || it.level in levelFilter)
        }
        refreshNextIndex()
        Log.i(
            "updateFilter",
            "type: $typeFilter, level: $levelFilter, totalSize: ${totalImages.size}, filteredSize: ${filteredImages.size}"
        )
    }

    fun update(
        imageFolderPath: String, timeInterval: Int,
        types: List<Int>,
        levels: List<Int>
    ) {
        val filterChanged = typeFilter != types || levelFilter != levels
        typeFilter = types
        levelFilter = levels
        val pathChanged = this.imageFolderPath != imageFolderPath
        if (pathChanged) {
            this.imageFolderPath = imageFolderPath
            prefHelper.put(KEY_IMAGE_FOLDER_PATH, imageFolderPath)
            totalImages = listOf()
            checkFolder()
            curIndex = 0
            nextIndex = 0
        }
        if (this.timeInterval != timeInterval) {
            this.timeInterval = timeInterval
            prefHelper.put(KEY_TIME_INTERVAL, timeInterval)
        }
        if (filterChanged && !pathChanged) {
            updateFilter()
        }
    }

    val nextImagePath: String?
        get() = filteredImages.getOrNull(nextIndex)?.path

    private val lockCurIndex: Int
        get() = imageSize - curIndex - 1
    val lockNextIndex: Int
        get() = imageSize - nextIndex - 1

    val curImage: ImageInfo?
        get() = filteredImages.getOrNull(curIndex)

    val curImagePath: String?
        get() = curImage?.path

    val lockCurImage: ImageInfo?
        get() = filteredImages.getOrNull(lockCurIndex)

    val lockCurImagePath: String?
        get() = lockCurImage?.path

    val lockNextImagePath: String?
        get() = filteredImages.getOrNull(lockNextIndex)?.path

    val imageSize
        get() = filteredImages.size

    // region 筛选

    // endregion
}

class AutoWallpaperService : Service() {

    companion object {

        private val TAG = AutoWallpaperService::class.java.simpleName

        const val SECOND = 1000L

        //        private const val MINUTE = 60 * SECOND
        private const val CHANNEL_ID = "autoWallpaper"
        private const val CHANNEL_NAME = "自动更换壁纸"
        private const val NOTIFICATION_ID = 1
    }

    private val handler by lazy { Handler() }
    private val runnable: Runnable by lazy {
        Runnable {
            changeWallpaper()
            handler.postDelayed(runnable, WallpaperData.timeInterval * SECOND)
            val nextTimestamp = System.currentTimeMillis() + WallpaperData.timeInterval * SECOND
            nextTimestampListener?.invoke(nextTimestamp)
        }
    }

    private fun changeWallpaper() {
        try {
            val imagePath = WallpaperData.nextImagePath ?: return
            val lockImagePath = WallpaperData.lockNextImagePath ?: return
            WallpaperData.refreshNextIndex()
            setWallpaper(imagePath, isSystem = true)
            setWallpaper(lockImagePath, isSystem = false)
            wallpaperChangeListener?.invoke()
            val notification = createNotification()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "设置壁纸出错：${e.message}")
        }
    }

    private var wallpaperChangeListener: (() -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var nextTimestampListener: ((Long) -> Unit)? = null

    private fun log(info: String) {
        Log.i(TAG, info)
    }

    private fun setWallpaper(imagePath: String, isSystem: Boolean) {
        val wpManager = WallpaperManager.getInstance(this)
        if (!isSystem && !WallpaperData.isChangeLock) return
        var bitmap = BitmapFactory.decodeFile(imagePath)
        if (isSystem && WallpaperData.isResizeSystem) {
//                log("oldWidth: ${bitmap.width}\toldHeight:${bitmap.height}")
            val displayMetrics = GlobalApplication.instance.resources.displayMetrics
            val screenScale = displayMetrics.widthPixels / displayMetrics.heightPixels.toFloat()
            val newWidth = (bitmap.height * screenScale).toInt()
//            log("screenScale: $screenScale")
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
        val flag = if (isSystem) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK
        wpManager.setBitmap(
            bitmap,
            null,
            true,
            flag
        )
    }

    override fun onCreate() {
        log("create")
        super.onCreate()
    }

    override fun onDestroy() {
        log("destroy")
        super.onDestroy()
    }

    private lateinit var manager: NotificationManager
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.setSound(null, null)

        manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val remoteView = RemoteViews(packageName, R.layout.notification)
        val systemImgName = applicationContext.getString(
            R.string.system_img_name,
            WallpaperData.curImage?.title
        )
        remoteView.setTextViewText(R.id.systemImgNameTextView, systemImgName)
        remoteView.setViewVisibility(
            R.id.lockImgNameTextView,
            if (WallpaperData.isChangeLock) View.VISIBLE else View.GONE
        )
        if (WallpaperData.isChangeLock) {
            val lockImgName = applicationContext.getString(
                R.string.lock_img_name,
                WallpaperData.lockCurImage?.title
            )
            remoteView.setTextViewText(R.id.lockImgNameTextView, lockImgName)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(remoteView)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder {
        return ImageBinder()
    }

    internal inner class ImageBinder : Binder() {

        fun setChangeListener(listener: () -> Unit) {
            wallpaperChangeListener = listener
        }

        fun setErrorListener(listener: (String) -> Unit) {
            errorListener = listener
        }

        fun setNextTimestampListener(listener: (Long) -> Unit) {
            nextTimestampListener = listener
        }

        fun startChangeWallpaper(
            imageFolderPath: String,
            timeInterval: Int,
            types: List<Int>,
            levels: List<Int>
        ) {
            WallpaperData.update(imageFolderPath, timeInterval, types, levels)
            directChangeWallpaper()
        }

        fun directChangeWallpaper() {
            handler.removeCallbacks(runnable)
            handler.post(runnable)
        }

        fun forceChange() {
            Thread {
                changeWallpaper()
            }.start()
        }

        fun stop() {
            handler.removeCallbacks(runnable)
            stopForeground(true)
        }
    }
}
