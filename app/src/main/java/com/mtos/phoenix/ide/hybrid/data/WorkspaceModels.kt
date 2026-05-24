package com.mtos.phoenix.ide.hybrid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val templateType: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workspace_files")
data class WorkspaceFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val filePath: String, // e.g., "shared/src/commonMain/kotlin/App.kt"
    val content: String,
    val isDirectory: Boolean = false
)

@Entity(tableName = "ide_settings")
data class IdeSetting(
    @PrimaryKey val key: String,
    val value: String
)
