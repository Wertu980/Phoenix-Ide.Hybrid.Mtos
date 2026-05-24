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

    private fun createTemplateFilesOnDisk(project: Project) {
        val rootDir = getProjectDir(project)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        val templateType = project.templateType
        if (templateType.contains("Android", ignoreCase = true) || templateType.contains("Calculator", ignoreCase = true)) {
            var packageName = "com.mtos.phoenix.ide.hybrid.androidapp"
            var targetSdk = "35"
            var buildConfigType = "build.gradle.kts"

            val packageRegex = """Namespace:\s*([a-zA-Z0-9._]+)""".toRegex()
            packageRegex.find(templateType)?.let {
                packageName = it.groupValues[1]
            }
            val sdkRegex = """API\s*(\2[0-9]|\3[0-9])""".toRegex()
            sdkRegex.find(templateType)?.let {
                targetSdk = it.groupValues[1]
            }
            if (templateType.contains("build.gradle") && !templateType.contains("build.gradle.kts")) {
                buildConfigType = "build.gradle"
            }

            val appDir = File(rootDir, "app")
            val srcDir = File(appDir, "src/main")
            val packagePath = packageName.replace('.', '/')
            val javaDir = File(srcDir, "java/$packagePath")
            val resDir = File(srcDir, "res/values")
            val layoutDir = File(srcDir, "res/layout")

            javaDir.mkdirs()
            resDir.mkdirs()
            layoutDir.mkdirs()

            // 1. MainActivity.kt
            val mainActivityFile = File(javaDir, "MainActivity.kt")
            mainActivityFile.writeText("""package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    GreetingScreen()
                }
            }
        }
    }
}

@Composable
fun GreetingScreen() {
    var count by remember { mutableStateOf(0) }

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
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Android Jetpack Compose",
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Welcome to your physical Android project compiled inside storage/emulated/0/!",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = { count++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Interactive Tap Counter: ${'$'}count")
                }
            }
        }
    }
}
""")

            // 2. AndroidManifest.xml
            val manifestFile = File(srcDir, "AndroidManifest.xml")
            manifestFile.writeText("""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="${project.name}"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""")

            // 3. Gradle build script
            val gradleFile = File(appDir, buildConfigType)
            if (buildConfigType.endsWith(".kts")) {
                gradleFile.writeText("""plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "$packageName"
    compileSdk = 35

    defaultConfig {
        applicationId = "$packageName"
        minSdk = 24
        targetSdk = $targetSdk
        versionCode = 1
        versionName = "1.0"
    }
}
""")
            } else {
                gradleFile.writeText("""plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace "$packageName"
    compileSdk 35

    defaultConfig {
        applicationId "$packageName"
        minSdk 24
        targetSdk $targetSdk
        versionCode 1
        versionName "1.0"
    }
}
""")
            }

            // 4. settings.gradle.kts
            val settingsGradle = File(rootDir, "settings.gradle.kts")
            settingsGradle.writeText("""rootProject.name = "${project.name}"
include(":app")
""")

            // 5. strings.xml
            val stringsXml = File(resDir, "strings.xml")
            stringsXml.writeText("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.name}</string>
</resources>
""")

        } else {
            // Flutter Project Creation on disk
            var orgName = "com.mtos.phoenix.ide.hybrid"
            val orgRegex = """Org:\s*([a-zA-Z0-9._]+)""".toRegex()
            orgRegex.find(templateType)?.let {
                orgName = it.groupValues[1]
            }

            val pList = mutableListOf<String>()
            if (templateType.contains("Android")) pList.add("android")
            if (templateType.contains("iOS")) pList.add("ios")
            if (templateType.contains("Web")) pList.add("web")
            if (pList.isEmpty()) {
                pList.add("android")
                pList.add("ios")
            }

            val libDir = File(rootDir, "lib")
            libDir.mkdirs()

            // 1. main.dart
            val mainDart = File(libDir, "main.dart")
            mainDart.writeText("""import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '${project.name}',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple, brightness: Brightness.dark),
        useMaterial3: true,
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  int _counter = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('${project.name} Flutter App'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            const Text(
              'Welcome to your physical Flutter project built on disk!',
            ),
            Text(
              '${'$'}_counter',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          setState(() {
            _counter++;
          });
        },
        tooltip: 'Increment',
        child: const Icon(Icons.add),
      ),
    );
  }
}
""")

            // 2. pubspec.yaml
            val pubspec = File(rootDir, "pubspec.yaml")
            pubspec.writeText("""name: ${project.name.lowercase().replace(" ", "_")}
description: A new Flutter project generated inside local device storage.
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter

flutter:
  uses-material-design: true
""")

            // Create directories for other selected platforms
            pList.forEach { platform ->
                File(rootDir, platform).mkdirs()
            }
        }
    }
}
