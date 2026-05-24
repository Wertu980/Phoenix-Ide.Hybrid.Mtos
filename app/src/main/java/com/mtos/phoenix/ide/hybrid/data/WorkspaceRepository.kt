package com.mtos.phoenix.ide.hybrid.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class WorkspaceRepository(
    private val context: Context,
    private val workspaceDao: WorkspaceDao
) {

    private val _allProjectsFlow = MutableStateFlow<List<Project>>(emptyList())
    val allProjects: Flow<List<Project>> = _allProjectsFlow.asStateFlow()

    // Map of projectId to files list Flow to dynamically update editor and file trees
    private val filesFlowMap = mutableMapOf<Int, MutableStateFlow<List<WorkspaceFile>>>()

    init {
        _allProjectsFlow.value = readProjects()
    }

    private val rootDir: File
        get() {
            // Priority 1: Check standard external storage if it is already writeable/granted
            try {
                val base = File("/storage/emulated/0")
                if (base.exists() && base.canWrite()) {
                    return base
                }
                val envBase = Environment.getExternalStorageDirectory()
                if (envBase.exists() && envBase.canWrite()) {
                    return envBase
                }
            } catch (e: Exception) {
                // Ignore and proceed to safe directories
            }

            // Priority 2: Use external app-specific sandbox storage (always allowed and writeable, no permissions needed)
            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) {
                val safeExternal = File(extDir, "PhoenixIDE")
                if (!safeExternal.exists()) {
                    safeExternal.mkdirs()
                }
                if (safeExternal.exists() && safeExternal.canWrite()) {
                    return safeExternal
                }
            }

            // Priority 3: Fall back to secure internal app storage (always writeable and safe, no permissions needed)
            val safeInternal = File(context.filesDir, "PhoenixIDE")
            if (!safeInternal.exists()) {
                safeInternal.mkdirs()
            }
            return safeInternal
        }

    private val projectsFile: File
        get() = File(rootDir, ".phoenix_projects.txt")

    private val settingsFile: File
        get() = File(rootDir, ".phoenix_settings.txt")

    // CSV format storage helper for Projects
    private fun readProjects(): List<Project> {
        val file = projectsFile
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull { line ->
                val tokens = line.split("|")
                if (tokens.size >= 3) {
                    Project(
                        id = tokens[0].toIntOrNull() ?: 0,
                        name = tokens[1],
                        templateType = tokens[2],
                        createdAt = tokens.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeProjects(projects: List<Project>) {
        try {
            val file = projectsFile
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val content = projects.joinToString("\n") { p ->
                "${p.id}|${p.name}|${p.templateType}|${p.createdAt}"
            }
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Key Value storage helper for Settings
    private fun readSettings(): Map<String, String> {
        val file = settingsFile
        if (!file.exists()) return emptyMap()
        return try {
            file.readLines().mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx != -1) {
                    line.substring(0, idx) to line.substring(idx + 1)
                } else null
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun writeSettings(settings: Map<String, String>) {
        try {
            val file = settingsFile
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val content = settings.entries.joinToString("\n") { "${it.key}=${it.value}" }
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getProjectDir(project: Project): File {
        val cleanName = project.name.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim()
        return File(rootDir, cleanName)
    }

    fun getFilesByProject(projectId: Int): Flow<List<WorkspaceFile>> {
        val flow = filesFlowMap.getOrPut(projectId) { MutableStateFlow(emptyList()) }
        refreshFilesForProject(projectId)
        return flow.asStateFlow()
    }

    private fun refreshFilesForProject(projectId: Int) {
        val project = readProjects().firstOrNull { it.id == projectId } ?: return
        val flow = filesFlowMap.getOrPut(projectId) { MutableStateFlow(emptyList()) }
        val dir = getProjectDir(project)
        if (dir.exists()) {
            val list = mutableListOf<WorkspaceFile>()
            var idCounter = 1

            fun walk(currentFile: File, relativePath: String) {
                val files = currentFile.listFiles() ?: return
                val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                for (f in sorted) {
                    if (f.name.startsWith(".")) continue
                    val nextRel = if (relativePath.isEmpty()) f.name else "$relativePath/${f.name}"
                    if (f.isDirectory) {
                        list.add(WorkspaceFile(
                            id = idCounter++,
                            projectId = projectId,
                            filePath = nextRel,
                            content = "",
                            isDirectory = true
                        ))
                        walk(f, nextRel)
                    } else {
                        list.add(WorkspaceFile(
                            id = idCounter++,
                            projectId = projectId,
                            filePath = nextRel,
                            content = try { f.readText() } catch (e: Exception) { "" },
                            isDirectory = false
                        ))
                    }
                }
            }
            walk(dir, "")
            flow.value = list
        } else {
            flow.value = emptyList()
        }
    }

    suspend fun getFileByPath(projectId: Int, filePath: String): WorkspaceFile? {
        val project = readProjects().firstOrNull { it.id == projectId } ?: return null
        val file = File(getProjectDir(project), filePath)
        if (file.exists() && file.isFile) {
            return WorkspaceFile(
                id = filePath.hashCode(),
                projectId = projectId,
                filePath = filePath,
                content = try { file.readText() } catch (e: Exception) { "" },
                isDirectory = false
            )
        }
        return null
    }

    suspend fun insertProject(project: Project): Int {
        val current = readProjects()
        val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val newProj = project.copy(id = nextId)
        val updated = current + newProj
        writeProjects(updated)
        _allProjectsFlow.value = updated

        // Prepare physical dirs and template assets on external storage disk
        try {
            createTemplateFilesOnDisk(newProj)
            refreshFilesForProject(nextId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return nextId
    }

    suspend fun deleteProject(projectId: Int) {
        val current = readProjects()
        val target = current.firstOrNull { it.id == projectId }
        if (target != null) {
            val updated = current.filterNot { it.id == projectId }
            writeProjects(updated)
            _allProjectsFlow.value = updated

            // Safely delete physical directories recursively if they exist
            try {
                val dir = getProjectDir(target)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            filesFlowMap.remove(projectId)
        }
    }

    suspend fun updateFileContent(fileId: Int, newContent: String) {
        for ((projId, flow) in filesFlowMap) {
            val list = flow.value
            val match = list.firstOrNull { it.id == fileId }
            if (match != null) {
                updateFileByPath(projId, match.filePath, newContent)
                break
            }
        }
    }

    suspend fun updateFileByPath(projectId: Int, filePath: String, newContent: String) {
        val project = readProjects().firstOrNull { it.id == projectId } ?: return
        try {
            val file = File(getProjectDir(project), filePath)
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            file.writeText(newContent)
            refreshFilesForProject(projectId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveSetting(key: String, value: String) {
        val current = readSettings().toMutableMap()
        current[key] = value
        writeSettings(current)
    }

    suspend fun getSetting(key: String): String? {
        return readSettings()[key]
    }

    private fun copyAssetFolder(assetPath: String, targetFolder: File, replacements: Map<String, String>) {
        val assets = try {
            context.assets.list(assetPath)
        } catch (e: Exception) {
            null
        } ?: return

        if (assets.isEmpty()) {
            // It's a file
            try {
                val content = context.assets.open(assetPath).use { it.bufferedReader().readText() }
                var updatedContent = content
                for ((placeholder, replacement) in replacements) {
                    updatedContent = updatedContent.replace(placeholder, replacement)
                }
                
                val fileName = File(assetPath).name
                val targetFile = if (fileName == "MainActivity.kt") {
                    val packageSubPath = replacements["__PACKAGE_NAME__"]?.replace('.', '/') ?: "com/example"
                    File(targetFolder, "$packageSubPath/$fileName")
                } else {
                    File(targetFolder, fileName)
                }
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(updatedContent)
            } catch (e: Exception) {
                targetFolder.mkdirs()
            }
        } else {
            // It is a directory
            targetFolder.mkdirs()
            for (asset in assets) {
                val childAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                val childTargetFolder = File(targetFolder, asset)
                copyAssetFolder(childAssetPath, childTargetFolder, replacements)
            }
        }
    }

    private fun createTemplateFilesOnDisk(project: Project) {
        val rootDir = getProjectDir(project)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        val templateType = project.templateType
        if (templateType.contains("Android", ignoreCase = true) || templateType.contains("Calculator", ignoreCase = true)) {
            var packageName = "com.mtos.phoenix.ide.hybrid.androidapp"
            var targetSdk = "34"

            val packageRegex = """Namespace:\s*([a-zA-Z0-9._]+)""".toRegex()
            packageRegex.find(templateType)?.let {
                packageName = it.groupValues[1]
            }
            val sdkRegex = """API\s*(\2[0-9]|\3[0-9]|34|35)""".toRegex()
            sdkRegex.find(templateType)?.let {
                targetSdk = it.groupValues[1]
            }

            val replacements = mapOf(
                "__PROJECT_NAME__" to project.name,
                "__PACKAGE_NAME__" to packageName,
                "__TARGET_SDK__" to targetSdk,
                "__MAIN_ACTIVITY_NAME__" to "MainActivity"
            )

            // Copy recursively from templates/android assets
            copyAssetFolder("templates/android", rootDir, replacements)

        } else {
            // Flutter Project Creation using templates/flutter assets
            var orgName = "com.mtos.phoenix.ide.hybrid"
            val orgRegex = """Org:\s*([a-zA-Z0-9._]+)""".toRegex()
            orgRegex.find(templateType)?.let {
                orgName = it.groupValues[1]
            }

            val appNameLower = project.name.lowercase().replace(Regex("[^a-z0-9_]"), "")
            val packageName = "$orgName.$appNameLower"

            val replacements = mapOf(
                "__PROJECT_NAME__" to project.name,
                "__PROJECT_NAME_LOWER__" to appNameLower,
                "__PACKAGE_NAME__" to packageName
            )

            // Copy recursively from templates/flutter assets
            copyAssetFolder("templates/flutter", rootDir, replacements)

            // Create placeholder extra folders if necessary
            val pList = mutableListOf<String>()
            if (templateType.contains("iOS")) pList.add("ios")
            if (templateType.contains("Web")) pList.add("web")
            pList.forEach { platform ->
                File(rootDir, platform).mkdirs()
            }
        }
    }
}
