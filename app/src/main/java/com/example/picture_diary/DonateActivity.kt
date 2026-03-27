package com.example.picture_diary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.picture_diary.databinding.ActivityDonateBinding

class DonateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDonateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDonateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "打赏支持"

        // 从assets目录加载微信二维码图片
        try {
            val inputStream = assets.open("self_res/wechat.png")
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            binding.qrCode.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: Exception) {
            android.util.Log.e("DonateActivity", "Error loading wechat.png", e)
        }

        // 设置关闭按钮点击事件
        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
