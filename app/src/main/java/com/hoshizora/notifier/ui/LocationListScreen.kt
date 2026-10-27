package com.hoshizora.notifier.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.hoshizora.notifier.worker.WorkScheduler
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
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

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val json = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.bufferedReader().readText()
            }
            if (json != null) {
                viewModel.importFromJson(
                    json = json,
                    onSuccess = {
                        Toast.makeText(context, "インポートしました", Toast.LENGTH_SHORT).show()
                    },
                    onError = {
                        Toast.makeText(context, "エラー: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("星空指数 監視地点") },
                actions = {
                    IconButton(onClick = { WorkScheduler.runOnce(context) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "今すぐ実行")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                    // Activity以外から起動される可能性を考慮
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
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "エクスポート")
                }
                SmallFloatingActionButton(
                    onClick = { importLauncher.launch("application/json") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Download, contentDescription = "インポート")
                }
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "地点を追加")
                }
            }
        }
    ) { padding ->
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = { it.location.id }) { item ->
                    LocationCard(
                        item = item,
                        onEditClick = { onEditClick(item.location.id) },
                        onDelete = { viewModel.delete(item.location) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    item: LocationWithResults,
    onEditClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.location.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "緯度 ${item.location.lat}  経度 ${item.location.lon}  標高 ${item.location.alt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            }

            if (item.results.isEmpty()) {
                Text(
                    "まだデータがありません（右上の再生ボタンで取得できます）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                val fetchedAt = item.results.firstOrNull()?.fetchedAt
                if (fetchedAt != null) {
                    Text(
                        "取得日時: ${formatFetchedAt(fetchedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                HourlyIndexTable(item.results)
            }
        }
    }
}

@Composable
private fun HourlyIndexTable(results: List<StarIndexResultEntity>) {
    Column {
        results.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { result ->
                    Text(
                        text = "${formatHourLabel(result)}: ${result.index}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 余りマスを埋めて列崩れを防ぐ
                repeat(4 - rowItems.size) {
                    Text("", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatHourLabel(result: StarIndexResultEntity): String {
    val prefix = if (result.isNextDay) "翌" else ""
    return "$prefix${result.hour}時"
}

private fun formatFetchedAt(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
    return sdf.format(Date(epochMillis))
}
