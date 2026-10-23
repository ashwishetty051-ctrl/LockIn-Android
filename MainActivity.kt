package com.lockin.app

import android.app.TimePickerDialog
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

val Navy = Color(0xFF0F172A)
val Amber = Color(0xFFFACC15)
val Paper = Color(0xFFF8FAFC)
val Slate = Color(0xFF334155)
val Muted = Color(0xFF94A3B8)
val SurfaceDark = Color(0xFF172338)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LockInApp() }
    }
}

private data class Destination(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val destinations = listOf(
    Destination("home", "Home", Icons.Default.Home),
    Destination("focus", "Focus", Icons.Default.Timer),
    Destination("alarm", "Alarm", Icons.Default.Alarm),
    Destination("bedtime", "Bedtime", Icons.Default.Bedtime),
    Destination("progress", "Progress", Icons.Default.ShowChart),
    Destination("room", "Room", Icons.Default.School)
)

@Composable
private fun LockInTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Amber,
            onPrimary = Navy,
            secondary = Amber,
            background = Navy,
            surface = SurfaceDark,
            onBackground = Paper,
            onSurface = Paper
        ),
        content = content
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LockInApp() {
    LockInTheme {
        val context = LocalContext.current
        val app = context.applicationContext as LockInApplication
        val settings by app.settings.settings.collectAsState(initial = AppSettings())
        val sessions by app.database.sessionDao().observeAll().collectAsState(initial = emptyList())
        var datePulse by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                datePulse = System.currentTimeMillis()
            }
        }
        val summary = remember(sessions, datePulse) { summarizeSessions(sessions) }
        var route by remember { mutableStateOf("home") }
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(settings.activeUntil, settings.bedtimeEnabled) {
            if (settings.activeUntil > 0L || settings.bedtimeEnabled) {
                try { FocusMonitorService.start(context) } catch (_: Exception) { }
            }
        }

        Scaffold(
            containerColor = Navy,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("LockIn", fontWeight = FontWeight.Black, letterSpacing = 0.3.sp)
                            Text("LESS SCROLLING. MORE BECOMING.", fontSize = 9.sp, letterSpacing = 1.2.sp, color = Muted)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Permissions and settings", tint = Amber)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Paper)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark) {
                    destinations.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.id,
                            onClick = { route = item.id },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 9.sp, maxLines = 1) },
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        ) { padding ->
            AnimatedContent(route, modifier = Modifier.padding(padding), label = "screen") { screen ->
                when (screen) {
                    "home" -> HomeScreen(settings, summary, onNavigate = { route = it })
                    "focus" -> FocusScreen(settings, app, summary)
                    "alarm" -> AlarmScreen(settings, app)
                    "bedtime" -> BedtimeScreen(settings, app)
                    "progress" -> ProgressScreen(summary)
                    "room" -> StudyRoomScreen()
                    else -> HomeScreen(settings, summary, onNavigate = { route = it })
                }
            }
        }
        if (showSettings) PermissionSettingsDialog(onDismiss = { showSettings = false })
    }
}

