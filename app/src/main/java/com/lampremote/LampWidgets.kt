package com.lampremote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

private fun broadcast(ctx: Context, cls: Class<*>, action: String, requestCode: Int): PendingIntent =
    PendingIntent.getBroadcast(
        ctx, requestCode,
        Intent(ctx, cls).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/** Widget 1: Lamp ON / Lamp OFF buttons. */
class LampWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_lamp).apply {
            setOnClickPendingIntent(R.id.w_on, broadcast(context, LampActionReceiver::class.java, LampActionReceiver.ACTION_ON, 1))
            setOnClickPendingIntent(R.id.w_off, broadcast(context, LampActionReceiver::class.java, LampActionReceiver.ACTION_OFF, 2))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Widget 2: pick 1..10 minutes with - / + then tap the timer button to make the lamp switch itself off. */
class TimerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_MINUS = "com.lampremote.TIMER_MINUS"
        const val ACTION_PLUS = "com.lampremote.TIMER_PLUS"
        private const val PREFS = "lamp_widget"
        private const val KEY = "timer_minutes"
        const val MIN = 1
        const val MAX = 10

        fun minutes(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 5).coerceIn(MIN, MAX)

        private fun build(ctx: Context): RemoteViews {
            val m = minutes(ctx)
            return RemoteViews(ctx.packageName, R.layout.widget_timer).apply {
                setTextViewText(R.id.t_value, "$m min")
                setOnClickPendingIntent(R.id.t_minus, broadcast(ctx, TimerWidgetProvider::class.java, ACTION_MINUS, 10))
                setOnClickPendingIntent(R.id.t_plus, broadcast(ctx, TimerWidgetProvider::class.java, ACTION_PLUS, 11))
                setOnClickPendingIntent(R.id.t_start, broadcast(ctx, LampActionReceiver::class.java, LampActionReceiver.ACTION_TIMER, 12))
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val delta = when (intent.action) {
            ACTION_MINUS -> -1
            ACTION_PLUS -> 1
            else -> { super.onReceive(context, intent); return }
        }
        // wrap around: 10 -> 1 and 1 -> 10
        val next = (minutes(context) - MIN + delta + (MAX - MIN + 1)) % (MAX - MIN + 1) + MIN
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, next).apply()
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
        onUpdate(context, mgr, ids)
    }
}
