package com.example.model

enum class CalibrationStep(val stepNumber: Int, val title: String, val description: String) {
    NOT_STARTED(0, "Ready to Calibrate", "Recalibrate the fuel gauge Coulomb counter and electrochemical capacity model"),
    STEP_1_DISCHARGE(1, "Deep Discharge", "Discharge battery down to 10% or lower to establish voltage floor"),
    STEP_2_REST(2, "Chemical Relaxation", "Let battery rest unplugged for 15 minutes to stabilize Open Circuit Voltage (OCV)"),
    STEP_3_FULL_CHARGE(3, "Uninterrupted 100% Charge", "Connect charger and charge continuously to 100% without unplugging"),
    STEP_4_SATURATION(4, "Top Saturation", "Keep plugged for 20-30 minutes at 100% for electrolyte absorption"),
    COMPLETED(5, "Calibration Complete", "Battery capacity model and Coulomb counter successfully calibrated")
}

data class CalibrationSessionState(
    val currentStep: CalibrationStep = CalibrationStep.NOT_STARTED,
    val startTimestamp: Long = 0L,
    val step2StartTime: Long = 0L,
    val step4StartTime: Long = 0L,
    val startLevel: Int = 0,
    val lowestDischargeLevel: Int = 100,
    val peakChargeLevel: Int = 0,
    val lastCalibratedTimestamp: Long = 0L,
    val calibratedCapacityMah: Int = 4500,
    val accuracyScorePercent: Int = 94, // 94% baseline -> 99.8% when calibrated
    val isWizardActive: Boolean = false
)
