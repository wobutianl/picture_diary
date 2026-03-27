package com.example.picture_diary

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.picture_diary.databinding.ActivityAboutBinding
import java.io.IOException
import java.io.InputStream

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "关于应用"

        // 读取并显示todo.txt文件内容
        loadTodoContent()

        // 设置关闭按钮点击事件
        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun loadTodoContent() {
        try {
            // 从assets目录中读取todo.txt文件
            val inputStream: InputStream = assets.open("self_res/todo.txt")
            val size: Int = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            
            // 将字节数组转换为字符串
            val content = String(buffer, Charsets.UTF_8)
            
            // 显示内容
            binding.todoContent.text = content
        } catch (e: IOException) {
            e.printStackTrace()
            binding.todoContent.text = "无法加载文件内容：${e.message}"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
