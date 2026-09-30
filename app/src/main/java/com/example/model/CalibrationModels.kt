package com.example.model

enum class CalibrationStep(val stepNumber: Int, val title: String, val description: String) {
    NOT_STARTED(0, "Not Started", "This app cannot recalibrate Android's fuel gauge or measure remaining capacity."),
    STEP_1_DISCHARGE(1, "Discharge Log", "Observe the device battery level; do not intentionally deep-discharge for this app."),
    STEP_2_REST(2, "Rest Log", "Record a pause in the observation cycle; this is not a cell calibration."),
    STEP_3_FULL_CHARGE(3, "Charge Log", "Observe charging state without assuming a capacity measurement."),
    STEP_4_SATURATION(4, "Final Log", "The app cannot alter charger behavior or coulomb-counter calibration."),
    COMPLETED(5, "Observation Complete", "The observation steps finished; capacity and fuel-gauge calibration remain unavailable.")
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
    val isWizardActive: Boolean = false
)
