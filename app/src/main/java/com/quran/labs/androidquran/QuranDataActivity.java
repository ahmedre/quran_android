package com.quran.labs.androidquran;

import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import java.io.File;

public class QuranDataActivity extends Activity implements
        DefaultDownloadReceiver.SimpleDownloadListener {

   public static final String TAG = "QuranDataActivity";
   public static final String PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY";

   private boolean mIsPaused = false;
   private AsyncTask<Void, Void, Boolean> mCheckPagesTask;
   private AlertDialog mErrorDialog = null;
   private AlertDialog mPromptForDownloadDialog = null;
   private SharedPreferences mSharedPreferences = null;
   private DefaultDownloadReceiver mDownloadReceiver = null;
   private boolean mNeedPortraitImages = false;
   private boolean mNeedLandscapeImages = false;
   private String mPatchUrl;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      /*
        // remove files for debugging purposes
        QuranUtils.debugRmDir(QuranUtils.getQuranBaseDirectory(), false);
        QuranUtils.debugLsDir(QuranUtils.getQuranBaseDirectory());
       */

      initializeQuranScreen();
      mSharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());

      // one time upgrade to v2.4.3
      if (!mSharedPreferences.contains(Constants.PREF_UPGRADE_TO_243)){
         String baseDir = QuranFileUtils.getQuranBaseDirectory(this);
         if (baseDir != null){
            baseDir = baseDir + File.separator;
            try {
               File f = new File(baseDir);
               if (f.exists() && f.isDirectory()){
                  String[] files = f.list();
                  if (files != null){
                     for (String file : files){
                        if (file.endsWith(".part")){
                           try {
                              new File(baseDir + file).delete();
                           }
                           catch (Exception e){}
                        }
                     }
                  }
               }
            }
            catch (Exception e){
            }
         }

         // update night mode preference and mark that we upgraded to 2.4.2ts
         mSharedPreferences.edit()
                 .putInt(Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS,
                         Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS)
                 .remove(Constants.PREF_UPGRADE_TO_242)
                 .putBoolean(Constants.PREF_UPGRADE_TO_243, true).apply();
      }
   }

   @Override
   protected void onResume(){
      super.onResume();

      mIsPaused = false;
      mDownloadReceiver = new DefaultDownloadReceiver(this,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES);
      mDownloadReceiver.setCanCancelDownload(true);
      String action = QuranDownloadNotifier.ProgressIntent.INTENT_NAME;
      LocalBroadcastManager.getInstance(this).registerReceiver(
            mDownloadReceiver,
            new IntentFilter(action));
      mDownloadReceiver.setListener(this);

      // check whether or not we need to download
      mCheckPagesTask = new CheckPagesAsyncTask(this);
      mCheckPagesTask.execute();
   }
   
   @Override
   protected void onPause() {
      mIsPaused = true;
      if (mDownloadReceiver != null) {
        mDownloadReceiver.setListener(null);
        LocalBroadcastManager.getInstance(this).
            unregisterReceiver(mDownloadReceiver);
        mDownloadReceiver = null;
      }

      if (mPromptForDownloadDialog != null){
         mPromptForDownloadDialog.dismiss();
         mPromptForDownloadDialog = null;
      }
      
      if (mErrorDialog != null){
         mErrorDialog.dismiss();
         mErrorDialog = null;
      }
      
      super.onPause();
   }

   @Override
   public void handleDownloadSuccess(){
      mSharedPreferences.edit()
         .remove(Constants.PREF_SHOULD_FETCH_PAGES).apply();
      runListView();
   }

  @Override
   public void handleDownloadFailure(int errId){
      if (mErrorDialog != null && mErrorDialog.isShowing()){
         return;
      }
      
      showFatalErrorDialog(errId);
   }
   
   private void showFatalErrorDialog(int errorId){
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(errorId);
      builder.setCancelable(false);
      builder.setPositiveButton(R.string.download_retry,
            new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mErrorDialog = null;
            removeErrorPreferences();
            downloadQuranImages(true);
         }
      });
      
      builder.setNegativeButton(R.string.download_cancel,
            new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            mErrorDialog = null;
            removeErrorPreferences();
            mSharedPreferences.edit().putBoolean(
                    Constants.PREF_SHOULD_FETCH_PAGES, false)
                    .apply();
            runListView();
         }
      });
      
      mErrorDialog = builder.create();
      mErrorDialog.show();
   }

   private void removeErrorPreferences(){
      mSharedPreferences.edit()
      .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR)
      .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM)
      .apply();
   }
   
   class CheckPagesAsyncTask extends AsyncTask<Void, Void, Boolean> {
      private final Context mAppContext;
      private String mPatchParam;
      public CheckPagesAsyncTask(Context context) {
        mAppContext = context.getApplicationContext();
      }

      @Override
      protected Boolean doInBackground(Void... params) {
         QuranFileUtils.migrateAudio(mAppContext);

         final QuranScreenInfo qsi = QuranScreenInfo.getInstance();
         if (!mSharedPreferences.contains(Constants.PREF_DEFAULT_IMAGES_DIR)) {
           /* previously, we would send any screen widths greater than 1280
            * to get 1920 images. this was problematic for various reasons,
            * including:
            * a. a texture limit for the maximum size of a bitmap that could
            *    be loaded, which the 1920x3106 images exceeded on devices
            *    with the minimum 2048 height capacity.
            * b. slow to switch pages due to the massive size of the gl
            *    texture loaded by android.
            *
            * consequently, in this new version, we make anything above 1024
            * fallback to a 1260 bucket (height of 2038). this works around
            * both problems (much faster page flipping now too) with a very
            * minor loss in quality.
            *
            * this code checks and sees, if the user already has a complete
            * folder of images - 1920, then 1280, then 1024 - and in any of
            * those cases, sets that in the pref so we load those instead of
            * the new 1260 images.
            */
           final String fallback =
               QuranFileUtils.getPotentialFallbackDirectory(mAppContext);
           if (fallback != null) {
             mSharedPreferences.edit()
                 .putString(Constants.PREF_DEFAULT_IMAGES_DIR, fallback)
                 .apply();
             qsi.setOverrideParam(fallback);
           }
         }

         final String width = qsi.getWidthParam();
         if (qsi.isTablet(mAppContext)){
            final String tabletWidth = qsi.getTabletWidthParam();
            boolean haveLandscape = QuranFileUtils.haveAllImages(mAppContext, tabletWidth);
            boolean havePortrait = QuranFileUtils.haveAllImages(mAppContext, width);
            mNeedPortraitImages = !havePortrait;
            mNeedLandscapeImages = !haveLandscape;
            if (haveLandscape && havePortrait) {
               // if we have the images, see if we need a patch set or not
               if (!QuranFileUtils.isVersion(mAppContext, width, 3) ||
                  !QuranFileUtils.isVersion(mAppContext, tabletWidth, 3)) {
                 mPatchParam = width + tabletWidth;
              }
            }
            return haveLandscape && havePortrait;
         }
         else {
            boolean haveAll = QuranFileUtils.haveAllImages(mAppContext,
                        QuranScreenInfo.getInstance().getWidthParam());
            mNeedPortraitImages = !haveAll;
            mNeedLandscapeImages = false;
            if (haveAll && !QuranFileUtils.isVersion(mAppContext, width, 3)) {
              mPatchParam = width;
            }
            return haveAll;
         }
      }
      
      @Override
      protected void onPostExecute(@NonNull Boolean result) {
         mCheckPagesTask = null;
         mPatchUrl = null;
         if (mIsPaused){ return; }
                  
         if (!result){
            String lastErrorItem = mSharedPreferences.getString(
                        QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, "");
            if (PAGES_DOWNLOAD_KEY.equals(lastErrorItem)){
               int lastError = mSharedPreferences.getInt(
                     QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, 0);
               int errorId = ServiceIntentHelper
                       .getErrorResourceFromErrorCode(lastError, false);
               showFatalErrorDialog(errorId);
            }
            else if (mSharedPreferences.getBoolean(
                    Constants.PREF_SHOULD_FETCH_PAGES, false)){
               downloadQuranImages(false);
            }
            else {
               promptForDownload();
            }
         }
         else {
            if (!TextUtils.isEmpty(mPatchParam)) {
              mPatchUrl = QuranFileUtils.getPatchFileUrl(mPatchParam, 3);
              promptForDownload();
              return;
            }
            runListView();
         }
      }      
   }

   /**
    * this method asks the service to download quran images.
    * 
    * there are two possible cases - the first is one in which we are not
    * sure if a download is going on or not (ie we just came in the app,
    * the files aren't all there, so we want to start downloading).  in
    * this case, we start the download only if we didn't receive any
    * broadcasts before starting it.
    * 
    * in the second case, we know what we are doing (either because the user
    * just clicked "download" for the first time or the user asked to retry
    * after an error), then we pass the force parameter, which asks the
    * service to just restart the download irrespective of anything else.
    * 
    * @param force whether to force the download to restart or not
    */
   private void downloadQuranImages(boolean force){
      // if any broadcasts were received, then we are already downloading
      // so unless we know what we are doing (via force), don't ask the
      // service to restart the download
      if (mDownloadReceiver != null &&
              mDownloadReceiver.didReceiveBroadcast() && !force){ return; }
      if (mIsPaused){ return; }

      QuranScreenInfo qsi = QuranScreenInfo.getInstance();
      
      String url;
      if (mNeedPortraitImages && !mNeedLandscapeImages){
         // phone (and tablet when upgrading on some devices, ex n10)
         url = QuranFileUtils.getZipFileUrl();
      }
      else if (mNeedLandscapeImages && !mNeedPortraitImages){
         // tablet (when upgrading from pre-tablet on some devices, ex n7).
         url = QuranFileUtils.getZipFileUrl(qsi.getTabletWidthParam());
      }
      else {
         // new tablet installation - if both image sets are the same
         // size, then just get the correct one only
         if (qsi.getTabletWidthParam().equals(qsi.getWidthParam())){
            url = QuranFileUtils.getZipFileUrl();
         }
         else {
            // otherwise download one zip with both image sets
            String widthParam = qsi.getWidthParam() +
                    qsi.getTabletWidthParam();
            url = QuranFileUtils.getZipFileUrl(widthParam);
         }
      }

      // if we have a patch url, just use that
      if (!TextUtils.isEmpty(mPatchUrl)){
         url = mPatchUrl;
      }

      String destination = QuranFileUtils.getQuranBaseDirectory(
              QuranDataActivity.this);
      
      // start service
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
              destination, getString(R.string.app_name), PAGES_DOWNLOAD_KEY,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES);
      
      if (!force){
         // handle race condition in which we missed the error preference and
         // the broadcast - if so, just rebroadcast errors so we handle them
         intent.putExtra(QuranDownloadService.EXTRA_REPEAT_LAST_ERROR, true);
      }

      startService(intent);
   }

   private void promptForDownload(){
      int message = R.string.downloadPrompt;
      if (QuranScreenInfo.getInstance().isTablet(this) &&
              (mNeedPortraitImages != mNeedLandscapeImages)){
         message = R.string.downloadTabletPrompt;
      }

      if (!TextUtils.isEmpty(mPatchUrl)){
         // patch message if applicable
         message = R.string.downloadImportantPrompt;
      }

      AlertDialog.Builder dialog = new AlertDialog.Builder(this);
      dialog.setMessage(message);
      dialog.setCancelable(false);
      dialog.setPositiveButton(R.string.downloadPrompt_ok,
            new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mPromptForDownloadDialog = null;
            mSharedPreferences.edit().putBoolean(
                    Constants.PREF_SHOULD_FETCH_PAGES, true)
                    .apply();
            downloadQuranImages(true);
         }
      });

      dialog.setNegativeButton(R.string.downloadPrompt_no, 
            new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mPromptForDownloadDialog = null;
            runListView();
         }
      });

      mPromptForDownloadDialog = dialog.create();
      mPromptForDownloadDialog.setTitle(R.string.downloadPrompt_title);
      mPromptForDownloadDialog.show();
   }

   protected void initializeQuranScreen() {
      QuranScreenInfo.getOrMakeInstance(this);
   }

   protected void runListView(boolean showTranslations){
      Intent i = new Intent(this, QuranActivity.class);
      if (showTranslations){
         i.putExtra(QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, true);
      }
      startActivity(i);
      finish();
   }

   protected void runListView(){
      boolean value = (mSharedPreferences.getBoolean(
              Constants.PREF_HAVE_UPDATED_TRANSLATIONS, false));
      runListView(value);
   }
}
