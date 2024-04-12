package com.lmqr.ha9_comp_service.command_runners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FIFOCommandRunner(
    filesDir: String
) : CommandRunner {
    private val job = Job()
    private val coroutinesScope: CoroutineScope = CoroutineScope(job + Dispatchers.IO)

    private val fifo = File(filesDir + File.separator + "refresh_screen_fifo")
    private var ostream: FileOutputStream? = null

    override fun runCommands(cmds: Array<String>) {
        coroutinesScope.launch {
            try {
                if (ostream == null)
                    ostream = FileOutputStream(fifo)

                ostream?.run {
                    for (tmpCmd in cmds)
                        write((tmpCmd + "\n").toByteArray())
                    flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    ostream?.close()
                    ostream = FileOutputStream(fifo)
                    ostream?.run {
                        for (tmpCmd in cmds)
                            write((tmpCmd + "\n").toByteArray())
                        flush()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        job.complete()
        ostream?.close()
    }
}