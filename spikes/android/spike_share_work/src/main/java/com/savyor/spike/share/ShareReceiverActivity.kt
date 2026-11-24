package com.savyor.spike.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val isMock = intent.getBooleanExtra("is_mock", false)

            val result = InstagramUrlCleaner.clean(sharedText)
            if (result.isValid && result.cleanUrl != null) {
                val workRequest = OneTimeWorkRequestBuilder<ProcessReelWorker>()
                    .setInputData(
                        workDataOf(
                            "reel_url" to result.cleanUrl,
                            "shortcode" to result.shortcode,
                            "is_mock" to isMock
                        )
                    )
                    .addTag("reel_processing")
                    .build()

                WorkManager.getInstance(applicationContext).enqueue(workRequest)

                Log.i(
                    "SpikeShareReceiver",
                    "[SPIKE_SHARE_RECEIVER] status=ENQUEUED id=${workRequest.id} shortcode=${result.shortcode} cleanUrl=${result.cleanUrl}"
                )
                Toast.makeText(this, "Savyor: Queued ${result.shortcode}", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(
                    "SpikeShareReceiver",
                    "[SPIKE_SHARE_RECEIVER] status=REJECTED reason=${result.rejectionReason} text=${sharedText?.take(60)}"
                )
                Toast.makeText(this, "Savyor: Rejected (${result.rejectionReason})", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }
}