@Composable
private fun Page(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().background(Navy).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun HomeScreen(settings: AppSettings, summary: ProgressSummary, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LockInApplication
    val scope = rememberCoroutineScope()
    var showGoalDialog by remember { mutableStateOf(false) }
    var goalMinutes by remember(settings.dailyGoalMinutes) { mutableIntStateOf(settings.dailyGoalMinutes) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Navy),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Text(greeting(), color = Muted, fontSize = 14.sp)
            Text(
                SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Amber),
                modifier = Modifier.fillMaxWidth().clickable { onNavigate("focus") }
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("MAKE THIS SESSION COUNT", color = Navy.copy(alpha = .7f), fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                    Text(if (settings.focusActive) "Your focus session is in progress." else "Less scrolling.\nMore becoming.", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
                    Button(
                        onClick = { onNavigate("focus") },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Paper),
                        shape = RoundedCornerShape(15.dp)
                    ) { Text(if (settings.focusActive) "Return to focus" else "Start focus", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("TODAY", "${summary.todayMinutes}", "focus minutes", Modifier.weight(1f))
                MetricCard("SESSIONS", "${summary.todayCompleted}", "completed today", Modifier.weight(1f))
                MetricCard("STREAK", "${summary.currentStreak}", "days", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily focus goal", color = Paper, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${summary.todayMinutes} / ${settings.dailyGoalMinutes} min", color = Amber, fontSize = 12.sp)
                    }
                    TextButton(onClick = { showGoalDialog = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Change daily goal", color = Amber, fontSize = 11.sp)
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (summary.todayMinutes.toFloat() / settings.dailyGoalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Amber,
                        trackColor = Navy
                    )
                    Text("Your goal is saved on this device.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallInfoCard("Next alarm", if (settings.alarmEnabled) formatTime(settings.alarmHour, settings.alarmMinute) else "Not set", Icons.Default.Alarm, Modifier.weight(1f)) { onNavigate("alarm") }
                SmallInfoCard("Bedtime", if (settings.bedtimeEnabled) formatTime(settings.bedtimeStartMinute / 60, settings.bedtimeStartMinute % 60) else "Not set", Icons.Default.Bedtime, Modifier.weight(1f)) { onNavigate("bedtime") }
            }
        }
        item {
            Text("YOUR SPACE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickLink("Focus", Icons.Default.Timer) { onNavigate("focus") }
                QuickLink("Progress", Icons.Default.ShowChart) { onNavigate("progress") }
                QuickLink("Study room", Icons.Default.School) { onNavigate("room") }
            }
        }
        if (settings.focusActive) item {
            Text("Session ends ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(settings.activeUntil))}", color = Amber, fontSize = 13.sp)
        }
    }
    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Daily focus goal", color = Paper) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$goalMinutes minutes", color = Amber, fontWeight = FontWeight.Bold)
                    Slider(
                        value = goalMinutes.toFloat(),
                        onValueChange = { goalMinutes = (it.toInt() / 15 * 15).coerceIn(15, 600) },
                        valueRange = 15f..600f,
                        steps = 38
                    )
                    Text("Choose 15 to 600 minutes. This goal is stored on your device.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { app.settings.saveGoal(goalMinutes) }
                    showGoalDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy)) { Text("Save goal") }
            },
            dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text("Cancel", color = Paper) } }
        )
    }
}

