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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.virtualed.app.data.AppIconIndicatorSettings
import com.virtualed.app.data.BatteryThresholdSettings
import com.virtualed.app.data.BlinkSettings
import com.virtualed.app.data.CountBadgeSettings
import com.virtualed.app.data.DndSettings
import com.virtualed.app.data.FeatureEnabledState
import com.virtualed.app.data.IndicatorColorSettings
import com.virtualed.app.data.PremiumState
import com.virtualed.app.data.QuietHoursSettings
import com.virtualed.app.overlay.FakeSleepActivityHolder
import com.virtualed.app.permission.PermissionChecker
import com.virtualed.app.service.VirtuaLedForegroundService
import com.virtualed.app.ui.CategorySettingsScreen
import com.virtualed.app.ui.KeywordSettingsScreen
import com.virtualed.app.ui.OnboardingScreen
import com.virtualed.app.ui.PremiumInfoScreen
import com.virtualed.app.ui.theme.VirtuaLedTheme
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

    // docs/plans/other/source-app-icon-indicator-mode/PLAN.md §7 改訂 (2026-08-30) — 上と同じ
    // boolean トグル方式で MainSwitchScreen ↔ PremiumInfoScreen を切り替える。
    var showPremiumInfo by remember { mutableStateOf(false) }

    // POST_NOTIFICATIONS は通常のランタイム許可ダイアログで足りるため、通知リスナー onboarding
    // とは独立に、起動時に一度だけ要求する (PLAN のスコープ注記参照)。
    RequestPostNotificationsPermission()

    if (!listenerGranted) {
        OnboardingScreen(onAllGranted = { listenerGranted = true })
    } else if (showKeywordSettings) {
        KeywordSettingsScreen(onBack = { showKeywordSettings = false })
    } else if (showCategorySettings) {
        CategorySettingsScreen(onBack = { showCategorySettings = false })
    } else if (showPremiumInfo) {
        PremiumInfoScreen(onBack = { showPremiumInfo = false })
    } else {
        MainSwitchScreen(
            onOpenKeywordSettings = { showKeywordSettings = true },
            onOpenCategorySettings = { showCategorySettings = true },
            onOpenPremiumInfo = { showPremiumInfo = true },
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
    onOpenPremiumInfo: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 固定値 false ではなく、実際に VirtuaLedForegroundService が動作中かを起動時に問い合わせる —
    // キーワード設定/カテゴリ設定画面へ遷移して戻ると MainSwitchScreen は composition から
    // 一旦外れて再度組み込まれるため、固定値 false のままだと FGS が実際には動作継続していても
    // スイッチの見た目だけ OFF に戻ってしまう (実機テストで発見)。
    var enabled by remember { mutableStateOf(isForegroundServiceRunning(context)) }

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

    // docs/plans/other/multi-notification-stacking/PLAN.md §5 — 通知件数バッジ表示設定。
    // 初期値は DataStore からの非同期読み込み待ちの間、デフォルト値 (OFF) を仮表示する。
    var countBadgeEnabled by remember { mutableStateOf(CountBadgeSettings.DEFAULT_ENABLED) }
    LaunchedEffect(Unit) {
        countBadgeEnabled = CountBadgeSettings.getEnabled(context)
    }

    // docs/plans/other/source-app-icon-indicator-mode/PLAN.md §7 — 通知元アプリ自身のアイコン
    // 表示設定。初期値は DataStore からの非同期読み込み待ちの間、デフォルト値 (OFF) を仮表示する。
    var appIconIndicatorEnabled by remember { mutableStateOf(AppIconIndicatorSettings.DEFAULT_ENABLED) }
    LaunchedEffect(Unit) {
        appIconIndicatorEnabled = AppIconIndicatorSettings.getEnabled(context)
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
        // 設定行が増え続けた結果、画面の高さに収まらなくなり、中央寄せの Box に収めていた
        // ことで上下ともはみ出して下部のボタンが押せなくなっていた不具合の修正。
        // スクロール可能にし、他の設定画面 (CategorySettingsScreen 等) と同じ
        // windowInsetsPadding(WindowInsets.safeDrawing) でステータスバー等との重なりも防ぐ。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
                Text(text = "VirtuaLED")
                // 並び順は 2026-08-30 のユーザー提案画像に合わせて全面的に組み替え:
                // 起動→見た目(色/バッジ/アプリアイコン)→詳細設定(カテゴリ/キーワード)→
                // 点滅間隔→止める条件(DND/夜間停止/バッテリー)。見出しテキストは置かず、
                // 上下の padding だけで緩くグルーピングする (提案画像に見出し行は無いため)。
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_app_launch_label),
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        setForegroundServiceEnabled(context, checked)
                    },
                )
                ColorPickerSection(
                    selectedColorArgb = indicatorColorArgb,
                    onColorChanged = onIndicatorColorChanged,
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_show_count_badge),
                    checked = countBadgeEnabled,
                    onCheckedChange = { checked ->
                        countBadgeEnabled = checked
                        coroutineScope.launch {
                            CountBadgeSettings.setEnabled(context, checked)
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
                // プレミアム未解放時は行自体を隠さず、案内画面への導線ボタンに置き換える
                // (2026-08-30、「未解放でも機能の存在に気づけるように」との方針転換 —
                // docs/plans/other/source-app-icon-indicator-mode/PLAN.md §7 改訂)。
                if (PremiumState.isPremiumUnlocked()) {
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_show_app_icon),
                        checked = appIconIndicatorEnabled,
                        onCheckedChange = { checked ->
                            appIconIndicatorEnabled = checked
                            coroutineScope.launch {
                                AppIconIndicatorSettings.setEnabled(context, checked)
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    SettingsNavigationRow(
                        label = stringResource(R.string.settings_show_app_icon),
                        buttonText = stringResource(R.string.settings_premium_cta_button),
                        onClick = onOpenPremiumInfo,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                SettingsNavigationRow(
                    label = stringResource(R.string.settings_category_settings_row_label),
                    buttonText = stringResource(R.string.settings_row_button_label),
                    onClick = onOpenCategorySettings,
                    modifier = Modifier.padding(top = 24.dp),
                )
                SettingsNavigationRow(
                    label = stringResource(R.string.settings_keyword_settings_row_label),
                    buttonText = stringResource(R.string.settings_row_button_label),
                    onClick = onOpenKeywordSettings,
                    modifier = Modifier.padding(top = 16.dp),
                )
                BlinkIntervalStepper(
                    intervalMs = blinkIntervalMs,
                    onIntervalChanged = { newIntervalMs ->
                        blinkIntervalMs = newIntervalMs
                        coroutineScope.launch {
                            BlinkSettings.setIntervalMs(context, newIntervalMs)
                        }
                    },
                    modifier = Modifier.padding(top = 24.dp),
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_show_during_dnd),
                    checked = showDuringDnd,
                    onCheckedChange = { checked ->
                        showDuringDnd = checked
                        coroutineScope.launch {
                            DndSettings.setHideDuringDnd(context, hide = !checked)
                        }
                    },
                    modifier = Modifier.padding(top = 24.dp),
                )
                QuietHoursSection(
                    window = quietHoursWindow,
                    onWindowChanged = onQuietHoursWindowChanged,
                )
                // 画面全体が左寄せの行 (SettingsSwitchRow 等) と中央寄せのブロックの混在で
                // 見づらいとの指摘への対応 (2026-08-30) — fillMaxWidth + 左寄せで他の行と揃え、
                // スライダーも固定幅 (240.dp) から幅いっぱいに変更。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.Start,
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
        }
    }
}

/**
 * MainSwitchScreen 内の設定行をグループ分けするための見出し (2026-08-30 追加)。
 * 独立した機能ではなく純粋な表示上の区切りのため、対応する PLAN.md は存在しない。
 */
@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 32.dp),
    )
}

