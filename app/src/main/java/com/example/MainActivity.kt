package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.Project
import com.example.data.WorkspaceFile
import com.example.data.WorkspaceRepository
import com.example.ui.CodeHighlighter
import com.example.ui.InteractiveEmulator
import com.example.viewmodel.BuildStatus
import com.example.viewmodel.GeminiStatus
import com.example.viewmodel.IdeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create standard App VM
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = WorkspaceRepository(database.workspaceDao())
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return IdeViewModel(repository) as T
            }
        }
        val viewModel: IdeViewModel by viewModels { factory }

        setContent {
            // CENTRALIZED DESIGN STYLING & COLORS
            val activeTheme by viewModel.ideTheme.collectAsState()
            
            // Generate ColorScheme based on selected theme
            val appColors = remember(activeTheme) {
                when (activeTheme) {
                    "Cobalt" -> IdeColors(
                        sidebarBg = Color(0xFF001F4D),
                        editorBg = Color(0xFF001633),
                        statusBg = Color(0xFF000E22),
                        accent = Color(0xFFFFC600),
                        primaryText = Color(0xFFCDD6F4),
                        secondaryText = Color(0xFF8E9AAA),
                        divider = Color(0xFF002B66)
                    )
                    "Darcula" -> IdeColors(
                        sidebarBg = Color(0xFF3C3F41),
                        editorBg = Color(0xFF2B2B2B),
                        statusBg = Color(0xFF242627),
                        accent = Color(0xFFCC7832),
                        primaryText = Color(0xFFA9B7C6),
                        secondaryText = Color(0xFF7F7F7F),
                        divider = Color(0xFF4B4B4B)
                    )
                    else -> IdeColors( // One Dark Pro (Default)
                        sidebarBg = Color(0xFF1E1E24),
                        editorBg = Color(0xFF141416),
                        statusBg = Color(0xFF0E0E10),
                        accent = Color(0xFFF95B6A),
                        primaryText = Color(0xFFCDD6F4),
                        secondaryText = Color(0xFF7F848E),
                        divider = Color(0xFF2B2C30)
                    )
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = appColors.accent,
                    background = appColors.editorBg,
                    surface = appColors.sidebarBg
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = appColors.editorBg
                ) {
                    MainIdeWorkspace(viewModel, appColors)
                }
            }
        }
    }
}

