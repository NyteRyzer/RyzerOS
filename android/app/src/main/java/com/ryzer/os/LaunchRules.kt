package com.ryzer.os

/** How Ryzer hands games to emulators (kept free of Android so it can be unit tested). */
object LaunchRules {
    /**
     * Every launch starts the emulator fresh (a new task, cleared of whatever it had open) unless its
     * emulators.json entry says "keep_task". Without this, picking a second game while the emulator is
     * still open in the background just brings the first game back.
     */
    fun startsFresh(emuFlags: List<String>): Boolean = "keep_task" !in emuFlags
}
