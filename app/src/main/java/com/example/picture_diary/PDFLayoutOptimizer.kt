package com.example.picture_diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class PDFLayoutOptimizer(private val context: Context) {
    
    // 页面配置
    private val pageSize = PageSize.A4
    private val marginTop = 50f      // 上边距（点，1英寸≈72点）
    private val marginBottom = 50f    // 下边距
    private val marginLeft = 50f      // 左边距
    private val marginRight = 50f     // 右边距
    private val minImageHeight = 80f   // 图片最小高度（低于此值另起一页）
    
    /**
     * 生成自适应布局的 PDF
     * @param filePath 输出文件路径
     * @param title 标题
     * @param textContent 文字内容
     * @param bitmap 图片
     */
    fun createAdaptivePDF(
        filePath: String,
        title: String,
        textContent: String,
        bitmap: Bitmap
    ): Boolean {
        return try {
            val fos = FileOutputStream(File(filePath))
            val writer = PdfWriter(fos)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc, pageSize)
            
            // 设置页面边距
            document.setMargins(marginTop, marginRight, marginBottom, marginLeft)
            
            // 计算页面可用高度（点，1点≈1/72英寸）
            val pageHeight = pageSize.height
            val availableHeight = pageHeight - marginTop - marginBottom
            
            // 1. 先添加标题（假设标题固定占用一行）
            val titleParagraph = Paragraph(title)
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10f)
            
            // 模拟测量标题高度（iText 7 没有直接测量方法，用估算）
            val titleHeight = 40f
            
            // 2. 测量文字内容的高度（估算）
            val textHeight = estimateTextHeight(textContent, pageWidth = pageSize.width - marginLeft - marginRight)
            
            // 3. 计算图片可用高度
            var imageHeight = availableHeight - titleHeight - textHeight - 20f  // 20f 为额外间距
            
            // 4. 如果图片高度太小，说明文字太多放不下，压缩图片或调整策略
            if (imageHeight < minImageHeight) {
                // 方案A：压缩图片到最小允许高度
                imageHeight = minImageHeight
                // 此时图片和文字可能会超出页面，需要确保文档自动换页
            }
            
            // 5. 缩放图片
            val scaledBitmap = scaleBitmapToHeight(bitmap, imageHeight.toInt())
            val imgByteArray = bitmapToByteArray(scaledBitmap)
            val image = Image(com.itextpdf.io.image.ImageDataFactory.create(imgByteArray))
                .setWidth(UnitValue.createPercentValue(80f))  // 宽度占页面的80%
                .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                .setMarginBottom(15f)
            
            // 6. 按顺序添加到文档
            document.add(titleParagraph)
            document.add(image)
            document.add(Paragraph(textContent).setFontSize(12f))
            
            document.close()
            fos.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 估算文本高度（简易算法）
     * @param text 文本内容
     * @param pageWidth 页面宽度（点）
     * @param fontSize 字体大小（点）
     * @return 估算高度（点）
     */
    private fun estimateTextHeight(text: String, pageWidth: Float, fontSize: Float = 12f): Float {
        // 平均每个字符宽度约为字体大小的 0.5 倍（中英文混合取中值）
        val avgCharWidth = fontSize * 0.6f
        // 每行可容纳字符数
        val charsPerLine = (pageWidth / avgCharWidth).toInt()
        // 总行数（按换行符分割，再处理长行换行）
        val lines = text.split("\n").flatMap { line ->
            if (line.length <= charsPerLine) listOf(line)
            else line.chunked(charsPerLine)
        }
        // 行高约字体大小的 1.5 倍
        val lineHeight = fontSize * 1.5f
        return lines.size * lineHeight
    }
    
    /**
     * 将 Bitmap 缩放到指定高度（等比例）
     */
    private fun scaleBitmapToHeight(bitmap: Bitmap, targetHeight: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val scale = targetHeight.toFloat() / originalHeight
        val targetWidth = (originalWidth * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
    
    /**
     * Bitmap 转字节数组（用于 iText）
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
