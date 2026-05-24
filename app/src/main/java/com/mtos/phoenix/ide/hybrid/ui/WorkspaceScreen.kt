package com.mtos.phoenix.ide.hybrid.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtos.phoenix.ide.hybrid.data.Project
import com.mtos.phoenix.ide.hybrid.data.WorkspaceFile
import com.mtos.phoenix.ide.hybrid.viewmodel.BuildStatus
import com.mtos.phoenix.ide.hybrid.viewmodel.GeminiStatus
import com.mtos.phoenix.ide.hybrid.viewmodel.IdeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: IdeViewModel,
    onBackToHome: () -> Unit
) {
    val activeProject by viewModel.activeProject.collectAsState()
    val workspaceFiles by viewModel.workspaceFiles.collectAsState()
    val activeFile by viewModel.activeFile.collectAsState()
    val editorCode by viewModel.editorCode.collectAsState()
    val isCodeModified by viewModel.isCodeModified.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val ideLayoutMode by viewModel.ideLayoutMode.collectAsState()
    val ideTheme by viewModel.ideTheme.collectAsState()
    val emulatorPlatform by viewModel.emulatorPlatform.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val geminiStatus by viewModel.geminiStatus.collectAsState()
    val copilotPrompt by viewModel.copilotPrompt.collectAsState()

    val isCloudCompilerEnabled by viewModel.isCloudCompilerEnabled.collectAsState()
    val cloudCompilerServerUrl by viewModel.cloudCompilerServerUrl.collectAsState()

    var showFileExplorer by remember { mutableStateOf(true) }
    var showTerminalLogs by remember { mutableStateOf(true) }
    var showEmulatorPreview by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (activeProject == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeProject?.name ?: "IDE Editor",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = activeProject?.templateType ?: "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHome, modifier = Modifier.testTag("workspace_back_btn")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Exit Workspace")
                    }
                },
                actions = {
                    // Quick Action: Build & Run App
                    Button(
                        onClick = { viewModel.runKmpBuild() },
                        enabled = buildStatus !is BuildStatus.Running,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (buildStatus is BuildStatus.Running) Color.Gray else Color(0xFF3DDC84),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 6.dp).testTag("run_build_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (buildStatus is BuildStatus.Running) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = "Run compiler",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (buildStatus is BuildStatus.Running) "BUILDING..." else "RUN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Toggles for UI panels
                    IconButton(onClick = { showFileExplorer = !showFileExplorer }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            tint = if (showFileExplorer) MaterialTheme.colorScheme.primary else Color.Gray,
                            contentDescription = "Toggle Files"
                        )
                    }
                    IconButton(onClick = { showTerminalLogs = !showTerminalLogs }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            tint = if (showTerminalLogs) MaterialTheme.colorScheme.primary else Color.Gray,
                            contentDescription = "Toggle Console"
                        )
                    }
                    IconButton(onClick = { showEmulatorPreview = !showEmulatorPreview }) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            tint = if (showEmulatorPreview) MaterialTheme.colorScheme.primary else Color.Gray,
                            contentDescription = "Toggle Device Screen"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = !showSettingsDialog }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            tint = if (showSettingsDialog) MaterialTheme.colorScheme.primary else Color.Gray,
                            contentDescription = "Engine Core Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E24)
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F12))
                .padding(innerPadding)
        ) {
            // Panel 1: File Explorer Tree
            AnimatedVisibility(
                visible = showFileExplorer,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(220.dp)
                        .background(Color(0xFF1A1A1F))
                        .border(1.dp, Color(0xFF2D2D37))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF25252D))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PROJECT FILES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (workspaceFiles.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No files found inside storage",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp)
                            ) {
                                items(workspaceFiles) { file ->
                                    val isSelected = activeFile?.filePath == file.filePath
                                    val parts = file.filePath.split('/')
                                    val depth = parts.size - 1

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!file.isDirectory) {
                                                    viewModel.selectFile(file)
                                                }
                                            }
                                            .background(if (isSelected) Color(0xFF2D2D3D) else Color.Transparent)
                                            .padding(
                                                start = (12 + (depth * 10)).dp,
                                                end = 12.dp,
                                                top = 6.dp,
                                                bottom = 6.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (file.isDirectory) Icons.Default.List else Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = if (file.isDirectory) Color(0xFFFFC107) else Color(0xFF64B5F6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = parts.last(),
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Panel 2: Code Editor & Console Logs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, Color(0xFF2D2D37))
            ) {
                // Editor File Header Tab
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp)
                        .background(Color(0xFF1E1E24))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFF3DDC84),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeFile?.filePath ?: "No active file",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCodeModified) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFFFB74D))
                                )
                            }
                        }

                        if (isCodeModified) {
                            TextButton(
                                onClick = { viewModel.saveCurrentFile() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Editor Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF16161A))
                ) {
                    if (activeFile != null) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Line numbers gutter
                            val lines = editorCode.lines()
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(36.dp)
                                    .background(Color(0xFF131316))
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                for (i in 1..lines.size) {
                                    Text(
                                        text = "$i",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
                                    )
                                }
                            }

                            // Text Editor input field
                            BasicTextField(
                                value = editorCode,
                                onValueChange = { viewModel.updateEditorCode(it) },
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .weight(1f)
                                    .testTag("code_editor_field")
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Select a file from the explorer to begin editing.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Simulated Terminal Logs Gutter
                AnimatedVisibility(
                    visible = showTerminalLogs,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color(0xFF0C0C0E))
                            .border(1.dp, Color(0xFF22222B))
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF15151A))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF3DDC84),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TERMINAL LOGGER CONSOLE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.clearTerminal() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        "CLEAR logs",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                reverseLayout = true
                            ) {
                                items(terminalLogs.reversed()) { log ->
                                    Text(
                                        text = log,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = when {
                                            log.contains("SUCCESS", ignoreCase = true) -> Color(0xFF81C784)
                                            log.contains("ERROR", ignoreCase = true) -> Color(0xFFE57373)
                                            log.startsWith(">") -> Color(0xFF3DDC84)
                                            else -> Color(0xFFCCCCCC)
                                        },
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Panel 3: Responsive Live Device Emulator Chassis Preview
            AnimatedVisibility(
                visible = showEmulatorPreview,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .background(Color(0xFF1E1E24))
                        .border(1.dp, Color(0xFF2D2D37))
                        .padding(8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header controller for device preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "EMULATOR DEVICE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )

                            // Emulator platform select toggle buttons
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF131316))
                            ) {
                                listOf("android" to "Android", "ios" to "iOS").forEach { (id, name) ->
                                    val isPlatformSelected = emulatorPlatform == id
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPlatformSelected) Color.White else Color.Gray,
                                        modifier = Modifier
                                            .clickable { viewModel.setEmulatorPlatform(id) }
                                            .background(if (isPlatformSelected) Color(0xFF2196F3) else Color.Transparent)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Adaptive mock client screen frame render
                        InteractiveEmulator(
                            templateType = activeProject?.templateType ?: "",
                            codeContent = editorCode,
                            platform = emulatorPlatform,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        var localUrlInput by remember { mutableStateOf(cloudCompilerServerUrl) }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compiler Server & Engine Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Choose whether your code package is built using real servers or simulated core on device.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Divider(color = Color.Gray.copy(alpha = 0.2f))
                    
                    // Toggle option for Remote Cloud Compiler server
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Remote Compiler Server", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Send codebase directly to an online compiler server", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isCloudCompilerEnabled,
                                onCheckedChange = { viewModel.setCloudCompilerEnabled(it) }
                            )
                        }
                    }
                    
                    if (isCloudCompilerEnabled) {
                        // Compiler API endpoint input field
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Compiler API URL Endpoint", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            OutlinedTextField(
                                value = localUrlInput,
                                onValueChange = { 
                                    localUrlInput = it
                                    viewModel.setCloudCompilerUrl(it) 
                                },
                                textStyle = TextStyle(fontSize = 12.sp),
                                placeholder = { Text("https://compiler.yourdomain.com/compile", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text(
                                text = "Your package server configuration must parse compiler request structures carrying the file list payload successfully.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        // Local simulation core active notice info
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top, 
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Local simulation core is active. Clicking RUN compiles the hot-reload mockup immediately inside the split-screen frame without remote connections.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    
                    Divider(color = Color.Gray.copy(alpha = 0.2f))
                    
                    // Device target platform custom chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Default Preview Platform", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("android" to "Android Emulation", "ios" to "iOS Emulation").forEach { (platformId, label) ->
                                val selected = emulatorPlatform == platformId
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setEmulatorPlatform(platformId) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
