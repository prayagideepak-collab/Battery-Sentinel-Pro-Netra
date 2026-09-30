package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.ai.BatteryDegradationPredictor
import com.example.ui.components.GeminiHealthInsightsContent
import com.example.model.CalibrationSessionState
import com.example.model.BatteryTelemetry
import com.example.ui.components.BatteryCalibrationWizardCard
import com.example.ui.components.OptimalChargingWindowCard
import com.example.ui.components.NightChargingThrottleCard
import com.example.ui.components.BatteryHealthTrendLineChart
import com.example.ui.components.ThermalAppCorrelationHeatmap
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import org.junit.Assert.assertTrue
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderUnavailableDegradationCard() {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.width(380.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    GeminiHealthInsightsContent(
                        BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(emptyList(), emptyList()),
                        null, false, emptyList(), false, {}, {}
                    )
                }
            }
        }
        captureCard("degradation-unavailable.png")
    }

    @Test
    fun renderUnavailableCalibrationCard() {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.width(380.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    BatteryCalibrationWizardCard(CalibrationSessionState(), BatteryTelemetry(), {}, {}, {})
                }
            }
        }
        captureCard("calibration-unavailable.png")
    }

    @Test
    fun renderUnavailableHealthAndThermalCards() {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.width(380.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    BatteryHealthTrendLineChart(emptyList())
                    ThermalAppCorrelationHeatmap(emptyList(), emptyList(), Modifier.padding(top = 20.dp))
                    NightChargingThrottleCard(
                        telemetry = BatteryTelemetry(level = 80, isCharging = true),
                        isFeatureEnabled = true, targetWakeHour = 7,
                        records = emptyList(), sessions = emptyList(),
                        onToggleFeature = {}, onSelectWakeHour = {},
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    OptimalChargingWindowCard(emptyList(), emptyList(), Modifier.padding(top = 20.dp))
                }
            }
        }
        captureCard("truthfulness-unavailable-cards.png")
    }

    private fun captureCard(filename: String) {
        compose.waitForIdle()
        // WindowCapture's PixelCopy/redraw wait is not supported reliably by Robolectric.
        // Draw the laid-out activity view directly using native graphics instead.
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            assertTrue("Render view must be laid out", view.width > 0 && view.height > 0)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val output = File("build/outputs/ui-renders").apply { mkdirs() }
        val file = File(output, filename)
        file.outputStream().use {
            assertTrue("PNG encoding must succeed", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        assertTrue("Render must not be empty", file.length() > 0L)
        bitmap.recycle()
    }
}
