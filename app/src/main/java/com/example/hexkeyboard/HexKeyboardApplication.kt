package com.example.hexkeyboard

import android.app.Application
import com.example.hexkeyboard.data.repository.EmojiProvider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HexKeyboardApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        // Inicialización asíncrona de EmojiProvider para evitar bloqueo del hilo principal
        applicationScope.launch(Dispatchers.IO) {
            EmojiProvider.initialize(this@HexKeyboardApplication)
        }
    }
}
