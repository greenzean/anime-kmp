package com.greenzean.animekmp.mal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.greenzean.animekmp.MainActivity
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import org.koin.android.ext.android.inject

class MalRedirectActivity : ComponentActivity() {
    private val redirects: MalRedirectHandler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            intent.data?.toString()?.let(redirects::onRedirect)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}
