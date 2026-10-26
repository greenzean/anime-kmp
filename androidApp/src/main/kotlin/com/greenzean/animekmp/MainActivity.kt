package com.greenzean.animekmp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.greenzean.animekmp.composeapp.App
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val deepLinks = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val malRedirects: MalRedirectHandler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(deepLinks = deepLinks) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let(deepLinks::tryEmit)
    }

    override fun onResume() {
        super.onResume()
        malRedirects.onActivityResumed()
    }
}