/**
 * ラベル左寄せ・スイッチ右寄せの設定行 (2026-08-30 追加 — ラベルとスイッチをひとまとめに
 * 中央寄せしていた旧レイアウトより操作しやすいとの指摘への対応)。画面幅いっぱいに広げ
 * `Arrangement.SpaceBetween` で両端に振り分ける、Android 標準設定画面と同じ形。
 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f) で残り幅いっぱいに広げ、長いラベルは折り返す — 横幅の狭い画面で
        // スイッチが画面外に押し出されるのを防ぐ (2026-08-30 の指摘への対応)。
        Text(text = label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * ラベル左寄せ・ボタン右寄せの設定行 (SettingsSwitchRow のボタン版、2026-08-30 追加)。
 * プレミアム未解放時の案内導線など、スイッチではなく別画面への遷移を提示したい行に使う。
 */
@Composable
private fun SettingsNavigationRow(
    label: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f) で残り幅いっぱいに広げ、長いラベルは折り返す — 横幅の狭い画面で
        // ボタンが画面外に押し出されるのを防ぐ (2026-08-30 の指摘への対応)。
        Text(text = label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Button(onClick = onClick) {
            Text(text = buttonText)
        }
    }
}

/**
 * 高重要度時の点滅間隔を「−/+ ボタン + 数値」で調整するステッパー (2026-08-30、スライダーより
 * 狙った値に合わせやすいとの提案でスライダーから変更)。STEP_MS 刻みで BlinkSettings の
 * 範囲内にクランプする。
 */
