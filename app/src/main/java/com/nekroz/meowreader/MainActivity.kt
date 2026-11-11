package com.nekroz.meowreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nekroz.meowreader.repo.MainRepo
import com.nekroz.meowreader.repo.model.AppPage
import com.nekroz.meowreader.ui.mainpage.MainPageScreen
import com.nekroz.meowreader.ui.readerpage.ReaderPageScreen
import com.nekroz.meowreader.ui.theme.MeowReaderTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    private val mainRepo: MainRepo get() = (application as MeowReaderApp).mainRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repo = mainRepo
            val theme by remember(repo) { repo.config.map { it.theme }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(initialValue = repo.current.theme)

            MeowReaderTheme(appTheme = theme) {
                MeowReaderNavHost(
                    // 根據設定檔記錄的頁面決定啟動頁，上次停在閱讀頁就直接回到閱讀頁。
                    startInReader = remember(repo) {
                        repo.current.lastPage == AppPage.ReaderPage && repo.current.openFile != null
                    },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 設定檔平常由背景 writer 延遲寫入；進入背景後行程隨時可能被回收，這裡同步落地。
        mainRepo.flush()
    }
}

private object Routes {
    const val MAIN = "main"
    const val READER = "reader"
}

@Composable
private fun MeowReaderNavHost(startInReader: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (startInReader) Routes.READER else Routes.MAIN,
    ) {
        composable(Routes.MAIN) {
            MainPageScreen(
                onOpenReader = {
                    navController.navigate(Routes.READER) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.READER) {
            ReaderPageScreen(
                onBack = {
                    // 連點返回時第二下進來頁面已經在退場了，不能再退一次。
                    if (navController.currentDestination?.route != Routes.READER) return@ReaderPageScreen

                    // 直接以閱讀頁當啟動頁時底下沒有主頁可退，這種情況才改成導向主頁。
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.READER) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
