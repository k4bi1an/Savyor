package com.savyor.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.savyor.app.work.EnqueueResult
import com.savyor.app.work.WorkManagerScheduler
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent?.action
        val type = intent?.type

        if (Intent.ACTION_SEND == action && type != null && "text/plain" == type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val scheduler = WorkManagerScheduler(this)
                lifecycleScope.launch {
                    when (val result = scheduler.enqueueReel(sharedText)) {
                        is EnqueueResult.Success -> {
                            val msg = if (result.isNew) {
                                "Saved to Savyor! Analyzing in background..."
                            } else {
                                "Reel already saved in Savyor library."
                            }
                            Toast.makeText(this@ShareReceiverActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                        is EnqueueResult.InvalidUrl -> {
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                "Savyor only accepts Instagram reel links.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is EnqueueResult.Error -> {
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                "Could not enqueue reel: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    finish()
                }
            } else {
                Toast.makeText(this, "Empty share text received", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }
}
