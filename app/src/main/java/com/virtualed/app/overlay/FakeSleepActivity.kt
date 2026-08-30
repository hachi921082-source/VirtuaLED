package com.virtualed.app.overlay

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.virtualed.app.R
import com.virtualed.app.category.IndicatorCategory
import com.virtualed.app.data.AppIconIndicatorSettings
import com.virtualed.app.data.CountBadgeSettings
import com.virtualed.app.data.IndicatorColorSettings
import com.virtualed.app.data.PendingNotificationsRepository
import com.virtualed.app.data.PremiumState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// スロットグループのドットサイズ (dp)。焼き付き防止のバウンディングボックス計算 (px 換算) と
// 実際の描画サイズの両方で共有する (docs/plans/other/burn-in-protection/PLAN.md §3)。
// 12dp はプレーンな円のドットとしては視認できたが、category-icons-and-assets のアイコン
// (封筒/電話等の細かい形状) では小さすぎて判別できないと実機テストで判明したため 24dp に拡大
// (ユーザー確認・実機調整)。24dp では件数バッジ (16dp) が角に重なるとアイコンの大部分を覆って
// しまうと multi-notification-stacking の実機テストで判明したため、32dp へ再拡大
// (ユーザー確認・実機調整)。
private const val INDICATOR_DOT_SIZE_DP = 32

// 2x2 グリッド内の各スロット間ギャップ (dp)。スロットが1件のみの場合は index==0 のみ描画される
// ため実質ノーオペレーション (docs/plans/other/multi-notification-stacking/PLAN.md §4)。
private const val QUADRANT_GAP_DP = INDICATOR_DOT_SIZE_DP + 8

// 件数バッジの直径 (dp)。CountBadgeSettings 有効時のみ描画する (同 PLAN.md §5)。
private const val COUNT_BADGE_SIZE_DP = 16

/**
 * フェイクスリープ中に表示される全画面 Activity。
 * setShowWhenLocked/setTurnScreenOn により、secure/非secure問わずロック画面の上に表示する
 * (docs/DECISIONS.md §18)。full-screen-intent 通知 (FakeSleepSessionController.startSession())
 * 経由で OS がこの Activity を起動する — Service が直接起動するわけではない。
 *
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §5、
 * docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §3〜§6 を実装。
 *
 * MVP スコープ: 黒背景、および PeekInteractionController による
 * ドット/プレビューテキストのコンテンツ view トグルのみ。位置は BurnInProtector が一定間隔で
 * ランダムに再配置し (docs/plans/other/burn-in-protection/PLAN.md)、色はユーザー設定の
 * 永続化色 (デフォルト白) を反映する (docs/plans/other/indicator-color-customization/PLAN.md)。
 * 最大4件までのスロットを固定2x2グリッドで並べ、5件目以降は OTHER オーバーフロースロットへ
 * 集約する (docs/plans/other/multi-notification-stacking/PLAN.md §3/§4)。座標ベースの
 * ヒットテストは引き続き対象外 — タップ判定は位置非依存のまま
 * (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §3)。
 */
class FakeSleepActivity : ComponentActivity() {
    // Service 起動時に1回だけ生成されるため、この Activity が存在する時点では常に非 null
    // (docs/plans/mvp/fake-sleep-overlay/PLAN.md §1/§2)。
    private val sessionController: FakeSleepSessionController = FakeSleepActivityHolder.sessionController!!
    private lateinit var peekController: PeekInteractionController
    private lateinit var burnInProtector: BurnInProtector

    // null = ドット表示 (INDICATOR_SHOWN)、非 null = プレビューテキスト表示 (PREVIEW_SHOWN)。
    private val previewLinesState = mutableStateOf<List<String>?>(null)

