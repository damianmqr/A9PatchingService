package com.lmqr.ha9_comp_service

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

fun getBackgroundFileImage(ctx: Context): File {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        val dir = File(Environment.getExternalStoragePublicDirectory("/"), "a9_comp_service")
        if (!dir.exists())
            dir.mkdirs()

        return File(dir, "bgimage")
    }
    return File(ctx.filesDir, "bgimage")
}