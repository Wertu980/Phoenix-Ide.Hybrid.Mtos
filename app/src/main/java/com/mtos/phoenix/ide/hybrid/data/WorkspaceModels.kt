package com.mtos.phoenix.ide.hybrid.data

data class Project(
    val id: Int = 0,
    val name: String,
    val templateType: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class WorkspaceFile(
    val id: Int = 0,
    val projectId: Int,
    val filePath: String, // e.g., "shared/src/commonMain/kotlin/App.kt"
    val content: String,
    val isDirectory: Boolean = false
)

data class IdeSetting(
    val key: String,
    val value: String
)
