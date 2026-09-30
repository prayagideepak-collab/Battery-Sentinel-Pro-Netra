package com.example

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.BatteryHealthTrendLineChart
import com.example.ui.components.ThermalAppCorrelationHeatmap
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** CPU-rendered Compose evidence, uploaded by CI for visual review before UI merges. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TruthfulnessCardRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun renderUnavailableHealthAndThermalCards() {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.width(380.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    BatteryHealthTrendLineChart(emptyList())
                    ThermalAppCorrelationHeatmap(emptyList(), emptyList(), Modifier.padding(top = 20.dp))
                }
            }
        }
        compose.waitForIdle()
        val output = File("build/outputs/ui-renders").apply { mkdirs() }
        File(output, "health-thermal-unavailable.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
