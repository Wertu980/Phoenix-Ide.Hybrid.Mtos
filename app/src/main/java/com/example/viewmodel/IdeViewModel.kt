package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Project
import com.example.data.WorkspaceFile
import com.example.data.WorkspaceRepository
import com.example.network.GeminiApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class GeminiStatus {
    object Idle : GeminiStatus()
    object Processing : GeminiStatus()
    data class Success(val response: String) : GeminiStatus()
    data class Error(val message: String) : GeminiStatus()
}

sealed class BuildStatus {
    object Idle : BuildStatus()
    object Running : BuildStatus()
    object Success : BuildStatus()
    data class Error(val logs: String) : BuildStatus()
}

class IdeViewModel(private val repository: WorkspaceRepository) : ViewModel() {

    // Projects list
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    // Active project
    private val _activeProject = MutableStateFlow<Project?>(null)
    val activeProject: StateFlow<Project?> = _activeProject.asStateFlow()

    // Workspace Files in active project
    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFile>> = _workspaceFiles.asStateFlow()

    // Selected file in editor
    private val _activeFile = MutableStateFlow<WorkspaceFile?>(null)
    val activeFile: StateFlow<WorkspaceFile?> = _activeFile.asStateFlow()

    // Current code inside editor (temporary non-persisted edits)
    private val _editorCode = MutableStateFlow("")
    val editorCode: StateFlow<String> = _editorCode.asStateFlow()

    private val _isCodeModified = MutableStateFlow(false)
    val isCodeModified: StateFlow<Boolean> = _isCodeModified.asStateFlow()

    // Simulated terminal history logs
    private val _terminalLogs = MutableStateFlow<List<String>>(listOf("Default multiplatform project environment initiated."))
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    // Settings
    private val _ideLayoutMode = MutableStateFlow("hybrid") // "studio", "vscode", "hybrid"
    val ideLayoutMode: StateFlow<String> = _ideLayoutMode.asStateFlow()

    private val _ideTheme = MutableStateFlow("One Dark Pro") // "One Dark Pro", "Cobalt", "Darcula", "Dracula"
    val ideTheme: StateFlow<String> = _ideTheme.asStateFlow()

    private val _emulatorPlatform = MutableStateFlow("android") // "android", "ios"
    val emulatorPlatform: StateFlow<String> = _emulatorPlatform.asStateFlow()

    private val _buildStatus = MutableStateFlow<BuildStatus>(BuildStatus.Idle)
    val buildStatus: StateFlow<BuildStatus> = _buildStatus.asStateFlow()

    // Gemini AI Code Assistant State
    private val _geminiStatus = MutableStateFlow<GeminiStatus>(GeminiStatus.Idle)
    val geminiStatus: StateFlow<GeminiStatus> = _geminiStatus.asStateFlow()

    private val _copilotPrompt = MutableStateFlow("")
    val copilotPrompt: StateFlow<String> = _copilotPrompt.asStateFlow()

    private var fileCollectJob: Job? = null

