package com.example.picture_diary

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.picture_diary.databinding.ActivityCalendarBinding

class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var dbHelper: DatabaseHelper
    private var listId: Long = 0
    private var listName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传递过来的 List ID 和名称
        listId = intent.getLongExtra("LIST_ID", 0)
        listName = intent.getStringExtra("LIST_NAME")
        title = "${listName} - 日历"

        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 初始化数据库帮助类
        dbHelper = DatabaseHelper(this)

        // 初始化RecyclerView
        val calendarAdapter = CalendarAdapter()
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(this, 7) // 7列日历
        binding.calendarRecyclerView.adapter = calendarAdapter
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // 日历适配器
    inner class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

        // 模拟日历数据
        private val days = (1..31).toList()

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CalendarViewHolder {
            val view = layoutInflater.inflate(R.layout.item_calendar_day, parent, false)
            return CalendarViewHolder(view)
        }

        override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
            val day = days[position]
            holder.dayTextView.text = day.toString()

            // 这里可以添加逻辑来检查当天是否有照片
            // 如果有照片，可以显示一个指示器

            holder.itemView.setOnClickListener {
                // 点击日期时，可以显示当天的照片
                val builder = android.app.AlertDialog.Builder(this@CalendarActivity)
                builder.setTitle("${listName} - $day 号")
                builder.setMessage("这里可以显示 $day 号的照片")
                builder.setPositiveButton("确定", null)
                builder.show()
            }
        }

        override fun getItemCount(): Int = days.size

        inner class CalendarViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val dayTextView: TextView = itemView.findViewById(R.id.dayTextView)
            val photoIndicator: ImageView = itemView.findViewById(R.id.photoIndicator)
        }
    }
}
