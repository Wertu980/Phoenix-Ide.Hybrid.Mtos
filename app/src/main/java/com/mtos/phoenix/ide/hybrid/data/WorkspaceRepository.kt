package com.mtos.phoenix.ide.hybrid.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class WorkspaceRepository(
    private val context: Context
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

    private fun copyAssetFolder(assetPath: String, destinationDir: File, replacements: Map<String, String>) {
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
                    File(destinationDir, "$packageSubPath/$fileName")
                } else {
                    File(destinationDir, fileName)
                }
                
                if (targetFile.exists() && targetFile.isDirectory) {
                    targetFile.deleteRecursively()
                }
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(updatedContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // It is a directory
            val nextDestinationDir = if (assetPath == "templates/android" || assetPath == "templates/flutter") {
                destinationDir
            } else {
                File(destinationDir, File(assetPath).name)
            }
            
            if (nextDestinationDir.exists() && nextDestinationDir.isFile) {
                nextDestinationDir.delete()
            }
            nextDestinationDir.mkdirs()
            
            for (asset in assets) {
                val childAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                copyAssetFolder(childAssetPath, nextDestinationDir, replacements)
            }
        }
    }

    private fun createTemplateFilesOnDisk(project: Project) {
        val rootDir = getProjectDir(project)
        try {
            if (rootDir.exists()) {
                rootDir.deleteRecursively()
            }
            rootDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
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

            // Core Robust Fallback check if assets did not copy anything
            val appDir = File(rootDir, "app")
            val srcDir = File(appDir, "src/main/java/${packageName.replace('.', '/')}")
            if (!srcDir.exists() || srcDir.listFiles().isNullOrEmpty()) {
                srcDir.mkdirs()
                
                // 1. Create MainActivity.kt
                if (templateType.contains("Calculator", ignoreCase = true)) {
                    File(srcDir, "MainActivity.kt").writeText("""
package $packageName

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
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF1E1E2E)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Android Composite Calculator Screen",
                        color = Color.White
                    )
                }
            }
        }
    }
}
                    """.trimIndent().trim())
                } else {
                    File(srcDir, "MainActivity.kt").writeText("""
package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Welcome to ${project.name}!")
                }
            }
        }
    }
}
                    """.trimIndent().trim())
                }

                // 2. Create strings.xml
                val resDir = File(appDir, "src/main/res/values")
                resDir.mkdirs()
                File(resDir, "strings.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.name}</string>
</resources>
                """.trimIndent().trim())

                // 3. Create AndroidManifest.xml
                val mainDir = File(appDir, "src/main")
                mainDir.mkdirs()
                File(mainDir, "AndroidManifest.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$packageName">
    <application
        android:label="${project.name}"
        android:theme="@android:style/Theme.Material.NoActionBar">
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
                """.trimIndent().trim())

                // 4. Create build.gradle.kts
                File(appDir, "build.gradle.kts").writeText("""
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "$packageName"
    compileSdk = $targetSdk
    defaultConfig {
        applicationId = "$packageName"
        minSdk = 24
        targetSdk = $targetSdk
        versionCode = 1
        versionName = "1.0"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
}
                """.trimIndent().trim())

                // 5. Create settings.gradle.kts
                File(rootDir, "settings.gradle.kts").writeText("""
rootProject.name = "${project.name}"
include(":app")
                """.trimIndent().trim())
            }

        } else {
            // Flutter Project Creation
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

            // Robust Fallback check if copyAssetFolder produced no files
            val libDir = File(rootDir, "lib")
            if (!libDir.exists() || libDir.listFiles().isNullOrEmpty()) {
                libDir.mkdirs()

                // 1. Create main.dart
                File(libDir, "main.dart").writeText("""
import 'package:flutter/material.dart';

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
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: '${project.name} Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  int _counter = 0;

  void _incrementCounter() {
    setState(() {
      _counter++;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            const Text(
              'You have pushed the button this many times:',
            ),
            Text(
              '${'$'}_counter',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _incrementCounter,
        tooltip: 'Increment',
        child: const Icon(Icons.add),
      ),
    );
  }
}
                """.trimIndent().trim())

                // 2. Create pubspec.yaml
                File(rootDir, "pubspec.yaml").writeText("""
name: $appNameLower
description: A new Flutter project generated in Phoenix IDE.
version: 1.0.0+1
environment:
  sdk: '>=3.0.0 <4.0.0'
dependencies:
  flutter:
    sdk: flutter
  cupertino_icons: ^1.0.6
dev_dependencies:
  flutter_test:
    sdk: flutter
flutter:
  uses-material-design: true
                """.trimIndent().trim())

                // 3. Create README.md
                File(rootDir, "README.md").writeText("""
# $appNameLower

A new Flutter template project created with Phoenix IDE Studio.

## Getting Started
This project is fully ready for split-screen emulator previewing, code modification, and server compiles.
                """.trimIndent().trim())
            }

            // Create placeholder extra folders if necessary
            val pList = mutableListOf<String>()
            if (templateType.contains("iOS")) pList.add("ios")
            if (templateType.contains("Web")) pList.add("web")
            pList.forEach { platform ->
                File(rootDir, platform).mkdirs()
            }
        }
        
        // Write the custom Aarch64 Linux sandbox setup script under the project root
        writeSandboxSetupScript(rootDir, project.name)
    }

    private fun writeSandboxSetupScript(rootDir: File, projectName: String) {
        val scriptContent = """
#!/bin/bash
# ==============================================================================
# Phoenix IDE - Aarch64 On-Device Linux Sandbox Installer (v2.1)
# Highly optimized for OpenJDK 21, Android SDK (cmdline-tools), Gradle & Flutter.
# ==============================================================================

set -e

# Colors for modern and beautiful terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "__DL__{PURPLE}================================================================__DL__{NC}"
echo -e "__DL__{CYAN}    _  _   _             _      ___   _ _   _ _   _ _  _   ___ __DL__{NC}"
echo -e "__DL__{CYAN}   /_\(_) | |\/|__ _ _ _/ |__  | __|_(_|_) | (_| | (_)| | | __|__DL__{NC}"
echo -e "__DL__{CYAN}  / _ \ | |  _  / _\` | '_ \ '_ \ | _| | _| | | _| | |  _| | _| | _| __DL__{NC}"
echo -e "__DL__{CYAN} /_/ \_\_| |_| |_\__,_|_| |_|_| |_|___|_|_|  |_|_|  |_|   |___|__DL__{NC}"
echo -e "__DL__{CYAN}         Phoenix IDE Aarch64 Hybrid Sandbox Setup Script           __DL__{NC}"
echo -e "__DL__{PURPLE}================================================================__DL__{NC}"
echo ""

# Check architecture
ARCH=__DL__(uname -m)
if [ "__DL__ARCH" != "aarch64" ] && [ "__DL__ARCH" != "arm64" ]; then
    echo -e "__DL__{YELLOW}[WARNING] Host architecture is __DL__{ARCH}. This installer is highly tuned for ARM64/Aarch64 environments (e.g., Termux, Proot-Distro, UserLAnd). Running on __DL__{ARCH} may require alternate binaries.__DL__{NC}"
else
    echo -e "__DL__{GREEN}[OK] Detected ARM64/Aarch64 Architecture! Environment check passed.__DL__{NC}"
fi

# Define path configurations
SANDBOX_DIR="__DL__{HOME}/phoenix-sandbox"
JAVA_DIR="__DL__{SANDBOX_DIR}/jvm/openjdk-21"
SDK_DIR="__DL__{SANDBOX_DIR}/android-sdk"
FLUTTER_DIR="__DL__{SANDBOX_DIR}/flutter"

echo -e "\n__DL__{BLUE}[1/5] Creating Sandbox Directory Structure under __DL__{SANDBOX_DIR}...__DL__{NC}"
mkdir -p "__DL__{SANDBOX_DIR}"
mkdir -p "__DL__{SANDBOX_DIR}/jvm"
mkdir -p "__DL__{SDK_DIR}/cmdline-tools"
mkdir -p "__DL__{SANDBOX_DIR}/bin"

# System dependencies installations
echo -e "\n__DL__{BLUE}[2/5] Preparing Sandbox Package Dependencies...__DL__{NC}"
if [ -f /etc/debian_version ] || [ -f /etc/ubuntu_version ]; then
    echo -e "__DL__{GREEN}Detected Debian/Ubuntu-based sandbox context. Updating apt repositories...__DL__{NC}"
    sudo apt-get update -y || apt-get update -y
    sudo apt-get install -y wget curl git unzip zip xz-utils libglu1-mesa libc6 libstdc++6 -y || apt-get install -y wget curl git unzip zip xz-utils libglu1-mesa -y
elif command -v pkg &> /dev/null; then
    echo -e "__DL__{GREEN}Detected pure Termux userland environment. Installing packages...__DL__{NC}"
    pkg update -y
    pkg install -y wget curl git unzip zip tar clang make openssl nodejs -y
else
    echo -e "__DL__{YELLOW}[INFO] Unknown environment type. Ensure wget, curl, git, unzip, and tar are manually installed.__DL__{NC}"
fi

# Install OpenJDK 21 for Aarch64
echo -e "\n__DL__{BLUE}[3/5] Configuring OpenJDK 21 for Aarch64...__DL__{NC}"
if [ -d "__DL__{JAVA_DIR}" ]; then
    echo -e "__DL__{YELLOW}OpenJDK 21 directory already exists under __DL__{JAVA_DIR}. Skipping download.__DL__{NC}"
else
    echo -e "__DL__{CYAN}Retrieving production-ready Eclipse Temurin ADPT OpenJDK 21 binary for Aarch64 Linux...__DL__{NC}"
    JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.2_13.tar.gz"
    wget -q --show-progress -O /tmp/jdk21.tar.gz "__DL__{JDK_URL}"
    echo -e "__DL__{GREEN}Extracting JDK 21 to __DL__{JAVA_DIR}...__DL__{NC}"
    tar -xzf /tmp/jdk21.tar.gz -C "__DL__{SANDBOX_DIR}/jvm"
    mv "__DL__{SANDBOX_DIR}/jvm/jdk-21.0.2+13" "__DL__{JAVA_DIR}"
    rm /tmp/jdk21.tar.gz
fi

# Install Android SDK Command-Line Tools
echo -e "\n__DL__{BLUE}[4/5] Installing Android SDK Tools...__DL__{NC}"
if [ -d "__DL__{SDK_DIR}/cmdline-tools/latest" ]; then
    echo -e "__DL__{YELLOW}Android SDK command-line-tools already exists. Skipping download.__DL__{NC}"
else
    echo -e "__DL__{CYAN}Downloading official Linux command-line-tools ZIP package...__DL__{NC}"
    CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    wget -q --show-progress -O /tmp/cmdline.zip "__DL__{CMDLINE_URL}"
    echo -e "__DL__{GREEN}Extracting SDK tools directory layout...__DL__{NC}"
    unzip -q /tmp/cmdline.zip -d "__DL__{SDK_DIR}/cmdline-tools"
    mv "__DL__{SDK_DIR}/cmdline-tools/cmdline-tools" "__DL__{SDK_DIR}/cmdline-tools/latest"
    rm /tmp/cmdline.zip
fi

# Install Flutter SDK
echo -e "\n__DL__{BLUE}[5/5] Deploying Flutter Stable SDK...__DL__{NC}"
if [ -d "__DL__{FLUTTER_DIR}" ]; then
    echo -e "__DL__{YELLOW}Flutter directory already exists under __DL__{FLUTTER_DIR}. Cruising ahead.__DL__{NC}"
else
    echo -e "__DL__{CYAN}Cloning Flutter Stable branch directly from upstream Github...__DL__{NC}"
    git clone https://github.com/flutter/flutter.git -b stable "__DL__{FLUTTER_DIR}" --depth 1
fi

# Set Environment Variables and finalize paths
echo -e "\n__DL__{BLUE}================================================================__DL__{NC}"
echo -e "__DL__{GREEN}🎉 INSTALLATION AND DEPLOYMENT WAS SUCCESSFUL!__DL__{NC}"
echo -e "__DL__{BLUE}================================================================__DL__{NC}"

# Generating setup profile environment paths sheet
PROFILE_FILE="__DL__{SANDBOX_DIR}/load_env.sh"
cat << EOF > "__DL__{PROFILE_FILE}"
# Phoenix IDE Environment Configuration Profile Sheet
export JAVA_HOME="__DL__{JAVA_DIR}"
export ANDROID_HOME="__DL__{SDK_DIR}"
export FLUTTER_HOME="__DL__{FLUTTER_DIR}"
export PATH="\\__DL__JAVA_HOME/bin:\\__DL__ANDROID_HOME/cmdline-tools/latest/bin:\\__DL__ANDROID_HOME/platform-tools:\\__DL__FLUTTER_HOME/bin:\\__DL__PATH"

echo -e "\\e[32m[Phoenix Sandbox] OpenJDK 21, Android SDK, and Flutter Environment active successfully!\\e[0m"
EOF
chmod +x "__DL__{PROFILE_FILE}"

echo -e "__DL__{YELLOW}Generated environment script sheet at: __DL__{PROFILE_FILE}__DL__{NC}"
echo -e "To load the environment instantly in your shell sessions, execute:"
echo -e "__DL__{CYAN}    source __DL__{PROFILE_FILE}__DL__{NC}"
echo -e "\nThen, accept SDK licenses and fetch build tools using:"
echo -e "__DL__{CYAN}    yes | sdkmanager --licenses__DL__{NC}"
echo -e "__DL__{CYAN}    sdkmanager \"platforms;android-34\" \"build-tools;34.0.0\" \"platform-tools\"__DL__{NC}"
echo -e ""
echo -e "Test your complete compiler configuration toolchain anytime:"
echo -e "__DL__{CYAN}    java -version__DL__{NC}"
echo -e "__DL__{CYAN}    flutter doctor__DL__{NC}"
echo -e "__DL__{PURPLE}================================================================__DL__{NC}"
        """.trimIndent()
            .replace("__DL__", "$")
            .trim()

        try {
            val scriptFile = File(rootDir, "setup_sandbox_aarch64.sh")
            scriptFile.writeText(scriptContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
