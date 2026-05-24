package com.mtos.phoenix.ide.hybrid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: Int)

    @Query("SELECT * FROM workspace_files WHERE projectId = :projectId")
    fun getFilesByProject(projectId: Int): Flow<List<WorkspaceFile>>

    @Query("SELECT * FROM workspace_files WHERE projectId = :projectId AND filePath = :filePath LIMIT 1")
    suspend fun getFileByPath(projectId: Int, filePath: String): WorkspaceFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<WorkspaceFile>)

    @Query("UPDATE workspace_files SET content = :newContent WHERE id = :fileId")
    suspend fun updateFileContent(fileId: Int, newContent: String)

    @Query("UPDATE workspace_files SET content = :newContent WHERE projectId = :projectId AND filePath = :filePath")
    suspend fun updateFileByPath(projectId: Int, filePath: String, newContent: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: IdeSetting)

    @Query("SELECT * FROM ide_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): IdeSetting?
}
