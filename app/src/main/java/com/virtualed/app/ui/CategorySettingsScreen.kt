package com.virtualed.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virtualed.app.R
import com.virtualed.app.category.IndicatorCategory
import com.virtualed.app.category.drawableRes
import com.virtualed.app.data.CategorySettings
import kotlinx.coroutines.launch

/**
 * アプリ別カテゴリ割り当て画面 (docs/plans/other/category-icons-and-assets/PLAN.md §4/§7)。
 * KeywordSettingsScreen と同じ boolean トグル方式 (ナビゲーションライブラリ不使用) で
 * MainSwitchScreen と行き来する (MainActivity.kt の showCategorySettings 参照)。
 *
 * アプリ選択は KeywordSettingsScreen と同じ制約・同じ理由により InstalledAppPicker.kt の
 * loadInstalledApps() を共用する。各アプリの選択は「保存」ボタンを持たず、選択と同時に
 * CategorySettings.setCategory() へ書き込む (この画面以外の設定項目と同じ write-through 方式)。
 * 「その他」チップは IndicatorCategory.OTHER への明示割り当てではなく、未割り当てへ戻す
 * (category = null) — CategoryMapper.resolve() が未割り当てを OTHER にフォールバックするため、
 * 見た目上は同じ表示になる5番目の選択肢として扱う (PLAN.md §7 の「4 named categories + OTHER/
 * unassigned」)。
 */
@Composable
fun CategorySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps(context)
    }

    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<IndicatorCategory?>(null) }
    LaunchedEffect(selectedPackage) {
        val pkg = selectedPackage
        selectedCategory = if (pkg != null) CategorySettings.getCategory(context, pkg) else null
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // enableEdgeToEdge() (MainActivity.onCreate()) によりコンテンツがシステムバー領域まで
        // 描画されるため、先頭行 (タイトル/戻るボタン) がステータスバーの時計/バッテリー表示と
        // 重なってしまう。safeDrawing でステータスバー/ナビゲーションバー分の余白を確保する
        // (実機テストで発見)。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.category_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.category_settings_back_button))
                }
            }
            Text(
                text = stringResource(R.string.category_settings_pick_app_label),
                modifier = Modifier.padding(top = 12.dp),
            )
            // 2026-08-30: 選んだアプリの直下にカテゴリ選択を展開する形へ変更 (以前はリストの外に
            // 別枠で表示しており、選択したアプリとの視覚的なつながりが分かりにくいとの指摘)。
            // 再タップで折りたたむ (アコーディオン方式)。
            LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(top = 4.dp)) {
                items(installedApps) { app ->
                    val isExpanded = selectedPackage == app.packageName
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPackage = if (isExpanded) null else app.packageName
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(text = app.label)
                        }
                        if (isExpanded) {
                            CategoryPickerRow(
                                selectedCategory = selectedCategory,
                                onCategorySelected = { category ->
                                    selectedCategory = category
                                    coroutineScope.launch {
                                        CategorySettings.setCategory(context, app.packageName, category)
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5択のカテゴリ選択チップ行。「その他」選択時は category = null (未割り当てへ戻す) を書き込む
 * (このコンポーザブル自体のクラス doc 参照)。
 *
 * 選択解除は「その他」チップ経由でも可能だが、それだけでは分かりにくい (実機テストで判明) ため、
 * 選択中のチップをもう一度タップすると未割り当てへ戻るオン/オフトグル動作も持たせる —
 * 一般的な選択チップ UI の慣習に沿った、より発見しやすい解除手段として追加する。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerRow(
    selectedCategory: IndicatorCategory?,
    onCategorySelected: (IndicatorCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        IndicatorCategory.PHONE to stringResource(R.string.category_settings_category_phone),
        IndicatorCategory.MAIL to stringResource(R.string.category_settings_category_mail),
        IndicatorCategory.SNS to stringResource(R.string.category_settings_category_sns),
        IndicatorCategory.CALENDAR to stringResource(R.string.category_settings_category_calendar),
        // null = 未割り当て。表示ラベルは「その他」で、明示的な OTHER 割り当てと見た目上区別しない。
        null to stringResource(R.string.category_settings_category_other),
    )
    // 5択のラベル (日本語) 幅の合計が画面幅を超えることが実機テストで判明。横スクロールは
    // 発見しにくいとの指摘 (2026-08-30) を受け、収まらない分は自動で次の行へ折り返す
    // FlowRow に変更 (横スクロールより見た目上わかりやすい)。
    FlowRow(modifier = modifier.padding(top = 8.dp)) {
        options.forEach { (category, label) ->
            val isSelected = selectedCategory == category
            Button(
                // 選択中のチップを再度タップすると未割り当て (null) へトグルオフする。
                // 「その他」チップ自体は category=null なので、この式は特別扱い不要で
                // そのまま正しく動作する。
                onClick = { onCategorySelected(if (isSelected) null else category) },
                colors = if (isSelected) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                modifier = Modifier.padding(end = 4.dp),
            ) {
                // 2026-08-30: 実際にインジケーターへ表示されるアイコン画像をチップにも表示する
                // (テキストだけでは分かりにくいとの指摘) — null (未割り当て/その他) は
                // OTHER と同じアイコンにフォールバックする (描画側の CategoryMapper.resolve() と
                // 同じ扱い、com/virtualed/app/category/IndicatorCategory.kt の drawableRes() を共用)。
                Image(
                    painter = painterResource((category ?: IndicatorCategory.OTHER).drawableRes()),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = label)
            }
        }
    }
}
