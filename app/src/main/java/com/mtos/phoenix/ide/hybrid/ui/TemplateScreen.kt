package com.mtos.phoenix.ide.hybrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.mtos.phoenix.ide.hybrid.R
import com.mtos.phoenix.ide.hybrid.viewmodel.IdeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: IdeViewModel,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTemplateType by remember { mutableStateOf("") } // "Android App Scaffold" / "Flutter Widget App"
    var newProjectName by remember { mutableStateOf("") }

    // Custom configuration states for Android Workspace Setup
    var androidPackageName by remember { mutableStateOf("com.mtos.phoenix.ide.hybrid.androidapp") }
    var selectedTargetSdk by remember { mutableStateOf("Android 15 (API 35)") }
    var androidBuildConfig by remember { mutableStateOf("build.gradle.kts") }
    var targetSdkDropdownExpanded by remember { mutableStateOf(false) }

    // Custom configuration states for Flutter Workspace Setup
    var flutterOrgName by remember { mutableStateOf("com.mtos.phoenix.ide.hybrid.flutterapp") }
    var selectedPlatforms by remember { mutableStateOf(setOf("Android", "iOS", "Web")) }

    // Synchronize package namespace suggestions as user customizes the project title
    LaunchedEffect(newProjectName) {
        val cleanName = newProjectName.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        if (cleanName.isNotBlank()) {
            androidPackageName = "com.mtos.phoenix.ide.hybrid.$cleanName"
            flutterOrgName = "com.mtos.phoenix.ide.hybrid.$cleanName"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Select Template",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_to_home_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Starter Templates",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Select a pre-designed developer scaffold to spin up a simulated coding environment immediately.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // --- ANDROID CARD ---
            TemplateCard(
                title = "Android Mobile Scaffold",
                description = "Modern Android setup built with Kotlin, Jetpack Compose, Material Design 3, and Kotlin Coroutines.",
                platformName = "Android",
                accentColor = Color(0xFF3DDC84),
                onSelect = {
                    selectedTemplateType = "Android App Scaffold"
                    newProjectName = "My Android App"
                    showCreateDialog = true
                },
                testTag = "template_card_android"
            )

            // --- FLUTTER CARD ---
            TemplateCard(
                title = "Flutter Mobile Scaffold",
                description = "Cross-platform mobile workspace leveraging Dart, rich Flutter widgets, and standard material themes.",
                platformName = "Flutter",
                accentColor = Color(0xFF02569B),
                onSelect = {
                    selectedTemplateType = "Flutter Widget App"
                    newProjectName = "My Flutter App"
                    showCreateDialog = true
                },
                testTag = "template_card_flutter"
            )
        }

        // Beautiful Material 3 creation modal with custom details config
        if (showCreateDialog) {
            val isAndroid = selectedTemplateType.contains("Android", ignoreCase = true)
            
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isAndroid) R.drawable.ic_android_logo else R.drawable.ic_flutter_logo
                            ),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (isAndroid) "Android Project Settings" else "Flutter Project Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Project Name Field
                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            label = { Text("Project Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_name_input_field"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (isAndroid) {
                            // --- ANDROID SPECIFIC SETTINGS ---
                            
                            // Package Name Field
                            OutlinedTextField(
                                value = androidPackageName,
                                onValueChange = { androidPackageName = it },
                                label = { Text("Package Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("package_name_input_field"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Language Display (Default Kotlin)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Source Language",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kotlin",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Default",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Target SDK Version Dropdown Selector (Android 10 - API 29 through Android 16 - API 36)
                            val sdkOptions = listOf(
                                "Android 10 (API 29)",
                                "Android 11 (API 30)",
                                "Android 12 (API 31)",
                                "Android 12L (API 32)",
                                "Android 13 (API 33)",
                                "Android 14 (API 34)",
                                "Android 15 (API 35)",
                                "Android 16 (API 36)"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Target SDK Version",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { targetSdkDropdownExpanded = true }
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedTargetSdk,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "SDK Options",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = targetSdkDropdownExpanded,
                                        onDismissRequest = { targetSdkDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.7f)
                                    ) {
                                        sdkOptions.forEach { sdk ->
                                            DropdownMenuItem(
                                                text = { Text(sdk, fontSize = 14.sp) },
                                                onClick = {
                                                    selectedTargetSdk = sdk
                                                    targetSdkDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Build Configuration Toggle Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Build Configuration File",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("build.gradle.kts", "build.gradle").forEach { config ->
                                        val isSelected = androidBuildConfig == config
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                                .clickable { androidBuildConfig = config }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = config,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                        } else {
                            // --- FLUTTER SPECIFIC SETTINGS ---
                            
                            // Organization bundle name ID
                            OutlinedTextField(
                                value = flutterOrgName,
                                onValueChange = { flutterOrgName = it },
                                label = { Text("Organization Identifier") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("flutter_org_input_field"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Target Platforms Select
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Target Platforms Select",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                                
                                val platformsList = listOf("Android", "iOS", "Web", "macOS", "Windows", "Linux")
                                val rowCount = (platformsList.size + 1) / 2
                                for (i in 0 until rowCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        for (j in 0..1) {
                                            val index = i * 2 + j
                                            if (index < platformsList.size) {
                                                val platform = platformsList[index]
                                                val isChecked = selectedPlatforms.contains(platform)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                                            else Color.Transparent
                                                        )
                                                        .clickable {
                                                            selectedPlatforms = if (isChecked) {
                                                                if (selectedPlatforms.size > 1) selectedPlatforms - platform else selectedPlatforms
                                                            } else {
                                                                selectedPlatforms + platform
                                                            }
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.Start
                                                ) {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = { checked ->
                                                            selectedPlatforms = if (checked) {
                                                                selectedPlatforms + platform
                                                            } else {
                                                                if (selectedPlatforms.size > 1) selectedPlatforms - platform else selectedPlatforms
                                                            }
                                                        },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = MaterialTheme.colorScheme.primary
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = platform,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                val generatedType = if (isAndroid) {
                                    "Android App Scaffold ($androidBuildConfig, $selectedTargetSdk, Kotlin Language, Namespace: $androidPackageName)"
                                } else {
                                    val platforms = selectedPlatforms.sorted().joinToString(", ")
                                    "Flutter Widget App (Platforms: $platforms, Org: $flutterOrgName)"
                                }
                                viewModel.createProject(newProjectName.trim(), generatedType)
                                showCreateDialog = false
                                onBack() // Head back to Home Screen
                            }
                        },
                        enabled = newProjectName.isNotBlank(),
                        modifier = Modifier.testTag("confirm_project_create_btn")
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun TemplateCard(
    title: String,
    description: String,
    platformName: String,
    accentColor: Color,
    onSelect: () -> Unit,
    testTag: String
) {
    val lightAccent = accentColor.copy(alpha = 0.08f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(lightAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (platformName == "Android") R.drawable.ic_android_logo else R.drawable.ic_flutter_logo
                        ),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = platformName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Ready to bootstrap",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