@Composable
private fun BlinkIntervalStepper(
    intervalMs: Long,
    onIntervalChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stepMs = 100L
    // 画面全体が左寄せの行 (SettingsSwitchRow 等) と中央寄せのブロックの混在で見づらい
    // との指摘への対応 (2026-08-30) — fillMaxWidth + 左寄せで他の行と揃える。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = stringResource(R.string.settings_blink_interval_title))
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                onIntervalChanged((intervalMs - stepMs).coerceAtLeast(BlinkSettings.MIN_INTERVAL_MS))
            }) {
                Text(text = "−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = stringResource(R.string.settings_blink_interval_value, intervalMs),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = {
                onIntervalChanged((intervalMs + stepMs).coerceAtMost(BlinkSettings.MAX_INTERVAL_MS))
            }) {
                Text(text = "+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * 夜間などの指定時間帯でフェイクスリープを完全抑止する設定 UI (内部識別子は
 * QuietHoursSettings のまま — 2026-08-30 に画面表示文言のみ和語表現へ変更、
 * `docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md` 自体は未改名)。
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

    // 画面全体が左寄せの行と中央寄せのブロックの混在で見づらいとの指摘への対応 (2026-08-30) —
    // 開始/終了ボタンの行も SettingsSwitchRow と同じ左マージン (24.dp) に揃える。
    Column(
        modifier = Modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_quiet_hours_enabled_label),
            checked = window.enabled,
            onCheckedChange = { checked -> onWindowChanged(window.copy(enabled = checked)) },
        )
        // window.enabled が false の間はボタン自体を押せなくする (2026-08-30 —
        // 以前は無効時もタップでき、無効なのに時刻だけ変更できてしまっていた)。
        Row(modifier = Modifier.padding(top = 8.dp, start = 24.dp)) {
            Button(
                enabled = window.enabled,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onWindowChanged(window.copy(startHour = hour, startMinute = minute)) },
                        window.startHour,
                        window.startMinute,
                        true, // 24時間表示
                    ).show()
                },
            ) {
                Text(text = stringResource(R.string.settings_quiet_hours_start_button, window.startHour, window.startMinute))
            }
            Button(
                enabled = window.enabled,
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

// プリセットパレット (5色固定)。厳密な色値はプロダクト決定ではなく実装時の見た目調整
// (docs/plans/other/indicator-color-customization/PLAN.md §3、docs/DECISIONS.md §23) —
// 黒背景に対して視認性よく区別できるよう白/赤/緑/シアン/青を選定。
private val INDICATOR_COLOR_PRESETS = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFFF44336.toInt(), // red
    0xFF4CAF50.toInt(), // green
    0xFF00BCD4.toInt(), // cyan
    0xFF2196F3.toInt(), // blue
)

/**
 * インジケーター色の選択 UI。プリセットスウォッチのタップで即座に永続化・反映する。
 * HEX 自由入力は廃止済み (docs/plans/other/indicator-color-customization/PLAN.md §4、
 * docs/DECISIONS.md §23)。
 */
@Composable
private fun ColorPickerSection(selectedColorArgb: Int, onColorChanged: (Int) -> Unit) {
    // 画面全体が左寄せの行 (SettingsSwitchRow 等) と中央寄せのブロックの混在で見づらい
    // との指摘への対応 (2026-08-30) — fillMaxWidth + 左寄せで他の行と揃える。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = stringResource(R.string.settings_indicator_color_label))
        // 選択中のプリセットそのものに太い枠を付けて示す方式 (2026-08-30) — 別途プレビュー用の
        // 丸を出す必要が無くなった。まだ誰にも配布していない (docs/DECISIONS.md §5) ため、
        // 5プリセットのどれとも一致しない値 (旧HEX入力由来) を気にする必要はない。
        Row(modifier = Modifier.padding(top = 8.dp)) {
            INDICATOR_COLOR_PRESETS.forEach { presetArgb ->
                val isSelected = presetArgb == selectedColorArgb
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(32.dp)
                        .background(Color(presetArgb), CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape,
                        )
                        .clickable { onColorChanged(presetArgb) },
                )
            }
        }
    }
}

// docs/plans/mvp/foreground-service-setup/PLAN.md §4 (Startup Model) の起動/停止経路。
// ON/OFF 経路は非対称 (docs/plans/mvp/onoff-notification-toggle/PLAN.md §5) —
// ON は FeatureEnabledState を明示的に true へ戻してから起動する (通知アクションで OFF に
// された状態のまま残っている可能性があるため)。OFF は ACTION_STOP → stopSelf() で完全停止する。
// ActivityManager.getRunningServices() は API 26 以降サードパーティアプリからは他アプリの
// サービスを見れなくなったが、呼び出し元自身のアプリのサービスは引き続き取得できる
// (公式ドキュメント記載の後方互換用途) — MainSwitchScreen の enabled 初期値をこれで問い合わせる。
private fun isForegroundServiceRunning(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    @Suppress("DEPRECATION")
    return activityManager.getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == VirtuaLedForegroundService::class.java.name }
}

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
