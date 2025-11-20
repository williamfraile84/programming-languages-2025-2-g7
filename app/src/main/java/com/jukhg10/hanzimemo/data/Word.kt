package com.jukhg10.hanzimemo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "traditional")
    val traditional: String,

    @ColumnInfo(name = "simplified")
    val simplified: String,

    @ColumnInfo(name = "pinyin")
    val pinyin: String,

    @ColumnInfo(name = "definition")
    val definition: String
)