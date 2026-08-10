package com.remotex.feature.ssh.terminal

class TerminalCommandHistory(
    private val maxEntries: Int = 100,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val items = mutableListOf<String>()

    val entries: List<String>
        get() = items.toList()

    fun record(command: String) {
        val normalized = command.trim()
        if (normalized.isEmpty()) return
        items.remove(normalized)
        items.add(0, normalized)
        while (items.size > maxEntries) items.removeAt(items.lastIndex)
    }

    fun clear() {
        items.clear()
    }
}