@Composable
private fun MetricCard(title: String, value: String, foot: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Muted, fontSize = 9.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(foot, color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SmallInfoCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Amber, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Muted, fontSize = 11.sp)
                Text(value, color = Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickLink(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun FocusScreen(settings: AppSettings, app: LockInApplication, summary: ProgressSummary) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember { discoverLaunchableApps(context) }
    var selected by remember(settings.selectedPackages) { mutableStateOf(settings.selectedPackages) }
    var selectedMinutes by remember(settings.durationMinutes) { mutableIntStateOf(settings.durationMinutes) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showEmergency by remember { mutableStateOf(false) }
    var emergencyCount by remember { mutableIntStateOf(10) }
    var focusActionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.activeUntil) {
        while (settings.activeUntil > 0) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(showEmergency) {
        if (showEmergency) {
            emergencyCount = 10
            while (emergencyCount > 0) {
                delay(1_000)
                emergencyCount--
            }
        }
    }
    Page {
        Text("FOCUS LOCK", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Choose what you’re protecting.", color = Paper, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        if (settings.activeUntil > 0L) {
            val remaining = ((settings.activeUntil - now).coerceAtLeast(0) / 1000).toInt()
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SESSION IN PROGRESS", color = Amber, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                    Text("%02d:%02d".format(remaining / 60, remaining % 60), color = Paper, fontSize = 50.sp, fontWeight = FontWeight.Black)
                    Text("Ends ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(settings.activeUntil))}", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    Text("${settings.selectedPackages.size} selected apps · ${settings.activePlannedMinutes} minute session", color = Muted, textAlign = TextAlign.Center)
                    val protectedAppNames = apps.filter { it.packageName in settings.selectedPackages }.map { it.label }
                    Text(
                        if (protectedAppNames.isEmpty()) "No apps selected"
                        else "Protected apps: ${protectedAppNames.joinToString()}",
                        color = Paper,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { showEmergency = true }, shape = RoundedCornerShape(15.dp)) {
                        Text("Emergency exit", color = Paper)
                    }
                }
            }
            Text("An interrupted session is recorded and resets today’s streak run.", color = Muted, fontSize = 12.sp)
        } else {
            Text("Duration", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(30, 45, 60, 90, 120).forEach { minutes ->
                    val active = selectedMinutes == minutes
                    Surface(
                        modifier = Modifier.weight(1f).clickable { selectedMinutes = minutes },
                        color = if (active) Amber else SurfaceDark,
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text("$minutes", modifier = Modifier.padding(vertical = 13.dp), color = if (active) Navy else Paper, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Distracting apps", color = Paper, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${selected.size} selected", color = Amber, fontSize = 12.sp)
            }
            Text("Only installed apps with a launcher icon are listed.", color = Muted, fontSize = 12.sp)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(apps, key = { it.packageName }) { info ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceDark)
                            .clickable {
                                selected = if (info.packageName in selected) selected - info.packageName else selected + info.packageName
                                scope.launch { app.settings.saveSelected(selected) }
                            }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(info, Modifier.size(34.dp))
                        Spacer(Modifier.width(11.dp))
                        Text(info.label, color = Paper, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Checkbox(checked = info.packageName in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + info.packageName else selected - info.packageName
                            scope.launch { app.settings.saveSelected(selected) }
                        })
                    }
                }
                if (apps.isEmpty()) item { Text("No launchable apps were found on this device.", color = Muted) }
            }
            Button(
                onClick = {
                    val usageAvailable = hasUsageAccess(context)
                    val overlayAvailable = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
                    focusActionError = when {
                        !usageAvailable -> "Enable Usage Access in Permissions & settings before LockIn can detect selected apps."
                        selected.isNotEmpty() && !overlayAvailable -> "Session timer started, but the blocking screen needs Display over other apps. Enable it in Permissions & settings."
                        else -> null
                    }
                    if (usageAvailable) scope.launch {
                        if (app.settings.startFocus(selectedMinutes, selected)) {
                            FocusMonitorService.start(context)
                        } else {
                            focusActionError = "A focus session is already active. Return to it instead of starting another."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Start $selectedMinutes-minute focus", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 3.dp)) }
            focusActionError?.let { Text(it, color = Amber, fontSize = 12.sp) }
        }
        if (summary.todayCompleted > 0) Text("Today: ${summary.todayCompleted} completed session${if (summary.todayCompleted == 1) "" else "s"}", color = Muted, fontSize = 12.sp)
    }
    if (showEmergency) {
        AlertDialog(
            onDismissRequest = { showEmergency = false },
            containerColor = SurfaceDark,
            title = { Text("End this session early?", color = Paper) },
            text = {
                Column {
                    Text("Your session will be marked interrupted, and your current streak will reset.", color = Muted)
                    Spacer(Modifier.height(16.dp))
                    Text(if (emergencyCount > 0) "Emergency exit available in $emergencyCount seconds" else "You can end the session now.", color = Amber, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    enabled = emergencyCount == 0,
                    onClick = {
                        scope.launch {
                            val ended = System.currentTimeMillis()
                            val started = settings.activeStartedAt
                            app.database.sessionDao().insert(
                                FocusSession(
                                    startedAt = started,
                                    endedAt = ended,
                                    plannedMinutes = settings.activePlannedMinutes,
                                    actualMinutes = ((ended - started) / 60_000L).toInt().coerceAtLeast(0),
                                    status = "interrupted"
                                )
                            )
                            app.settings.clearFocus()
                            context.stopService(Intent(context, FocusMonitorService::class.java))
                        }
                        showEmergency = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy)
                ) { Text("End session") }
            },
            dismissButton = { TextButton(onClick = { showEmergency = false }) { Text("Keep focusing", color = Paper) } }
        )
    }
}

private data class LaunchableApp(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable)

@Suppress("DEPRECATION")
private fun discoverLaunchableApps(context: Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val pm = context.packageManager
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { resolve ->
            val name = resolve.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val pkg = resolve.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) null else LaunchableApp(pkg, name, resolve.loadIcon(pm))
        }
        .distinctBy { it.packageName }
        .filterNot { it.packageName in ProtectedPackages.forDevice(context) }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

@Composable
private fun AppIcon(info: LaunchableApp, modifier: Modifier = Modifier) {
    val bitmap = remember(info.packageName) {
        val drawable = info.icon
        val bmp = android.graphics.Bitmap.createBitmap(80, 80, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        android.graphics.Bitmap.createScaledBitmap(bmp, 48, 48, true).asImageBitmap()
    }
    androidx.compose.foundation.Image(bitmap, contentDescription = "${info.label} icon", modifier = modifier)
}

@Composable
private fun AlarmScreen(settings: AppSettings, app: LockInApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExactDialog by remember { mutableStateOf(false) }
    var exactPermissionReady by remember { mutableStateOf(AlarmScheduler.hasExactAlarmAccess(context)) }
    val exactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        exactPermissionReady = AlarmScheduler.hasExactAlarmAccess(context)
        if (exactPermissionReady) {
            scope.launch {
                app.settings.saveAlarm(true, settings.alarmHour, settings.alarmMinute)
                AlarmScheduler.schedule(context, settings.alarmHour, settings.alarmMinute)
            }
        }
    }
    Page {
        Text("WAKE-UP ALARM", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Start the day on purpose.", color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Your alarm", color = Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(settings.alarmHour, settings.alarmMinute), color = Paper, fontWeight = FontWeight.Black, fontSize = 42.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.alarmEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                scope.launch { app.settings.saveAlarm(false, settings.alarmHour, settings.alarmMinute) }
                                AlarmScheduler.cancel(context)
                            } else if (!AlarmScheduler.hasExactAlarmAccess(context)) {
                                showExactDialog = true
                            } else {
                                scope.launch {
                                    app.settings.saveAlarm(true, settings.alarmHour, settings.alarmMinute)
                                    AlarmScheduler.schedule(context, settings.alarmHour, settings.alarmMinute)
                                }
                            }
                        }
                    )
                }
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                scope.launch {
                                    app.settings.saveAlarm(settings.alarmEnabled, hour, minute)
                                    if (settings.alarmEnabled && AlarmScheduler.hasExactAlarmAccess(context)) {
                                        AlarmScheduler.schedule(context, hour, minute)
                                    }
                                }
                            },
                            settings.alarmHour, settings.alarmMinute, false
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Choose alarm time", fontWeight = FontWeight.Bold) }
            }
        }
        InfoCard("No snooze button", "Solve three quick addition, subtraction or multiplication questions to dismiss the ringing alarm.")
        InfoCard(
            "How Android alarm delivery works",
            "LockIn uses AlarmManager.setAlarmClock(). Exact-alarm access is required on Android 12 and newer. Full-screen display also depends on notification and device settings. Battery controls and manufacturer rules may delay or limit delivery."
        )
        val notificationsOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        val fullScreenAllowed = Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Text(
            "Exact alarm: ${if (exactPermissionReady) "allowed" else "not allowed"}  ·  Notifications: ${if (notificationsOn) "on" else "off"}  ·  Full screen: ${if (fullScreenAllowed) "allowed" else "restricted"}",
            color = if (exactPermissionReady && notificationsOn && fullScreenAllowed) Amber else Muted,
            fontSize = 11.sp
        )
    }
    if (showExactDialog) {
        AlertDialog(
            onDismissRequest = { showExactDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Allow exact alarms?", color = Paper) },
            text = { Text("Android requires this special access so LockIn can schedule an alarm-clock event at your chosen time. You can turn it off later in system settings.", color = Muted) },
            confirmButton = {
                Button(onClick = {
                    showExactDialog = false
                    if (Build.VERSION.SDK_INT >= 31) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        exactPermissionLauncher.launch(intent)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy)) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showExactDialog = false }) { Text("Not now", color = Paper) } }
        )
    }
}

