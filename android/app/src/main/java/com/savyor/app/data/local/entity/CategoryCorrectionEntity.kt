package com.savyor.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_corrections")
data class CategoryCorrectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "reel_shortcode")
    val reelShortcode: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "from_category")
    val fromCategory: String,

    @ColumnInfo(name = "to_category")
    val toCategory: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
