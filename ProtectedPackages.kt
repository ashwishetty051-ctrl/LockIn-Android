package com.lockin.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Keep the user's default calling app and core system controls reachable, even if
 * a previously saved selection contains one of these package names.
 */
object ProtectedPackages {
    fun forDevice(context: Context): Set<String> {
        val protected = mutableSetOf(
            context.packageName,
            "com.android.settings",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )
        resolvePackage(context, Intent(Intent.ACTION_DIAL))?.let(protected::add)
        resolvePackage(context, Intent(SettingsAction.SETTINGS))?.let(protected::add)
        return protected
    }

    private fun resolvePackage(context: Context, intent: Intent): String? =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    private object SettingsAction {
        const val SETTINGS = "android.settings.SETTINGS"
    }
}
