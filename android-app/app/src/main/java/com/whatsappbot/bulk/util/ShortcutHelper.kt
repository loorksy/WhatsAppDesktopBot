package com.whatsappbot.bulk.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.ui.QuickBubbleActivity

object ShortcutHelper {
    const val SHORTCUT_ID = "quick_bubble"

    fun quickIntent(context: Context): Intent {
        return Intent(context, QuickBubbleActivity::class.java).apply {
            action = QuickBubbleActivity.ACTION_QUICK_BUBBLE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    fun ensureDynamicShortcut(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_bubble_short))
            .setLongLabel(context.getString(R.string.shortcut_bubble_long))
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(quickIntent(context))
            .build()
        runCatching { sm.addDynamicShortcuts(listOf(shortcut)) }
    }

    fun requestPinShortcut(context: Context): Boolean {
        ensureDynamicShortcut(context)
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_LONG).show()
            return false
        }
        val info = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_bubble_short))
            .setLongLabel(context.getString(R.string.shortcut_bubble_long))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(quickIntent(context))
            .build()
        val ok = ShortcutManagerCompat.requestPinShortcut(context, info, null)
        if (ok) {
            Toast.makeText(context, R.string.shortcut_pin_requested, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_LONG).show()
        }
        return ok
    }
}
