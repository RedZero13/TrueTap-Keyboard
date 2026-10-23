package com.truetap.keyboard.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.truetap.keyboard.settings.CalibrationActivity

/**
 * Debug builds only. Lets the screenshot script change settings and screens:
 *   am broadcast -n com.truetap.keyboard/.demo.DemoReceiver --es mode floating --es reset 1
 * Every string extra is written to the keyboard's preferences ("true"/"false" as booleans,
 * digits as ints); "launch"=calibration opens that screen, "reset" recreates the chat screen.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val edit = context.getSharedPreferences("truetap", Context.MODE_PRIVATE).edit()
        for (key in extras.keySet()) {
            if (key == "launch" || key == "reset") continue
            val v = extras.getString(key) ?: continue
            when {
                v == "true" || v == "false" -> edit.putBoolean(key, v.toBoolean())
                v.toIntOrNull() != null -> edit.putInt(key, v.toInt())
                else -> edit.putString(key, v)
            }
        }
        edit.commit()
        when (extras.getString("launch")) {
            "calibration" -> context.startActivity(Intent(context, CalibrationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "demo" -> context.startActivity(Intent(context, DemoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        if (extras.containsKey("reset")) DemoActivity.current?.recreate()
    }
}
