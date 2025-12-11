package com.achacynthia.phonemanagerapp

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.achacynthia.phonemanagerapp.ui.theme.PhoneManagerAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// Simple data holder for app information
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val permissions: List<String>,
    val isSystemApp: Boolean,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneManagerAppTheme {
                Scaffold { innerPadding ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)) {
                        AppList()
                    }
                }
            }
        }
    }
}

@Composable
fun AppList() {
    val context = LocalContext.current
    val pm = context.packageManager

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var query by remember { mutableStateOf("") }

    // List state to allow programmatic scrolling
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Load apps once on composition
    LaunchedEffect(Unit) {
        apps = loadInstalledApps(pm)
    }

    // When query changes, scroll to the first matching item (if any)
    LaunchedEffect(query, apps) {
        if (query.isNotBlank()) {
            val idx = apps.indexOfFirst { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            if (idx >= 0) {
                // animate to the found item
                listState.animateScrollToItem(idx)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // H1 heading at the top
        Text(
            text = "Welcome! Learn more about the apps in your phone",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Search bar at top (always visible)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { androidx.compose.material3.Text(text = "Search apps by name or package") },
            leadingIcon = { androidx.compose.material3.Text(text = "🔍") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Text(text = "Clear")
                    }
                }
            },
        )

        // Always show the full list; highlight matches instead of hiding others
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps) { app ->
                val isMatch = query.isNotBlank() && (app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                ) {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = "${app.label} icon",
                                    modifier = Modifier.size(64.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(modifier = Modifier.size(64.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                // Highlight the app name when it matches the query
                                Text(
                                    text = app.label,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Button remains at bottom-right
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = { selectedApp = app }) {
                                Text(text = "LEARN more")
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedApp != null) {
        val a = selectedApp!!
        AlertDialog(
            onDismissRequest = { selectedApp = null },
            confirmButton = {
                Button(onClick = { selectedApp = null }) { Text("Close") }
            },
            title = { Text(text = a.label) },
            text = {
                val perms = if (a.permissions.isEmpty()) "None" else a.permissions.joinToString("\n")
                Column {
                    Text(text = "Package: ${a.packageName}")
                    Text(text = "Version: ${a.versionName ?: "unknown"}")
                    Text(text = "System app: ${a.isSystemApp}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Permissions:")
                    Text(text = perms)
                }
            }
        )
    }
}

suspend fun loadInstalledApps(pm: PackageManager): List<AppInfo> = withContext(Dispatchers.IO) {
    val out = mutableListOf<AppInfo>()
    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

    for (pkg in packages) {
        try {
            // package.applicationInfo may be nullable in some analysis contexts - guard it
            val ai = pkg.applicationInfo ?: continue
            val label = ai.loadLabel(pm).toString()

            // Try to read version name safely
            val versionName = try {
                val pInfo = pm.getPackageInfo(pkg.packageName, 0)
                pInfo.versionName
            } catch (_: Exception) {
                null
            }

            val requested = pkg.requestedPermissions?.toList() ?: emptyList()
            val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

            val drawable = try { ai.loadIcon(pm) } catch (_: Exception) { null }
            val bitmap = drawable?.let { drawableToBitmap(it).asImageBitmap() }

            out += AppInfo(
                packageName = pkg.packageName,
                label = label,
                versionName = versionName,
                permissions = requested,
                isSystemApp = isSystem,
                iconBitmap = bitmap
            )
        } catch (_: Exception) {
            // skip packages we can't read
        }
    }

    out.sortedBy { it.label.lowercase() }
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        drawable.bitmap?.let { return it }
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}