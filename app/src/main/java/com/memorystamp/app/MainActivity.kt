package com.memorystamp.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
        Pages.load(this)
        Letters.load(this)
        val startTab = intent.getIntExtra("tab", 0)
        setContent { CuteTheme { App(startTab) } }
    }
}

private val TABS = listOf(
    Triple(R.drawable.ic_camera, "Chụp", 0),
    Triple(R.drawable.ic_book, "Nhật ký", 1),
    Triple(R.drawable.ic_scrap, "Sổ tay", 2),
    Triple(R.drawable.ic_mail, "Thư", 3),
)

@Composable
private fun App(startTab: Int) {
    val prefs = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var gallery by remember { mutableStateOf(prefs.getBoolean("gallery", true)) }
    val ready = Letters.all.count { it.ready && !it.opened }

    Scaffold(
        containerColor = Palette.Cream,
        bottomBar = {
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()
                    .shadow(8.dp, CircleShape, ambientColor = Palette.Rose, spotColor = Palette.Rose)
                    .background(Palette.Paper, CircleShape).padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TABS.forEach { (icon, label, i) ->
                    val on = tab == i && !settings
                    Row(
                        Modifier.clip(CircleShape).background(if (on) Palette.PinkSoft else Color.Transparent)
                            .clickable(onClickLabel = label) { tab = i; settings = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Icon(painterResource(icon), label, tint = if (on) Palette.Rose else Palette.Ink, modifier = Modifier.size(22.dp))
                            if (i == 3 && ready > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(Palette.Rose, CircleShape))
                        }
                        AnimatedVisibility(on) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Rose, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
            if (settings) {
                BackHandler { settings = false }
                SettingsScreen(gallery, { settings = false }) {
                    gallery = it
                    prefs.edit().putBoolean("gallery", it).apply()
                }
            } else when (tab) {
                0 -> CameraScreen(saveToGallery = gallery)
                1 -> JournalScreen(onSettings = { settings = true })
                2 -> ScrapbookScreen()
                else -> LettersScreen()
            }
        }
    }
}

@Composable
private fun SettingsScreen(gallery: Boolean, onBack: () -> Unit, setGallery: (Boolean) -> Unit) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Bubble(R.drawable.ic_back, "Quay lại", onClick = onBack)
            Text("Cài đặt", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lưu thêm vào thư viện ảnh", style = MaterialTheme.typography.titleMedium)
                Text("Tem hiện trong Gallery (Pictures/MemoryStamp) và vẫn còn khi gỡ app.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(gallery, setGallery, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Rose, uncheckedTrackColor = Palette.PinkSoft, uncheckedBorderColor = Palette.Pink, uncheckedThumbColor = Palette.Pink))
        }
        Text("Sao lưu", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tem, sổ tay và thư chỉ nằm trong máy này. Gỡ app hoặc mất máy là mất nhật ký, nên thỉnh thoảng hãy xuất một file sao lưu và cất ở nơi khác.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Xuất sao lưu", true) { export.launch("MemoryStamp-${LocalDate.now()}.zip") }
            Pill("Nhập sao lưu", false) { restore.launch(arrayOf("application/zip", "application/octet-stream")) }
        }
        Text("Đang có ${Stamps.all.size} tem, ${Pages.all.size} trang sổ tay, ${Letters.all.size} lá thư.", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
    }
}
