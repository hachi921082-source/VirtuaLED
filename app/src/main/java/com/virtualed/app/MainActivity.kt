package com.virtualed.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.virtualed.app.service.VirtuaLedForegroundService
import com.virtualed.app.ui.theme.VirtuaLedTheme

// 暫定の起動画面。permission-onboarding 実装後、この暫定スイッチは正式な onboarding フローに
// 置き換えられる (docs/plans/mvp/foreground-service-setup/PLAN.md §4 の起動モデル確認用)。
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VirtuaLedTheme {
                PlaceholderScreen()
            }
        }
    }
}

@Composable
fun PlaceholderScreen() {
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
fun PlaceholderScreenPreview() {
    VirtuaLedTheme {
        PlaceholderScreen()
    }
}
