package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.task.TranslationListTask;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.List;

public class UpgradeTranslationListener implements TranslationListTask.TranslationsUpdatedListener {
  private static final String TAG = UpgradeTranslationListener.class.getSimpleName();

  @NonNull private final Context mAppContext;

  public UpgradeTranslationListener(@NonNull Context context) {
    mAppContext = context.getApplicationContext();
  }

  @Override
  public void translationsUpdated(List<TranslationItem> items) {
    if (items == null){ return; }

    boolean needsUpgrade = false;
    for (TranslationItem item : items){
      if (item.exists && item.localVersion != null &&
          item.latestVersion > 0 &&
          item.latestVersion > item.localVersion){
        needsUpgrade = true;
        break;
      }
    }

    Log.d(TAG, "done checking translations - " +
        (needsUpgrade ? "" : "no ") + "upgrade needed");
    PreferenceManager.getDefaultSharedPreferences(mAppContext).edit().putBoolean(
        Constants.PREF_HAVE_UPDATED_TRANSLATIONS, needsUpgrade).apply();
  }
}
