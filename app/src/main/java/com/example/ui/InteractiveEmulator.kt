package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InteractiveEmulator(
    templateType: String,
    codeContent: String,
    platform: String,
    modifier: Modifier = Modifier
) {
    // Attempt to extract customizable parameters from codeContent to simulate compile hot-reload!
    val extractedTitle = remember(codeContent) {
        // Find simple double quoted headers
        if (templateType.contains("Calculator")) {
            val titleRegex = """Text\(\s*text\s*=\s*"([^"]+)"""".toRegex()
            titleRegex.find(codeContent)?.groupValues?.get(1) ?: "KMP CALCULATOR"
        } else if (templateType.contains("Gemini")) {
            val titleRegex = """text\s*=\s*"([^"]+)"""".toRegex()
            titleRegex.find(codeContent)?.groupValues?.get(1) ?: "Gemini Multiplatform Explorer"
        } else {
            val titleRegex = """text\s*=\s*"([^"]+)"""".toRegex()
            titleRegex.find(codeContent)?.groupValues?.get(1) ?: "KMP Greeting Module"
        }
    }

    val extractedGreeting = remember(codeContent) {
        if (templateType.contains("Gemini")) {
            val greetingRegex = """ChatMessage\(\s*"([^"]+)"""".toRegex()
            greetingRegex.find(codeContent)?.groupValues?.get(1) 
                ?: "Hello! I am a Gemini AI assistant inside a Kotlin Multiplatform app."
        } else {
            "Ready to Greeting..."
        }
    }

    val extractedBgColor = remember(codeContent) {
        val bgRegex = """Color\(0x([0-9a-fA-F]+)\)""".toRegex()
        val match = bgRegex.find(codeContent)
        if (match != null) {
            val hex = match.groupValues[1]
            try {
                Color(hex.toLong(16))
            } catch (e: Exception) {
                if (templateType.contains("Calculator")) Color(0xFF1E1E2E) else Color(0xFF0F172A)
            }
        } else {
            if (templateType.contains("Calculator")) Color(0xFF1E1E2E) else if (templateType.contains("Gemini")) Color(0xFF0F172A) else Color(0xFF0F172A)
        }
    }

    // Outer Device Chassis
    Box(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 340.dp)
            .padding(8.dp)
            .background(Color(0xFF111214), RoundedCornerShape(36.dp))
            .border(3.dp, if (platform == "android") Color(0xFF3D3F42) else Color(0xFFD4D4D8), RoundedCornerShape(36.dp))
            .padding(6.dp)
    ) {
        // Phone Screen Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .background(extractedBgColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Device Status Bar Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(Color(0x33000000))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "12:15",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Device Notch Mockup
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(18.dp)
                            .background(Color.Black, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📶 5G",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .border(1.dp, Color.White, RoundedCornerShape(2.dp))
                                .padding(1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(6.dp)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Render specific project view inside virtual device screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        templateType.contains("Calculator") -> {
                            SimulatedCalculatorApp(title = extractedTitle, bgColor = extractedBgColor)
                        }
                        templateType.contains("Gemini") -> {
                            SimulatedGeminiApp(title = extractedTitle, welcomeMsg = extractedGreeting, bgColor = extractedBgColor)
                        }
                        else -> {
                            SimulatedGreetingApp(title = extractedTitle, bgColor = extractedBgColor)
                        }
                    }
                }

                // Device Safety Gesture Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

// 1. Simulated Calculator KMP screen
@Composable
fun SimulatedCalculatorApp(title: String, bgColor: Color) {
    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var activeOp by remember { mutableStateOf<String?>(null) }
    var isNewNumber by remember { mutableStateOf(true) }

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
                "+" -> op1 + op2
                "-" -> op1 - op2
                "×" -> op1 * op2
                "÷" -> if (op2 != 0.0) op1 / op2 else Double.NaN
                else -> 0.0
            }
            display = if (result.isNaN()) "Error" else if (result % 1.0 == 0.0) result.toInt().toString() else result.toString()
        } catch (e: Exception) {
            display = "Error"
        }
        operand1 = null
        activeOp = null
        isNewNumber = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(),
            color = Color(0xFFA6ADC8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Screen Output
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f)
                .padding(vertical = 8.dp)
                .background(Color(0xFF11111B), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                color = Color(0xFFCDD6F4),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Keypad grid
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(0.7f).fillMaxWidth()
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
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { char ->
                        val isOperation = char in listOf("+", "-", "×", "÷", "=")
                        val isClear = char == "C"
                        val weight = if (char == "0" || char == "C") 2f else 1f

                        Button(
                            onClick = {
                                when {
                                    isClear -> {
                                        display = "0"
                                        operand1 = null
                                        activeOp = null
                                        isNewNumber = true
                                    }
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
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                        ) {
                            Text(text = char, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// 2. Simulated Gemini Chat Client
data class SimMessage(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatedGeminiApp(title: String, welcomeMsg: String, bgColor: Color) {
    var promptText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<SimMessage>() }

    // Synchronize welcome greeting
    LaunchedEffect(welcomeMsg) {
        messages.clear()
        messages.add(SimMessage(welcomeMsg, false))
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Simple Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.widthIn(max = 220.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Send row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = promptText,
                onValueChange = { promptText = it },
                placeholder = { Text("Ask simulated Gemini...", fontSize = 11.sp, color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(fontSize = 12.sp),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    val text = promptText.trim()
                    if (text.isNotEmpty()) {
                        messages.add(SimMessage(text, true))
                        promptText = ""
                        messages.add(SimMessage("Query built in KMP sandbox. Simulated response returned SUCCESS!", false))
                    }
                },
                modifier = Modifier.size(36.dp).background(Color(0xFF3B82F6), CircleShape)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// 3. Simulated Greeting screen
@Composable
fun SimulatedGreetingApp(title: String, bgColor: Color) {
    var greetingText by remember { mutableStateOf("Ready for Welcome...") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = greetingText,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { 
                        greetingText = "Hello World! Compiled on platform: Multiplatform Shared Sandbox Engine [v2.2]"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Trigger Greeting", fontSize = 12.sp)
                }
            }
        }
    }
}
