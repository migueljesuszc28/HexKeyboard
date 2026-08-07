package com.example.hexkeyboard.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class PermissionActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // No necesitamos manejar el resultado aquí, el servicio volverá a verificarlo
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val permission = intent.getStringExtra("request_permission")
        if (permission != null) {
            requestPermissionLauncher.launch(permission)
        } else {
            finish()
        }
    }
}
