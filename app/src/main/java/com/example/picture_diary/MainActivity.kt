package com.example.picture_diary

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.example.picture_diary.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var listAdapter: ArrayAdapter<String>
    private val listItems = mutableListOf<String>()
    private val listIds = mutableListOf<Long>()
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // 初始化数据库帮助类
        dbHelper = DatabaseHelper(this)
        
        // 加载 List 数据
        loadLists()
        
        // 初始化 ListView
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listItems)
        binding.contentMain.listView.adapter = listAdapter
        
        // 添加点击事件监听器
        binding.contentMain.listView.setOnItemClickListener { parent, view, position, id ->
            val selectedItem = listItems[position]
            val selectedId = listIds[position]
            val intent = android.content.Intent(this, ListDetailActivity::class.java)
            intent.putExtra("LIST_NAME", selectedItem)
            intent.putExtra("LIST_ID", selectedId)
            startActivity(intent)
        }
        
        // 添加长按事件监听器
        binding.contentMain.listView.setOnItemLongClickListener { parent, view, position, id ->
            val currentItem = listItems[position]
            val currentId = listIds[position]
            
            // 创建对话框构建器
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Edit List Name")
            
            // 添加输入框，默认显示当前名称
            val input = android.widget.EditText(this)
            input.setText(currentItem)
            builder.setView(input)
            
            // 设置确定按钮
            builder.setPositiveButton("Save") { dialog, which ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    // 更新数据库
                    dbHelper.updateList(currentId, newName)
                    // 更新列表
                    listItems[position] = newName
                    listAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "Updated: $newName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a list name", Toast.LENGTH_SHORT).show()
                }
            }
            
            // 设置取消按钮
            builder.setNegativeButton("Cancel", null)
            
            // 显示对话框
            builder.show()
            true
        }

        binding.fab.setOnClickListener { view ->
            // 创建对话框构建器
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Create New List")
            
            // 添加输入框
            val input = android.widget.EditText(this)
            input.hint = "Enter list name"
            builder.setView(input)
            
            // 设置确定按钮
            builder.setPositiveButton("Create") { dialog, which ->
                val listName = input.text.toString().trim()
                if (listName.isNotEmpty()) {
                    // 插入数据库
                    val listId = dbHelper.insertList(listName)
                    // 添加到列表
                    listItems.add(listName)
                    listIds.add(listId)
                    listAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "Added: $listName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a list name", Toast.LENGTH_SHORT).show()
                }
            }
            
            // 设置取消按钮
            builder.setNegativeButton("Cancel", null)
            
            // 显示对话框
            builder.show()
        }
    }
    
    // 加载 List 数据
    private fun loadLists() {
        val lists = dbHelper.getAllLists()
        listItems.clear()
        listIds.clear()
        for (list in lists) {
            listIds.add(list.first)
            listItems.add(list.second)
        }
        // 如果没有数据，添加默认数据
        if (listItems.isEmpty()) {
            val defaultLists = listOf("每月自拍", "每年家庭合照", "旅行合照")
            for (listName in defaultLists) {
                val listId = dbHelper.insertList(listName)
                listIds.add(listId)
                listItems.add(listName)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_about -> {
                // 启动关于应用页面
                val intent = android.content.Intent(this, AboutActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_update -> {
                // 显示应用更新对话框
                val builder = android.app.AlertDialog.Builder(this)
                builder.setTitle("应用更新")
                builder.setMessage("当前版本已是最新版本。")
                builder.setPositiveButton("确定", null)
                builder.show()
                true
            }
            R.id.action_donate -> {
                // 启动打赏页面
                val intent = android.content.Intent(this, DonateActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}