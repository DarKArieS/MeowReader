package com.nekroz.meowreader.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nekroz.meowreader.repo.model.AppTheme

/**
 * App 的 Material 3 主題。minSdk 已經高於 Android 12，一律使用跟著桌布走的動態色彩。
 *
 * @param appTheme 使用者在全域設定選的主題；[AppTheme.Default] 表示跟隨系統。
 */
@Composable
fun MeowReaderTheme(
    appTheme: AppTheme = AppTheme.Default,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appTheme) {
        AppTheme.Default -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    // 畫面是 edge-to-edge，系統列的圖示顏色要跟著 App 自己的主題走，而不是系統的深淺色設定。
    val activity = LocalActivity.current
    val view = LocalView.current
    if (activity != null && !view.isInEditMode) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/**
 * 畫面背景不是主題色時（例如 ReaderPage 的自訂背景色），在該畫面顯示期間改用配得上背景的
 * 系統列圖示顏色，離開時還原成主題預設值。
 *
 * @param darkIcons 背景偏亮、需要深色圖示時為 true
 */
@Composable
fun SystemBarIconsOverride(darkIcons: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    val themeDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f

    DisposableEffect(activity, view, darkIcons, themeDarkIcons) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
        onDispose {
            controller.isAppearanceLightStatusBars = themeDarkIcons
            controller.isAppearanceLightNavigationBars = themeDarkIcons
        }
    }
}
