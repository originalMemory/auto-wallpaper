package com.example.autowallpaper

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.autowallpaper.helper.hideSoftKeyboard
import kotlinx.android.synthetic.main.activity_main.*
import me.rosuh.filepicker.bean.FileItemBeanImpl
import me.rosuh.filepicker.config.AbstractFileFilter
import me.rosuh.filepicker.config.FilePickerManager
import java.util.Timer
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity() {

    private var timer: Timer? = null
    private var nextTimestamp: Long = 0
    private var imageBinder: AutoWallpaperService.ImageBinder? = null
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            imageBinder = service as? AutoWallpaperService.ImageBinder
            imageBinder?.setChangeListener {
                if (!isForeground) return@setChangeListener
                runOnUiThread {
                    renderImage()
                }
            }
            imageBinder?.setErrorListener {
                toast(it)
            }
            imageBinder?.setNextTimestampListener {
                nextTimestamp = it
                if (isForeground) startTimer()
            }
            if (WallpaperData.imageSize > 0) {
                imageBinder?.directChangeWallpaper()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            imageBinder = null
        }
    }
    private var isForeground = false

    @SuppressLint("SetTextI18n")
    private fun renderImage() {
        WallpaperData.nextImagePath?.let {
            val fileName = it.substringAfterLast('/')
            systemNextIndexTextView.text =
                "[${WallpaperData.nextIndex + 1}/${WallpaperData.imageSize}]\n$fileName"
            Glide.with(this).load(it).into(systemNexImageView)
        }

        WallpaperData.lockNextImagePath?.let {
            val fileName = it.substringAfterLast('/')
            lockNextIndexTextView.text =
                "[${WallpaperData.lockNextIndex + 1}/${WallpaperData.imageSize}]\n$fileName"
            Glide.with(this).load(it).into(lockNexImageView)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadConfig()
        setupInterface()
        if (WallpaperData.nextImagePath != null) {
            val intent = Intent(this, AutoWallpaperService::class.java)
            startForegroundService(intent)
        }
        Log.i("testWu", "更新壁纸")
//        WallpaperManager.getInstance(this).setResource(R.raw.default_bg)
    }

    private fun loadConfig() {
        WallpaperData.load()
        imageFolderPathTextView.text = WallpaperData.imageFolderPath.ifEmpty { "未选择文件夹" }
        timeIntervalEditText.setText(WallpaperData.timeInterval.toString())
        val defCountdown = "${WallpaperData.timeInterval} 秒"
        countdownTextView.text = defCountdown
        for (type in WallpaperData.typeFilter) {
            when (type) {
                1 -> type1CheckBox.isChecked = true
                2 -> type2CheckBox.isChecked = true
                3 -> type3CheckBox.isChecked = true
            }
        }
        for (level in WallpaperData.levelFilter) {
            when (level) {
                1 -> level1CheckBox.isChecked = true
                2 -> level2CheckBox.isChecked = true
                3 -> level3CheckBox.isChecked = true
                4 -> level4CheckBox.isChecked = true
                5 -> level5CheckBox.isChecked = true
                6 -> level6CheckBox.isChecked = true
                7 -> level7CheckBox.isChecked = true
                8 -> level8CheckBox.isChecked = true
            }
        }
        renderImage()
    }

    private fun bindAutoWallpaperService() {
        val intent = Intent(this, AutoWallpaperService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupInterface() {
        imageFolderPathTextView.setOnClickListener {
            val filter = object : AbstractFileFilter() {

                override fun doFilter(listData: ArrayList<FileItemBeanImpl>): ArrayList<FileItemBeanImpl> {
                    return ArrayList(listData.filter {
                        it.isDir
                    })
                }
            }
            FilePickerManager
                .from(this)
                .maxSelectable(1)
                .filter(filter)
                .skipDirWhenSelect(false)
                .setTheme(R.style.FilePickerThemeCrane)
                .forResult(FilePickerManager.REQUEST_CODE)
        }
        container.setOnTouchListener { _, _ ->
            this.hideSoftKeyboard(timeIntervalEditText)
            container.requestFocus()
            true
        }
        saveButton.setOnClickListener {
            saveConfig()
        }
        stopButton.setOnClickListener {
            val intent = Intent(this, AutoWallpaperService::class.java)
            imageBinder?.stop()
            stopTimer()
            stopService(intent)
        }
        nextButton.setOnClickListener {
            imageBinder?.forceChange()
        }
        randomCheckBox.isChecked = WallpaperData.isRandom
        randomCheckBox.setOnClickListener {
            WallpaperData.isRandom = randomCheckBox.isChecked
        }
        changeLockCheckBox.isChecked = WallpaperData.isChangeLock
        changeLockCheckBox.setOnClickListener {
            WallpaperData.isChangeLock = changeLockCheckBox.isChecked
        }
        resizeSystemCheckBox.isChecked = WallpaperData.isResizeSystem
        resizeSystemCheckBox.setOnClickListener {
            WallpaperData.isResizeSystem = resizeSystemCheckBox.isChecked
        }
    }

    private fun saveConfig() {
        val imageFolderPath = imageFolderPathTextView.text.toString()

        val timeInterval = timeIntervalEditText.text.toString().toInt()
        if (timeInterval == 0) {
            toast("时间不能为 0 ")
            return
        }
        val types = mutableListOf<Int>()
        if (type1CheckBox.isChecked) types.add(1)
        if (type2CheckBox.isChecked) types.add(2)
        if (type3CheckBox.isChecked) types.add(3)
        val levels = mutableListOf<Int>()
        if (level1CheckBox.isChecked) levels.add(1)
        if (level2CheckBox.isChecked) levels.add(2)
        if (level3CheckBox.isChecked) levels.add(3)
        if (level4CheckBox.isChecked) levels.add(4)
        if (level5CheckBox.isChecked) levels.add(5)
        if (level6CheckBox.isChecked) levels.add(6)
        if (level7CheckBox.isChecked) levels.add(7)
        if (level8CheckBox.isChecked) levels.add(8)

        imageBinder?.startChangeWallpaper(imageFolderPath, timeInterval, types, levels)
        timeIntervalEditText.clearFocus()
    }

    private fun toast(content: String) {
        Toast.makeText(this, content, Toast.LENGTH_SHORT).show()
    }

    private fun startTimer() {
        timer?.cancel()
        val newTimer = Timer()
        newTimer.scheduleAtFixedRate(
            timerTask {
                if (nextTimestamp == 0.toLong()) {
                    stopTimer()
                    return@timerTask
                }
                val interval =
                    (nextTimestamp - System.currentTimeMillis()) / AutoWallpaperService.SECOND
                if (interval < 0) {
                    stopTimer()
                    return@timerTask
                }
                val text = "$interval 秒"
                runOnUiThread {
                    countdownTextView.text = text
                }
            },
            0,
            AutoWallpaperService.SECOND
        )
        timer = newTimer
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        val text = "${WallpaperData.timeInterval} 秒"
        runOnUiThread {
            countdownTextView.text = text
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FilePickerManager.REQUEST_CODE && resultCode == RESULT_OK) {
            val list = FilePickerManager.obtainData()
            list.firstOrNull()?.let {
                imageFolderPathTextView.text = it
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStart() {
        super.onStart()
        if (imageBinder == null) {
            bindAutoWallpaperService()
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        imageBinder?.let {
            renderImage()
        }
        startTimer()
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
        stopTimer()
    }
}