    // other/ 階層の視覚拡張機能 (近接センサーによる一時非表示・焼き付き防止の再配置など) が
    // ここ経由で書き込む (docs/DECISIONS.md §20、docs/plans/mvp/fake-sleep-overlay/PLAN.md §7)。
    private val indicatorUiState = FakeSleepIndicatorUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // アイドルタイムアウトによる画面消灯を抑止する。これが無いと OS の screen_off_timeout
        // 経過後に電源ボタン再押下と同一の ACTION_SCREEN_OFF が飛び、セッションが誤終了する。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        burnInProtector = BurnInProtector(
            indicatorUiState = indicatorUiState,
            // 回転/構成変更対策のため、都度 WindowManager から読み直す (キャッシュしない)。
            screenBoundsPx = {
                val bounds = windowManager.currentWindowMetrics.bounds
                IntSize(bounds.width(), bounds.height())
            },
            // dp→px 変換はここ (Compose 外・非 Composable なコンテキスト) で行う — BurnInProtector
            // 自体は Density を持たないプレーンクラスのため (docs/plans/other/burn-in-protection/
            // PLAN.md §3 からの実装上の乖離、詳細は同 PLAN.md 参照)。
            slotGroupSizePx = (INDICATOR_DOT_SIZE_DP * resources.displayMetrics.density).roundToInt(),
        )
        // Activity インスタンスとセッションは 1:1 (PeekInteractionController と同様) —
        // startSession() 自体はこの Activity 生成前に呼ばれ、その時点では burnInProtector が
        // まだ存在しないため、ここで直接初期状態を通知する
        // (docs/plans/other/burn-in-protection/PLAN.md §4 からの実装上の乖離)。
        burnInProtector.onSessionActiveChanged(true)
        // セッション開始時に1回だけ永続化色を読み込んで反映する。以後アクティブセッション中に
        // 設定画面側で変更された場合は FakeSleepActivityHolder.indicatorUiState 経由で直接
        // tintColor へ書き込まれる (docs/plans/other/indicator-color-customization/PLAN.md §5)。
        lifecycleScope.launch {
            val colorArgb = IndicatorColorSettings.getColorArgb(applicationContext)
            indicatorUiState.tintColor.value = Color(colorArgb)
        }
        peekController = PeekInteractionController(
            activity = this,
            sessionController = sessionController,
            pendingRepo = PendingNotificationsRepository,
            onUiStateChanged = { state -> burnInProtector.onUiStateChanged(state) },
        )
        setContent {
            FakeSleepContent(
                previewLinesState = previewLinesState,
                indicatorVisibleState = indicatorUiState.indicatorVisible,
                indicatorOffsetState = indicatorUiState.indicatorOffset,
                tintColorState = indicatorUiState.tintColor,
                slotsState = indicatorUiState.slots,
                blinkIntervalMsState = indicatorUiState.blinkIntervalMs,
                onTap = { peekController.onTap() },
            )
        }
        FakeSleepActivityHolder.current = this
        FakeSleepActivityHolder.indicatorUiState = indicatorUiState
        FakeSleepActivityHolder.burnInProtector = burnInProtector
        // indicatorUiState 登録直後の初回評価 — startSession() 時点では indicatorUiState が
        // まだ存在しないため no-op に終わっている (docs/plans/other/importance-based-sizing/
        // PLAN.md §6、burn-in-protection PLAN.md §4 と同様の乖離)。
        sessionController.recomputeIndicatorStateNow()
    }

    /** ドットからプレビューテキストへ、コンテンツ view を切り替える (同一 Activity インスタンス内)。 */
    fun showPreviewText(lines: List<String>) {
        previewLinesState.value = lines
    }

    /** プレビューテキストからドットへ、コンテンツ view を戻す。 */
    fun showDot() {
        previewLinesState.value = null
    }

    /** 電源ボタン再押下によるフルディスミス (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §6)。 */
    fun onPowerButtonPressed() {
        peekController.cancelPreviewTimeout()
        sessionController.endSession()
    }

    override fun onDestroy() {
        // endSession() 済みでも stale なコールバックが発火しないようにするバックストップ (§5)。
        peekController.cancelPreviewTimeout()
        // endSession() 側でも呼ばれるが、Activity が先に破棄される経路に備えたバックストップ
        // (docs/plans/other/burn-in-protection/PLAN.md §4)。stop() は running ガードで冪等。
        burnInProtector.onSessionActiveChanged(false)
        if (FakeSleepActivityHolder.current === this) FakeSleepActivityHolder.current = null
        if (FakeSleepActivityHolder.indicatorUiState === indicatorUiState) {
            FakeSleepActivityHolder.indicatorUiState = null
        }
        if (FakeSleepActivityHolder.burnInProtector === burnInProtector) {
            FakeSleepActivityHolder.burnInProtector = null
        }
        super.onDestroy()
    }
}

