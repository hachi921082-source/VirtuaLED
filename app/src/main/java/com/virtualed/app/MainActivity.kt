package com.virtualed.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.app.TimePickerDialog
import com.virtualed.app.autostop.QuietHoursScheduler
import com.virtualed.app.data.BatteryThresholdSettings
import com.virtualed.app.data.BlinkSettings
import com.virtualed.app.data.DndSettings
import com.virtualed.app.data.FeatureEnabledState
import com.virtualed.app.data.IndicatorColorSettings
import com.virtualed.app.data.QuietHoursSettings
import com.virtualed.app.data.parseHexColorOrNull
import com.virtualed.app.overlay.FakeSleepActivityHolder
import com.virtualed.app.permission.PermissionChecker
import com.virtualed.app.service.VirtuaLedForegroundService
import com.virtualed.app.ui.CategorySettingsScreen
import com.virtualed.app.ui.KeywordSettingsScreen
import com.virtualed.app.ui.OnboardingScreen
import com.virtualed.app.ui.theme.VirtuaLedTheme
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VirtuaLedTheme {
                VirtuaLedApp()
            }
        }
    }
}

/**
 * アプリのルート画面。通知リスナーアクセスが未許可の間は OnboardingScreen を表示し、
 * 許可済みになった時点でメインのスイッチ画面へ切り替える
 * (docs/plans/mvp/permission-onboarding/PLAN.md §5)。
 */
@Composable
fun VirtuaLedApp() {
    val context = LocalContext.current
    var listenerGranted by remember {
        mutableStateOf(PermissionChecker.hasNotificationListenerAccess(context))
    }

    // docs/plans/other/importance-based-sizing/PLAN.md §3 — MainSwitchScreen ↔
    // KeywordSettingsScreen 間のシンプルな boolean トグル切り替え。ナビゲーションライブラリは
    // 使わず、OnboardingScreen ↔ MainSwitchScreen の切り替えと同じ方針。
    var showKeywordSettings by remember { mutableStateOf(false) }

    // docs/plans/other/category-icons-and-assets/PLAN.md §7 — 上と同じ boolean トグル方式で
    // MainSwitchScreen ↔ CategorySettingsScreen を切り替える。
    var showCategorySettings by remember { mutableStateOf(false) }

    // POST_NOTIFICATIONS は通常のランタイム許可ダイアログで足りるため、通知リスナー onboarding
    // とは独立に、起動時に一度だけ要求する (PLAN のスコープ注記参照)。
    RequestPostNotificationsPermission()

    if (!listenerGranted) {
        OnboardingScreen(onAllGranted = { listenerGranted = true })
    } else if (showKeywordSettings) {
        KeywordSettingsScreen(onBack = { showKeywordSettings = false })
    } else if (showCategorySettings) {
        CategorySettingsScreen(onBack = { showCategorySettings = false })
    } else {
        MainSwitchScreen(
            onOpenKeywordSettings = { showKeywordSettings = true },
            onOpenCategorySettings = { showCategorySettings = true },
        )
    }
}

@Composable
private fun RequestPostNotificationsPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ユーザーの選択結果は本 MVP スコープでは追跡しない (PLAN のスコープ注記参照) */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

