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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.virtualed.app.permission.PermissionChecker
import com.virtualed.app.service.VirtuaLedForegroundService
import com.virtualed.app.ui.OnboardingScreen
import com.virtualed.app.ui.theme.VirtuaLedTheme

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

    // POST_NOTIFICATIONS は通常のランタイム許可ダイアログで足りるため、通知リスナー onboarding
    // とは独立に、起動時に一度だけ要求する (PLAN のスコープ注記参照)。
    RequestPostNotificationsPermission()

    if (listenerGranted) {
        MainSwitchScreen()
    } else {
        OnboardingScreen(onAllGranted = { listenerGranted = true })
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
fun MainSwitchScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }

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
            }
        }
    }
}

// docs/plans/mvp/foreground-service-setup/PLAN.md §4 (Startup Model) の起動/停止経路。
private fun setForegroundServiceEnabled(context: Context, enabled: Boolean) {
    val intent = Intent(context, VirtuaLedForegroundService::class.java)
    if (enabled) {
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
