package com.recipebros.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val incomingShare = mutableStateOf<String?>(null)
    private val incomingVideo = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptShare(intent)
        setContent {
            RecipeBrosApp(
                incomingText = incomingShare.value,
                consumeIncoming = { incomingShare.value = null },
                incomingVideo = incomingVideo.value,
                consumeIncomingVideo = { incomingVideo.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
    }

    private fun acceptShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        when {
            intent.type?.startsWith("text/") == true ->
                incomingShare.value = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.type?.startsWith("video/") == true -> {
                @Suppress("DEPRECATION")
                val shared: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                incomingVideo.value = shared ?: intent.clipData?.getItemAt(0)?.uri
            }
        }
    }
}
