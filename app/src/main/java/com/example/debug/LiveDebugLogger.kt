package com.example.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object LiveDebugLogger {
    private const val MAX_LOGS = 150
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val timestamp: String,
        val message: String,
        val level: LogLevel
    )

    enum class LogLevel {
        INFO, SUCCESS, WARN, ERROR, DATA
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _wsStatus = MutableStateFlow("Disconnected")
    val wsStatus: StateFlow<String> = _wsStatus.asStateFlow()

    fun setWsStatus(status: String) {
        _wsStatus.value = status
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            message = message,
            level = level
        )
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllLogsText(): String {
        return _logs.value.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
    }
}
