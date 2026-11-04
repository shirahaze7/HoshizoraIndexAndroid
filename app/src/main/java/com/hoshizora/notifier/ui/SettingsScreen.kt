package com.hoshizora.notifier.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import java.util.Locale

/** 選択可能な時間帯: 0時〜8時、18時〜23時 */
private val SELECTABLE_HOURS: List<Int> = (0..8) + (18..23)

private fun hourLabel(hour: Int): String = "${hour}時"

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
    var runHourInt by remember { mutableIntStateOf(settings.runHour.coerceIn(0, 23)) }
    var runMinuteInt by remember { mutableIntStateOf(settings.runMinute.coerceIn(0, 59)) }
    var notifyWhenNone by remember { mutableStateOf(settings.notifyWhenNone) }
    var fetchHours by remember { mutableFloatStateOf(settings.fetchHours.toFloat()) }

    var showTimePickerDialog by remember { mutableStateOf(false) }

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

                    val levelDescription = when (selectedLevel) {
                        0 -> "悪天候（厚い雲や悪天候。天体観測には不向き）"
                        1 -> "薄暗（雲が多く、一部の明るい星が薄っすら見える程度）"
                        2 -> "標準（標準的な夜空。主要な星座や明るい星が楽しめる。※光害が多い場所ではこれが最大かも）"
                        3 -> "良好（澄んだ綺麗な星空！暗い星も観測可能）"
                        4 -> "最高（雲ひとつない絶好の夜空！最高の観測・天の川も撮影チャンス）"
                        else -> ""
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = levelDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                        "監視対象の時間帯 (0時〜8時、18時〜24時)",
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

            // セクション 3: 実行時刻 & 通知 (Material 3 TimePicker ダイアログ導入)
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

                    // タップして時刻を設定するM3スタイルのインターフェース
                    Surface(
                        onClick = { showTimePickerDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "自動実行予定時刻",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format(Locale.JAPAN, "%02d:%02d", runHourInt, runMinuteInt),
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showTimePickerDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("変更")
                            }
                        }
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
                    settings.runHour = runHourInt
                    settings.runMinute = runMinuteInt
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

    // Material 3 TimePicker ダイアログ
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = runHourInt,
            initialMinute = runMinuteInt,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = {
                Text(
                    "自動確認時刻の変更",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runHourInt = timePickerState.hour
                        runMinuteInt = timePickerState.minute
                        showTimePickerDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}
