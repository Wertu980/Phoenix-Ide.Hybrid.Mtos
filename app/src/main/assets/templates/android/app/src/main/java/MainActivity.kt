package __PACKAGE_NAME__

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
                    text = "Welcome to your real Android compiler project saved inside memory disk!",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = { count++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Interactive Tap Counter: $count")
                }
            }
        }
    }
}
