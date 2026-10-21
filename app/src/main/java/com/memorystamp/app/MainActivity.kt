package com.memorystamp.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Stamps.load(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFC8643B),
                    secondaryContainer = Color(0xFFF6D9C4),
                    background = Color(0xFFFBF6EE),
                    surface = Color(0xFFFBF6EE),
                    surfaceVariant = Color(0xFFEFE5D6),
                ),
            ) { App() }
        }
    }
}

private val TABS = listOf("📷" to "Chụp", "📅" to "Nhật ký", "⚙️" to "Cài đặt")

@Composable
private fun App() {
    val prefs = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var gallery by remember { mutableStateOf(prefs.getBoolean("gallery", true)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(icon) }, label = { Text(label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> CameraScreen(saveToGallery = gallery)
                1 -> JournalScreen()
                else -> SettingsScreen(gallery) {
                    gallery = it
                    prefs.edit().putBoolean("gallery", it).apply()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(gallery: Boolean, setGallery: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Stamps.exportZip(context, uri) }.isSuccess }
            Toast.makeText(context, if (ok) "Đã xuất sao lưu" else "Xuất sao lưu lỗi", Toast.LENGTH_SHORT).show()
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { Stamps.importZip(context, uri) }.getOrNull() }
            Toast.makeText(context, if (n != null) "Đã nhập $n tem" else "File sao lưu không đọc được", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lưu thêm vào thư viện ảnh", style = MaterialTheme.typography.titleMedium)
                Text("Tem hiện trong Gallery (Pictures/MemoryStamp) và vẫn còn khi gỡ app.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(gallery, setGallery)
        }
        Text("Sao lưu", style = MaterialTheme.typography.titleMedium)
        Text(
            "Mọi tem chỉ nằm trong máy này. Gỡ app hoặc mất máy là mất nhật ký, nên thỉnh thoảng hãy xuất một file sao lưu và cất ở nơi khác.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ export.launch("MemoryStamp-${LocalDate.now()}.zip") }) { Text("Xuất sao lưu") }
            OutlinedButton({ restore.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Nhập sao lưu") }
        }
        Text("Đang có ${Stamps.all.size} tem trong máy.", style = MaterialTheme.typography.labelMedium)
    }
}
