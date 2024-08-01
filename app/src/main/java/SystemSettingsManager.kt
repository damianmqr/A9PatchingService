import android.content.Context
import android.provider.Settings

object SystemSettingsManager {
    private fun forceUpdate(context: Context){
        val brightness = getBrightnessFromSetting(context)
        if (brightness <= 0)
            return

        if (brightness == 255 || brightness and 1 == 0) {
            setBrightnessSetting(context, brightness - 1)
        } else {
            setBrightnessSetting(context, brightness + 1)
        }
        val uri = Settings.System
            .getUriFor(Settings.System.SCREEN_BRIGHTNESS)
        context.applicationContext.contentResolver.notifyChange(uri, null)
    }

    fun setNightLightMode(context: Context, nightLightMode: NightLightMode) {
        try {
            val contentResolver = context.applicationContext.contentResolver
            Settings.Secure.putInt(
                contentResolver,
                "night_display_activated",
                if (nightLightMode == NightLightMode.Disabled) 0 else 1
            )
            Settings.Secure.putInt(
                contentResolver,
                "night_display_auto_mode",
                if (nightLightMode != NightLightMode.Auto) 0 else 1
            )
           forceUpdate(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleNightLightMode(context: Context) {
        val contentResolver = context.applicationContext.contentResolver
        try{
            val currentSetting = Settings.Secure.getInt(
                contentResolver,
                "night_display_activated",
                0
            )
            Settings.Secure.putInt(
                contentResolver,
                "night_display_activated",
                currentSetting xor 1
            )
            forceUpdate(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getNightLightMode(context: Context): NightLightMode {
        val contentResolver = context.applicationContext.contentResolver
        return try {
            when {
                Settings.Secure.getInt(
                    contentResolver,
                    "night_display_activated",
                    0
                ) == 0 -> NightLightMode.Disabled
                Settings.Secure.getInt(
                    contentResolver,
                    "night_display_auto_mode",
                    0
                ) == 0 -> NightLightMode.Manual
                else -> NightLightMode.Auto
            }
        } catch (e: Exception) {
            NightLightMode.Disabled
        }
    }

    fun setNextNightLightMode(context: Context): NightLightMode {
        val nextMode = when(getNightLightMode(context)){
            NightLightMode.Manual -> NightLightMode.Auto
            NightLightMode.Auto -> NightLightMode.Disabled
            else -> NightLightMode.Manual
        }
        setNightLightMode(context, nextMode)
        return nextMode
    }

    fun getBrightnessFromSetting(context: Context): Int {
        val contentResolver = context.applicationContext.contentResolver
        return Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            255
        )
    }

    fun setBrightnessSetting(context: Context, value: Int) {
        try {
            val contentResolver = context.applicationContext.contentResolver
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    enum class NightLightMode {
        Manual,
        Auto,
        Disabled
    }
}