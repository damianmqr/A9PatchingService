package com.lmqr.ha9_comp_service.command_runners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import kotlin.math.max
import kotlin.math.min

class RootCommandRunner : CommandRunner {
    private val job = Job()
    private val coroutinesScope: CoroutineScope = CoroutineScope(job + Dispatchers.IO)

    private var process: Process? = null

    override fun runCommands(cmds: Array<String>) {
        coroutinesScope.launch {
            if (process?.isAlive != true)
                process = Runtime.getRuntime().exec("su")

            process?.run {
                DataOutputStream(outputStream).run {
                    for (tmpCmd in cmds) {
                        when (tmpCmd) {
                            "setup" -> arrayOf(
                                "service call SurfaceFlinger 1008 i32 1",
                                "settings put global window_animation_scale 0",
                                "settings put global transition_animation_scale 0",
                                "settings put global animator_duration_scale 0",
                            )

                            "r" -> arrayOf(
                                "echo 1 > \"/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_force_clear\""
                            )

                            "c" -> arrayOf(
                                "echo 515 > \"/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode\""
                            )

                            "b" -> arrayOf(
                                "echo 513 > \"/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode\""
                            )

                            "s" -> arrayOf(
                                "echo 518 > \"/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode\""
                            )

                            "p" -> arrayOf(
                                "echo 521 > \"/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode\""
                            )

                            "bl" -> arrayOf(
                                "echo 0 > \"/sys/class/leds/aw99703-bl-2/brightness\"",
                                "chmod 444 \"/sys/class/leds/aw99703-bl-2/brightness\"",
                            )

                            "un" -> arrayOf(
                                "chmod 644 \"/sys/class/leds/aw99703-bl-2/brightness\""
                            )

                            "bl1" -> arrayOf(
                                "echo 0 > \"/sys/class/leds/aw99703-bl-1/brightness\"",
                                "chmod 444 \"/sys/class/leds/aw99703-bl-1/brightness\"",
                            )

                            "un1" -> arrayOf(
                                "chmod 644 \"/sys/class/leds/aw99703-bl-1/brightness\""
                            )

                            else -> try {
                                if (tmpCmd.startsWith("sb1")) {
                                    val v = tmpCmd.substring(3).toInt()
                                    arrayOf(
                                        "echo \"${
                                            max(
                                                min(v, 2200),
                                                0
                                            )
                                        }\" > \"/sys/class/leds/aw99703-bl-1/brightness\""
                                    )
                                } else if (tmpCmd.startsWith("sb2")) {
                                    val v = tmpCmd.substring(3).toInt()
                                    arrayOf(
                                        "echo \"${
                                            max(
                                                min(v, 2200),
                                                0
                                            )
                                        }\" > \"/sys/class/leds/aw99703-bl-2/brightness\""
                                    )
                                } else {
                                    null
                                }
                            } catch (ex: NumberFormatException) {
                                ex.printStackTrace()
                                null
                            }
                        }?.forEach { cmd ->
                            writeBytes(cmd + "\n")
                        }
                    }
                    flush()
                }
            }
        }
    }

    override fun onDestroy() {
        process?.run {
            DataOutputStream(outputStream).run {
                writeBytes("exit\n")
                flush()
            }
            destroy()
        }
        process = null
    }
}