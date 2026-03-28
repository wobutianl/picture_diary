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
    private val REQUEST_PICK_IMAGE = 2
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
            builder.setPositiveButton("保存") { dialog, which ->
                val note = noteEditText.text.toString().trim()
                // 更新数据库
                dbHelper.updatePhoto(selectedPhoto.id, note)
                // 更新列表
                photoList[position] = selectedPhoto.copy(note = note)
                Toast.makeText(this, "saved", Toast.LENGTH_SHORT).show()
            }
            
            // 设置删除按钮
            builder.setNeutralButton("删除") { dialog, which ->
                // 显示确认对话框
                val confirmBuilder = android.app.AlertDialog.Builder(this)
                confirmBuilder.setTitle("确认删除")
                confirmBuilder.setMessage("确定要删除这张照片吗？")
                confirmBuilder.setPositiveButton("确定") { _, _ ->
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
            builder.setNegativeButton("取消", null)
            
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

        // 添加照片长按事件监听器，实现功能列表
        binding.photoGrid.setOnItemLongClickListener { parent, view, position, id ->
            val selectedPhoto = photoList[position]
            
            // 显示功能列表对话框
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("照片操作")
            builder.setItems(arrayOf("保存到本地", "分享照片", "查看详情")) { _, which ->
                when (which) {
                    0 -> {
                        // 保存到本地
                        val confirmBuilder = android.app.AlertDialog.Builder(this)
                        confirmBuilder.setTitle("保存照片")
                        confirmBuilder.setMessage("确定要将照片保存到本地吗？")
                        confirmBuilder.setPositiveButton("确定") { _, _ ->
                            // 保存照片到本地
                            savePhotoToLocal(selectedPhoto.image)
                        }
                        confirmBuilder.setNegativeButton("取消", null)
                        confirmBuilder.show()
                    }
                    1 -> {
                        // 分享照片
                        sharePhoto(selectedPhoto.image)
                    }
                    2 -> {
                        // 查看详情（跳转到详情对话框）
                        // 这里可以直接调用点击事件的逻辑
                        binding.photoGrid.performItemClick(view, position, id)
                    }
                }
            }
            builder.setNegativeButton("取消", null)
            builder.show()
            
            true
        }

        // 设置拍摄按钮点击事件
        binding.captureButton.setOnClickListener {
            dispatchTakePictureIntent()
        }

        // 设置+号按钮点击事件
        binding.plusButton.setOnClickListener {
            openGallery()
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

    private fun openGallery() {
        // 检查存储权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
                return
            }
        }
        
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_PICK_IMAGE)
    }

    private fun processSelectedImage(uri: android.net.Uri): Boolean {
        try {
            // 读取图片
            val inputStream = contentResolver.openInputStream(uri)
            val imageBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (imageBitmap == null) {
                runOnUiThread {
                    Toast.makeText(this, "无法加载选中的图片", Toast.LENGTH_SHORT).show()
                }
                return false
            }
            
            // 创建文件保存图片
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val imageFile = java.io.File.createTempFile(
                imageFileName, /* 前缀 */
                ".jpg", /* 后缀 */
                storageDir /* 目录 */
            )
            
            // 保存图片到文件
            val fos = java.io.FileOutputStream(imageFile)
            imageBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
            
            val path = imageFile.absolutePath
            
            // 生成缩略图
            val thumbnailPath = generateThumbnail(path)
            if (thumbnailPath == null) {
                runOnUiThread {
                    Toast.makeText(this, "生成缩略图失败", Toast.LENGTH_SHORT).show()
                }
                return false
            }
            
            // 获取图片中的位置和时间信息
            val (imageLocation, imageTime) = getImageInfo(uri)
            
            val time = if (imageTime.isNotEmpty()) {
                imageTime
            } else {
                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(java.util.Date())
            }
            
            val location = if (imageLocation.isNotEmpty()) {
                imageLocation
            } else if (currentLocationAddress != null && currentLocationAddress!!.isNotEmpty()) {
                currentLocationAddress!!
            } else if (currentLocation != null) {
                "${currentLocation?.latitude}, ${currentLocation?.longitude}"
            } else {
                ""
            }
            val note = ""
            
            // 保存到数据库
            val photoId = dbHelper.insertPhoto(listId, path, thumbnailPath, time, location, note)
            
            // 加载缩略图用于显示
            val thumbnailBitmap = android.graphics.BitmapFactory.decodeFile(thumbnailPath)
            
            // 添加到列表并更新UI
            runOnUiThread {
                val photoData = DatabaseHelper.PhotoData(photoId, thumbnailBitmap, path, thumbnailPath, time, location, note)
                photoList.add(photoData)
                photoAdapter.notifyDataSetChanged()
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("ListDetailActivity", "Error processing selected image", e)
            runOnUiThread {
                Toast.makeText(this, "处理图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return false
        }
    }

    private fun getImageInfo(uri: android.net.Uri): Pair<String, String> {
        var location = ""
        var time = ""
        
        try {
            // 从图片中读取EXIF数据
            val exifInterface = if (uri.scheme == "content") {
                // 对于content URI，使用InputStream创建ExifInterface
                val inputStream = contentResolver.openInputStream(uri)
                android.media.ExifInterface(inputStream!!)
            } else {
                // 对于file URI，直接使用路径
                android.media.ExifInterface(uri.path!!)
            }
            
            // 获取经纬度
            val latitude = getGPSCoordinate(exifInterface, android.media.ExifInterface.TAG_GPS_LATITUDE, android.media.ExifInterface.TAG_GPS_LATITUDE_REF)
            val longitude = getGPSCoordinate(exifInterface, android.media.ExifInterface.TAG_GPS_LONGITUDE, android.media.ExifInterface.TAG_GPS_LONGITUDE_REF)
            
            // 检查是否有有效的地理位置信息
            if (latitude != null && longitude != null) {
                // 将经纬度转换为地址
                location = getAddressFromCoordinates(latitude, longitude)
            }
            
            // 获取拍摄时间
            val dateTime = exifInterface.getAttribute(android.media.ExifInterface.TAG_DATETIME)
            if (dateTime != null) {
                // 格式化时间：YYYY:MM:DD HH:MM:SS -> YYYY/MM/DD HH:MM:SS
                try {
                    // 分割日期和时间部分
                    val parts = dateTime.split(" ")
                    if (parts.size == 2) {
                        val datePart = parts[0].replace(":", "/")
                        val timePart = parts[1]
                        time = "$datePart $timePart"
                    } else {
                        time = dateTime
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ListDetailActivity", "Error formatting time", e)
                    time = dateTime
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ListDetailActivity", "Error getting image info", e)
        }
        
        return Pair(location, time)
    }

    private fun getGPSCoordinate(exifInterface: android.media.ExifInterface, latitudeTag: String, latitudeRefTag: String): Double? {
        val latLongStr = exifInterface.getAttribute(latitudeTag)
        val latLongRef = exifInterface.getAttribute(latitudeRefTag)
        
        if (latLongStr == null || latLongRef == null) {
            return null
        }
        
        // 解析度分秒格式的经纬度
        val parts = latLongStr.split(",".toRegex()).dropLastWhile { it.isEmpty() }
        if (parts.size != 3) {
            return null
        }
        
        try {
            // 度
            val degrees = parts[0].trim().toDouble()
            // 分
            val minutes = parts[1].trim().toDouble()
            // 秒
            val seconds = parts[2].trim().toDouble()
            
            // 转换为十进制格式
            var coordinate = degrees + (minutes / 60.0) + (seconds / 3600.0)
            
            // 根据方向调整符号
            if (latLongRef == "S" || latLongRef == "W") {
                coordinate = -coordinate
            }
            
            return coordinate
        } catch (e: NumberFormatException) {
            android.util.Log.e("ListDetailActivity", "Error parsing GPS coordinate", e)
            return null
        }
    }

    private fun getAddressFromCoordinates(latitude: Double, longitude: Double): String {
        val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val addressString = buildString {
                    if (address.countryName != null) append(address.countryName).append(" ")
                    if (address.adminArea != null) append(address.adminArea).append(" ")
                    if (address.locality != null) append(address.locality).append(" ")
                    if (address.thoroughfare != null) append(address.thoroughfare).append(" ")
                    if (address.featureName != null) append(address.featureName)
                }
                return addressString.trim()
            }
        } catch (e: Exception) {
            android.util.Log.e("ListDetailActivity", "Error getting address from coordinates", e)
        }
        return ""
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
                    // 生成缩略图
                    val thumbnailPath = generateThumbnail(path)
                    if (thumbnailPath == null) {
                        Toast.makeText(this, "生成缩略图失败", Toast.LENGTH_SHORT).show()
                        return
                    }
                    
                    // 加载高分辨率图片
                    val imageBitmap = android.graphics.BitmapFactory.decodeFile(path)
                    
                    // 获取图片中的位置和时间信息
                    val (imageLocation, imageTime) = getImageInfo(android.net.Uri.fromFile(java.io.File(path)))
                    
                    val time = if (imageTime.isNotEmpty()) {
                        imageTime
                    } else {
                        java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(java.util.Date())
                    }
                    
                    val location = if (imageLocation.isNotEmpty()) {
                        imageLocation
                    } else if (currentLocationAddress != null && currentLocationAddress!!.isNotEmpty()) {
                        currentLocationAddress!!
                    } else if (currentLocation != null) {
                        "${currentLocation?.latitude}, ${currentLocation?.longitude}"
                    } else {
                        ""
                    }
                    val note = ""
                    
                    // 保存到数据库
                    val photoId = dbHelper.insertPhoto(listId, path, thumbnailPath, time, location, note)
                    
                    // 加载缩略图用于显示
                    val thumbnailBitmap = android.graphics.BitmapFactory.decodeFile(thumbnailPath)
                    
                    // 添加到列表
                    val photoData = DatabaseHelper.PhotoData(photoId, thumbnailBitmap, path, thumbnailPath, time, location, note)
                    photoList.add(photoData)
                    photoAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("ListDetailActivity", "Error loading high-res image", e)
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            // 从相册选择图片
            try {
                // 检查位置权限并获取位置
                checkLocationPermission()
                
                // 处理图片选择
                if (data != null) {
                    // 显示加载指示器
                    val progressDialog = android.app.ProgressDialog(this)
                    progressDialog.setTitle("处理中")
                    progressDialog.setMessage("正在处理图片...")
                    progressDialog.setCancelable(false)
                    progressDialog.show()
                    
                    // 在后台线程处理图片
                    Thread {
                        // 收集所有需要处理的URI
                        val uris = mutableListOf<android.net.Uri>()
                        
                        if (data.clipData != null) {
                            val clipData = data.clipData!!
                            for (i in 0 until clipData.itemCount) {
                                uris.add(clipData.getItemAt(i).uri)
                            }
                        } else if (data.data != null) {
                            uris.add(data.data!!)
                        }
                        
                        var successCount = 0
                        // 处理所有图片
                        for (uri in uris) {
                            if (processSelectedImage(uri)) {
                                successCount++
                            }
                        }
                        
                        // 在主线程更新UI
                        runOnUiThread {
                            progressDialog.dismiss()
                            if (successCount > 0) {
                                Toast.makeText(this, "成功添加 $successCount 张图片", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                }
            } catch (e: Exception) {
                android.util.Log.e("ListDetailActivity", "Error picking images", e)
                Toast.makeText(this, "Error picking images: ${e.message}", Toast.LENGTH_SHORT).show()
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
    private var locationListener: android.location.LocationListener? = null

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
            val pdfPath = getPdfFilePath()
            android.util.Log.d("ListDetailActivity", "Generating PDF at: $pdfPath")
            
            if (photoList.isEmpty()) {
                android.util.Log.d("ListDetailActivity", "photoList is empty")
                Toast.makeText(this, "No photos to export", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (pdfPath.isNullOrEmpty()) {
                android.util.Log.e("ListDetailActivity", "PDF path is empty")
                Toast.makeText(this, "生成PDF失败：文件路径为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 确保父目录存在
            val pdfFile = java.io.File(pdfPath)
            val parentDir = pdfFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                val created = parentDir.mkdirs()
                android.util.Log.d("ListDetailActivity", "Created parent directory: $created, path: ${parentDir.absolutePath}")
            }
            
            // 使用FileOutputStream以覆盖模式打开文件，确保能够替换现有文件
            val fileOutputStream = try {
                java.io.FileOutputStream(pdfPath, false) // false表示覆盖模式
            } catch (e: Exception) {
                android.util.Log.e("ListDetailActivity", "Error creating FileOutputStream: ${e.message}", e)
                Toast.makeText(this, "生成PDF失败：无法创建文件", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 创建 PDF 文档
            val pdfWriter = try {
                PdfWriter(fileOutputStream)
            } catch (e: Exception) {
                android.util.Log.e("ListDetailActivity", "Error creating PdfWriter: ${e.message}", e)
                fileOutputStream.close()
                Toast.makeText(this, "生成PDF失败：无法创建PDF写入器", Toast.LENGTH_SHORT).show()
                return
            }
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
            
            val listTitle = intent.getStringExtra("LIST_NAME") ?: "Unknown List"
            
            // 按日期分组照片
            val photosByDate = mutableMapOf<String, MutableList<DatabaseHelper.PhotoData>>()
            
            for (photoData in photoList) {
                // 提取日期部分（去掉时间）
                val date = photoData.time.split(" ")[0]
                if (!photosByDate.containsKey(date)) {
                    photosByDate[date] = mutableListOf()
                }
                photosByDate[date]?.add(photoData)
            }
            
            android.util.Log.d("ListDetailActivity", "Photos grouped by date: ${photosByDate.size} groups")
            
            // 处理每个日期组
            for ((date, photos) in photosByDate) {
                android.util.Log.d("ListDetailActivity", "Processing date: $date, photos: ${photos.size}")
                
                // 为每个日期组创建一个新页面
                pdfDocument.addNewPage()
                
                // 计算页面布局
                val pageSize = pdfDocument.defaultPageSize
                val margin = 36f
                val availableWidth = pageSize.width - (2 * margin)
                val availableHeight = pageSize.height - (2 * margin)
                
                // 设置文档边距
                document.setMargins(margin, margin, margin, margin)
                
                // 添加日期标题
                val dateTitle = Paragraph(date)
                    .setFontSize(16f)
                    .setBold()
                    .setMarginBottom(20f)
                if (font != null) {
                    dateTitle.setFont(font)
                }
                document.add(dateTitle)
                
                // 计算每张照片的高度
                val baseFontSize = 12f
                val lineHeight = baseFontSize * 1.5f
                val charsPerLine = (availableWidth / (baseFontSize * 0.5)).toInt()
                
                // 每张照片的文字高度估算
                val estimatedTextHeight = 100f // 估算文字高度
                val estimatedPhotoHeight = 200f // 估算每张照片总高度
                
                // 计算每页可容纳的照片数量
                val maxPhotosPerPage = (availableHeight / estimatedPhotoHeight).toInt()
                
                // 分批处理照片
                val photoBatches = photos.chunked(maxPhotosPerPage)
                
                for ((batchIndex, batch) in photoBatches.withIndex()) {
                    if (batchIndex > 0) {
                        pdfDocument.addNewPage()
                        document.setMargins(margin, margin, margin, margin)
                        document.add(dateTitle)
                    }
                    
                    // 处理当前批次的照片
                    for ((index, photoData) in batch.withIndex()) {
                        try {
                            // 准备文字内容
                            val timeText = "时间：${photoData.time}"
                            val locationText = "地址：${if (photoData.location.isNotEmpty()) photoData.location else "未知"}"
                            val noteText = "信息：${if (photoData.note.isNotEmpty()) photoData.note else "无"}"
                            
                            // 计算文字高度
                            val timeLines = (timeText.length + charsPerLine - 1) / charsPerLine
                            val locationLines = (locationText.length + charsPerLine - 1) / charsPerLine
                            val noteLines = (noteText.length + charsPerLine - 1) / charsPerLine
                            val totalTextHeight = (timeLines + locationLines + noteLines) * lineHeight + 30f
                            
                            // 计算图片可用高度
                            val imageAvailableHeight = availableHeight - totalTextHeight - 50f // 预留空间
                            val minImageHeight = 100f
                            val safeImageHeight = if (imageAvailableHeight > minImageHeight) imageAvailableHeight else minImageHeight
                            
                            // 加载原始图片用于PDF生成
                            val originalBitmap = android.graphics.BitmapFactory.decodeFile(photoData.imagePath)
                            // 调整图片大小
                            val imageData = ImageDataFactory.create(bitmapToByteArray(originalBitmap))
                            val image = Image(imageData)
                            
                            // 计算图片宽度，保持原始比例
                            val imageWidth = availableWidth
                            val imageHeight = (imageWidth * imageData.height) / imageData.width
                            
                            var finalImageHeight = imageHeight
                            var finalImageWidth = imageWidth
                            
                            // 确保图片高度不超过可用高度
                            if (finalImageHeight > safeImageHeight) {
                                finalImageHeight = safeImageHeight
                                finalImageWidth = (finalImageHeight * imageData.width) / imageData.height
                            }
                            
                            // 确保图片宽度不超过可用宽度
                            if (finalImageWidth > availableWidth) {
                                finalImageWidth = availableWidth
                                finalImageHeight = (finalImageWidth * imageData.height) / imageData.width
                            }
                            
                            // 添加照片
                            image.setWidth(finalImageWidth)
                            image.setHeight(finalImageHeight)
                            image.setMarginBottom(15f)
                            document.add(image)
                            
                            // 添加文字信息
                            val timePara = Paragraph(timeText)
                                .setFontSize(baseFontSize)
                                .setMarginBottom(8f)
                            if (font != null) timePara.setFont(font)
                            document.add(timePara)
                            
                            val locationPara = Paragraph(locationText)
                                .setFontSize(baseFontSize)
                                .setMarginBottom(8f)
                            if (font != null) locationPara.setFont(font)
                            document.add(locationPara)
                            
                            val notePara = Paragraph(noteText)
                                .setFontSize(baseFontSize)
                                .setMarginBottom(20f) // 增加底部边距，区分不同照片
                            if (font != null) notePara.setFont(font)
                            document.add(notePara)
                            
                            android.util.Log.d("ListDetailActivity", "Added photo $index for date $date")
                        } catch (e: Exception) {
                            android.util.Log.e("ListDetailActivity", "Error adding photo $index for date $date", e)
                            val errorPara = Paragraph("Error adding photo: ${e.message}")
                                .setMarginTop(10f)
                                .setMarginBottom(20f)
                            document.add(errorPara)
                        }
                    }
                }
            }
            
            // 关闭文档
            document.close()
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
        // 使用Download目录下的PictureDiary子目录
        val directory = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val pictureDiaryDir = java.io.File(directory, "PictureDiary")
        // 确保目录存在
        if (!pictureDiaryDir.exists()) {
            val created = pictureDiaryDir.mkdirs()
            android.util.Log.d("ListDetailActivity", "Created directory: $created, path: ${pictureDiaryDir.absolutePath}")
        }
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val listName = intent.getStringExtra("LIST_NAME") ?: "Unknown List"
        val fileName = "${currentYear}_${listName.replace(" ", "_")}.pdf"
        val pdfPath = "${pictureDiaryDir.absolutePath}/$fileName"
        android.util.Log.d("ListDetailActivity", "PDF path: $pdfPath")
        return pdfPath
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
        try {
            // 实现LocationListener
            locationListener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    // 获取到新位置
                    currentLocation = location
                    getAddressFromLocation(location)
                    // 移除监听器，避免重复获取
                    locationManager.removeUpdates(this)
                }
                
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            
            // 优先使用GPS提供者
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                locationListener?.let {
                    locationManager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, it, null)
                }
            }
            
            // 如果GPS不可用，尝试使用网络提供者
            if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) && 
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                locationListener?.let {
                    locationManager.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, it, null)
                }
            }
            
            // 同时获取最后已知位置作为备选
            val lastKnownLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) ?: 
                                   locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) ?: 
                                   locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
            
            if (lastKnownLocation != null) {
                currentLocation = lastKnownLocation
                getAddressFromLocation(lastKnownLocation)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("ListDetailActivity", "Error getting location", e)
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
                        if (address.countryName != null) append(address.countryName).append(" ")
                        if (address.adminArea != null) append(address.adminArea).append(" ")
                        if (address.locality != null) append(address.locality).append(" ")
                        if (address.thoroughfare != null) append(address.thoroughfare).append(" ")
                        if (address.featureName != null) append(address.featureName)
                    }
                    runOnUiThread {
                        currentLocationAddress = addressString
                    }
                } else {
                    // 地址解析失败，使用经纬度作为位置信息
                    runOnUiThread {
                        currentLocationAddress = "${location.latitude}, ${location.longitude}"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ListDetailActivity", "Error getting address", e)
                // 异常情况下，使用经纬度作为位置信息
                runOnUiThread {
                    currentLocationAddress = "${location.latitude}, ${location.longitude}"
                }
            }
        }.start()
    }

    // 处理权限请求结果
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // 权限已授予，检查是否是从打开相册请求的权限
                    if (permissions.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        // 重新打开相册
                        openGallery()
                    } else {
                        // 继续生成PDF
                        Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
                        generatePdf()
                    }
                } else {
                    Toast.makeText(this, "需要存储权限才能保存照片、生成PDF或选择图片", Toast.LENGTH_SHORT).show()
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

    // 保存照片到本地
    private fun savePhotoToLocal(bitmap: android.graphics.Bitmap) {
        try {
            // 使用应用专用的外部存储目录，避免权限问题
            val directory = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val pictureDiaryDir = java.io.File(directory, "PictureDiary")
            if (!pictureDiaryDir.exists()) {
                pictureDiaryDir.mkdirs()
            }

            // 创建文件名
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
            val fileName = "IMG_${timeStamp}.jpg"
            val file = java.io.File(pictureDiaryDir, fileName)

            // 保存图片
            val fos = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()

            Toast.makeText(this, "照片已保存到: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("ListDetailActivity", "Error saving photo", e)
            Toast.makeText(this, "保存照片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 生成缩略图
    private fun generateThumbnail(originalPath: String): String? {
        try {
            // 加载原始图片
            val originalBitmap = android.graphics.BitmapFactory.decodeFile(originalPath)
            if (originalBitmap == null) {
                android.util.Log.e("ListDetailActivity", "Failed to decode original image")
                return null
            }

            // 计算缩略图尺寸（保持比例）
            val maxThumbnailSize = 300 // 最大边长为300像素
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = maxThumbnailSize.toFloat() / Math.max(width, height)
            val thumbnailWidth = (width * scale).toInt()
            val thumbnailHeight = (height * scale).toInt()

            // 创建缩略图
            val thumbnailBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, thumbnailWidth, thumbnailHeight, true)

            // 保存缩略图到文件
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
            val thumbnailFileName = "THUMB_${timeStamp}_"
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            // 确保目录存在
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs()
            }
            val thumbnailFile = java.io.File.createTempFile(
                thumbnailFileName, /* 前缀 */
                ".jpg", /* 后缀 */
                storageDir /* 目录 */
            )

            // 保存缩略图
            val fos = java.io.FileOutputStream(thumbnailFile)
            thumbnailBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, fos) // 75% 质量
            fos.flush()
            fos.close()

            return thumbnailFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ListDetailActivity", "Error generating thumbnail", e)
            return null
        }
    }

    // 分享照片
    private fun sharePhoto(bitmap: android.graphics.Bitmap) {
        try {
            // 创建临时文件
            val cacheDir = externalCacheDir
            val file = java.io.File(cacheDir, "shared_photo.jpg")
            val fos = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()

            // 创建分享意图
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.example.picture_diary.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // 启动分享对话框，包含微信选项
            val chooser = Intent.createChooser(intent, "分享照片")
            // 检查是否有应用可以处理这个意图
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(chooser)
            } else {
                Toast.makeText(this, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("ListDetailActivity", "Error sharing photo", e)
            Toast.makeText(this, "分享照片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除位置监听器，避免内存泄漏
        val locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        locationListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (e: SecurityException) {
                android.util.Log.e("ListDetailActivity", "Error removing location listener", e)
            }
        }
    }
}
