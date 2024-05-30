package com.lmqr.ha9_comp_service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import java.io.FileOutputStream


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        checkPermissions()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    activity?.run {
                        val file = File(filesDir, "bg_image")
                        contentResolver.openInputStream(it)?.run {
                            FileOutputStream(file).use { fileOutputStream ->
                                copyTo(fileOutputStream)
                            }
                            close()
                            onUpdatedImage()
                            Toast.makeText(
                                requireContext(),
                                "Background image updated successfully.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

        private fun onUpdatedImage() {
            preferenceManager.sharedPreferences?.run {
                edit().putBoolean(
                    "aod_image_updated", !getBoolean(
                        "aod_image_updated", false
                    )
                ).apply()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            (findPreference("grant_notif_perms") as Preference?)?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    requireContext().startActivity(intent)
                    true
                }


            (findPreference("select_aod_bg") as Preference?)?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    imagePickerLauncher.launch("image/*")
                    true
                }


            (findPreference("remove_aod_bg") as Preference?)?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val file = File(requireContext().filesDir, "bg_image")
                    if (file.exists()) {
                        if (file.delete()) {
                            Toast.makeText(
                                requireContext(),
                                "Background image removed successfully.",
                                Toast.LENGTH_SHORT
                            ).show()
                            onUpdatedImage()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Failed to remove background image.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "No background image to remove.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
        }
    }

    private fun checkPermissions(){
        if(!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName.equals(packageName))
                return true
        }
        return false
    }
}