package com.example.picture_diary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "picture_diary.db"
        private const val DATABASE_VERSION = 2
        
        // List 表
        private const val TABLE_LIST = "lists"
        private const val COLUMN_LIST_ID = "id"
        private const val COLUMN_LIST_NAME = "name"
        
        // Photo 表
        private const val TABLE_PHOTO = "photos"
        private const val COLUMN_PHOTO_ID = "id"
        private const val COLUMN_PHOTO_LIST_ID = "list_id"
        private const val COLUMN_PHOTO_IMAGE_PATH = "image_path"
        private const val COLUMN_PHOTO_TIME = "time"
        private const val COLUMN_PHOTO_LOCATION = "location"
        private const val COLUMN_PHOTO_NOTE = "note"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建 List 表
        val createListTable = "CREATE TABLE $TABLE_LIST ($COLUMN_LIST_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_LIST_NAME TEXT)"
        db.execSQL(createListTable)
        
        // 创建 Photo 表
        val createPhotoTable = "CREATE TABLE $TABLE_PHOTO ($COLUMN_PHOTO_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_PHOTO_LIST_ID INTEGER, $COLUMN_PHOTO_IMAGE_PATH TEXT, $COLUMN_PHOTO_TIME TEXT, $COLUMN_PHOTO_LOCATION TEXT, $COLUMN_PHOTO_NOTE TEXT, FOREIGN KEY($COLUMN_PHOTO_LIST_ID) REFERENCES $TABLE_LIST($COLUMN_LIST_ID))"
        db.execSQL(createPhotoTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 删除旧表
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PHOTO")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LIST")
        // 创建新表
        onCreate(db)
    }

    // 插入 List
    fun insertList(name: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LIST_NAME, name)
        }
        return db.insert(TABLE_LIST, null, values)
    }

    // 更新 List
    fun updateList(id: Long, name: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LIST_NAME, name)
        }
        return db.update(TABLE_LIST, values, "$COLUMN_LIST_ID = ?", arrayOf(id.toString()))
    }

    // 删除 List
    fun deleteList(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_PHOTO, "$COLUMN_PHOTO_LIST_ID = ?", arrayOf(id.toString()))
        db.delete(TABLE_LIST, "$COLUMN_LIST_ID = ?", arrayOf(id.toString()))
    }

    // 获取所有 List
    fun getAllLists(): List<Pair<Long, String>> {
        val db = readableDatabase
        val lists = mutableListOf<Pair<Long, String>>()
        val cursor = db.rawQuery("SELECT $COLUMN_LIST_ID, $COLUMN_LIST_NAME FROM $TABLE_LIST", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                lists.add(Pair(id, name))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lists
    }

    // 插入 Photo
    fun insertPhoto(listId: Long, imagePath: String, time: String, location: String, note: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PHOTO_LIST_ID, listId)
            put(COLUMN_PHOTO_IMAGE_PATH, imagePath)
            put(COLUMN_PHOTO_TIME, time)
            put(COLUMN_PHOTO_LOCATION, location)
            put(COLUMN_PHOTO_NOTE, note)
        }
        return db.insert(TABLE_PHOTO, null, values)
    }

    // 更新 Photo
    fun updatePhoto(id: Long, note: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PHOTO_NOTE, note)
        }
        return db.update(TABLE_PHOTO, values, "$COLUMN_PHOTO_ID = ?", arrayOf(id.toString()))
    }

    // 删除 Photo
    fun deletePhoto(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_PHOTO, "$COLUMN_PHOTO_ID = ?", arrayOf(id.toString()))
    }

    // 获取指定 List 的所有 Photo
    fun getPhotosByListId(listId: Long): List<PhotoData> {
        val db = readableDatabase
        val photos = mutableListOf<PhotoData>()
        val cursor = db.rawQuery("SELECT $COLUMN_PHOTO_ID, $COLUMN_PHOTO_IMAGE_PATH, $COLUMN_PHOTO_TIME, $COLUMN_PHOTO_LOCATION, $COLUMN_PHOTO_NOTE FROM $TABLE_PHOTO WHERE $COLUMN_PHOTO_LIST_ID = ?", arrayOf(listId.toString()))
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(0)
                val imagePath = cursor.getString(1)
                val time = cursor.getString(2)
                val location = cursor.getString(3)
                val note = cursor.getString(4)
                val image = BitmapFactory.decodeFile(imagePath)
                if (image != null) {
                    photos.add(PhotoData(id, image, time, location, note))
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        return photos
    }

    // 获取指定 List 的所有 Photo 的时间信息（用于日历视图）
    fun getPhotoTimesByListId(listId: Long): List<String> {
        val db = readableDatabase
        val times = mutableListOf<String>()
        val cursor = db.rawQuery("SELECT $COLUMN_PHOTO_TIME FROM $TABLE_PHOTO WHERE $COLUMN_PHOTO_LIST_ID = ?", arrayOf(listId.toString()))
        if (cursor.moveToFirst()) {
            do {
                val time = cursor.getString(0)
                times.add(time)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return times
    }

    // 数据类：PhotoData
    data class PhotoData(val id: Long, val image: Bitmap, val time: String, val location: String, val note: String)
}
