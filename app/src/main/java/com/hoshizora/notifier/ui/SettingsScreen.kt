package com.hoshizora.notifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoshizora.notifier.data.SettingsRepository
import com.hoshizora.notifier.ui.theme.StarIndexColors
import com.hoshizora.notifier.worker.WorkScheduler

/** 選択可能な時間帯: 18時〜23時、翌0時〜8時 */
private val SELECTABLE_HOURS: List<Int> = (18..23) + (0..8)

private fun hourLabel(hour: Int): String =
    if (hour in 0..8) "翌${hour}時" else "${hour}時"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    var threshold by remember { mutableFloatStateOf(settings.threshold.toFloat()) }
    var selectedHours by remember {
        mutableStateOf(settings.targetHours.asSequence().filter { it in SELECTABLE_HOURS }.toSet())
    }
    var runHour by remember { mutableStateOf(settings.runHour.toString()) }
    var runMinute by remember { mutableStateOf(settings.runMinute.toString()) }
    var notifyWhenNone by remember { mutableStateOf(settings.notifyWhenNone) }
    var fetchHours by remember { mutableFloatStateOf(settings.fetchHours.toFloat()) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "設定",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // セクション 1: 通知閾値
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "通知する最小の星空指数",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val selectedLevel = threshold.toInt().coerceIn(0, 4)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "指数 $selectedLevel 以上で通知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StarIndexColors.getBackgroundColor(selectedLevel),
                            contentColor = StarIndexColors.getTextColor(selectedLevel)
                        ) {
                            Text(
                                text = "★$selectedLevel",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = 0f..4f,
                        steps = 3,
                    )
                }
            }

            // セクション 2: 対象時間帯
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "監視対象の時間帯 (18時〜翌8時)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.height(180.dp),
                        userScrollEnabled = false
                    ) {
                        items(SELECTABLE_HOURS) { hour ->
                            val isSelected = selectedHours.contains(hour)
                            FilterChip(
                                modifier = Modifier.padding(2.dp),
                                selected = isSelected,
                                onClick = {
                                    selectedHours = if (isSelected) {
                                        selectedHours - hour
                                    } else {
                                        selectedHours + hour
                                    }
                                },
                                label = {
                                    Text(
                                        hourLabel(hour),
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // セクション 3: 実行時刻 & 通知
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "毎日の自動確認時刻",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = runHour,
                            onValueChange = { runHour = it.filter { c -> c.isDigit() } },
                            label = { Text("時") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = runMinute,
                            onValueChange = { runMinute = it.filter { c -> c.isDigit() } },
                            label = { Text("分") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "条件に合う地点がない日も通知する",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = notifyWhenNone,
                            onCheckedChange = { notifyWhenNone = it },
                        )
                    }
                }
            }

            // セクション 4: 取得期間
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "取得期間",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "実行時刻から ${fetchHours.toInt()}時間後まで",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = fetchHours,
                        onValueChange = { fetchHours = it },
                        valueRange = 8f..72f,
                        steps = 7,
                    )
                    Text(
                        "指数データを実行時刻から何時間後まで取得・表示するか設定します（APIは約72時間分まで提供）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    settings.threshold = threshold.toInt()
                    settings.targetHours = selectedHours
                    settings.runHour = runHour.toIntOrNull()?.coerceIn(0, 23)
                        ?: SettingsRepository.DEFAULT_RUN_HOUR
                    settings.runMinute = runMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    settings.notifyWhenNone = notifyWhenNone
                    settings.fetchHours = fetchHours.toInt()
                    WorkScheduler.scheduleDaily(context)
                    onDone()
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    "設定を保存",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
