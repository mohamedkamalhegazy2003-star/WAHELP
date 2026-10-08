package com.local.wahelper.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.local.wahelper.data.AppDatabase
import com.local.wahelper.data.CapturedMessage
import com.local.wahelper.status.StatusItem
import com.local.wahelper.status.StatusRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                NavigationBarItem(
                    selected = current == "messages",
                    onClick = { nav.navigate("messages") },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("الرسايل") }
                )
                NavigationBarItem(
                    selected = current == "status",
                    onClick = { nav.navigate("status") },
                    icon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    label = { Text("الاستوريهات") }
                )
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "messages", modifier = Modifier.padding(padding)) {
            composable("messages") { MessagesScreen() }
            composable("status") { StatusScreen() }
        }
    }
}

@Composable
fun MessagesScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).messageDao() }
    val messages by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val listenerEnabled = remember { isNotificationAccessGranted(context) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (!listenerEnabled) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("لازم تفعّل صلاحية \"الوصول للإشعارات\" عشان البرنامج يقدر يقرأ رسايل واتساب.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text("فتح الإعدادات") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لسه مفيش رسايل اتسجلت. أي رسالة واتساب جديدة هتظهر هنا.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.id }) { msg -> MessageCard(msg) }
            }
        }
    }
}

@Composable
fun MessageCard(msg: CapturedMessage) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg.sender, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    SimpleDateFormat("HH:mm  d/M", Locale("ar")).format(Date(msg.timestamp)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(4.dp))
            when (msg.kind) {
                "IMAGE" -> Text("📷 صورة: ${msg.text}")
                "AUDIO" -> Text("🎤 رسالة صوتية جديدة")
                "FILE" -> Text("📎 ملف: ${msg.text}")
                else -> Text(msg.text)
            }
            msg.imagePath?.let {
                Spacer(Modifier.height(8.dp))
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun StatusScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wahelper", 0) }
    var treeUri by remember {
        mutableStateOf(prefs.getString("status_tree", null)?.let { Uri.parse(it) })
    }
    val repo = remember { StatusRepository(context) }
    var items by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            treeUri = uri
            prefs.edit { putString("status_tree", uri.toString()) }
            items = repo.listStatuses(uri)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("اختار مجلد واتساب (Android/media/com.whatsapp/WhatsApp/Media) مرة واحدة بس، والبرنامج هيلاقي الاستوريهات بنفسه من غير ما يفتح واتساب — يعني محدش هيعرف إنك شُفتها.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { folderPicker.launch(null) }) {
                    Text(if (treeUri == null) "اختيار المجلد" else "تغيير المجلد")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty() && treeUri != null) {
            Text("مفيش استوريهات محفوظة دلوقتي، او المجلد اتغير. جرب تفتح واتساب (بس من غير ما تدخل على استوري حد) عشان يحمّل الاستوريهات الجديدة.")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.uri.toString() }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!item.isVideo) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                Text("🎬 فيديو")
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            scope.launch { repo.saveToDownloads(item) }
                        }) {
                            Icon(Icons.Filled.Image, contentDescription = "حفظ")
                        }
                    }
                }
            }
        }

        LaunchedEffect(treeUri) {
            treeUri?.let { items = repo.listStatuses(it) }
        }
    }
}

private fun isNotificationAccessGranted(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return enabled?.contains(context.packageName) == true
}
