package com.jukhg10.hanzimemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jukhg10.hanzimemo.data.AppDatabase
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.ui.AppNavigation
import com.jukhg10.hanzimemo.ui.theme.HanziMemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getInstance(this)
        val repository = DictionaryRepository(
            characterDao = database.characterDao(),
            wordDao = database.wordDao(),
            studyDao = database.studyDao()
        )

        enableEdgeToEdge()
        setContent {
            HanziMemoTheme {
                AppNavigation(repository = repository)
            }
        }
    }
}