// docs/plans/mvp/foreground-service-setup/PLAN.md §4 で追加された暫定スイッチ画面。
// permission-onboarding のゲートを通過した後にのみ到達する
// (docs/plans/mvp/onoff-notification-toggle/PLAN.md で本格的な UI に置き換え予定)。
@Composable
fun MainSwitchScreen(
    onOpenKeywordSettings: () -> Unit = {},
    onOpenCategorySettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }

    // docs/plans/other/dnd-handling/PLAN.md §4 — UI 表示は内部フラグ hide_during_dnd の反転表示。
    // 初期値は DataStore からの非同期読み込み待ちの間、デフォルト値 (= 表示しない) を仮表示する。
    var showDuringDnd by remember { mutableStateOf(!DndSettings.DEFAULT_HIDE_DURING_DND) }
    LaunchedEffect(Unit) {
        showDuringDnd = !DndSettings.getHideDuringDnd(context)
    }

    // docs/plans/other/auto-stop-conditions/PLAN-2-battery.md §5 — バッテリー残量しきい値設定。
    // 初期値は DataStore からの非同期読み込み待ちの間、デフォルト値を仮表示する。
    var batteryThreshold by remember { mutableStateOf(BatteryThresholdSettings.DEFAULT_THRESHOLD_PERCENT) }
    LaunchedEffect(Unit) {
        batteryThreshold = BatteryThresholdSettings.getThresholdPercent(context)
    }

    // docs/plans/other/indicator-color-customization/PLAN.md §2/§3 — インジケーター色設定。
    // 初期値は DataStore からの非同期読み込み待ちの間、デフォルト値 (白) を仮表示する。
    var indicatorColorArgb by remember { mutableStateOf(IndicatorColorSettings.DEFAULT_COLOR_ARGB) }
    LaunchedEffect(Unit) {
        indicatorColorArgb = IndicatorColorSettings.getColorArgb(context)
    }
    // 永続化に加え、セッションが現在アクティブなら FakeSleepActivityHolder 経由で共有
    // MutableState へ直接書き込み、再起動なしで即座に反映する
    // (docs/plans/other/indicator-color-customization/PLAN.md §5)。セッション非アクティブなら
    // indicatorUiState は null のため単なる永続化のみで no-op となる。
    val onIndicatorColorChanged: (Int) -> Unit = { colorArgb ->
        indicatorColorArgb = colorArgb
        coroutineScope.launch {
            IndicatorColorSettings.setColorArgb(context, colorArgb)
            FakeSleepActivityHolder.indicatorUiState?.tintColor?.value = Color(colorArgb)
        }
    }

    // docs/plans/other/importance-based-sizing/PLAN.md §5 — 高重要度時の点滅間隔設定。
    // 初期値は DataStore からの非同期読み込み待ちの間、デフォルト値を仮表示する。
    var blinkIntervalMs by remember { mutableStateOf(BlinkSettings.DEFAULT_INTERVAL_MS) }
    LaunchedEffect(Unit) {
        blinkIntervalMs = BlinkSettings.getIntervalMs(context)
    }

    // docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §8 — クワイエットアワー設定。
    // 初期値は DataStore からの非同期読み込み待ちの間、デフォルト値 (無効/01:00-07:00) を仮表示する。
    var quietHoursWindow by remember { mutableStateOf(QuietHoursSettings.DEFAULT_WINDOW) }
    LaunchedEffect(Unit) {
        quietHoursWindow = QuietHoursSettings.getWindow(context)
    }
    // トグル/時刻ピッカーいずれの変更も、永続化と AlarmManager 予約の両方をその場で更新する
    // (「保存」ボタンを持たない、この画面の他設定と同じ write-through 方式)。
    val onQuietHoursWindowChanged: (QuietHoursSettings.Window) -> Unit = { window ->
        quietHoursWindow = window
        coroutineScope.launch {
            QuietHoursSettings.setWindow(context, window)
            QuietHoursScheduler(context).reschedule(window)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "VirtuaLED")
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        setForegroundServiceEnabled(context, checked)
                    }
                )
                Row(
                    modifier = Modifier.padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.settings_show_during_dnd))
                    Switch(
                        checked = showDuringDnd,
                        onCheckedChange = { checked ->
                            showDuringDnd = checked
                            coroutineScope.launch {
                                DndSettings.setHideDuringDnd(context, hide = !checked)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Column(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.settings_battery_threshold_label, batteryThreshold))
                    Slider(
                        value = batteryThreshold.toFloat(),
                        onValueChange = { batteryThreshold = it.toInt() },
                        onValueChangeFinished = {
                            coroutineScope.launch {
                                BatteryThresholdSettings.setThresholdPercent(context, batteryThreshold)
                            }
                        },
                        valueRange = BatteryThresholdSettings.MIN_THRESHOLD_PERCENT.toFloat()..
                            BatteryThresholdSettings.MAX_THRESHOLD_PERCENT.toFloat(),
                        // 1..100 の 1% 刻み (端点2つを除いた中間ステップ数)
                        steps = BatteryThresholdSettings.MAX_THRESHOLD_PERCENT -
                            BatteryThresholdSettings.MIN_THRESHOLD_PERCENT - 1,
                        modifier = Modifier.width(240.dp),
                    )
                }
                ColorPickerSection(
                    selectedColorArgb = indicatorColorArgb,
                    onColorChanged = onIndicatorColorChanged,
                )
                Column(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.settings_blink_interval_label, blinkIntervalMs))
                    Slider(
                        value = blinkIntervalMs.toFloat(),
                        onValueChange = { blinkIntervalMs = it.toLong() },
                        onValueChangeFinished = {
                            coroutineScope.launch {
                                BlinkSettings.setIntervalMs(context, blinkIntervalMs)
                            }
                        },
                        valueRange = BlinkSettings.MIN_INTERVAL_MS.toFloat()..BlinkSettings.MAX_INTERVAL_MS.toFloat(),
                        modifier = Modifier.width(240.dp),
                    )
                }
                QuietHoursSection(
                    window = quietHoursWindow,
                    onWindowChanged = onQuietHoursWindowChanged,
                )
                Button(
                    onClick = onOpenKeywordSettings,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(text = stringResource(R.string.settings_keyword_settings_button))
                }
                Button(
                    onClick = onOpenCategorySettings,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(text = stringResource(R.string.settings_category_settings_button))
                }
            }
        }
    }
}

