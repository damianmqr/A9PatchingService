package com.lmqr.ha9_comp_service

import java.io.DataOutputStream

class RootCommandRunner {
    private var process: Process? = null

    private fun endProcess(){
        process?.run {
            DataOutputStream(outputStream).run {
                writeBytes("exit\n")
                flush()
            }
            destroy()
        }
        process = null
    }

    fun runAsRoot(cmds: Array<String>, forceNewProcess: Boolean = false) {
        if(forceNewProcess)
            endProcess()

        if(process?.isAlive != true)
            process = Runtime.getRuntime().exec("su")

        process?.run {
            DataOutputStream(outputStream).run {
                for (tmpCmd in cmds) {
                    writeBytes(tmpCmd + "\n")
                }
                flush()
            }
        }
    }

    fun onDestroy() {
        endProcess()
    }
}