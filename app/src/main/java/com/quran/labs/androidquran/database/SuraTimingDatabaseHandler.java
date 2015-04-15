package com.quran.labs.androidquran.database;

import com.crashlytics.android.Crashlytics;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SuraTimingDatabaseHandler {
  private SQLiteDatabase mDatabase = null;

  public static class TimingsTable {
    public static final String TABLE_NAME = "timings";
    public static final String COL_SURA = "sura";
    public static final String COL_AYAH = "ayah";
    public static final String COL_TIME = "time";
  }

  private static final Object sDatabaseLock = new Object();
  private static Map<String, SuraTimingDatabaseHandler> sSuraDatabaseMap = new HashMap<>();

  public static SuraTimingDatabaseHandler getDatabaseHandler(String path) {
    synchronized (sDatabaseLock) {
      SuraTimingDatabaseHandler handler = sSuraDatabaseMap.get(path);
      if (handler == null) {
        handler = new SuraTimingDatabaseHandler(path);
        sSuraDatabaseMap.put(path, handler);
      }
      return handler;
    }
  }

  private SuraTimingDatabaseHandler(String path) throws SQLException {
    Crashlytics.log("opening gapless data file, " + path);
    try {
      mDatabase = SQLiteDatabase.openDatabase(path, null,
          SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    } catch (SQLException se) {
      Crashlytics.log("database at " + path +
          (new File(path).exists() ? " exists" : " doesn't exist"));
      Crashlytics.logException(se);
      mDatabase = null;
    }
  }

  public boolean validDatabase() {
    return (mDatabase != null) && mDatabase.isOpen();
  }

  public Cursor getAyahTimings(int sura) {
    if (!validDatabase()) return null;
    try {
      return mDatabase.query(TimingsTable.TABLE_NAME,
          new String[]{TimingsTable.COL_SURA,
              TimingsTable.COL_AYAH, TimingsTable.COL_TIME},
          TimingsTable.COL_SURA + "=" + sura,
          null, null, null, TimingsTable.COL_AYAH + " ASC");
    } catch (Exception e) {
      return null;
    }
  }
}
