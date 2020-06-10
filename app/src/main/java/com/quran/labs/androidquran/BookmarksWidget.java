package com.quran.labs.androidquran;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.widgets.WidgetService;

public class BookmarksWidget extends AppWidgetProvider {

  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    final int numWidgets = appWidgetIds.length;
    for (int i = 0; i < numWidgets; ++i) {
      Intent serviceIntent = new Intent(context, WidgetService.class);
      serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
      serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));

      RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.bookmarks_widget);

      Intent intent = new Intent(context, QuranDataActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.widget_icon_button, pendingIntent);

      intent = new Intent(context, SearchActivity.class);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.widget_btn_search, pendingIntent);

      intent = new Intent(context, QuranActivity.class);
      intent.setAction(ShortcutsActivity.ACTION_JUMP_TO_LATEST);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.widget_btn_go_to_quran, pendingIntent);

      widget.setRemoteAdapter(R.id.list_view_widget, serviceIntent);
      Intent clickIntent = new Intent(context, PagerActivity.class);
      PendingIntent clickPendingIntent = PendingIntent
          .getActivity(context, 0,
              clickIntent,
              PendingIntent.FLAG_UPDATE_CURRENT);
      widget.setPendingIntentTemplate(R.id.list_view_widget, clickPendingIntent);
      widget.setEmptyView(R.id.list_view_widget, R.id.empty_view);

      appWidgetManager.updateAppWidget(appWidgetIds[i], widget);
    }
    super.onUpdate(context, appWidgetManager, appWidgetIds);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);
    updateWidget(context);
  }

  public static void updateWidget(Context context) {
    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
    int appWidgetIds[] = appWidgetManager.getAppWidgetIds(new ComponentName(context, BookmarksWidget.class));
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.list_view_widget);
  }
}