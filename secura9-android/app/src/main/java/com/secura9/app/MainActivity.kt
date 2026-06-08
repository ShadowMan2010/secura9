package com.secura9.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.data.model.AppUpdate
import com.secura9.app.navigation.AppNavigation
import com.secura9.app.ui.components.UpdateDialog
import com.secura9.app.ui.theme.Bg
import com.secura9.app.ui.theme.Secura9Theme
import com.secura9.app.utils.UpdateManager

class MainActivity : ComponentActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = FirebaseRepository()
        updateManager = UpdateManager(this)

        val pendingUpdate = intent?.let {
            if (it.getBooleanExtra("show_update", false)) {
                AppUpdate(
                    versionName = it.getStringExtra("update_version") ?: "",
                    apkUrl = it.getStringExtra("update_apk_url") ?: "",
                    changelog = it.getStringExtra("update_changelog") ?: "",
                    forceUpdate = it.getBooleanExtra("update_force", false),
                )
            } else null
        }

        setContent {
            Secura9Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Bg,
                ) {
                    var showUpdate by remember { mutableStateOf(pendingUpdate != null) }
                    var currentUpdate by remember { mutableStateOf(pendingUpdate) }

                    AppNavigation(
                        repository = repository,
                        onUpdateAvailable = { update ->
                            currentUpdate = update
                            showUpdate = true
                        }
                    )

                    if (showUpdate && currentUpdate != null) {
                        UpdateDialog(
                            update = currentUpdate!!,
                            onDismiss = { showUpdate = false },
                            onDownload = {
                                updateManager.downloadAndInstall(currentUpdate!!)
                                showUpdate = false
                            },
                        )
                    }
                }
            }
        }
    }
}
