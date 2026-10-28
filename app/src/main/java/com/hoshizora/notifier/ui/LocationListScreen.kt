package com.hoshizora.notifier.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.hoshizora.notifier.ui.theme.DarkOutline
import com.hoshizora.notifier.ui.theme.SecondaryCyan
import com.hoshizora.notifier.ui.theme.StarIndexColors
import com.hoshizora.notifier.ui.theme.StarryBackground
import com.hoshizora.notifier.worker.WorkScheduler
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val threshold = viewModel.getThreshold()

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
                    Icon(Icons.Default.Upload, contentDescription = "エクスポート")
                }
                SmallFloatingActionButton(
                    onClick = { importLauncher.launch("application/json") },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.Download, contentDescription = "インポート")
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
            
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "右下の + から監視地点を追加してください",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    items(items, key = { it.location.id }) { item ->
                        LocationCard(
                            item = item,
                            threshold = threshold,
                            onEditClick = { onEditClick(item.location.id) },
                            onDelete = { viewModel.delete(item.location) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    item: LocationWithResults,
    threshold: Int,
    onEditClick: () -> Unit,
    onDelete: () -> Unit
) {
    val hasHighIndex = item.results.any { it.index >= threshold }

    // グローエフェクトのアニメーション
    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        border = if (hasHighIndex) {
            BorderStroke(2.dp, SecondaryCyan.copy(alpha = glowAlpha))  // 高指数時は脈動するシアン
        } else {
            BorderStroke(1.dp, DarkOutline.copy(alpha = 0.5f))  // 通常時は星の輝きのような青紫
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.location.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "緯度 ${item.location.lat}  経度 ${item.location.lon}  標高 ${item.location.alt}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
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
                val fetchedAt = item.results.firstOrNull()?.fetchedAt
                if (fetchedAt != null) {
                    Text(
                        text = "取得日時: ${formatFetchedAt(fetchedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }
                HourlyIndexList(item.results)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourlyIndexList(results: List<StarIndexResultEntity>) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN) }

    // 日付ごとにグループ化（sortedされたMapを使用）
    val grouped = results.groupBy { result ->
        try {
            OffsetDateTime.parse(result.datetime).toLocalDate()
        } catch (_: Exception) {
            null
        }
    }.toSortedMap(nullsLast(naturalOrder()))

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        grouped.forEach { (date, itemsForDate) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (date != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = date.format(dateFormatter),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        // 今日・明日の表示
                        val today = java.time.LocalDate.now()
                        val label = when {
                            date == today -> "今日"
                            date == today.plusDays(1) -> "明日"
                            else -> null
                        }
                        if (label != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = label,
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
                    itemsForDate.forEach { result ->
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

    // 高指数時にチップを脈動させる
    val shouldPulse = index >= 3
    val infiniteTransition = rememberInfiniteTransition(label = "chipPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        contentColor = textColor,
        modifier = Modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
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

private fun formatFetchedAt(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
    return sdf.format(Date(epochMillis))
}