@Composable
private fun BedtimeScreen(settings: AppSettings, app: LockInApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember { discoverLaunchableApps(context) }
    var selected by remember(settings.bedtimePackages) { mutableStateOf(settings.bedtimePackages) }
    var enabled by remember(settings.bedtimeEnabled) { mutableStateOf(settings.bedtimeEnabled) }
    var warning by remember { mutableStateOf<String?>(null) }
    Page {
        Text("BEDTIME LOCK", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Make room for rest.", color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily schedule", color = Paper, fontWeight = FontWeight.Bold)
                        Text("Repeats every day on this device", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        if (it && !hasUsageAccess(context)) warning = "Enable Usage Access in Permissions & settings before bedtime monitoring can work."
                        else saveBedtime(enabled, settings, selected, app, context, scope)
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeChoice("Starts", settings.bedtimeStartMinute, Modifier.weight(1f)) { minutes ->
                        scope.launch {
                            app.settings.saveBedtime(enabled, minutes, settings.bedtimeEndMinute, selected)
                            if (enabled) FocusMonitorService.start(context)
                        }
                    }
                    TimeChoice("Ends", settings.bedtimeEndMinute, Modifier.weight(1f)) { minutes ->
                        scope.launch {
                            app.settings.saveBedtime(enabled, settings.bedtimeStartMinute, minutes, selected)
                            if (enabled) FocusMonitorService.start(context)
                        }
                    }
                }
            }
        }
        Text("Restrict after bedtime", color = Paper, fontWeight = FontWeight.Bold)
        Text("Calls and essential system functions stay available. Choose the distracting apps you want covered.", color = Muted, fontSize = 12.sp)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(apps, key = { it.packageName }) { info ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceDark)
                        .clickable {
                            selected = if (info.packageName in selected) selected - info.packageName else selected + info.packageName
                            saveBedtime(enabled, settings, selected, app, context, scope)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(info, Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(info.label, color = Paper, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Checkbox(checked = info.packageName in selected, onCheckedChange = { checked ->
                        selected = if (checked) selected + info.packageName else selected - info.packageName
                        saveBedtime(enabled, settings, selected, app, context, scope)
                    })
                }
            }
        }
        InfoCard(
            "Overnight behavior",
            "Schedules that cross midnight are supported. If a focus session is active, its selected apps and end time take priority; bedtime restrictions begin after focus ends."
        )
        warning?.let { Text(it, color = Amber, fontSize = 12.sp) }
        if (enabled && selected.isNotEmpty() && !Settings.canDrawOverlays(context)) {
            Text("Display over other apps is off. The monitor can detect selected apps but cannot show its blocking screen until you enable the overlay permission.", color = Amber, fontSize = 12.sp)
        }
    }
}

