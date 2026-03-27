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

        // 延迟初始化数据库和RecyclerView，提高启动速度
        android.os.Handler().post {
            // 初始化数据库帮助类
            dbHelper = DatabaseHelper(this)
            // 初始化RecyclerView
            updateCalendar()
        }
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

    // 日历适配器实例
    private var calendarAdapter: CalendarAdapter? = null

    // 更新日历显示
    private fun updateCalendar() {
        // 只创建一次布局管理器
        if (binding.calendarRecyclerView.layoutManager == null) {
            binding.calendarRecyclerView.layoutManager = GridLayoutManager(this, 7) // 7列日历
        }
        
        // 创建新的适配器并设置
        calendarAdapter = CalendarAdapter()
        binding.calendarRecyclerView.adapter = calendarAdapter
    }



    // 日历适配器
    inner class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

        private val calendarData: List<Any>
        private val photosByDate: Map<String, List<DatabaseHelper.PhotoData>>

        // 构造函数中生成日历数据和预加载照片
        init {
            calendarData = generateCalendarData()
            photosByDate = loadPhotosByDate()
        }

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

        // 加载所有照片并按日期分组
        private fun loadPhotosByDate(): Map<String, List<DatabaseHelper.PhotoData>> {
            val allPhotos = dbHelper.getPhotosByListId(listId)
            return allPhotos.groupBy { 
                // 提取日期部分（格式：yyyy/MM/dd）
                it.time.substring(0, 10)
            }
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
                val hasPhoto = checkHasPhoto(item)
                if (hasPhoto) {
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
                    
                    if (hasPhoto) {
                        // 从内存中获取当天的照片
                        val monthStr = String.format("%02d", currentMonth + 1)
                        val dayStr = String.format("%02d", item)
                        val dateStr = "${currentYear}/$monthStr/$dayStr"
                        val photos = getPhotosByDate(dateStr)
                        
                        if (photos.isNotEmpty()) {
                            // 显示照片和信息的对话框
                            val dialogView = layoutInflater.inflate(R.layout.dialog_photo_gallery, null)
                            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.photoRecyclerView)
                            
                            // 创建适配器显示照片
                            val photoAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                                    val view = layoutInflater.inflate(R.layout.item_photo_gallery, parent, false)
                                    return object : RecyclerView.ViewHolder(view) {
                                        val imageView: ImageView = view.findViewById(R.id.photoImageView)
                                        val timeTextView: TextView = view.findViewById(R.id.timeTextView)
                                        val locationTextView: TextView = view.findViewById(R.id.locationTextView)
                                        val noteTextView: TextView = view.findViewById(R.id.noteTextView)
                                    }
                                }
                                
                                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                                    val photo = photos[position]
                                    val viewHolder = holder as RecyclerView.ViewHolder
                                    val imageView = viewHolder.itemView.findViewById<ImageView>(R.id.photoImageView)
                                    val timeTextView = viewHolder.itemView.findViewById<TextView>(R.id.timeTextView)
                                    val locationTextView = viewHolder.itemView.findViewById<TextView>(R.id.locationTextView)
                                    val noteTextView = viewHolder.itemView.findViewById<TextView>(R.id.noteTextView)
                                    
                                    imageView.setImageBitmap(photo.image)
                                    timeTextView.text = photo.time
                                    locationTextView.text = photo.location
                                    noteTextView.text = photo.note
                                }
                                
                                override fun getItemCount(): Int = photos.size
                            }
                            
                            recyclerView.layoutManager = GridLayoutManager(this@CalendarActivity, 1)
                            recyclerView.adapter = photoAdapter
                            
                            builder.setView(dialogView)
                        } else {
                            builder.setMessage("当天没有照片")
                        }
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
        private fun checkHasPhoto(day: Int): Boolean {
            val monthStr = String.format("%02d", currentMonth + 1)
            val dayStr = String.format("%02d", day)
            val dateStr = "${currentYear}/$monthStr/$dayStr"
            return photosByDate.containsKey(dateStr)
        }

        // 从内存中获取指定日期的照片
        private fun getPhotosByDate(dateStr: String): List<DatabaseHelper.PhotoData> {
            return photosByDate.getOrDefault(dateStr, emptyList())
        }

        override fun getItemCount(): Int = calendarData.size

        inner class CalendarViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val dayTextView: TextView = itemView.findViewById(R.id.dayTextView)
            val photoIndicator: ImageView = itemView.findViewById(R.id.photoIndicator)
        }
    }
}
