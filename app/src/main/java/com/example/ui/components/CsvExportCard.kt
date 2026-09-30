package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.viewmodel.NetraViewModel

@Composable
fun CsvExportCard(
    viewModel: NetraViewModel,
    totalRecords: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }

    SentinelCard(
        title = "Export Telemetry (CSV)",
        icon = Icons.Default.TableChart,
        dotState = DotState.CONNECTED,
        accentColor = NetraCyan,
        modifier = modifier.testTag("csv_export_card")
    ) {
        Text(
            text = "Export the entire time-series database ($totalRecords records, historical charging cycles, and diagnostic events) to a standard CSV format for external analysis in Python, Excel, or MATLAB.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                isExporting = true
                viewModel.exportTelemetryCsv(context) { success ->
                    isExporting = false
                    if (!success) {
                        Toast.makeText(context, "Failed to generate CSV export", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            enabled = !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("export_csv_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NetraCyan, contentColor = Color.Black)
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generating CSV Dataset...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export & Share Telemetry CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