@Composable
private fun FakeSleepContent(
    previewLinesState: MutableState<List<String>?>,
    indicatorVisibleState: MutableState<Boolean>,
    indicatorOffsetState: MutableState<IntOffset>,
    tintColorState: MutableState<Color>,
    slotsState: MutableState<List<IndicatorSlotUiState>>,
    blinkIntervalMsState: MutableState<Long?>,
    onTap: () -> Unit,
) {
    val previewLines = previewLinesState.value
    val indicatorVisible = indicatorVisibleState.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        // 近接センサー被覆中は indicatorVisible が false になり、ドット/プレビューいずれの
        // 描画も抑止する。previewLinesState 自体は変更しないため、被覆解除時は直前の
        // 表示状態がそのまま復元される (docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md §3)。
        if (indicatorVisible) {
            if (previewLines == null) {
                IndicatorGroup(
                    offsetState = indicatorOffsetState,
                    tintColorState = tintColorState,
                    slotsState = slotsState,
                    blinkIntervalMsState = blinkIntervalMsState,
                )
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    previewLines.forEach { line -> Text(text = line, color = Color.White) }
                }
            }
        }
    }
}

/**
 * インジケータースロットグループの位置決めコンテナ。画面左上を基準とする絶対座標系で、
 * offsetState (BurnInProtector が書き換える) の値だけグループ全体をずらして描画する
 * (docs/plans/other/burn-in-protection/PLAN.md §3)。最大4件までのスロットを固定2x2グリッドの
 * 各象限へ配置する — スロットが1件のみなら quadrantOffset(0) は常にノーオペレーションのため、
 * 単一通知時の見た目は従来と完全に同一 (docs/plans/other/multi-notification-stacking/PLAN.md §4、
 * MAX_SLOTS==1 の縮退ケース)。色は tintColorState (ユーザー設定の永続化色、デフォルト白) から
 * 取る (docs/plans/other/indicator-color-customization/PLAN.md §5)。点滅は blinkIntervalMsState
 * が非 null の間、グループ全体で同期して行う — スロット単位ではない
 * (docs/plans/other/importance-based-sizing/PLAN.md §7)。高重要度・カテゴリアイコン/プレーンな円
 * フォールバック・件数バッジは SlotIcon に委ねる (§5/§6)。
 */
@Composable
private fun IndicatorGroup(
    offsetState: MutableState<IntOffset>,
    tintColorState: MutableState<Color>,
    slotsState: MutableState<List<IndicatorSlotUiState>>,
    blinkIntervalMsState: MutableState<Long?>,
) {
    val slots = slotsState.value
    val blinkIntervalMs = blinkIntervalMsState.value

    // ハードなオン/オフ切り替えのみ (フェード/補間は行わない — PLAN.md はそこまで要求していない)。
    // blinkIntervalMs が変化するたびキーとして LaunchedEffect が再起動し、null になった場合は
    // 即座に表示状態へ戻る (= 点滅しない、従来どおりの挙動)。
    var blinkVisible by remember { mutableStateOf(true) }
    LaunchedEffect(blinkIntervalMs) {
        if (blinkIntervalMs == null) {
            blinkVisible = true
            return@LaunchedEffect
        }
        while (isActive) {
            blinkVisible = true
            delay(blinkIntervalMs / 2)
            blinkVisible = false
            delay(blinkIntervalMs / 2)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (blinkVisible) {
            slots.forEachIndexed { index, slot ->
                SlotIcon(
                    slot = slot,
                    tintColor = tintColorState.value,
                    modifier = Modifier
                        .offset { offsetState.value }
                        .quadrantOffset(index),
                )
            }
        }
    }
}

// 固定2x2グリッド内での象限オフセット。index==0 (単一スロット MVP のケースを含む) は常に
// (0dp, 0dp) のためノーオペレーション (docs/plans/other/multi-notification-stacking/PLAN.md §4)。
private fun Modifier.quadrantOffset(index: Int): Modifier = this.offset(
    x = if (index % 2 == 0) 0.dp else QUADRANT_GAP_DP.dp,
    y = if (index < 2) 0.dp else QUADRANT_GAP_DP.dp,
)

/**
 * 通知元アプリ自身のランチャーアイコンを解決する (source-app-icon-indicator-mode)。
 * packageName をキーに remember() するため、点滅トグルなど packageName が変わらない再構成では
 * PackageManager へ問い合わせ直さない (docs/plans/other/source-app-icon-indicator-mode/PLAN.md §6)。
 * アプリアンインストール後の NameNotFoundException 等、解決失敗は runCatching で吸収して null を
 * 返す — 呼び出し側 (SlotIcon) がカテゴリアイコン/ドットへフォールバックする。
 */
@Composable
private fun rememberAppIconBitmap(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName.isNullOrEmpty()) {
            null
        } else {
            runCatching { context.packageManager.getApplicationIcon(packageName) }
                .getOrNull()
                ?.toBitmap()
                ?.asImageBitmap()
        }
    }
}

