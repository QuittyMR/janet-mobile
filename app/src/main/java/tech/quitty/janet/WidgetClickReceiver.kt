package tech.quitty.janet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class WidgetClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == JanetWidgetProvider.ACTION_RECORD) {
            val serviceIntent = Intent(context, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mainActivityIntent)
        }
    }
}