private fun saveBedtime(
    enabled: Boolean, settings: AppSettings, selected: Set<String>, app: LockInApplication,
    context: Context, scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        app.settings.saveBedtime(enabled, settings.bedtimeStartMinute, settings.bedtimeEndMinute, selected)
        if (enabled) FocusMonitorService.start(context)
        else if (!settings.focusActive) context.stopService(Intent(context, FocusMonitorService::class.java))
    }
}

@Composable
private fun TimeChoice(title: String, minuteOfDay: Int, modifier: Modifier = Modifier, onSelected: (Int) -> Unit) {
    val context = LocalContext.current
    Card(
        modifier.clickable {
            TimePickerDialog(context, { _, hour, minute -> onSelected(hour * 60 + minute) }, minuteOfDay / 60, minuteOfDay % 60, false).show()
        },
        shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Navy)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, color = Muted, fontSize = 11.sp)
            Text(formatTime(minuteOfDay / 60, minuteOfDay % 60), color = Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProgressScreen(summary: ProgressSummary) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Navy),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("YOUR PROGRESS", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Small promises, kept.", color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (summary.completed == 0 && summary.interrupted == 0) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ShowChart, null, tint = Amber, modifier = Modifier.size(30.dp))
                        Text("Your progress starts here.", color = Paper, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Complete a focus session to see real minutes, streaks and weekly activity. No sample history is included.", color = Muted)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("FOCUS", "${summary.totalMinutes}", "total minutes", Modifier.weight(1f))
                MetricCard("DONE", "${summary.completed}", "sessions", Modifier.weight(1f))
                MetricCard("EXITED", "${summary.interrupted}", "interrupted", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("CURRENT STREAK", "${summary.currentStreak}", "days with completion", Modifier.weight(1f))
                MetricCard("BEST STREAK", "${summary.bestStreak}", "days", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Last 7 days · focus minutes", color = Paper, fontWeight = FontWeight.Bold)
                    val weekLabels = (6 downTo 0).map { offset ->
                        LocalDate.now().minusDays(offset.toLong()).dayOfWeek
                            .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                            .take(1)
                    }
                    WeeklyChart(summary.weeklyMinutes, weekLabels)
                }
            }
        }
        item {
            Text("ACHIEVEMENTS", color = Muted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            AchievementCard("First promise", "Complete your first focus session", summary.completed >= 1)
            AchievementCard("One focused hour", "Record 60 focus minutes", summary.totalMinutes >= 60)
            AchievementCard("A full week", "Build a 7-day completion streak", summary.bestStreak >= 7)
        }
        item {
            Text("A streak counts consecutive calendar days with a completed session. An interrupted latest session resets the current streak; the best historical streak remains.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WeeklyChart(values: List<Int>, days: List<String>) {
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val count = 7
        val segment = size.width / count
        val barWidth = segment * .42f
        values.forEachIndexed { index, value ->
            val barHeight = (value.toFloat() / max) * (size.height - 38f)
            val x = segment * index + (segment - barWidth) / 2
            drawRoundRect(
                color = if (value > 0) Amber else Color(0xFF334155),
                topLeft = Offset(x, size.height - 30f - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(4f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
            drawContext.canvas.nativeCanvas.drawText(
                days[index],
                x + barWidth / 2,
                size.height - 6f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }
}

@Composable
private fun AchievementCard(title: String, caption: String, earned: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = if (earned) Amber else Muted, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, color = Paper, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(caption, color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(if (earned) "EARNED" else "LOCKED", color = if (earned) Amber else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StudyRoomScreen() {
    Page {
        Text("STUDY ROOM", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("A little company for the work.", color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("These are example profiles for demonstration only. They are not real people, online friends or live activity.", color = Muted, fontSize = 13.sp)
        DemoProfile("A", "Aman", "Studying for 52 minutes", "FOCUSING")
        DemoProfile("R", "Riya", "Streak of 12 days", "ON A STREAK")
        Spacer(Modifier.height(4.dp))
        InfoCard("Private by design", "Study Room works offline. LockIn does not connect to other students or upload your activity.")
    }
}

@Composable
private fun DemoProfile(initial: String, name: String, status: String, badge: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Amber),
                contentAlignment = Alignment.Center
            ) { Text(initial, color = Navy, fontWeight = FontWeight.Black, fontSize = 21.sp) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(status, color = Muted, fontSize = 12.sp)
            }
            Text(badge, color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Paper, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(body, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun PermissionSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val usage = remember(refresh) { hasUsageAccess(context) }
    val overlay = remember(refresh) { Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context) }
    val notification = remember(refresh) { androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exact = remember(refresh) { AlarmScheduler.hasExactAlarmAccess(context) }
    val fullScreen = remember(refresh) {
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Permissions & settings", color = Paper, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PermissionRow(
                        "Usage Access", usage,
                        "Lets LockIn check which app is currently in use.",
                        "Open Usage Access settings"
                    ) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        refresh++
                    }
                }
                item {
                    PermissionRow(
                        "Display over other apps", overlay,
                        "Lets LockIn show the blocking screen above a selected app.",
                        "Open overlay settings"
                    ) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        refresh++
                    }
                }
                item {
                    PermissionRow(
                        "Notifications", notification,
                        "Shows focus status and alarm notifications.",
                        if (Build.VERSION.SDK_INT >= 33) "Allow notifications" else "Open app notification settings"
                    ) {
                        if (Build.VERSION.SDK_INT >= 33 && !notification) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    }
                }
                item {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text("Open notification settings", color = Amber, fontSize = 11.sp) }
                }
                item {
                    PermissionRow(
                        "Exact alarms", exact,
                        "Allows a wake-up alarm at the time you chose.",
                        "Open exact-alarm settings"
                    ) {
                        if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        refresh++
                    }
                }
                item {
                    PermissionRow(
                        "Full-screen alarm", fullScreen,
                        "Android may restrict opening the alarm screen over the lock screen.",
                        "Review alarm display access"
                    ) {
                        if (Build.VERSION.SDK_INT >= 34) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
                item { Text("After changing a system permission, return to LockIn to refresh its status. Android and device-maker battery rules can still restrict background operation.", color = Muted, fontSize = 11.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("Done", color = Amber) } }
    )
}

@Composable
private fun PermissionRow(title: String, enabled: Boolean, description: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Navy).padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Paper, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text(if (enabled) "ON" else "OFF", color = if (enabled) Amber else Muted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
        Text(description, color = Muted, fontSize = 11.sp)
        TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(action, color = Amber, fontSize = 11.sp)
        }
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

private fun formatTime(hour: Int, minute: Int): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, minute.coerceIn(0, 59))
        }.time
    )

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

