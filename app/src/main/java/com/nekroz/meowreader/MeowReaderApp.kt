package com.nekroz.meowreader

import android.app.Application
import com.nekroz.meowreader.repo.DocumentRepo
import com.nekroz.meowreader.repo.MainRepo
import java.io.File

/**
 * 持有整個 App 共用的 repository。專案規模不大，不另外引入 DI 框架，
 * ViewModel 直接從 Application 取用。
 */
class MeowReaderApp : Application() {

    val mainRepo: MainRepo by lazy { MainRepo(File(filesDir, MainRepo.CONFIG_FILE_NAME)) }

    val documentRepo: DocumentRepo by lazy { DocumentRepo(this) }
}
