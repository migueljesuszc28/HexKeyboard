package com.example.hexkeyboard

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.hexkeyboard.data.repository.EmojiProvider
import com.example.hexkeyboard.data.repository.ThemeUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltAndroidApp
class HexKeyboardApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        // Inicialización asíncrona de EmojiProvider y aplicación del idioma de la app
        applicationScope.launch(Dispatchers.IO) {
            EmojiProvider.initialize(this@HexKeyboardApplication)
            val appLang = ThemeUtils.getDataStore(this@HexKeyboardApplication).data.first()[ThemeUtils.APP_LANGUAGE] ?: "es"
            if (appLang.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appLang))
                }
            }
        }
    }
}
