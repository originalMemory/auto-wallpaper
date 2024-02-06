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

class MainActivity : AppCompatActivity() {

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
            imageBinder?.setCountDownListener {
                if (!isForeground) return@setCountDownListener
                val text = "$it 秒"
                runOnUiThread {
                    countdownTextView.text = text
                }
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
        WallpaperData.curImagePath?.let {
            val fileName = it.substringAfterLast('/')
            systemCurIndexTextView.text =
                "[${WallpaperData.curIndex + 1}/${WallpaperData.imageSize}]\n$fileName"
            Glide.with(this).load(it).into(systemCurImageView)
        }

        WallpaperData.nextImagePath?.let {
            val fileName = it.substringAfterLast('/')
            systemNextIndexTextView.text =
                "[${WallpaperData.nextIndex + 1}/${WallpaperData.imageSize}]\n$fileName"
            Glide.with(this).load(it).into(systemNexImageView)
        }

        WallpaperData.lockCurImagePath?.let {
            val fileName = it.substringAfterLast('/')
            lockCurIndexTextView.text =
                "[${WallpaperData.lockCurIndex + 1}/${WallpaperData.imageSize}]\n$fileName"
            Glide.with(this).load(it).into(lockCurImageView)
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
            val imageFolderPath = imageFolderPathTextView.text.toString()

            val timeInterval = timeIntervalEditText.text.toString().toInt()
            if (timeInterval == 0) {
                toast("时间不能为 0 ")
                return@setOnClickListener
            }
            imageBinder?.startChangeWallpaper(imageFolderPath, timeInterval)
            timeIntervalEditText.clearFocus()
        }
        stopButton.setOnClickListener {
            val intent = Intent(this, AutoWallpaperService::class.java)
            imageBinder?.stop()
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

    private fun toast(content: String) {
        Toast.makeText(this, content, Toast.LENGTH_SHORT).show()
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
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
    }
}
