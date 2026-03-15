package top.niunaijun.blackboxa.view.main

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.niunaijun.blackbox.BlackBoxCore

// ─── Colores ──────────────────────────────────────────────────────────────────
private val BgDark       = Color(0xFF0D0D0D)
private val CardDark     = Color(0xFF1A1A1A)
private val AccentBlue   = Color(0xFF1A73E8)
private val AccentBlue2  = Color(0xFF0D47A1)
private val TextPrimary  = Color(0xFFFFFFFF)
private val TextSecond   = Color(0xFF888888)
private val DividerColor = Color(0xFF252525)

private const val TAG = "JustuMainActivity"
private const val PREFS_NAME = "justumulticlones_prefs"

// ─── Modelo ───────────────────────────────────────────────────────────────────
data class CloneSpace(val id: Int, val userId: Int, val apps: MutableList<DeviceApp> = mutableStateListOf())
data class DeviceApp(val pkgName: String, val label: String, val bitmap: ImageBitmap)

// ─── Persistencia ─────────────────────────────────────────────────────────────
fun saveSpacesData(context: Context, spaces: List<CloneSpace>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    editor.putInt("space_count", spaces.size)
    spaces.forEach { space ->
        val pkgs = space.apps.joinToString(",") { it.pkgName }
        editor.putString("space_${space.id}_apps", pkgs)
    }
    editor.apply()
}

fun loadSpaceCount(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt("space_count", 1)
}

fun loadSpacePkgs(context: Context, spaceId: Int): List<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val str = prefs.getString("space_${spaceId}_apps", "") ?: ""
    return if (str.isBlank()) emptyList() else str.split(",").filter { it.isNotBlank() }
}

// ─── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            BlackBoxCore.get().onBeforeMainActivityOnCreate(this)
        } catch (e: Exception) {
            Log.e(TAG, "onBeforeMainActivityOnCreate: ${e.message}")
        }

        setContent {
            MaterialTheme(
                colors = darkColors(
                    background   = BgDark,
                    surface      = CardDark,
                    primary      = AccentBlue,
                    secondary    = AccentBlue2,
                    onBackground = TextPrimary,
                    onSurface    = TextPrimary
                )
            ) {
                Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
                    JustuRoot()
                }
            }
        }

        try {
            BlackBoxCore.get().onAfterMainActivityOnCreate(this)
        } catch (e: Exception) {
            Log.e(TAG, "onAfterMainActivityOnCreate: ${e.message}")
        }
    }
}

