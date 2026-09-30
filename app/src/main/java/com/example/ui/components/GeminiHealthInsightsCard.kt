package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.ConversationalLongevityInsight
import com.example.ai.DegradationReport
import com.example.ai.FailureRiskLevel
import com.example.model.DotState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraTeal
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel

@Composable
fun GeminiHealthInsightsCard(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val report by viewModel.degradationReport.collectAsStateWithLifecycle()
    val longevityInsight by viewModel.longevityInsight.collectAsStateWithLifecycle()
    val isLongevityLoading by viewModel.isLongevityLoading.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    GeminiHealthInsightsContent(
        report, longevityInsight, isLongevityLoading, chatMessages, isChatLoading,
        onRefresh = viewModel::generateFirebaseLongevityInsights,
        onQuestion = viewModel::sendConversationalQuestion, modifier = modifier
    )
}

@Composable
fun GeminiHealthInsightsContent(
    report: DegradationReport,
    longevityInsight: ConversationalLongevityInsight?,
    isLongevityLoading: Boolean,
    chatMessages: List<com.example.model.LongevityChatMessage>,
    isChatLoading: Boolean,
    onRefresh: () -> Unit,
    onQuestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var userQueryText by remember { mutableStateOf("") }
    var isChatExpanded by remember { mutableStateOf(false) }

    val riskColor = when (report.riskLevel) {
        FailureRiskLevel.CRITICAL -> StatusRed
        FailureRiskLevel.ELEVATED -> StatusAmber
        FailureRiskLevel.MODERATE -> NetraTeal
        FailureRiskLevel.LOW -> NetraEmerald
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    SentinelCard(
        title = "Battery History Insights",
        icon = Icons.Default.AutoAwesome,
        dotState = if (report.riskLevel == null) DotState.STANDBY else if (report.riskLevel == FailureRiskLevel.CRITICAL) DotState.CRITICAL else DotState.CONNECTED,
        accentColor = NetraCyan,
        trailingAction = {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp).testTag("refresh_longevity_insights_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Insights",
                    tint = NetraCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        modifier = modifier.testTag("gemini_health_insights_card")
    ) {
        // Machine Learning Failure / Degradation Risk Predictor Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(riskColor.copy(alpha = 0.12f))
                .border(1.dp, riskColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (report.riskLevel == FailureRiskLevel.CRITICAL || report.riskLevel == FailureRiskLevel.ELEVATED) Icons.Default.Warning else Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = report.riskPercent?.let { "Failure Risk: $it% (${report.riskLevel})" } ?: "Failure risk: Unavailable",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = riskColor
                        )
                    }

                    Text(
                        text = report.estimatedCapacityHealthPercent?.let { "Capacity health: $it%" } ?: "Capacity health: Unavailable",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "• Pattern: ${report.primaryRiskFactor}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Mitigation: ${report.keyMitigation}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Degradation Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniFactorBox("HV DWELL", report.highVoltageDwellMinutes?.let { "${it}m" } ?: "Unavailable", NetraCyan, Modifier.weight(1f))
                    MiniFactorBox("HEAT TIME", report.thermalStressHours?.let { "${String.format("%.1f", it)}h" } ?: "Unavailable", StatusAmber, Modifier.weight(1f))
                    MiniFactorBox("DEEP DROPS", report.deepDischargeCount?.let { "${it}x" } ?: "Unavailable", StatusRed, Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Conversational Longevity Blueprint (Firebase AI SDK output)
        if (isLongevityLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NetraCyan, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Summarizing available battery records...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            longevityInsight?.let { insight ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = insight.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetraCyan
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NetraEmerald.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Habit score: Unavailable",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NetraEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = insight.summary,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = insight.detailedAdvice,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Personalized Longevity Action Plan:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NetraEmerald
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    insight.personalizedActionPlan.forEach { action ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            Text("💡 ", fontSize = 11.sp)
                            Text(action, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Electrochemical Mechanism: ${insight.electrochemicalExplanation}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Powered by ${insight.aiModelUsed}",
                        fontSize = 9.sp,
                        color = NetraCyan.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Conversational Assistant Accordion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { isChatExpanded = !isChatExpanded }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Chat, contentDescription = null, tint = NetraCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isChatExpanded) "Hide Longevity Q&A Assistant" else "Ask Gemini About Your Battery Habits",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetraCyan
                )
            }
            Icon(
                imageVector = if (isChatExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = NetraCyan
            )
        }

        AnimatedVisibility(visible = isChatExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                // Quick prompt suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip("Overnight charge safe?", onClick = { onQuestion("Is overnight charging safe for my battery?") }, modifier = Modifier.weight(1f))
                    SuggestionChip("Why did it heat up?", onClick = { onQuestion("Why did my battery heat up recently?") }, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chat Messages List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chatMessages.takeLast(6).forEach { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (msg.isUser) NetraCyan.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (msg.isUser) "You" else "Battery History",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.isUser) NetraCyan else NetraEmerald
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg.text,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (isChatLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = NetraEmerald, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reading available battery records...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Query input bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userQueryText,
                        onValueChange = { userQueryText = it },
                        placeholder = { Text("Ask about longevity, heat, or cycles...", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("longevity_chat_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NetraCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (userQueryText.isNotBlank()) {
                                onQuestion(userQueryText)
                                userQueryText = ""
                            }
                        },
                        modifier = Modifier.testTag("send_longevity_chat_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = NetraCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniFactorBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(0.5.dp, NetraCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 10.sp, color = NetraCyan, fontWeight = FontWeight.Medium)
    }
}
