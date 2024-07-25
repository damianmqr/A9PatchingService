package com.lmqr.ha9_comp_service.command_runners

object Commands {
    fun BLACK_THRESHOLD(v: Int) = "stb${v}"
    fun WHITE_THRESHOLD(v: Int) = "stw${v}"
    fun CONTRAST(v: Int) = "sco${v}"

    const val FORCE_CLEAR = "r"
    const val COMMIT_BITMAP = "cm"
    const val SPEED_CLEAR = "c"
    const val SPEED_BALANCED = "b"
    const val SPEED_SMOOTH = "s"
    const val SPEED_FAST = "p"
}