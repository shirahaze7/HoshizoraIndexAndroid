package com.hoshizora.notifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hoshizora.notifier.data.SettingsRepository
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
    // 保存済みの値のうち、選択可能範囲(18時〜翌8時)に含まれるものだけを反映
    var selectedHours by remember {
        mutableStateOf(settings.targetHours.asSequence().filter { it in SELECTABLE_HOURS }.toSet())
    }
    var runHour by remember { mutableStateOf(settings.runHour.toString()) }
    var runMinute by remember { mutableStateOf(settings.runMinute.toString()) }
    var notifyWhenNone by remember { mutableStateOf(settings.notifyWhenNone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    append("星空指数: ")
                    withStyle(style = SpanStyle(color = Color.Red)) {
                        append(threshold.toInt().toString())
                    }
                    append(" 以上で通知（数が大きいほど良く見える）")
                },
            )
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = 0f..4f,
                steps = 3,
            )

            Text("対象時間帯 (18時〜翌8時の範囲で選択)")
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.height(200.dp),
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
                        label = { Text(hourLabel(hour)) },
                    )
                }
            }

            Text("毎日の実行時刻")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = runHour,
                    onValueChange = { runHour = it.filter { c -> c.isDigit() } },
                    label = { Text("時") },
                    modifier = Modifier.wrapContentWidth(),
                )
                OutlinedTextField(
                    value = runMinute,
                    onValueChange = { runMinute = it.filter { c -> c.isDigit() } },
                    label = { Text("分") },
                    modifier = Modifier.wrapContentWidth(),
                )
            }

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = notifyWhenNone,
                    onCheckedChange = { notifyWhenNone = it },
                )
                Text("該当地点がない日も通知する")
            }

            Button(
                onClick = {
                    settings.threshold = threshold.toInt()
                    settings.targetHours = selectedHours
                    settings.runHour = runHour.toIntOrNull()?.coerceIn(0, 23)
                        ?: SettingsRepository.DEFAULT_RUN_HOUR
                    settings.runMinute = runMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    settings.notifyWhenNone = notifyWhenNone
                    WorkScheduler.scheduleDaily(context)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}
