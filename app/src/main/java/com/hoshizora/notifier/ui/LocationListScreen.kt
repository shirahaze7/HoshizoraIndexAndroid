package com.hoshizora.notifier.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.hoshizora.notifier.ui.theme.DarkOutline
import com.hoshizora.notifier.ui.theme.SecondaryCyan
import com.hoshizora.notifier.ui.theme.StarIndexColors
import com.hoshizora.notifier.ui.theme.StarryBackground
import com.hoshizora.notifier.worker.WorkScheduler
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationListScreen(
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: LocationListViewModel = viewModel(
        factory = LocationListViewModel.factory(LocalContext.current.applicationContext)
    )
) {
    val items by viewModel.locationsWithResults.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val maxDistanceKm by viewModel.maxDistanceKm.collectAsState()
    val isSearchOpen by viewModel.isSearchOpen.collectAsState()
    val isFetchingLocation by viewModel.isFetchingLocation.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun fetchCurrentLocation() {
        viewModel.setFetchingLocation(true)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    viewModel.updateCurrentLocation(loc)
                    viewModel.setFetchingLocation(false)
                    Toast.makeText(context, "現在地を取得しました", Toast.LENGTH_SHORT).show()
                } else {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    ).addOnSuccessListener { newLoc ->
                        viewModel.updateCurrentLocation(newLoc)
                        viewModel.setFetchingLocation(false)
                        if (newLoc != null) {
                            Toast.makeText(context, "現在地を取得しました", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "現在地の取得に失敗しました", Toast.LENGTH_SHORT).show()
                        }
                    }.addOnFailureListener {
                        viewModel.setFetchingLocation(false)
                        Toast.makeText(context, "現在地の取得に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }.addOnFailureListener {
                viewModel.setFetchingLocation(false)
                Toast.makeText(context, "現在地の取得に失敗しました", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            viewModel.setFetchingLocation(false)
            Toast.makeText(context, "位置情報の権限がありません", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(context, "位置情報の利用が拒否されました", Toast.LENGTH_SHORT).show()
        }
    }

    fun onRequestLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            val json = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.bufferedReader().readText()
            }
            json?.let { content ->
                viewModel.importFromJson(
                    json = content,
                    onSuccess = {
                        Toast.makeText(context, "インポートしました", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(context, "エラー: ${error.message}", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "星空指数 監視地点",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSearchOpen() }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "検索",
                            tint = if (isSearchOpen || searchQuery.isNotEmpty() || maxDistanceKm != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = { WorkScheduler.runOnce(context) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "今すぐ実行")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            try {
                                val json = viewModel.exportToJson()
                                val cacheFile = File(context.cacheDir, "hoshizora_locations.json")
                                cacheFile.writeText(json)

                                val contentUri: Uri = FileProvider.getUriForFile(
                                    context.applicationContext,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )

                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    clipData = ClipData.newRawUri("Locations JSON", contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val chooser = Intent.createChooser(intent, "JSONとしてエクスポート")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "エクスポート失敗: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.Share, contentDescription = "エクスポート")
                }
                SmallFloatingActionButton(
                    onClick = { importLauncher.launch("application/json") },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.Add, contentDescription = "インポート")
                }
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "地点を追加")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 星空背景
            StarryBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 検索フィルターパネル
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchPanel(
                        searchQuery = searchQuery,
                        currentLocation = currentLocation,
                        maxDistanceKm = maxDistanceKm,
                        isFetchingLocation = isFetchingLocation,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onRequestLocation = { onRequestLocation() },
                        onClearLocation = { viewModel.updateCurrentLocation(null) },
                        onMaxDistanceChange = { viewModel.updateMaxDistanceKm(it) },
                        onResetFilters = { viewModel.clearSearchFilter() }
                    )
                }

                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (searchQuery.isNotEmpty() || maxDistanceKm != null) {
                            Text(
                                "検索条件に一致する地点が見つかりませんでした",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.clearSearchFilter() }) {
                                Text("検索条件をクリア")
                            }
                        } else {
                            Text(
                                "右下の + から監視地点を追加してください",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = items,
                            key = { it.location.id },
                            contentType = { "location_card" }
                        ) { item ->
                            LocationCard(
                                item = item,
                                onOpenMap = { openGoogleMaps(context, item.location.lat, item.location.lon, item.location.name) },
                                onEditClick = { onEditClick(item.location.id) },
                                onDelete = { viewModel.delete(item.location) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    searchQuery: String,
    currentLocation: android.location.Location?,
    maxDistanceKm: Double?,
    isFetchingLocation: Boolean,
    onQueryChange: (String) -> Unit,
    onRequestLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onMaxDistanceChange: (Double?) -> Unit,
    onResetFilters: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 地点名検索テキストフィールド
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("地点名で検索") },
                placeholder = { Text("例: 東京、山頂") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                },
                singleLine = true
            )

            // 現在地取得セクション
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onRequestLocation,
                    enabled = !isFetchingLocation
                ) {
                    if (isFetchingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("現在地取得中...")
                    } else {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (currentLocation != null) "現在地を更新" else "現在地を取得")
                    }
                }

                if (currentLocation != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.JAPAN,
                                "現在地: %.2f, %.2f",
                                currentLocation.latitude,
                                currentLocation.longitude
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = onClearLocation,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "現在地を解除",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 距離制限（? km以内）絞り込み
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "現在地からの距離絞り込み",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (currentLocation == null) {
                    Text(
                        text = "※距離絞り込みを利用するには上の「現在地を取得」を押してください",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                val distancePresets = listOf(null to "指定なし", 5.0 to "5km", 10.0 to "10km", 30.0 to "30km", 50.0 to "50km", 100.0 to "100km", 200.0 to "200km")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    distancePresets.forEach { (dist, label) ->
                        val selected = (maxDistanceKm == dist)
                        FilterChip(
                            selected = selected,
                            onClick = { onMaxDistanceChange(dist) },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    OutlinedTextField(
                        value = maxDistanceKm?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "",
                        onValueChange = { text ->
                            if (text.isEmpty()) {
                                onMaxDistanceChange(null)
                            } else {
                                text.toDoubleOrNull()?.let { onMaxDistanceChange(it) }
                            }
                        },
                        label = { Text("カスタム距離 (km)") },
                        placeholder = { Text("例: 15") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (maxDistanceKm != null) {
                                IconButton(onClick = { onMaxDistanceChange(null) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "クリア")
                                }
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty() || maxDistanceKm != null || currentLocation != null) {
                        TextButton(onClick = onResetFilters) {
                            Text("リセット")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    item: LocationWithResults,
    onOpenMap: () -> Unit,
    onEditClick: () -> Unit,
    onDelete: () -> Unit
) {
    // グローエフェクトのアニメーション (hasHighIndex が true の時のみ動かす)
    val glowAlpha = if (item.hasHighIndex) {
        val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
        val alphaState = infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
        alphaState.value
    } else {
        1f
    }

    val cardBorder = remember(item.hasHighIndex, glowAlpha) {
        if (item.hasHighIndex) {
            BorderStroke(2.dp, SecondaryCyan.copy(alpha = glowAlpha))
        } else {
            BorderStroke(1.dp, DarkOutline.copy(alpha = 0.5f))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        border = cardBorder
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.location.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.distanceText != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = item.distanceText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "緯度 ${item.location.lat}  経度 ${item.location.lon}  標高 ${item.location.alt}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onOpenMap) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Googleマップで開く",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "編集",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "削除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item.results.isEmpty()) {
                Text(
                    text = "まだデータがありません（右上の再生ボタンで取得できます）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                item.fetchedAtText?.let {
                    Text(
                        text = "取得日時: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }
                HourlyIndexList(item.groupedResults)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourlyIndexList(groupedResults: List<FormattedGroupedResults>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        groupedResults.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (group.dateText.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = group.dateText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (group.relativeDayLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = group.relativeDayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    group.results.forEach { result ->
                        IndexChip(result = result)
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexChip(result: StarIndexResultEntity) {
    val index = result.index.coerceIn(0, 4)
    val bgColor = StarIndexColors.getBackgroundColor(index)
    val textColor = StarIndexColors.getTextColor(index)

    // 高指数(★3以上)の時のみパルスアニメーションを実行
    val shouldPulse = index >= 3
    val pulseScaleState = if (shouldPulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "chipPulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        null
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        contentColor = textColor,
        modifier = Modifier.graphicsLayer {
            if (pulseScaleState != null) {
                val scale = pulseScaleState.value
                scaleX = scale
                scaleY = scale
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = "${result.hour}時",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "★$index",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                fontSize = 12.sp
            )
        }
    }
}

private fun openGoogleMaps(context: android.content.Context, lat: Double, lon: Double, name: String) {
    val encodedName = Uri.encode(name)
    val gmmIntentUri = "geo:$lat,$lon?q=$lat,$lon($encodedName)".toUri()
    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        val webUri = "https://www.google.com/maps/search/?api=1&query=$lat,$lon".toUri()
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
        try {
            context.startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "マップアプリを開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }
}
