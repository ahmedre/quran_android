package com.quran.labs.androidquran

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.quran.labs.androidquran.ui.QuranActivity

class SearchWidget : AppWidgetProvider() {

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    // There may be multiple widgets active, so update all of them
    for (appWidgetId in appWidgetIds) {
      val widget = RemoteViews(context.packageName, R.layout.search_widget)

      var intent = Intent(context, QuranDataActivity::class.java)
      var pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
      widget.setOnClickPendingIntent(R.id.search_widget_icon_button, pendingIntent)

      intent = Intent(context, SearchActivity::class.java)
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
      widget.setOnClickPendingIntent(R.id.search_widget_btn_search, pendingIntent)

      intent = Intent(context, QuranActivity::class.java)
      intent.action = ShortcutsActivity.ACTION_JUMP_TO_LATEST
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
      widget.setOnClickPendingIntent(R.id.search_widget_btn_go_to_quran, pendingIntent)

      appWidgetManager.updateAppWidget(appWidgetId, widget)
    }
    super.onUpdate(context, appWidgetManager, appWidgetIds)
  }
}