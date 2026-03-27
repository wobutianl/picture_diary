package com.example.picture_diary

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.picture_diary.databinding.ActivityCalendarBinding
import java.util.Calendar

class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var dbHelper: DatabaseHelper
    private var listId: Long = 0
    private var listName: String? = null
    
    // 当前月份和年份
    private var currentYear: Int
    private var currentMonth: Int // 0-11

    init {
        val calendar = Calendar.getInstance()
        currentYear = calendar.get(Calendar.YEAR)
        currentMonth = calendar.get(Calendar.MONTH)
    }

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

        // 设置月份标题
        updateMonthTitle()

        // 设置切换按钮点击事件
        binding.btnPrevMonth.setOnClickListener {
            prevMonth()
        }

        binding.btnNextMonth.setOnClickListener {
            nextMonth()
        }

        binding.btnPrevYear.setOnClickListener {
            prevYear()
        }

        binding.btnNextYear.setOnClickListener {
            nextYear()
        }

        // 初始化RecyclerView
        updateCalendar()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // 更新月份标题
    private fun updateMonthTitle() {
        binding.monthTitle.text = "${currentYear}年${currentMonth + 1}月"
    }

    // 切换到上个月
    private fun prevMonth() {
        if (currentMonth == 0) {
            currentMonth = 11
            currentYear--
        } else {
            currentMonth--
        }
        updateMonthTitle()
        updateCalendar()
    }

    // 切换到下个月
    private fun nextMonth() {
        if (currentMonth == 11) {
            currentMonth = 0
            currentYear++
        } else {
            currentMonth++
        }
        updateMonthTitle()
        updateCalendar()
    }

    // 切换到上一年
    private fun prevYear() {
        currentYear--
        updateMonthTitle()
        updateCalendar()
    }

    // 切换到下一年
    private fun nextYear() {
        currentYear++
        updateMonthTitle()
        updateCalendar()
    }

    // 更新日历显示
    private fun updateCalendar() {
        val calendarAdapter = CalendarAdapter()
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(this, 7) // 7列日历
        binding.calendarRecyclerView.adapter = calendarAdapter
    }

    // 日历适配器
    inner class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

        private val calendarData: List<Any> = generateCalendarData()
        private val photoTimes: List<String> = dbHelper.getPhotoTimesByListId(listId)

        // 生成日历数据
        private fun generateCalendarData(): List<Any> {
            val data = mutableListOf<Any>()
            val calendar = Calendar.getInstance()
            calendar.set(currentYear, currentMonth, 1)

            // 获取当月第一天是星期几
            val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0-6，0表示星期日

            // 添加空白日期
            for (i in 0 until firstDayOfWeek) {
                data.add(" ")
            }

            // 获取当月的天数
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            // 添加当月日期
            for (day in 1..daysInMonth) {
                data.add(day)
            }

            return data
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CalendarViewHolder {
            val view = layoutInflater.inflate(R.layout.item_calendar_day, parent, false)
            return CalendarViewHolder(view)
        }

        override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
            val item = calendarData[position]
            
            if (item is Int) {
                // 显示日期
                holder.dayTextView.text = item.toString()
                holder.dayTextView.visibility = android.view.View.VISIBLE
                
                // 检查当天是否有照片
                if (hasPhoto(item)) {
                    // 有照片，设置蓝色背景
                    holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                    holder.photoIndicator.visibility = android.view.View.VISIBLE
                } else {
                    // 没有照片，使用默认背景
                    holder.itemView.setBackgroundResource(R.drawable.calendar_day_background)
                    holder.photoIndicator.visibility = android.view.View.GONE
                }

                holder.itemView.setOnClickListener {
                    // 点击日期时，可以显示当天的照片
                    val builder = android.app.AlertDialog.Builder(this@CalendarActivity)
                    builder.setTitle("${listName} - ${currentYear}年${currentMonth + 1}月$item 日")
                    if (hasPhoto(item)) {
                        builder.setMessage("当天有照片")
                    } else {
                        builder.setMessage("当天没有照片")
                    }
                    builder.setPositiveButton("确定", null)
                    builder.show()
                }
            } else {
                // 空白日期
                holder.dayTextView.visibility = android.view.View.INVISIBLE
                holder.itemView.setOnClickListener(null)
                holder.itemView.setBackgroundResource(R.drawable.calendar_day_background)
            }
        }

        // 检查指定日期是否有照片
        private fun hasPhoto(day: Int): Boolean {
            // 构建日期字符串，格式与数据库中存储的一致（使用两位数的月份和日期）
            val monthStr = String.format("%02d", currentMonth + 1)
            val dayStr = String.format("%02d", day)
            val dateStr = "${currentYear}/$monthStr/$dayStr"
            // 检查是否有对应日期的照片
            return photoTimes.any { it.startsWith(dateStr) }
        }

        override fun getItemCount(): Int = calendarData.size

        inner class CalendarViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val dayTextView: TextView = itemView.findViewById(R.id.dayTextView)
            val photoIndicator: ImageView = itemView.findViewById(R.id.photoIndicator)
        }
    }
}
