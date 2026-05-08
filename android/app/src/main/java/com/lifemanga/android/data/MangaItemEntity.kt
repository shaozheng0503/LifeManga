package com.lifemanga.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga_items")
data class MangaItemEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val styleKey: String,
    val userPrompt: String,
    val isFavorite: Boolean,
    val inputImagePathsCsv: String,
    val outputImagePathsCsv: String,
) {
    val inputImagePaths: List<String>
        get() = if (inputImagePathsCsv.isBlank()) emptyList() else inputImagePathsCsv.split('|')
    val outputImagePaths: List<String>
        get() = if (outputImagePathsCsv.isBlank()) emptyList() else outputImagePathsCsv.split('|')
    val style: MangaStyle?
        get() = MangaStyle.fromKey(styleKey)
}