/**
 * インジケータースロット1件分の描画。有効化条件を満たす場合はまず通知元アプリ自身のアイコンを
 * 試み (source-app-icon-indicator-mode、docs/detailed-spec.md §20)、解決できなければ
 * slot.category が非 null ならカテゴリアイコンを、null なら従来どおりプレーンな円 (ドット) を
 * 描画する — 円描画は未割り当てが実装される前の MVP 既定表示であり、フォールバックとして残す
 * (docs/plans/other/category-icons-and-assets/PLAN.md §5)。
 * 高重要度スロットは 1.5x スケールで描画する (docs/plans/other/importance-based-sizing/PLAN.md §6)。
 * CountBadgeSettings が有効かつ count > 1 の場合のみ、右上コーナーに件数バッジを重ねる
 * (デフォルト OFF、docs/plans/other/multi-notification-stacking/PLAN.md §5)。
 */
@Composable
private fun SlotIcon(slot: IndicatorSlotUiState, tintColor: Color, modifier: Modifier) {
    val dotSizeDp = if (slot.highImportance) INDICATOR_DOT_SIZE_DP * 1.5f else INDICATOR_DOT_SIZE_DP.toFloat()
    val category = slot.category
    // プレミアム未解放時は永続化済みの設定値に関わらず常に強制 OFF
    // (docs/detailed-spec.md §20 "Forced off when isPremiumUnlocked is false")。
    // slot.packageName が null (OTHER オーバーフロースロット) の場合は remember() 内で
    // 早期に null を返すため、この分岐自体が常にフォールバックへ流れる (docs/DECISIONS.md §20)。
    val appIconEnabled = PremiumState.isPremiumUnlocked() && AppIconIndicatorSettings.enabledCached
    val appIconBitmap = if (appIconEnabled) rememberAppIconBitmap(slot.packageName) else null
    Box(modifier = modifier) {
        if (appIconBitmap != null) {
            // 実アプリアイコンは多色画像のため tintColor を適用しない
            // (docs/detailed-spec.md §19 に追記された注記、§20)。
            Image(
                bitmap = appIconBitmap,
                contentDescription = null,
                modifier = Modifier.size(dotSizeDp.dp),
            )
        } else if (category != null) {
            // Image + ColorFilter.tint() は View 側の ImageView.setColorFilter() の
            // Compose 版相当 (PLAN.md §2/§6 が指定する着色経路)。androidx.compose.material3.Icon
            // は tint 引数しか持たず内部変換が暗黙的なため、ここでは明示的に Image + colorFilter
            // を使う。
            Image(
                painter = painterResource(category.drawableRes()),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tintColor),
                modifier = Modifier.size(dotSizeDp.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(dotSizeDp.dp)
                    .background(tintColor, CircleShape),
            )
        }
        if (CountBadgeSettings.enabledCached && slot.count > 1) {
            // 角に完全に内包すると小さいアイコンをほぼ覆ってしまうため、バッジの半分ほどが
            // アイコンの外側にはみ出す標準的なバッジ配置にする (ユーザー確認・実機調整)。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (COUNT_BADGE_SIZE_DP / 2).dp, y = -(COUNT_BADGE_SIZE_DP / 2).dp)
                    .size(COUNT_BADGE_SIZE_DP.dp)
                    .background(Color.Red, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = slot.count.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// slot.category からドローアブルリソースへのコンパイル時解決 (docs/plans/other/
// category-icons-and-assets/PLAN.md §6) — IndicatorCategory.assetName の文字列は
// アセット命名の文書化用途のみで、ここでは使わない (ランタイム文字列解決はしない)。
private fun IndicatorCategory.drawableRes(): Int = when (this) {
    IndicatorCategory.PHONE -> R.drawable.ic_notify_phone
    IndicatorCategory.MAIL -> R.drawable.ic_notify_mail
    IndicatorCategory.SNS -> R.drawable.ic_notify_sns
    IndicatorCategory.CALENDAR -> R.drawable.ic_notify_calendar
    IndicatorCategory.OTHER -> R.drawable.ic_notify_other
}
