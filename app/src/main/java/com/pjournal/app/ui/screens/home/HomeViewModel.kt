package com.pjournal.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pjournal.app.PJournalApp
import com.pjournal.app.data.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HomeState(
    val weekDates: List<WeekDay> = emptyList(),
    val streak: Int = 0,
    val totalEntries: Int = 0,
    val todayCount: Int = 0,
    val syncMessage: String? = null
)

data class WeekDay(
    val label: String,
    val dateKey: String,
    val isToday: Boolean,
    val hasEntry: Boolean,
    val isFuture: Boolean
)

class HomeViewModel : ViewModel() {
    private val repository = JournalRepository(
        PJournalApp.instance.database.journalEntryDao()
    )

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val today = cal.time
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)

            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            cal.add(Calendar.DAY_OF_MONTH, -(dayOfWeek - Calendar.MONDAY))
            if (dayOfWeek == Calendar.SUNDAY) cal.add(Calendar.DAY_OF_MONTH, -7)

            val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            val dateKeys = (0..6).map {
                val d = cal.clone() as Calendar
                d.add(Calendar.DAY_OF_MONTH, it)
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d.time)
            }

            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)

            val weekDays = dateKeys.mapIndexed { i, dk ->
                WeekDay(
                    label = dayLabels[i],
                    dateKey = dk,
                    isToday = dk == todayDate,
                    hasEntry = repository.countByDate(dk) > 0,
                    isFuture = dk > todayDate
                )
            }

            _state.value = HomeState(
                weekDates = weekDays,
                streak = repository.getStreak(),
                totalEntries = repository.getTotalCount(),
                todayCount = repository.countByDate(todayDate)
            )
        }
    }

    fun syncWebDav() {
        viewModelScope.launch {
            _state.value = _state.value.copy(syncMessage = "同步中...")
            try {
                val result = PJournalApp.instance.syncManager.performSync()
                _state.value = _state.value.copy(syncMessage = result.toMessage())
            } catch (e: Exception) {
                _state.value = _state.value.copy(syncMessage = "同步失败: ${e.message}")
            }
        }
    }

    fun clearSyncMessage() {
        _state.value = _state.value.copy(syncMessage = null)
    }
}

class HomeViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel() as T
    }
}