    init {
        loadSettings()
        observeProjects()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.getSetting("layout_mode")?.let { _ideLayoutMode.value = it }
            repository.getSetting("theme")?.let { _ideTheme.value = it }
            repository.getSetting("platform")?.let { _emulatorPlatform.value = it }
        }
    }

    private fun observeProjects() {
        viewModelScope.launch {
            repository.allProjects.collectLatest { pList ->
                _projects.value = pList
                if (pList.isNotEmpty() && _activeProject.value == null) {
                    val lastProjId = repository.getSetting("active_project_id")?.toIntOrNull()
                    val targetProj = pList.firstOrNull { it.id == lastProjId } ?: pList.first()
                    selectProject(targetProj)
                } else if (pList.isEmpty()) {
                    // Create a default project to begin with info
                    createProject("Kotlin Multiplatform Demo", "Compose Multiplatform Calculator")
                }
            }
        }
    }

    fun selectProject(project: Project) {
        _activeProject.value = project
        viewModelScope.launch {
            repository.saveSetting("active_project_id", project.id.toString())
        }

        // Cancel previous file registration and start observing new project's files
        fileCollectJob?.cancel()
        fileCollectJob = viewModelScope.launch {
            repository.getFilesByProject(project.id).collectLatest { files ->
                _workspaceFiles.value = files
                val lastPath = repository.getSetting("active_file_${project.id}")
                val file = files.firstOrNull { it.filePath == lastPath } 
                    ?: files.firstOrNull { !it.isDirectory && it.filePath.endsWith(".kt") }
                    ?: files.firstOrNull { !it.isDirectory }
                
                file?.let { selectFile(it) }
            }
        }
    }

    fun selectFile(file: WorkspaceFile) {
        _activeFile.value = file
        _editorCode.value = file.content
        _isCodeModified.value = false
        
        activeProject.value?.let { proj ->
            viewModelScope.launch {
                repository.saveSetting("active_file_${proj.id}", file.filePath)
            }
        }
    }

    fun updateEditorCode(newCode: String) {
        _editorCode.value = newCode
        _isCodeModified.value = _activeFile.value?.content != newCode
    }

    fun saveCurrentFile() {
        val file = _activeFile.value ?: return
        val currentCode = _editorCode.value
        viewModelScope.launch {
            repository.updateFileContent(file.id, currentCode)
            _activeFile.value = file.copy(content = currentCode)
            _isCodeModified.value = false
            addTerminalLog("File saved: ${file.filePath}")
        }
    }

    fun createProject(name: String, templateType: String) {
        viewModelScope.launch {
            val newId = repository.insertProject(Project(name = name, templateType = templateType))
            addTerminalLog("Created project: $name with template $templateType")
        }
    }

    fun deleteCurrentProject() {
        val active = _activeProject.value ?: return
        viewModelScope.launch {
            repository.deleteProject(active.id)
            _activeProject.value = null
            _activeFile.value = null
            _workspaceFiles.value = emptyList()
            _editorCode.value = ""
            addTerminalLog("Deleted project: ${active.name}")
        }
    }

    fun setIdeLayoutMode(mode: String) {
        _ideLayoutMode.value = mode
        viewModelScope.launch { repository.saveSetting("layout_mode", mode) }
    }

    fun setIdeTheme(theme: String) {
        _ideTheme.value = theme
        viewModelScope.launch { repository.saveSetting("theme", theme) }
    }

    fun setEmulatorPlatform(platform: String) {
        _emulatorPlatform.value = platform
        viewModelScope.launch { repository.saveSetting("platform", platform) }
    }

    fun updateCopilotPrompt(prompt: String) {
        _copilotPrompt.value = prompt
    }

    fun clearTerminal() {
        _terminalLogs.value = listOf("Terminal cleared.")
    }

    fun addTerminalLog(log: String) {
        _terminalLogs.value = _terminalLogs.value + "> $log"
    }

    fun runKmpBuild() {
        if (_buildStatus.value is BuildStatus.Running) return
        
        _buildStatus.value = BuildStatus.Running
        viewModelScope.launch {
            val active = _activeProject.value
            val buildLogs = mutableListOf<String>()
            val appendLog = { s: String ->
                buildLogs.add(s)
                _terminalLogs.value = _terminalLogs.value + s
            }

            appendLog("> Starting gradle daemon to compile in parallel...")
            delay(800)
            appendLog("> :shared:compileKotlinIosArm64 UP-TO-DATE")
            delay(500)
            appendLog("> :shared:compileKotlinAndroid SUCCESS [0.4s]")
            delay(400)
            appendLog("> :shared:linkReleaseFrameworkIos SUCCESS")
            delay(600)
            appendLog("> :androidApp:assembleDebug SUCCESS [1.2s]")
            delay(400)
            appendLog("> Syncing multiplatform project artifacts with device simulator...")
            delay(500)
            appendLog("> Launching application on simulated ${if (_emulatorPlatform.value == "android") "Android Pixel Fold" else "iOS iPhone Pro"} inside IDE split view...")

            _buildStatus.value = BuildStatus.Success
        }
    }

    fun triggerGeminiCopilot() {
        val prompt = _copilotPrompt.value.trim()
        if (prompt.isEmpty()) return

        _geminiStatus.value = GeminiStatus.Processing
        viewModelScope.launch {
            val activeFile = _activeFile.value
            val systemPrompt = """You are a software co-pilot for a modern Android Studio + VS Code Hybrid IDE.
Your user is writing Kotlin Multiplatform (KMP) code.
Keep code blocks short, clean, and directly matching Jetpack Compose or Kotlin best practices.
Provide ONLY the code or highly refined direct answers, avoid any introductory filler or long conversational noise where possible, so it is easy to paste."""

            val userContextPrompt = """Active File path: ${activeFile?.filePath ?: "None"}
Active File current code:
${activeFile?.content ?: "None"}

User Copilot prompt:
$prompt"""

            val response = GeminiApiClient.generateCode(userContextPrompt, systemPrompt)
            if (response.startsWith("Error:") || response.startsWith("Exception occurred")) {
                _geminiStatus.value = GeminiStatus.Error(response)
            } else {
                _geminiStatus.value = GeminiStatus.Success(response)
            }
        }
    }

    fun applyCopilotCodeToActiveFile(gptCode: String) {
        val cleanCode = extractCodeBlock(gptCode)
        updateEditorCode(cleanCode)
        saveCurrentFile()
        _geminiStatus.value = GeminiStatus.Idle
        _copilotPrompt.value = ""
    }

    private fun extractCodeBlock(raw: String): String {
        val marker = "```"
        if (!raw.contains(marker)) return raw
        val parts = raw.split(marker)
        for (i in 1 until parts.size step 2) {
            val block = parts[i]
            val lines = block.lines()
            if (lines.isNotEmpty()) {
                val header = lines[0].trim().lowercase()
                if (header == "kotlin" || header == "swift" || header == "gradle" || header == "groovy" || header == "xml") {
                    return lines.drop(1).joinToString("\n").trim()
                }
            }
            return block.trim()
        }
        return raw.trim()
    }
}