enum class JustuScreen { HOME, IMPORT, MENU }

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun JustuRoot() {
    val context = LocalContext.current

    // Cargar apps del dispositivo en hilo IO
    val deviceApps = remember { mutableStateListOf<DeviceApp>() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val pm = context.packageManager
            val temp = mutableListOf<DeviceApp>()
            pm.queryIntentActivities(intent, 0).forEach { info ->
                val pkg = info.activityInfo.applicationInfo.packageName
                if (pkg == context.packageName) return@forEach
                try {
                    val pkgInfo = pm.getPackageInfo(pkg, 0)
                    val isSys = (pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSys) return@forEach
                    val labelId = pkgInfo.applicationInfo.labelRes
                    val label = if (labelId == 0) pkg
                                else pm.getResourcesForApplication(pkg).getString(labelId)
                    val icon = pkgInfo.applicationInfo.loadIcon(pm)
                    val bmp = drawableToBitmap(icon)
                    temp.add(DeviceApp(pkg, label, bmp))
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading app $pkg: ${e.message}")
                }
            }
            temp.sortBy { it.label.lowercase() }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                deviceApps.addAll(temp)
            }
        }
    }

    // Cargar espacios guardados
    val spaces = remember { mutableStateListOf<CloneSpace>() }
    LaunchedEffect(deviceApps.size) {
        if (deviceApps.isEmpty() || spaces.isNotEmpty()) return@LaunchedEffect
        val count = loadSpaceCount(context)

        // Crear usuarios en BlackBox si no existen
        try {
            val existingUsers = BlackBoxCore.get().users
            for (i in existingUsers.size until count) {
                BlackBoxCore.get().createUser("Espacio ${i + 1}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating users: ${e.message}")
        }

        for (i in 1..count) {
            val pkgs = loadSpacePkgs(context, i)
            val spaceApps = mutableStateListOf<DeviceApp>()
            pkgs.forEach { pkg ->
                val app = deviceApps.find { it.pkgName == pkg }
                if (app != null) spaceApps.add(app)
            }
            spaces.add(CloneSpace(i, i - 1, spaceApps))
        }
    }

    if (spaces.isEmpty() && deviceApps.isNotEmpty()) {
        LaunchedEffect(Unit) {
            if (spaces.isEmpty()) {
                try { BlackBoxCore.get().createUser("Espacio 1") } catch (e: Exception) {}
                spaces.add(CloneSpace(1, 0))
            }
        }
    }

    var screen      by remember { mutableStateOf(JustuScreen.HOME) }
    var targetSpace by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var toastMsg    by rememberSaveable { mutableStateOf("") }
    var showToast   by rememberSaveable { mutableStateOf(false) }

    fun toast(msg: String) { toastMsg = msg; showToast = true }

    // Guardar cuando cambian los espacios
    LaunchedEffect(spaces.size, spaces.map { it.apps.size }.sum()) {
        if (spaces.isNotEmpty()) saveSpacesData(context, spaces)
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            JustuScreen.HOME -> JustuHomeScreen(
                spaces     = spaces,
                onOpenMenu = { screen = JustuScreen.MENU },
                onAddSpace = {
                    val newId = spaces.size + 1
                    val userId = newId - 1
                    try { BlackBoxCore.get().createUser("Espacio $newId") } catch (e: Exception) {}
                    spaces.add(CloneSpace(newId, userId))
                    saveSpacesData(context, spaces)
                    toast("Espacio $newId creado ✓")
                },
                onImport    = { spaceIdx -> targetSpace = spaceIdx; searchQuery = ""; screen = JustuScreen.IMPORT },
                onRemoveApp = { spaceIdx, pkg ->
                    val space = spaces.getOrNull(spaceIdx)
                    space?.apps?.removeIf { it.pkgName == pkg }
                    try {
                        BlackBoxCore.get().uninstallPackageAsUser(pkg, space?.userId ?: 0)
                    } catch (e: Exception) {}
                    saveSpacesData(context, spaces)
                    toast("App eliminada")
                },
                onStartApp  = { spaceIdx, app ->
                    val space = spaces.getOrNull(spaceIdx)
                    val userId = space?.userId ?: 0
                    try {
                        val result = BlackBoxCore.get().launchApk(app.pkgName, userId)
                        if (!result) toast("Error al iniciar ${app.label}")
                    } catch (e: Exception) {
                        toast("Error: ${e.message}")
                    }
                },
                toast = ::toast
            )
            JustuScreen.IMPORT -> JustuImportScreen(
                deviceApps  = deviceApps,
                searchQuery = searchQuery,
                onSearch    = { searchQuery = it },
                onBack      = { screen = JustuScreen.HOME },
                onAddApp    = { app ->
                    val space = spaces.getOrNull(targetSpace)
                    if (space != null) {
                        if (space.apps.any { it.pkgName == app.pkgName }) {
                            toast("Ya está en Espacio ${space.id}")
                        } else {
                            try {
                                BlackBoxCore.get().installPackageAsUser(app.pkgName, space.userId)
                                space.apps.add(app)
                                saveSpacesData(context, spaces)
                                toast("${app.label} clonado en Espacio ${space.id} ✓")
                            } catch (e: Exception) {
                                toast("Error al clonar ${app.label}")
                            }
                        }
                    }
                }
            )
            JustuScreen.MENU -> JustuMenuScreen(
                onBack       = { screen = JustuScreen.HOME },
                onImportApps = { targetSpace = 0; screen = JustuScreen.IMPORT },
                toast        = ::toast
            )
        }

        // Toast flotante
        if (showToast && toastMsg.isNotEmpty()) {
            LaunchedEffect(toastMsg) {
                kotlinx.coroutines.delay(2200)
                showToast = false
            }
            Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = RoundedCornerShape(50), color = AccentBlue, elevation = 8.dp) {
                    Text(toastMsg, color = Color.White,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── HOME ─────────────────────────────────────────────────────────────────────
@Composable
fun JustuHomeScreen(
    spaces:      List<CloneSpace>,
    onOpenMenu:  () -> Unit,
    onAddSpace:  () -> Unit,
    onImport:    (Int) -> Unit,
    onRemoveApp: (Int, String) -> Unit,
    onStartApp:  (Int, DeviceApp) -> Unit,
    toast:       (String) -> Unit
) {
    Scaffold(
        backgroundColor = BgDark,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSpace, backgroundColor = AccentBlue, contentColor = Color.White) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo espacio")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 50.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("JustuMulticlones", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menú", tint = TextPrimary)
                    }
                }
            }

            items(spaces.indices.toList()) { idx ->
                val space = spaces[idx]
                JustuSpaceCard(
                    space       = space,
                    onImport    = { onImport(idx) },
                    onRemoveApp = { pkg -> onRemoveApp(idx, pkg) },
                    onStartApp  = { app -> onStartApp(idx, app) }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(2.dp, DividerColor, RoundedCornerShape(18.dp))
                        .clickable { onAddSpace() }.padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = TextSecond)
                        Spacer(Modifier.width(6.dp))
                        Text("Nuevo espacio", color = TextSecond, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Tarjeta Espacio ──────────────────────────────────────────────────────────
@Composable
fun JustuSpaceCard(
    space:       CloneSpace,
    onImport:    () -> Unit,
    onRemoveApp: (String) -> Unit,
    onStartApp:  (DeviceApp) -> Unit
) {
    Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = CardDark, shape = RoundedCornerShape(18.dp), elevation = 2.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(8.dp), color = AccentBlue) {
                    Text("Espacio ${space.id}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                IconButton(onClick = onImport, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Clonar", tint = AccentBlue, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            if (space.apps.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📱", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Toca + para clonar apps", color = TextSecond, fontSize = 13.sp)
                    }
                }
            } else {
                space.apps.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { app ->
                            JustuAppItem(app = app, onClick = { onStartApp(app) }, onLongClick = { onRemoveApp(app.pkgName) }, modifier = Modifier.weight(1f))
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ─── App Item ─────────────────────────────────────────────────────────────────
@Composable
fun JustuAppItem(app: DeviceApp, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(bitmap = app.bitmap, contentDescription = app.label, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(5.dp))
        Text(app.label, fontSize = 10.sp, color = TextPrimary.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ─── IMPORT ───────────────────────────────────────────────────────────────────
@Composable
fun JustuImportScreen(deviceApps: List<DeviceApp>, searchQuery: String, onSearch: (String) -> Unit, onBack: () -> Unit, onAddApp: (DeviceApp) -> Unit) {
    val filtered = remember(searchQuery, deviceApps.size) {
        if (searchQuery.isBlank()) deviceApps
        else deviceApps.filter { it.label.contains(searchQuery, true) || it.pkgName.contains(searchQuery, true) }
    }

    Scaffold(
        backgroundColor = BgDark,
        topBar = {
            TopAppBar(backgroundColor = CardDark, elevation = 0.dp,
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = TextPrimary) } },
                title = { Text("Clonar App", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp) })
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(value = searchQuery, onValueChange = onSearch,
                    placeholder = { Text("Buscar app...", color = TextSecond) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecond) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = TextPrimary, backgroundColor = CardDark,
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = DividerColor, cursorColor = AccentBlue),
                    shape = RoundedCornerShape(14.dp))
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Apps instaladas", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecond)
                    Text("${filtered.size} apps", fontSize = 12.sp, color = TextSecond)
                }
            }
            if (deviceApps.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentBlue)
                            Spacer(Modifier.height(12.dp))
                            Text("Cargando apps...", color = TextSecond, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(filtered) { app ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(bitmap = app.bitmap, contentDescription = app.label, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.pkgName, color = TextSecond, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onAddApp(app) }) {
                            Icon(Icons.Filled.Add, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                        }
                    }
                    Divider(color = DividerColor, thickness = 0.5.dp)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─── MENÚ ─────────────────────────────────────────────────────────────────────
@Composable
fun JustuMenuScreen(onBack: () -> Unit, onImportApps: () -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current
    val items = listOf(
        "Clonar Apps"          to onImportApps,
        "Activar Google Play"  to {
            try {
                val users = BlackBoxCore.get().users
                users.forEach { user ->
                    listOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending")
                        .forEach { pkg -> try { BlackBoxCore.get().installPackageAsUser(pkg, user.id) } catch (e: Exception) {} }
                }
                toast("Google Play activado ✓")
            } catch (e: Exception) { toast("Error al activar Google Play") }
        },
        "Info del dispositivo" to { toast("Android ${android.os.Build.VERSION.RELEASE} — ${android.os.Build.MODEL}") }
    )
    Scaffold(
        backgroundColor = BgDark,
        topBar = {
            TopAppBar(backgroundColor = CardDark, elevation = 0.dp,
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = TextPrimary) } },
                title = { Text("Menú", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp) })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            items.forEachIndexed { i, (label, action) ->
                Row(Modifier.fillMaxWidth().clickable { action() }.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (i == 0) Color(0xFF4FC3F7) else TextPrimary)
                    Icon(Icons.Filled.ChevronRight, null, tint = TextSecond)
                }
                Divider(color = DividerColor, thickness = 0.5.dp)
            }
        }
    }
}

// ─── Utilidad ─────────────────────────────────────────────────────────────────
fun drawableToBitmap(icon: Drawable): ImageBitmap {
    val bmp = Bitmap.createBitmap(icon.intrinsicWidth, icon.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val c = Canvas(); c.setBitmap(bmp)
    icon.setBounds(0, 0, c.width, c.height); icon.draw(c)
    c.setBitmap(null)
    return bmp.asImageBitmap()
}
