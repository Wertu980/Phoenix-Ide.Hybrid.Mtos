package com.mtos.phoenix.ide.hybrid.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class WorkspaceRepository(private val workspaceDao: WorkspaceDao) {

    val allProjects: Flow<List<Project>> = workspaceDao.getAllProjects()

    fun getFilesByProject(projectId: Int): Flow<List<WorkspaceFile>> =
        workspaceDao.getFilesByProject(projectId)

    suspend fun getFileByPath(projectId: Int, filePath: String): WorkspaceFile? =
        workspaceDao.getFileByPath(projectId, filePath)

    suspend fun insertProject(project: Project): Int {
        val id = workspaceDao.insertProject(project).toInt()
        createTemplateFiles(id, project.templateType)
        return id
    }

    suspend fun deleteProject(projectId: Int) {
        workspaceDao.deleteProject(projectId)
    }

    suspend fun updateFileContent(fileId: Int, newContent: String) {
        workspaceDao.updateFileContent(fileId, newContent)
    }

    suspend fun updateFileByPath(projectId: Int, filePath: String, newContent: String) {
        workspaceDao.updateFileByPath(projectId, filePath, newContent)
    }

    suspend fun saveSetting(key: String, value: String) {
        workspaceDao.saveSetting(IdeSetting(key, value))
    }

    suspend fun getSetting(key: String): String? {
        return workspaceDao.getSetting(key)?.value
    }

    private suspend fun createTemplateFiles(projectId: Int, templateType: String) {
        val files = mutableListOf<WorkspaceFile>()

        when (templateType) {
            "Compose Multiplatform Calculator" -> {
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/commonMain/kotlin/Calculator.kt",
                    content = """// Common Math Engine for Android & iOS
package com.example.shared

class Calculator {
    fun add(a: Double, b: Double): Double = a + b
    fun subtract(a: Double, b: Double): Double = a - b
    fun multiply(a: Double, b: Double): Double = a * b
    fun divide(a: Double, b: Double): Double {
        if (b == 0.0) throw IllegalArgumentException("Cannot divide by zero")
        return a / b
    }
}"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/commonMain/kotlin/App.kt",
                    content = """// KMP Compose Multiplatform Shared UI Screen
package com.example.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var activeOp by remember { mutableStateOf<String?>(null) }
    var isNewNumber by remember { mutableStateOf(true) }

    val calc = remember { Calculator() }

    fun onNumber(num: String) {
        if (isNewNumber || display == "0") {
            display = num
            isNewNumber = false
        } else {
            display += num
        }
    }

    fun onOp(op: String) {
        operand1 = display.toDoubleOrNull()
        activeOp = op
        isNewNumber = true
    }

    fun onEqual() {
        val op1 = operand1 ?: return
        val op2 = display.toDoubleOrNull() ?: return
        val op = activeOp ?: return

        try {
            val result = when (op) {
                "+" -> calc.add(op1, op2)
                "-" -> calc.subtract(op1, op2)
                "×" -> calc.multiply(op1, op2)
                "÷" -> calc.divide(op1, op2)
                else -> 0.0
            }
            display = if (result % 1.0 == 0.0) result.toInt().toString() else result.toString()
        } catch (e: Exception) {
            display = "Error"
        }
        operand1 = null
        activeOp = null
        isNewNumber = true
    }

    fun onClear() {
        display = "0"
        operand1 = null
        activeOp = null
        isNewNumber = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "KMP CALCULATOR",
            color = Color(0xFFA6ADC8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )

        // Screen Output
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Color(0xFF11111B), RoundedCornerShape(12.dp))
                .padding(20.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                color = Color(0xFFC9D1D9),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Keypad grid
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val buttons = listOf(
                listOf("C", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", "=")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { char ->
                        val isOperation = char in listOf("+", "-", "×", "÷", "=")
                        val isClear = char == "C"
                        val weight = if (char == "0" || char == "C") 2f else 1f

                        Button(
                            onClick = {
                                when {
                                    isClear -> onClear()
                                    char == "=" -> onEqual()
                                    isOperation -> onOp(char)
                                    else -> onNumber(char)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isClear -> Color(0xFFF38BA8)
                                    isOperation -> Color(0xFFFAB387)
                                    else -> Color(0xFF313244)
                                },
                                contentColor = when {
                                    isClear -> Color(0xFF11111B)
                                    isOperation -> Color(0xFF11111B)
                                    else -> Color(0xFFCDD6F4)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(weight)
                                .height(64.dp)
                        ) {
                            Text(text = char, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "androidApp/src/main/java/MainActivity.kt",
                    content = """package com.example.androidApp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.shared.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "iosApp/iosApp/ContentView.swift",
                    content = """import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeAppView()
            .ignoresSafeArea(.keyboard)
    }
}

struct ComposeAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        AppKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "build.gradle.kts",
                    content = """plugins {
    kotlin("multiplatform") version "2.0.0"
    id("com.android.application") version "8.2.0"
    id("org.jetbrains.compose") version "1.6.0"
}

kotlin {
    androidTarget()
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.8.2")
        }
    }
}

android {
    namespace = "com.example.androidApp"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}"""
                ))
            }
            "Compose Multiplatform Gemini Chat" -> {
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/commonMain/kotlin/App.kt",
                    content = """// Gemini Chat Companion inside KMP App Workspace
package com.example.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChatMessage(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var promptText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>(
        ChatMessage("Hello! I am a Gemini AI assistant inside a Kotlin Multiplatform app. How can I help you compile ideas today?", false)
    ) }

    fun sendMessage() {
        val text = promptText.trim()
        if (text.isEmpty()) return
        messages.add(ChatMessage(text, true))
        promptText = ""
        
        // Mocking responses since API layer runs on the server side in the main framework
        messages.add(ChatMessage("Checking dependencies and compiling prompt. That's a great KMP concept!", false))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // App bar
        TopAppBar(
            title = {
                Text(
                    text = "Gemini Multiplatform Explorer",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
        )

        // Message List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isUser) Color(0xFF3B82F6) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = Color.White,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Input bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = promptText,
                onValueChange = { promptText = it },
                placeholder = { Text("Ask Gemini model...") },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF334155),
                    unfocusedContainerColor = Color(0xFF334155),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { sendMessage() },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF3B82F6), contentColor = Color.White)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/commonMain/kotlin/Models.kt",
                    content = """package com.example.shared

data class UserProfile(
    val username: String,
    val bio: String
)"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "build.gradle.kts",
                    content = """plugins {
    kotlin("multiplatform") version "2.1.0"
}

kotlin {
    androidTarget()
}"""
                ))
            }
            else -> {
                // KMP Hello World / Basic Greeting (Default)
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/commonMain/kotlin/Platform.kt",
                    content = """package com.example.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/androidMain/kotlin/Platform.android.kt",
                    content = """package com.example.shared

class AndroidPlatform : Platform {
    override val name: String = "Android SDK 34"
}

actual fun getPlatform(): Platform = AndroidPlatform()"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/iosMain/kotlin/Platform.ios.kt",
                    content = """package com.example.shared

class IOSPlatform : Platform {
    override val name: String = "iOS 17"
}

actual fun getPlatform(): Platform = IOSPlatform()"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/commonMain/kotlin/Greeting.kt",
                    content = """package com.example.shared

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hello World! Compiled on platform: " + platform.name
    }
}"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "shared/src/commonMain/kotlin/App.kt",
                    content = """// Standard KMP Greeting Screen
package com.example.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    var message by remember { mutableStateOf("Ready to Greeting...") }
    val greeting = remember { Greeting() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "KMP Greeting Module",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = message,
                    fontSize = 16.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )

                Button(
                    onClick = { message = greeting.greet() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Trigger Shared Logic Greeting", color = Color.White)
                }
            }
        }
    }
}"""
                ))
                files.add(WorkspaceFile(
                    projectId = projectId,
                    filePath = "build.gradle.kts",
                    content = """plugins {
    kotlin("multiplatform") version "2.2.10"
}"""
                ))
            }
        }

        workspaceDao.insertFiles(files)
    }
}