// Custom IDE styling configuration mapper
data class IdeColors(
    val sidebarBg: Color,
    val editorBg: Color,
    val statusBg: Color,
    val accent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val divider: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainIdeWorkspace(viewModel: IdeViewModel, colors: IdeColors) {
    val activeProject by viewModel.activeProject.collectAsState()
    val allProjects by viewModel.projects.collectAsState()
    val workspaceFiles by viewModel.workspaceFiles.collectAsState()
    val activeFile by viewModel.activeFile.collectAsState()
    val editorCode by viewModel.editorCode.collectAsState()
    val isCodeModified by viewModel.isCodeModified.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val layoutMode by viewModel.ideLayoutMode.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val targetPlatform by viewModel.emulatorPlatform.collectAsState()
    val geminiStatus by viewModel.geminiStatus.collectAsState()
    val copilotPrompt by viewModel.copilotPrompt.collectAsState()
    val activeTheme by viewModel.ideTheme.collectAsState()

    // Screen dimension checks for adaptive responsive dual pane
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    // Tab state inside bottom console drawer (0 = Terminal, 1 = Gemini Copilot, 2 = simulated Emulator)
    var activeConsoleTab by remember { mutableStateOf(0) }
    
    // UI Expand / Collapse drawer states
    var isExplorerVisible by remember { mutableStateOf(true) }
    var showProjectCreatorDialog by remember { mutableStateOf(false) }
    var showDevicePreviewSheet by remember { mutableStateOf(false) }

    // Quick Action template values for creating project
    var newProjName by remember { mutableStateOf("") }
    var newProjTemplate by remember { mutableStateOf("Compose Multiplatform Calculator") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        bottomBar = {
            // Elite VS Code styled bottom status bar label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(colors.statusBg)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Branch indicator icon
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "main*",
                        color = colors.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Kotlin Multiplatform sandbox active",
                        color = colors.secondaryText,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "UTF-8",
                        color = colors.secondaryText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Kotlin (KMP)",
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.editorBg)
        ) {
            // ==================== 1. MAIN TOP HEADER BAR (Android Studio + VS Code hybrid look) ====================
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeProject?.name ?: "No project selective",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = activeProject?.templateType ?: "KMP Template",
                            fontSize = 10.sp,
                            color = colors.secondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { isExplorerVisible = !isExplorerVisible },
                        modifier = Modifier.testTag("explorer_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle Files Panel",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Compilation Target Dropdown
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable {
                                val nextPlatform = if (targetPlatform == "android") "ios" else "android"
                                viewModel.setEmulatorPlatform(nextPlatform)
                                viewModel.addTerminalLog("Switched compilation emulator build to: $nextPlatform")
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (targetPlatform == "android") Icons.Default.PlayArrow else Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (targetPlatform == "android") Color(0xFF3DDC84) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (targetPlatform == "android") "Android Foldable" else "iOS iPhone simulator",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Main Run/Compile Button
                    IconButton(
                        onClick = {
                            viewModel.runKmpBuild()
                            if (!isWideScreen) {
                                showDevicePreviewSheet = true
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("play_run_main_btn")
                    ) {
                        if (buildStatus is BuildStatus.Running) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Run App", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Layout Mode & Theme selector toggle menu
                    var showSettingsMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false },
                        modifier = Modifier.background(colors.sidebarBg)
                    ) {
                        DropdownMenuItem(
                            text = { Text("IDE Theme: One Dark Pro", color = Color.White) },
                            onClick = { viewModel.setIdeTheme("One Dark Pro"); showSettingsMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("IDE Theme: Cobalt Sea", color = Color.White) },
                            onClick = { viewModel.setIdeTheme("Cobalt"); showSettingsMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("IDE Theme: Studio Darcula", color = Color.White) },
                            onClick = { viewModel.setIdeTheme("Darcula"); showSettingsMenu = false }
                        )
                        Divider(color = colors.divider)
                        DropdownMenuItem(
                            text = { Text("+ Create New Project", color = colors.accent, fontWeight = FontWeight.Bold) },
                            onClick = { showProjectCreatorDialog = true; showSettingsMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("- Delete Current Project", color = Color.Red) },
                            onClick = { viewModel.deleteCurrentProject(); showSettingsMenu = false }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.sidebarBg)
            )

            // ==================== 2. MAIN CENTER ROW WORKSPACE WORKAREA ====================
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Left Files Explorer panel
                AnimatedVisibility(visible = isExplorerVisible) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(if (isWideScreen) 240.dp else 180.dp)
                                .background(colors.sidebarBg)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Folder explorer title label
                                Row(
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "PROJECT FILES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                    // Create Project Action trigger
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "New workspace project",
                                        tint = Color.White,
                                        modifier = Modifier
                                                .size(16.dp)
                                                .clickable { showProjectCreatorDialog = true }
                                    )
                                }

                                // Dynamic Switch Project Dropdown
                                var showProjDropdown by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.editorBg)
                                            .clickable { showProjDropdown = true }
                                            .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = activeProject?.name ?: "Select Project",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }

                                    DropdownMenu(
                                        expanded = showProjDropdown,
                                        onDismissRequest = { showProjDropdown = false },
                                        modifier = Modifier.background(colors.sidebarBg)
                                    ) {
                                        allProjects.forEach { proj ->
                                            DropdownMenuItem(
                                                text = { Text(proj.name, color = Color.White) },
                                                onClick = {
                                                    viewModel.selectProject(proj)
                                                    showProjDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Divider(color = colors.divider, modifier = Modifier.padding(vertical = 4.dp))

                                // Hierarchical Render Tree of KMP workspace
                                LazyColumn(
                                    modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                ) {
                                    // Dynamic grouping folders for easier visual flow
                                    val commonMainFiles = workspaceFiles.filter { it.filePath.contains("commonMain") }
                                    val buildFiles = workspaceFiles.filter { !it.filePath.contains("/") }
                                    val otherFiles = workspaceFiles.filter { it.filePath.contains("/") && !it.filePath.contains("commonMain") }

                                    item {
                                        Text(
                                            "📂 shared (KMP module)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    items(commonMainFiles) { file ->
                                        val isSelected = activeFile?.id == file.id
                                        Row(
                                            modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, top = 3.dp, bottom = 3.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else Color.Transparent)
                                                    .clickable { viewModel.selectFile(file) }
                                                    .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.List,
                                                contentDescription = null,
                                                tint = if (isSelected) colors.accent else Color(0xFF61AFEF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = file.filePath.substringAfterLast("/"),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else colors.primaryText
                                            )
                                        }
                                    }

                                    item {
                                        Text(
                                            "📂 platforms (Android & iOS)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(otherFiles) { file ->
                                        val isSelected = activeFile?.id == file.id
                                        val fileExt = file.filePath.substringAfterLast(".")
                                        Row(
                                            modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, top = 3.dp, bottom = 3.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else Color.Transparent)
                                                    .clickable { viewModel.selectFile(file) }
                                                    .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (fileExt == "swift") Icons.Default.Star else Icons.Default.List,
                                                contentDescription = null,
                                                tint = if (fileExt == "swift") Color(0xFFFA5732) else Color(0xFFA6ADC8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = file.filePath.substringAfterLast("/"),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else colors.primaryText
                                            )
                                        }
                                    }

                                    item {
                                        Text(
                                            "📂 buildscripts",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(buildFiles) { file ->
                                        val isSelected = activeFile?.id == file.id
                                        Row(
                                            modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, top = 3.dp, bottom = 3.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else Color.Transparent)
                                                    .clickable { viewModel.selectFile(file) }
                                                    .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Build,
                                                contentDescription = null,
                                                tint = Color(0xFFCD906B),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = file.filePath,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else colors.primaryText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Divider(
                            color = colors.divider,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                        )
                    }
                }

                // Center code editor and bottom panel
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Editor active filename tabs line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(colors.sidebarBg)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeFile?.let { selected ->
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(colors.editorBg)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selected.filePath.substringAfterLast("/"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (isCodeModified) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Blue unsaved dot layout indicator
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(colors.accent, CircleShape)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (isCodeModified) {
                            Text(
                                text = "Save code content to db backup",
                                color = colors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clickable { viewModel.saveCurrentFile() }
                            )
                        }
                    }

                    // Main editor code field layout
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxWidth()
                            .background(colors.editorBg)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Professional Line Number Margin on the left!
                            val lines = editorCode.lines()
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .width(36.dp)
                                    .background(colors.sidebarBg)
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (i in 1..kotlin.math.max(lines.size, 1)) {
                                    Text(
                                        text = i.toString(),
                                        color = colors.secondaryText,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.height(18.dp)
                                    )
                                }
                            }

                            // Dynamic VisualTransformation Syntax Highlighter Input Field
                            TextField(
                                value = editorCode,
                                onValueChange = { viewModel.updateEditorCode(it) },
                                textStyle = TextStyle(
                                    color = colors.primaryText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = colors.primaryText,
                                    unfocusedTextColor = colors.primaryText,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                visualTransformation = { text ->
                                    TransformedText(
                                        text = CodeHighlighter.highlight(text.text, activeTheme),
                                        offsetMapping = OffsetMapping.Identity
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(4.dp)
                                    .testTag("code_editor_field")
                            )
                        }
                    }

                    Divider(color = colors.divider)

                    // Bottom Console Drawer (Console Tabs Selector)
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxWidth()
                            .background(colors.sidebarBg)
                    ) {
                        // Tab labels row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(colors.statusBg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf("Terminal Outputs", "Gemini CoPilot")
                            tabs.forEachIndexed { idx, title ->
                                val isSelected = activeConsoleTab == idx
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .clickable { activeConsoleTab = idx }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) colors.accent else colors.secondaryText,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (activeConsoleTab == 0) {
                                IconButton(
                                    onClick = { viewModel.clearTerminal() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Tab detailed bodies
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            when (activeConsoleTab) {
                                0 -> {
                                    // Terminal logs console
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(terminalLogs) { log ->
                                            Text(
                                                text = log,
                                                color = if (log.contains("SUCCESS") || log.contains("saved")) Color(0xFF4CAF50) else if (log.contains("UP-TO-DATE")) Color(0xFF81C784) else Color(0xFFCDD6F4),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                1 -> {
                                    // Gemini prompt integration helper
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextField(
                                                value = copilotPrompt,
                                                onValueChange = { viewModel.updateCopilotPrompt(it) },
                                                placeholder = { Text("Generate KMP compose layouts or functions with Gemini...", fontSize = 11.sp) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = colors.editorBg,
                                                    unfocusedContainerColor = colors.editorBg
                                                ),
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("copilot_input")
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = { viewModel.triggerGeminiCopilot() },
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(40.dp)
                                            ) {
                                                if (geminiStatus is GeminiStatus.Processing) {
                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 1.dp)
                                                } else {
                                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Ask", fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        // AI Output View
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                                .background(colors.editorBg, RoundedCornerShape(6.dp))
                                                .padding(6.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            when (val status = geminiStatus) {
                                                is GeminiStatus.Idle -> {
                                                    Column(
                                                        modifier = Modifier.fillMaxSize(),
                                                        verticalArrangement = Arrangement.Center,
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text("Enter prompt to construct shared Multiplatform code", color = colors.secondaryText, fontSize = 11.sp, textAlign = TextAlign.Center)
                                                    }
                                                }
                                                is GeminiStatus.Processing -> {
                                                    Text("Gemini generating and analyzing workspace files context...", color = colors.accent, fontSize = 11.sp)
                                                }
                                                is GeminiStatus.Success -> {
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("CO-PILOT CODE GENERATED:", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            Button(
                                                                onClick = { viewModel.applyCopilotCodeToActiveFile(status.response) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(26.dp)
                                                            ) {
                                                                Text("Pate & Run Code", fontSize = 10.sp)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(text = status.response, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                    }
                                                }
                                                is GeminiStatus.Error -> {
                                                    Text(text = "Error: ${status.message}", color = Color.Red, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Split side simulated Device Emulator on Wide Screens!
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        Divider(
                            color = colors.divider,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(340.dp)
                                .background(Color(0xFF1E1E24)),
                            contentAlignment = Alignment.Center
                        ) {
                            InteractiveEmulator(
                                templateType = activeProject?.templateType ?: "",
                                codeContent = editorCode,
                                platform = targetPlatform,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================== 3. MOBILE ADAPTIVE DEVICE SIMULATION MODAL SHEET ====================
    if (!isWideScreen && showDevicePreviewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDevicePreviewSheet = false },
            containerColor = Color(0xFF111214)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                InteractiveEmulator(
                    templateType = activeProject?.templateType ?: "",
                    codeContent = editorCode,
                    platform = targetPlatform,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // ==================== 4. NEW WORKSPACE PROJECT DIALOG ====================
    if (showProjectCreatorDialog) {
        AlertDialog(
            onDismissRequest = { showProjectCreatorDialog = false },
            containerColor = colors.sidebarBg,
            title = { Text("Create Multiplatform Project", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newProjName,
                        onValueChange = { newProjName = it },
                        label = { Text("Project Name", color = colors.secondaryText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.divider
                        )
                    )

                    Column {
                        Text("Select KMP Template Module:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        val templates = listOf(
                            "Compose Multiplatform Calculator",
                            "Compose Multiplatform Gemini Chat",
                            "KMP Basic Greeting Sandbox"
                        )
                        templates.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { newProjTemplate = t }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = newProjTemplate == t,
                                    onClick = { newProjTemplate = t },
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                                )
                                Text(t, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = newProjName.trim().ifEmpty { "My Multiplatform App" }
                        viewModel.createProject(finalName, newProjTemplate)
                        newProjName = ""
                        showProjectCreatorDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text("Initialize Workspace", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProjectCreatorDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}