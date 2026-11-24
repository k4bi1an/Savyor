package com.savyor.spike.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Spike3Screen(workManager = WorkManager.getInstance(applicationContext))
        }
    }
}

@Composable
fun Spike3Screen(workManager: WorkManager) {
    val workInfos by workManager.getWorkInfosByTagLiveData("reel_processing")
        .observeAsState(emptyList())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9F9F8)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "SAVYOR — Spike 3",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "Share Intent + WorkManager (Survives Doze / Kill)",
                fontSize = 13.sp,
                color = Color(0xFF6B6B6B)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enqueued Background Work Requests (${workInfos.size}):",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workInfos) { info ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E6E1))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Job: ${info.id}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF6B6B6B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "State: ${info.state}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (info.state) {
                                    WorkInfo.State.SUCCEEDED -> Color(0xFF2F5D50)
                                    WorkInfo.State.FAILED -> Color(0xFFB3382C)
                                    WorkInfo.State.RUNNING -> Color(0xFF1E88E5)
                                    else -> Color(0xFF6B6B6B)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
