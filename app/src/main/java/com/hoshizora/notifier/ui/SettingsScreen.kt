package com.hoshizora.notifier.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
import com.hoshizora.notifier.data.SettingsRepository
import com.hoshizora.notifier.notification.NotificationHelper
import com.hoshizora.notifier.ui.theme.StarIndexColors
import com.hoshizora.notifier.worker.WorkScheduler

/** 選択可能な時間帯: 18時〜23時、翌0時〜8時 */
private val SELECTABLE_HOURS: List<Int> = (18..23) + (0..8)

private fun hourLabel(hour: Int): String =
    if (hour in 0..8) "翌${hour}時" else "${hour}時"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
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

    var isNotificationEnabled by remember {
        mutableStateOf(NotificationHelper.areNotificationsEnabled(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        isNotificationEnabled = NotificationHelper.areNotificationsEnabled(context)
    }

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
            // 警告セクション: 通知が無効の場合
            if (!isNotificationEnabled) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "通知機能が無効になっています",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "端末の通知許可が無効になっているため、定期星空チェックの結果通知を受け取ることができません。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("通知許可を設定する")
                        }
                    }
                }
            }

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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SELECTABLE_HOURS.forEach { hour ->
                            val isSelected = selectedHours.contains(hour)
                            FilterChip(
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
                        "指数データを実行時刻から何時間後まで取得・表示するか設定します（約72時間分まで提供）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // セクション 5: チュートリアル / ガイド
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "ヘルプ & チュートリアル",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "アプリの使い方や星空指数の見方、特徴を紹介するチュートリアル・ウォークスルー画面を表示します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onOpenOnboarding,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "チュートリアル・使い方を見る",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
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
                    WorkScheduler.scheduleDaily(context, forceUpdate = true)
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
