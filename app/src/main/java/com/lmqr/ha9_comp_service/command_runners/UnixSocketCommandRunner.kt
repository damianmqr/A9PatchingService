package com.lmqr.ha9_comp_service.command_runners

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class UnixDomainSocketClient(private val socketName: String) {
    private var socket: LocalSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    fun connect() {
        try {
            socket = LocalSocket().apply {
                connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            }
            outputStream = socket?.outputStream
            inputStream = socket?.inputStream
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendCommand(command: String) {
        if (socket?.isConnected != true) {
            disconnect()
            connect()
        }
        try {
            outputStream?.write((command + "\n").toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            connect()
            try {
                outputStream?.write((command + "\n").toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
            inputStream = null
            outputStream = null
            socket = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

class UnixSocketCommandRunner : CommandRunner {
    private var unixDomainSocketClient: UnixDomainSocketClient? = null
    override fun runCommands(cmds: Array<String>) {
        if (unixDomainSocketClient == null)
            unixDomainSocketClient = UnixDomainSocketClient("a9_eink_socket").apply {
                connect()
            }

        unixDomainSocketClient?.run {
            for (cmd in cmds)
                sendCommand(cmd)
        }
    }

    override fun onDestroy() {
        unixDomainSocketClient?.disconnect()
    }

}