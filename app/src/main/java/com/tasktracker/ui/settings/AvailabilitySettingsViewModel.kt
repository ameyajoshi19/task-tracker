package com.tasktracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.domain.model.AvailabilitySlot
import com.tasktracker.domain.model.AvailabilitySlotType
import com.tasktracker.domain.repository.AvailabilitySlotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class AvailabilitySettingsUiState(
    val slots: Map<AvailabilitySlotType, List<AvailabilitySlot>> = emptyMap(),
    val expandedSlotType: AvailabilitySlotType? = null,
)

@HiltViewModel
class AvailabilitySettingsViewModel @Inject constructor(
    private val repository: AvailabilitySlotRepository,
) : ViewModel() {

    private val expandedSlotType = MutableStateFlow<AvailabilitySlotType?>(null)

    val uiState: StateFlow<AvailabilitySettingsUiState> = combine(
        repository.observeAll(),
        expandedSlotType,
    ) { slots, expanded ->
        AvailabilitySettingsUiState(
            slots = slots.groupBy { it.slotType },
            expandedSlotType = expanded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AvailabilitySettingsUiState(),
    )

    fun toggleExpanded(slotType: AvailabilitySlotType) {
        expandedSlotType.update { current ->
            if (current == slotType) null else slotType
        }
    }

    fun updateSlot(slot: AvailabilitySlot) {
        viewModelScope.launch {
            repository.update(slot)
        }
    }

    fun copyToAllDays(slotType: AvailabilitySlotType, sourceDay: DayOfWeek) {
        viewModelScope.launch {
            val slotsForType = repository.getBySlotType(slotType)
            val source = slotsForType.find { it.dayOfWeek == sourceDay } ?: return@launch
            for (slot in slotsForType) {
                if (slot.dayOfWeek != sourceDay) {
                    repository.update(
                        slot.copy(
                            startTime = source.startTime,
                            endTime = source.endTime,
                            enabled = source.enabled,
                        )
                    )
                }
            }
        }
    }
}
