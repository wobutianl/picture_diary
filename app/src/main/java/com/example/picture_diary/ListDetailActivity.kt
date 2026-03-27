package com.example.picture_diary

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.picture_diary.databinding.ActivityListDetailBinding
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Image
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.font.PdfFont
import java.io.IOException

class ListDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListDetailBinding
    private val REQUEST_IMAGE_CAPTURE = 1
    private val photoList = mutableListOf<DatabaseHelper.PhotoData>()
    private lateinit var photoAdapter: PhotoAdapter
    private lateinit var dbHelper: DatabaseHelper
    private var listId: Long = 0
    private var photoUri: android.net.Uri? = null
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityListDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传递过来的 List 名称和 ID
        val listName = intent.getStringExtra("LIST_NAME")
        listId = intent.getLongExtra("LIST_ID", 0)
        title = listName

        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 初始化数据库帮助类
        dbHelper = DatabaseHelper(this)
        
        // 加载照片数据
        loadPhotos()

        // 初始化 GridView 适配器
        photoAdapter = PhotoAdapter()
        binding.photoGrid.adapter = photoAdapter

        // 添加照片点击事件监听器
        binding.photoGrid.setOnItemClickListener { parent, view, position, id ->
            val selectedPhoto = photoList[position]
            // 创建对话框显示照片信息
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Photo Details")
            
            // 创建自定义布局
            val dialogView = layoutInflater.inflate(R.layout.dialog_photo_detail, null)
            val photoImageView = dialogView.findViewById<ImageView>(R.id.photoImageView)
            val timeTextView = dialogView.findViewById<android.widget.TextView>(R.id.timeTextView)
            val locationTextView = dialogView.findViewById<android.widget.TextView>(R.id.locationTextView)
            val noteEditText = dialogView.findViewById<android.widget.EditText>(R.id.noteEditText)
            
            // 设置照片
            photoImageView.setImageBitmap(selectedPhoto.image)
            
            // 设置照片信息
            timeTextView.text = "拍摄时间: ${selectedPhoto.time}"
            locationTextView.text = "拍摄地点: ${selectedPhoto.location}"
            noteEditText.setText(selectedPhoto.note)
            noteEditText.hint = "添加文字注释..."
            
            builder.setView(dialogView)
            
            // 设置确定按钮（保存注释）
            builder.setPositiveButton("Save") { dialog, which ->
                val note = noteEditText.text.toString().trim()
                // 更新数据库
                dbHelper.updatePhoto(selectedPhoto.id, note)
                // 更新列表
                photoList[position] = selectedPhoto.copy(note = note)
                Toast.makeText(this, "saved", Toast.LENGTH_SHORT).show()
            }
            
            // 设置删除按钮
            builder.setNeutralButton("Delete") { dialog, which ->
                // 显示确认对话框
                val confirmBuilder = android.app.AlertDialog.Builder(this)
                confirmBuilder.setTitle("Confirm Delete")
                confirmBuilder.setMessage("Are you sure you want to delete this photo?")
                confirmBuilder.setPositiveButton("Yes") { _, _ ->
                    // 从数据库中删除
                    dbHelper.deletePhoto(selectedPhoto.id)
                    // 从列表中删除
                    photoList.removeAt(position)
                    photoAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
                }
                confirmBuilder.setNegativeButton("No", null)
                confirmBuilder.show()
            }
            
            // 设置取消按钮
            builder.setNegativeButton("Cancel", null)
            
            // 显示对话框
            val dialog = builder.show()
            // 设置对话框大小，确保备注信息框能够完全显示
            val window = dialog.window
            if (window != null) {
                val displayMetrics = resources.displayMetrics
                val width = (displayMetrics.widthPixels * 0.9).toInt()
                val height = (displayMetrics.heightPixels * 0.8).toInt()
                window.setLayout(width, height)
            }
        }

        // 设置拍摄按钮点击事件
        binding.captureButton.setOnClickListener {
            dispatchTakePictureIntent()
        }


    }

    private fun dispatchTakePictureIntent() {
        // 先检查位置权限并获取位置
        checkLocationPermission()
        
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // 创建临时文件来存储高分辨率图片
            val photoFile: java.io.File? = try {
                createImageFile()
            } catch (ex: java.io.IOException) {
                // 错误处理
                android.util.Log.e("ListDetailActivity", "Error creating image file", ex)
                null
            }
            photoFile?.also {
                // 使用 FileProvider 生成 URI
                photoUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "com.example.picture_diary.fileprovider",
                    it
                )
                // 授予临时权限
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
        }
    }

    private var currentPhotoPath: String? = null

    private fun createImageFile(): java.io.File {
        // 创建一个唯一的文件名
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val image = java.io.File.createTempFile(
            imageFileName, /* 前缀 */
            ".jpg", /* 后缀 */
            storageDir /* 目录 */
        )
        // 保存文件路径
        currentPhotoPath = image.absolutePath
        return image
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // 再次尝试获取位置信息，确保最新位置
            getCurrentLocation()
            
            // 使用文件路径加载高分辨率图片
            currentPhotoPath?.let { path ->
                try {
                    // 加载高分辨率图片
                    val imageBitmap = android.graphics.BitmapFactory.decodeFile(path)
                    val time = java.text.SimpleDateFormat("yyyy/MM/dd").format(java.util.Date())
                    val location = if (currentLocationAddress != null && currentLocationAddress!!.isNotEmpty()) {
                        currentLocationAddress!!
                    } else if (currentLocation != null) {
                        "${currentLocation?.latitude}, ${currentLocation?.longitude}"
                    } else {
                        "未知"
                    }
                    val note = ""
                    
                    // 保存到数据库
                    val photoId = dbHelper.insertPhoto(listId, path, time, location, note)
                    
                    // 添加到列表
                    val photoData = DatabaseHelper.PhotoData(photoId, imageBitmap, time, location, note)
                    photoList.add(photoData)
                    photoAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("ListDetailActivity", "Error loading high-res image", e)
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_calendar -> {
                // 切换到日历页面
                android.os.Handler().post {
                    val intent = android.content.Intent(this, CalendarActivity::class.java)
                    intent.putExtra("LIST_ID", listId)
                    intent.putExtra("LIST_NAME", title)
                    startActivity(intent)
                }
                true
            }
            R.id.action_settings -> {
                // 显示确认对话框
                val confirmBuilder = android.app.AlertDialog.Builder(this)
                confirmBuilder.setTitle("确认导出")
                confirmBuilder.setMessage("确定要导出当前列表的所有照片和信息为PDF文件吗？")
                confirmBuilder.setPositiveButton("确定") { _, _ ->
                    generatePdf()
                }
                confirmBuilder.setNegativeButton("取消", null)
                confirmBuilder.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 加载照片数据
    private fun loadPhotos() {
        val photos = dbHelper.getPhotosByListId(listId)
        photoList.clear()
        photoList.addAll(photos)
    }

    // 自定义适配器用于显示照片
    inner class PhotoAdapter : android.widget.BaseAdapter() {
        override fun getCount(): Int = photoList.size

        override fun getItem(position: Int): Any = photoList[position]

        override fun getItemId(position: Int): Long = photoList[position].id

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val imageView: ImageView
            if (convertView == null) {
                imageView = ImageView(this@ListDetailActivity)
                imageView.layoutParams = android.widget.AbsListView.LayoutParams(300, 300)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                imageView = convertView as ImageView
            }
            imageView.setImageBitmap(photoList[position].image)
            return imageView
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private val REQUEST_STORAGE_PERMISSION = 1
    private val REQUEST_LOCATION_PERMISSION = 2
    private var currentLocation: android.location.Location? = null
    private var currentLocationAddress: String? = null

    // 生成 PDF 文件
    private fun generatePdf() {
        // 检查存储权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
                return
            }
        }
        
        try {
            // 创建 PDF 文档
            val pdfPath = getPdfFilePath()
            android.util.Log.d("ListDetailActivity", "Generating PDF at: $pdfPath")
            val pdfWriter = PdfWriter(pdfPath)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)
            
            // 尝试加载中文字体
            var font: PdfFont? = null
            try {
                // 尝试使用 iText 7 中专门用于支持中文的字体
                try {
                    // 尝试使用 STSong-Light 字体（iText 7 内置的支持中文的字体）
                    font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
                    android.util.Log.d("ListDetailActivity", "Loaded font: STSong-Light")
                } catch (e: Exception) {
                    android.util.Log.e("ListDetailActivity", "Error loading STSong-Light font", e)
                    // 如果加载失败，尝试使用其他方法
                    try {
                        // 尝试使用系统字体
                        val fontPath = "/system/fonts/DroidSansFallback.ttf"
                        font = PdfFontFactory.createFont(fontPath, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
                        android.util.Log.d("ListDetailActivity", "Loaded system font: $fontPath")
                    } catch (e: Exception) {
                        android.util.Log.e("ListDetailActivity", "Error loading system font", e)
                        // 如果加载失败，尝试使用 Helvetica 字体
                        try {
                            font = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA)
                            android.util.Log.d("ListDetailActivity", "Loaded built-in font: Helvetica")
                        } catch (e: Exception) {
                            android.util.Log.e("ListDetailActivity", "Error loading Helvetica font", e)
                            // 如果加载失败，使用默认字体
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ListDetailActivity", "Error loading font", e)
                // 如果加载失败，使用默认字体
            }
            
            // 确保字体不为 null
            if (font == null) {
                try {
                    font = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA)
                    android.util.Log.d("ListDetailActivity", "Loaded fallback font: Helvetica")
                } catch (e: Exception) {
                    android.util.Log.e("ListDetailActivity", "Error loading fallback font", e)
                }
            }
            
            // 添加标题
            val title = intent.getStringExtra("LIST_NAME") ?: "Unknown List"
            android.util.Log.d("ListDetailActivity", "Adding title: $title")
            val titlePara = Paragraph(title)
                .setFontSize(24f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20f)
            if (font != null) {
                titlePara.setFont(font)
            }
            document.add(titlePara)
            android.util.Log.d("ListDetailActivity", "Added title")
            
            // 添加照片和信息
            android.util.Log.d("ListDetailActivity", "photoList size: ${photoList.size}")
            if (photoList.isEmpty()) {
                android.util.Log.d("ListDetailActivity", "photoList is empty")
                val noPhotosPara = Paragraph("No photos found")
                    .setMarginTop(20f)
                    .setTextAlignment(TextAlignment.CENTER)
                document.add(noPhotosPara)
                android.util.Log.d("ListDetailActivity", "Added no photos message")
            } else {
                for ((index, photoData) in photoList.withIndex()) {
                    android.util.Log.d("ListDetailActivity", "Processing photo $index: ${photoData.time}, ${photoData.location}, ${photoData.note}")
                    try {
                        // 添加照片
                        android.util.Log.d("ListDetailActivity", "Adding photo $index")
                        val imageData = ImageDataFactory.create(bitmapToByteArray(photoData.image))
                        val image = Image(imageData)
                            .setWidth(300f)
                            .setAutoScaleHeight(true)
                            .setMarginBottom(10f) // 添加底部边距
                        document.add(image)
                        android.util.Log.d("ListDetailActivity", "Added photo $index")
                        
                        // 添加信息
                        android.util.Log.d("ListDetailActivity", "Adding info for photo $index")
                        
                        val timePara = Paragraph("时间：${photoData.time}")
                            .setFontSize(12f)
                            .setMarginBottom(5f)
                        if (font != null) {
                            timePara.setFont(font)
                        }
                        document.add(timePara)
                        android.util.Log.d("ListDetailActivity", "Added time info: ${photoData.time}")
                        
                        val locationText = if (photoData.location.isNotEmpty()) photoData.location else "未知"
                        val locationPara = Paragraph("地址：$locationText")
                            .setFontSize(12f)
                            .setMarginBottom(5f)
                        if (font != null) {
                            locationPara.setFont(font)
                        }
                        document.add(locationPara)
                        android.util.Log.d("ListDetailActivity", "Added location info: $locationText")
                        
                        val noteText = if (photoData.note.isNotEmpty()) photoData.note else "无"
                        val notePara = Paragraph("信息：$noteText")
                            .setFontSize(12f)
                            .setMarginBottom(20f)
                        if (font != null) {
                            notePara.setFont(font)
                        }
                        document.add(notePara)
                        android.util.Log.d("ListDetailActivity", "Added note info: $noteText")
                        
                        android.util.Log.d("ListDetailActivity", "Finished processing photo $index")
                    } catch (e: Exception) {
                        android.util.Log.e("ListDetailActivity", "Error adding photo $index", e)
                        val errorPara = Paragraph("Error adding photo: ${e.message}")
                            .setMarginTop(10f)
                            .setMarginBottom(20f)
                        document.add(errorPara)
                    }
                }
            }
            
            // 关闭文档
            android.util.Log.d("ListDetailActivity", "Closing document")
            document.close()
            android.util.Log.d("ListDetailActivity", "Document closed")
            android.util.Log.d("ListDetailActivity", "PDF generated successfully at: $pdfPath")
            
            // 显示成功提示
            Toast.makeText(this, "PDF generated successfully: $pdfPath", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("ListDetailActivity", "Error generating PDF", e)
            e.printStackTrace()
            Toast.makeText(this, "Failed to generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 获取 PDF 文件路径
    private fun getPdfFilePath(): String {
        val directory = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val listName = intent.getStringExtra("LIST_NAME") ?: "Unknown List"
        val fileName = "${currentYear}_${listName.replace(" ", "_")}.pdf"
        return "${directory.absolutePath}/$fileName"
    }

    // 辅助方法：Bitmap 转 ByteArray
    private fun bitmapToByteArray(bitmap: android.graphics.Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }

    // 检查并请求位置权限
    private fun checkLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
            } else {
                getCurrentLocation()
            }
        } else {
            getCurrentLocation()
        }
    }

    // 获取当前位置
    private fun getCurrentLocation() {
        val locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            try {
                currentLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                if (currentLocation == null) {
                    currentLocation = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                }
                // 获取地址信息
                currentLocation?.let {
                    getAddressFromLocation(it)
                }
            } catch (e: SecurityException) {
                android.util.Log.e("ListDetailActivity", "Error getting location", e)
            }
        }
    }

    // 从位置获取地址信息
    private fun getAddressFromLocation(location: android.location.Location) {
        val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
        Thread {
            try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val addressString = buildString {
                        if (address.adminArea != null) append(address.adminArea).append(" ")
                        if (address.locality != null) append(address.locality).append(" ")
                        if (address.thoroughfare != null) append(address.thoroughfare).append(" ")
                        if (address.featureName != null) append(address.featureName)
                    }
                    runOnUiThread {
                        currentLocationAddress = addressString
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ListDetailActivity", "Error getting address", e)
            }
        }.start()
    }

    // 处理权限请求结果
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    generatePdf()
                } else {
                    Toast.makeText(this, "Storage permission is required to generate PDF", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation()
                } else {
                    Toast.makeText(this, "Location permission is required to get location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
