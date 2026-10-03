package com.virtualed.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.virtualed.app.R
import com.virtualed.app.permission.PermissionChecker
import com.virtualed.app.ui.theme.VirtuaLedTheme

/**
 * 通知リスナーアクセスと全画面通知の許可を案内する onboarding 画面。
 *
 * docs/plans/mvp/permission-onboarding/PLAN.md §3 を実装。MVP スコープは通知リスナー
 * アクセス 1 件のみだったが、全画面通知の許可が OS 側で取り消されうると判明したため
 * (docs/QA.md #24)、そのカードを追加した (GitHub issue #49)。両方が許可済みになった時点で
 * onAllGranted を呼ぶ。全画面通知が既に許可済み (API 33 以下は常に許可扱い) なら、
 * そのカードは「許可済み」表示になるだけで追加の操作は不要。
 */
@Composable
fun OnboardingScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    var listenerGranted by remember {
        mutableStateOf(PermissionChecker.hasNotificationListenerAccess(context))
    }
    var fullScreenIntentGranted by remember {
        mutableStateOf(PermissionChecker.canUseFullScreenIntent(context))
    }

    // §4 — Settings 画面から戻ってきたタイミングで再チェックする
    RecheckOnResume {
        listenerGranted = PermissionChecker.hasNotificationListenerAccess(context)
        fullScreenIntentGranted = PermissionChecker.canUseFullScreenIntent(context)
    }

    LaunchedEffect(listenerGranted, fullScreenIntentGranted) {
        if (listenerGranted && fullScreenIntentGranted) onAllGranted()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            PermissionCard(
                granted = listenerGranted,
                title = stringResource(R.string.onboarding_listener_title),
                description = stringResource(R.string.onboarding_listener_description),
                onGrantClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )
            PermissionCard(
                granted = fullScreenIntentGranted,
                title = stringResource(R.string.onboarding_fsi_title),
                description = stringResource(R.string.onboarding_fsi_description),
                onGrantClick = {
                    context.startActivity(PermissionChecker.fullScreenIntentSettingsIntent(context))
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * 権限 1 件分の案内カード。許可済みならチェック済み表示、未許可なら Settings 遷移ボタンを表示する
 * (docs/plans/mvp/permission-onboarding/PLAN.md §3 の「未許可の権限のみ案内する」方針)。
 */
@Composable
private fun PermissionCard(
    granted: Boolean,
    title: String,
    description: String,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (granted) {
                Text(
                    text = stringResource(R.string.onboarding_granted_label),
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                Button(onClick = onGrantClick) {
                    Text(text = stringResource(R.string.onboarding_grant_button))
                }
            }
        }
    }
}

/**
 * ホストの Lifecycle の ON_RESUME イベントごとに onResume を呼び出す。
 *
 * 通知リスナーアクセスの許可状態はコールバックで観測できず、ユーザーが Settings 画面で許可して
 * 戻ってきたタイミングで再チェックする以外に検知手段がない
 * (docs/plans/mvp/permission-onboarding/PLAN.md §4)。全画面通知の許可も同様のため、
 * MainSwitchScreen からも使う (GitHub issue #49)。
 */
@Composable
fun RecheckOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    VirtuaLedTheme {
        OnboardingScreen(onAllGranted = {})
    }
}
