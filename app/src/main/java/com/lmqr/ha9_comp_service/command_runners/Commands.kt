package com.lmqr.ha9_comp_service.command_runners

object Commands {
    fun YELLOW_BRIGHTNESS(v: Int) = "sb1${v}"
    fun WHITE_BRIGHTNESS(v: Int) = "sb2${v}"
    fun YELLOW_BRIGHTNESS_ALT(v: Int) = "sa1${v}"
    fun WHITE_BRIGHTNESS_ALT(v: Int) = "sa2${v}"

    fun BLACK_THRESHOLD(v: Int) = "stb${v}"
    fun WHITE_THRESHOLD(v: Int) = "stw${v}"
    fun CONTRAST(v: Int) = "sco${v}"

    const val FORCE_CLEAR = "r"
    const val COMMIT_BITMAP = "cm"
    const val UNLOCK_WHITE = "un"
    const val LOCK_WHITE = "bl"
    const val UNLOCK_YELLOW = "un1"
    const val LOCK_YELLOW = "bl1"
    const val SPEED_CLEAR = "c"
    const val SPEED_BALANCED = "b"
    const val SPEED_SMOOTH = "s"
    const val SPEED_FAST = "p"
}