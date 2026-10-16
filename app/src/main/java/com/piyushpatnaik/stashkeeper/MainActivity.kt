package com.piyushpatnaik.stashkeeper

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piyushpatnaik.stashkeeper.data.StashFunctions
import com.piyushpatnaik.stashkeeper.data.StashSession
import com.piyushpatnaik.stashkeeper.data.routeOf
import com.piyushpatnaik.stashkeeper.ui.ProvideDisplay
import com.piyushpatnaik.stashkeeper.ui.StashApp
import com.piyushpatnaik.stashkeeper.ui.StashKeeperTheme
import com.piyushpatnaik.stashkeeper.ui.requestNativeRefreshRate

class StashViewModel(app: Application) : AndroidViewModel(app) {
    val store = StashSession.get(app)

    init {
        store.recordToday()
    }
}

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)
    private val refreshRate = mutableIntStateOf(60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshRate.intValue = requestNativeRefreshRate()
        pendingRoute.value = routeOf(intent)
        setContent {
            val model: StashViewModel = viewModel()
            StashKeeperTheme {
                ProvideDisplay(refreshRate.intValue) {
                    StashApp(model.store, pendingRoute)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        StashFunctions.acquire(this)
    }

    override fun onResume() {
        super.onResume()
        refreshRate.intValue = requestNativeRefreshRate()
    }

    override fun onStop() {
        StashFunctions.release()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = routeOf(intent)
    }
}
