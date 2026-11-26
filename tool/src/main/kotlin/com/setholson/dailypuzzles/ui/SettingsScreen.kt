package com.setholson.dailypuzzles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.setholson.dailypuzzles.data.LockoutPolicy
import com.setholson.dailypuzzles.data.OfflinePuzzleBank
import com.setholson.dailypuzzles.data.PuzzleRepository
import com.setholson.dailypuzzles.model.PuzzleSettings
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: PuzzleRepository,
) : LightViewModel<Unit>() {

    private val _settings = MutableStateFlow(PuzzleSettings())
    val settings: StateFlow<PuzzleSettings> = _settings.asStateFlow()

    val todayIso: String
        get() = OfflinePuzzleBank.todayLocalIso()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            _settings.value = repository.getSettings()
        }
    }

    fun toggleSchedule() = update { it.copy(lockoutScheduleEnabled = !it.lockoutScheduleEnabled) }

    fun adjustStart(deltaMinutes: Int) = update { s ->
        val total = ((s.unlockStartHour * 60 + s.unlockStartMinute + deltaMinutes) % 1440 + 1440) % 1440
        s.copy(unlockStartHour = total / 60, unlockStartMinute = total % 60)
    }

    fun adjustEnd(deltaMinutes: Int) = update { s ->
        val total = ((s.unlockEndHour * 60 + s.unlockEndMinute + deltaMinutes) % 1440 + 1440) % 1440
        s.copy(unlockEndHour = total / 60, unlockEndMinute = total % 60)
    }

    fun toggleLockToday() = update { s ->
        val nextIso = if (s.lockedUntilDateIso == todayIso) "" else todayIso
        s.copy(lockedUntilDateIso = nextIso)
    }

    fun toggleDarkMode() = update { s ->
        val nextDark = !s.darkMode
        if (nextDark) LightThemeController.setDarkTheme() else LightThemeController.setLightTheme()
        s.copy(darkMode = nextDark)
    }

    private fun update(transform: (PuzzleSettings) -> PuzzleSettings) {
        viewModelScope.launch {
            val updated = transform(_settings.value)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }
}

class SettingsScreen(
    private val sealedActivity: SealedLightActivity,
) : LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    private val repository by lazy { PuzzleRepository.getInstance(lightContext) }
    private val isForegroundFlow = MutableStateFlow(true)

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel(): SettingsViewModel = SettingsViewModel(repository)

    override fun willShow() {
        super.willShow()
        isForegroundFlow.value = true
    }

    override fun willHide() {
        isForegroundFlow.value = false
        super.willHide()
    }

    override fun onAppPause() {
        isForegroundFlow.value = false
        super.onAppPause()
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val settings by viewModel.settings.collectAsState()
        val isForeground by isForegroundFlow.collectAsState()
        val lockedToday = settings.lockedUntilDateIso == viewModel.todayIso

        LightTheme(colors = themeColors) {
            val content = LightThemeTokens.colors.content
            val bg = LightThemeTokens.colors.background
            val sidePad = 1.5f.gridUnitsAsDp()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .swipeToBack { goBack() },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    BatteryTopBar(
                        isForeground = isForeground,
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                        ),
                        center = LightTopBarCenter.Text("Settings"),
                        rightButton = null,
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = sidePad),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = sidePad, top = 8.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Daily Lockout Schedule
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Daily Unlock Window",
                                            color = content,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "Lock puzzles outside chosen hours",
                                            color = content.copy(alpha = 0.65f),
                                            fontSize = 12.sp,
                                        )
                                    }
                                    SettingsPill(
                                        label = if (settings.lockoutScheduleEnabled) "ON" else "OFF",
                                        active = settings.lockoutScheduleEnabled,
                                        onClick = { viewModel.toggleSchedule() },
                                    )
                                }

                                if (settings.lockoutScheduleEnabled) {
                                    TimeStepperRow(
                                        label = "Opens",
                                        value = LockoutPolicy.formatTime(
                                            settings.unlockStartHour,
                                            settings.unlockStartMinute,
                                        ),
                                        onMinus = { viewModel.adjustStart(-30) },
                                        onPlus = { viewModel.adjustStart(30) },
                                    )
                                    TimeStepperRow(
                                        label = "Locks",
                                        value = LockoutPolicy.formatTime(
                                            settings.unlockEndHour,
                                            settings.unlockEndMinute,
                                        ),
                                        onMinus = { viewModel.adjustEnd(-30) },
                                        onPlus = { viewModel.adjustEnd(30) },
                                    )
                                }
                            }

                            // Manual Lock Until Tomorrow
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .lightClickable { viewModel.toggleLockToday() }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lockedToday) "Locked Until Tomorrow" else "Lock Until Tomorrow",
                                        color = content,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = if (lockedToday) {
                                            "Tap to unlock today\u2019s puzzles"
                                        } else {
                                            "Step away now; fresh set tomorrow"
                                        },
                                        color = content.copy(alpha = 0.65f),
                                        fontSize = 12.sp,
                                    )
                                }
                                SettingsPill(
                                    label = if (lockedToday) "Locked" else "Lock",
                                    active = lockedToday,
                                    onClick = { viewModel.toggleLockToday() },
                                )
                            }

                            // OLED Dark Mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .lightClickable { viewModel.toggleDarkMode() }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "OLED Dark Mode",
                                        color = content,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Pure-black screen saves battery",
                                        color = content.copy(alpha = 0.65f),
                                        fontSize = 12.sp,
                                    )
                                }
                                SettingsPill(
                                    label = if (settings.darkMode) "Dark" else "Light",
                                    active = settings.darkMode,
                                    onClick = { viewModel.toggleDarkMode() },
                                )
                            }

                            // Offline & Privacy Note
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, content.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "100% Offline & Private",
                                    color = content,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Zero internet and zero permissions. Everyone gets the exact same daily puzzles based on today\u2019s date.",
                                    color = content.copy(alpha = 0.68f),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background
    Box(
        modifier = Modifier
            .background(if (active) content else bg, RoundedCornerShape(6.dp))
            .border(1.dp, content.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .lightClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) bg else content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TimeStepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = content.copy(alpha = 0.8f),
            fontSize = 14.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .border(1.dp, content.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .lightClickable(onClick = onMinus)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(text = "\u221230m", color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = value,
                color = content,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .border(1.dp, content.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .lightClickable(onClick = onPlus)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(text = "+30m", color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