/**
 * クワイエットアワー (毎日の時間帯でフェイクスリープを完全抑止する) の設定 UI。
 * 有効/無効スイッチ + 開始/終了の各ボタンから標準 TimePickerDialog (View ベース) を開く。
 * HH:mm のテキスト入力パースを自前実装せず、Android 標準ダイアログに委ねる
 * (docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §8)。
 */
@Composable
private fun QuietHoursSection(
    window: QuietHoursSettings.Window,
    onWindowChanged: (QuietHoursSettings.Window) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.settings_quiet_hours_enabled_label))
            Switch(
                checked = window.enabled,
                onCheckedChange = { checked -> onWindowChanged(window.copy(enabled = checked)) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onWindowChanged(window.copy(startHour = hour, startMinute = minute)) },
                    window.startHour,
                    window.startMinute,
                    true, // 24時間表示
                ).show()
            }) {
                Text(text = stringResource(R.string.settings_quiet_hours_start_button, window.startHour, window.startMinute))
            }
            Button(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onWindowChanged(window.copy(endHour = hour, endMinute = minute)) },
                        window.endHour,
                        window.endMinute,
                        true,
                    ).show()
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(text = stringResource(R.string.settings_quiet_hours_end_button, window.endHour, window.endMinute))
            }
        }
    }
}

// プリセットパレット。厳密な色値はプロダクト決定ではなく実装時の見た目調整
// (docs/plans/other/indicator-color-customization/PLAN.md §3)。
private val INDICATOR_COLOR_PRESETS = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFFF44336.toInt(), // red
    0xFFFF9800.toInt(), // orange
    0xFFFFEB3B.toInt(), // yellow
    0xFF4CAF50.toInt(), // green
    0xFF00BCD4.toInt(), // cyan
    0xFF2196F3.toInt(), // blue
    0xFF9C27B0.toInt(), // purple
)

/**
 * インジケーター色の選択 UI。プリセットスウォッチのタップは即座に永続化・反映し、HEX 欄は
 * 解析成功時のみ永続化する (docs/plans/other/indicator-color-customization/PLAN.md §3/§4)。
 */
@Composable
private fun ColorPickerSection(selectedColorArgb: Int, onColorChanged: (Int) -> Unit) {
    var hexInput by remember(selectedColorArgb) {
        mutableStateOf(String.format(Locale.ROOT, "#%08X", selectedColorArgb))
    }
    var hexError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.settings_indicator_color_label))
        Row(modifier = Modifier.padding(top = 8.dp)) {
            INDICATOR_COLOR_PRESETS.forEach { presetArgb ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(32.dp)
                        .background(Color(presetArgb), CircleShape)
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable {
                            hexError = false
                            onColorChanged(presetArgb)
                        },
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    hexInput = input
                    val parsed = parseHexColorOrNull(input)
                    if (parsed != null) {
                        hexError = false
                        onColorChanged(parsed)
                    } else {
                        hexError = true
                    }
                },
                label = { Text(text = stringResource(R.string.settings_indicator_color_hex_label)) },
                singleLine = true,
                isError = hexError,
                modifier = Modifier.width(160.dp),
            )
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .background(Color(selectedColorArgb), CircleShape)
                    .border(1.dp, Color.Gray, CircleShape),
            )
        }
        if (hexError) {
            Text(
                text = stringResource(R.string.settings_indicator_color_hex_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// docs/plans/mvp/foreground-service-setup/PLAN.md §4 (Startup Model) の起動/停止経路。
// ON/OFF 経路は非対称 (docs/plans/mvp/onoff-notification-toggle/PLAN.md §5) —
// ON は FeatureEnabledState を明示的に true へ戻してから起動する (通知アクションで OFF に
// された状態のまま残っている可能性があるため)。OFF は ACTION_STOP → stopSelf() で完全停止する。
private fun setForegroundServiceEnabled(context: Context, enabled: Boolean) {
    val intent = Intent(context, VirtuaLedForegroundService::class.java)
    if (enabled) {
        FeatureEnabledState.setEnabled(true) // resume, 通知アクションで OFF にされていた場合に備える
        intent.action = VirtuaLedForegroundService.ACTION_START
        ContextCompat.startForegroundService(context, intent)
    } else {
        intent.action = VirtuaLedForegroundService.ACTION_STOP
        context.startService(intent)
    }
}

@Preview(showBackground = true)
@Composable
fun MainSwitchScreenPreview() {
    VirtuaLedTheme {
        MainSwitchScreen()
    }
}
