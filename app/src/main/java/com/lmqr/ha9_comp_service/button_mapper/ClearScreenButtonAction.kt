package com.lmqr.ha9_comp_service.button_mapper

import android.content.Context
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands

class ClearScreenButtonAction(private val commandRunner: CommandRunner) : ButtonAction {
    override fun execute(context: Context) {
        commandRunner.runCommands(arrayOf(Commands.FORCE_CLEAR))
    }

}