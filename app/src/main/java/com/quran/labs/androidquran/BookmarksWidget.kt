package com.quran.labs.androidquran

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.widgets.WidgetService
import javax.inject.Inject

class BookmarksWidget : AppWidgetProvider() {

  @Inject
  lateinit var bookmarkModel: BookmarkModel

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    for (appWidgetId in appWidgetIds) {
      val serviceIntent = Intent(context, WidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
      }

      val widget = RemoteViews(context.packageName, R.layout.bookmarks_widget)

      var intent = Intent(context, QuranDataActivity::class.java)
      var pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
      widget.setOnClickPendingIntent(R.id.widget_icon_button, pendingIntent)

      intent = Intent(context, SearchActivity::class.java)
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
      widget.setOnClickPendingIntent(R.id.widget_btn_search, pendingIntent)

      intent = Intent(context, QuranActivity::class.java)
      intent.action = ShortcutsActivity.ACTION_JUMP_TO_LATEST
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
      widget.setOnClickPendingIntent(R.id.widget_btn_go_to_quran, pendingIntent)

      widget.setRemoteAdapter(R.id.list_view_widget, serviceIntent)
      val clickIntent = Intent(context, PagerActivity::class.java)
      val clickPendingIntent = PendingIntent
          .getActivity(context, 0,
              clickIntent,
              PendingIntent.FLAG_UPDATE_CURRENT)
      widget.setPendingIntentTemplate(R.id.list_view_widget, clickPendingIntent)
      widget.setEmptyView(R.id.list_view_widget, R.id.empty_view)

      appWidgetManager.updateAppWidget(appWidgetId, widget)
    }
    super.onUpdate(context, appWidgetManager, appWidgetIds)
  }

  override fun onEnabled(context: Context) {
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    bookmarkModel.onEnabledBookmarksWidget()
    super.onEnabled(context)
  }

  override fun onDisabled(context: Context) {
    bookmarkModel.onDisabledBookmarksWidget()
    super.onDisabled(context)
  }
}
