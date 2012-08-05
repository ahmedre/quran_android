package com.quran.labs.androidquran.ui;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Bookmark;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.*;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.helpers.ShowBookmarkListTask;
import com.quran.labs.androidquran.ui.helpers.ShowBookmarkListTask.OnBookmarkSelectedListener;
import com.quran.labs.androidquran.util.*;
import com.quran.labs.androidquran.widgets.AudioStatusBar;

import java.io.File;
import java.io.Serializable;
import java.util.Locale;

public class PagerActivity extends SherlockFragmentActivity implements
        AudioStatusBar.AudioBarListener,
        DefaultDownloadReceiver.DownloadListener {
   private static final String TAG = "PagerActivity";
   private static final String AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY";
   private static final String LAST_AUDIO_DL_REQUEST = "LAST_AUDIO_DL_REQUEST";
   private static final String LAST_READ_PAGE = "LAST_READ_PAGE";
   private static final String LAST_READING_MODE_IS_TRANSLATION =
           "LAST_READING_MODE_IS_TRANSLATION";

   public static final String EXTRA_JUMP_TO_TRANSLATION = "jumpToTranslation";
   public static final String EXTRA_HIGHLIGHT_SURA = "highlightSura";
   public static final String EXTRA_HIGHLIGHT_AYAH = "highlightAyah";

   private QuranPageWorker mWorker = null;
   private SharedPreferences mPrefs = null;
   private long mLastPopupTime = 0;
   private boolean mIsActionBarHidden = true;
   private AudioStatusBar mAudioStatusBar = null;
   private ViewPager mViewPager = null;
   private QuranPageAdapter mPagerAdapter = null;
   private boolean mShouldReconnect = false;
//   private SparseArray<Boolean> mBookmarksCache = null;
   private DownloadAudioRequest mLastAudioDownloadRequest = null;
   private boolean mShowingTranslation = false;
   private int mHighlightedSura = -1;
   private int mHighlightedAyah = -1;
   private boolean mShouldOverridePlaying = false;
   private DefaultDownloadReceiver mDownloadReceiver;
   private boolean mNeedsPermissionToDownloadOver3g = true;
   private AlertDialog mPromptDialog = null;
   private Handler mHandler = new Handler();

   @Override
   public void onCreate(Bundle savedInstanceState){
      if (QuranSettings.isArabicNames(this)){
         Resources resources = getResources();
         Configuration config = resources.getConfiguration();
         config.locale = new Locale("ar");
         resources.updateConfiguration(config,
                 resources.getDisplayMetrics());
      }

      setTheme(R.style.QuranAndroid);
      getSherlock().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
      requestWindowFeature(
             com.actionbarsherlock.view.Window.FEATURE_INDETERMINATE_PROGRESS);

      super.onCreate(savedInstanceState);
//      mBookmarksCache = new SparseArray<Boolean>();

      // make sure to remake QuranScreenInfo if it doesn't exist, as it
      // is needed to get images, to get the highlighting db, etc.
      QuranScreenInfo.getOrMakeInstance(this);

      int page = -1;

      if (savedInstanceState != null){
         android.util.Log.d(TAG, "non-null saved instance state!");
         Serializable lastAudioRequest =
                 savedInstanceState.getSerializable(LAST_AUDIO_DL_REQUEST);
         if (lastAudioRequest != null &&
                 lastAudioRequest instanceof DownloadAudioRequest){
            android.util.Log.d(TAG, "restoring request from saved instance!");
            mLastAudioDownloadRequest = (DownloadAudioRequest)lastAudioRequest;
         }
         page = savedInstanceState.getInt(LAST_READ_PAGE, -1);
         if (page != -1){ page = Constants.PAGES_LAST - page; }
         mShowingTranslation = savedInstanceState
                 .getBoolean(LAST_READING_MODE_IS_TRANSLATION, false);
      }
      
      getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
      mPrefs = PreferenceManager.getDefaultSharedPreferences(
            getApplicationContext());

      getSupportActionBar().setDisplayShowHomeEnabled(true);
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().hide();
      mIsActionBarHidden = true;

      int background = getResources().getColor(
              R.color.transparent_actionbar_color);
      setContentView(R.layout.quran_page_activity);
      getSupportActionBar().setBackgroundDrawable(
              new ColorDrawable(background));
      mAudioStatusBar = (AudioStatusBar)findViewById(R.id.audio_area);
      mAudioStatusBar.setAudioBarListener(this);

      Intent intent = getIntent();
      Bundle extras = intent.getExtras();
      if (extras != null){
         if (page == -1){
            page = Constants.PAGES_LAST -
                    extras.getInt("page", Constants.PAGES_FIRST);
         }

         mShowingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION,
                 mShowingTranslation);
         mHighlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
         mHighlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);
      }
      updateActionBarTitle(Constants.PAGES_LAST - page);

      mWorker = new QuranPageWorker(this);
      mLastPopupTime = System.currentTimeMillis();
      mPagerAdapter = new QuranPageAdapter(
              getSupportFragmentManager(), mShowingTranslation);
      mViewPager = (ViewPager)findViewById(R.id.quran_pager);
      mViewPager.setAdapter(mPagerAdapter);

      mViewPager.setOnPageChangeListener(new OnPageChangeListener(){

         @Override
         public void onPageScrollStateChanged(int state) {}

         @Override
         public void onPageScrolled(int position, float positionOffset,
               int positionOffsetPixels) {
         }

         @Override
         public void onPageSelected(int position) {
            Log.d(TAG, "onPageSelected(): " + position);
            int page = Constants.PAGES_LAST - position;
            QuranSettings.setLastPage(PagerActivity.this, page);
            if (QuranSettings.shouldDisplayMarkerPopup(PagerActivity.this)){
               mLastPopupTime = QuranDisplayHelper.displayMarkerPopup(
                       PagerActivity.this, page, mLastPopupTime);
            }
            updateActionBarTitle(page);

//            if (mBookmarksCache.get(page) == null){
//               // we don't have the key
//               new IsPageBookmarkedTask().execute(page);
//            }
         }
      });

      mViewPager.setCurrentItem(page);
      QuranSettings.setLastPage(this, Constants.PAGES_LAST - page);
      setLoading(false);
      
      // just got created, need to reconnect to service
      mShouldReconnect = true;

      // enforce orientation lock
      if (QuranSettings.isLockOrientation(this)){
         int current = getResources().getConfiguration().orientation;
         if (QuranSettings.isLandscapeOrientation(this)){
            if (current == Configuration.ORIENTATION_PORTRAIT){
               setRequestedOrientation(
                       ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
               return;
            }
         }
         else if (current == Configuration.ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
         }
      }
   }

   @Override
   public void onResume(){
      mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
      LocalBroadcastManager.getInstance(this).registerReceiver(
              mAudioReceiver,
              new IntentFilter(AudioService.AudioUpdateIntent.INTENT_NAME));

      mDownloadReceiver = new DefaultDownloadReceiver(this,
              QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
      String action = QuranDownloadService.ProgressIntent.INTENT_NAME;
      LocalBroadcastManager.getInstance(this).registerReceiver(
              mDownloadReceiver,
              new IntentFilter(action));
      mDownloadReceiver.setListener(this);

      super.onResume();
      if (mShouldReconnect){
         startService(new Intent(AudioService.ACTION_CONNECT));
         mShouldReconnect = false;
      }

      if (mHighlightedSura > 0 && mHighlightedAyah > 0){
         mHandler.postDelayed(
                 new Runnable() {
                    public void run() {
                       highlightAyah(mHighlightedSura, mHighlightedAyah, false);
                    }
                 }, 750);
      }
   }

   public void showGetRequiredFilesDialog(){
      if (mPromptDialog != null){ return; }
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.download_extra_data)
             .setPositiveButton(R.string.downloadPrompt_ok,
                     new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int option){
                  downloadRequiredFiles();
                  dialog.dismiss();
                  mPromptDialog = null;
                }
             })
             .setNegativeButton(R.string.downloadPrompt_no,
                     new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int option){
                           dialog.dismiss();
                           mPromptDialog = null;
                        }
             });
      mPromptDialog = builder.create();
      mPromptDialog.show();
   }

   public void downloadRequiredFiles(){
      int downloadType = QuranDownloadService.DOWNLOAD_TYPE_AUDIO;
      if (mAudioStatusBar.getCurrentMode() == AudioStatusBar.STOPPED_MODE){
         // if we're stopped, use audio download bar as our progress bar
         mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
         if (mIsActionBarHidden){ toggleActionBar(); }
      }
      else {
         // if audio is playing, let's not disrupt it - do this using a
         // different type so the broadcast receiver ignores it.
         downloadType = QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB;
      }

      boolean haveDownload = false;
      if (!QuranFileUtils.haveAyaPositionFile()){
         String url = QuranFileUtils.getAyaPositionFileUrl();
         String destination = QuranFileUtils.getQuranDatabaseDirectory();
         // start the download
         String notificationTitle = getString(R.string.highlighting_database);
         Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
                 destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
                 downloadType);
         startService(intent);

         haveDownload = true;
      }

      if (!QuranFileUtils.hasArabicSearchDatabase()){
         String url = QuranFileUtils.getArabicSearchDatabaseUrl();

         // show "downloading required files" unless we already showed that for
         // highlighting database, in which case, show "downloading search data"
         String notificationTitle = getString(R.string.highlighting_database);
         if (haveDownload){
            notificationTitle = getString(R.string.search_data);
         }

         Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
                 QuranFileUtils.getQuranDatabaseDirectory(), notificationTitle,
                 AUDIO_DOWNLOAD_KEY, downloadType);
         intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
                 QuranDataProvider.QURAN_ARABIC_DATABASE);
         startService(intent);
      }

      if (downloadType != QuranDownloadService.DOWNLOAD_TYPE_AUDIO){
         // if audio is playing, just show a status notification
         Toast.makeText(this, R.string.downloading_title,
                 Toast.LENGTH_SHORT).show();
      }
   }

   @Override
   public void onNewIntent(Intent intent){
      if (intent == null){ return; }

      Bundle extras = intent.getExtras();
      if (extras != null){
         int page = Constants.PAGES_LAST -
                 extras.getInt("page", Constants.PAGES_FIRST);
         updateActionBarTitle(Constants.PAGES_LAST - page);

         boolean currentValue = mShowingTranslation;
         mShowingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION,
              mShowingTranslation);
         mHighlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
         mHighlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);

         if (mShowingTranslation != currentValue){
            if (mShowingTranslation){
               mPagerAdapter.setTranslationMode();
            }
            else { mPagerAdapter.setQuranMode(); }

            invalidateOptionsMenu();
         }

         if (mHighlightedAyah > 0 && mHighlightedSura > 0){
            // this will jump to the right page automagically
            highlightAyah(mHighlightedSura, mHighlightedAyah, true);
         }
         else { mViewPager.setCurrentItem(page); }

         setIntent(intent);
      }
   }

   public void jumpTo(int page){
      Intent i = new Intent(this, PagerActivity.class);
      i.putExtra("page", page);
      onNewIntent(i);
   }

   @Override
   public void onPause(){
      if (mPromptDialog != null){
         mPromptDialog.dismiss();
         mPromptDialog = null;
      }

      LocalBroadcastManager.getInstance(this)
              .unregisterReceiver(mAudioReceiver);
      mDownloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
              .unregisterReceiver(mDownloadReceiver);
      mDownloadReceiver = null;
      super.onPause();
   }

   @Override
   protected void onDestroy() {
      android.util.Log.d(TAG, "onDestroy()");
      super.onDestroy();
   }

   @Override
   public void onSaveInstanceState(Bundle state){
      if (mLastAudioDownloadRequest != null){
         state.putSerializable(LAST_AUDIO_DL_REQUEST,
                 mLastAudioDownloadRequest);
      }
      state.putSerializable(LAST_READ_PAGE,
              Constants.PAGES_LAST - mViewPager.getCurrentItem());
      state.putBoolean(LAST_READING_MODE_IS_TRANSLATION, mShowingTranslation);
      super.onSaveInstanceState(state);
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);
      MenuInflater inflater = getSupportMenuInflater();
      inflater.inflate(R.menu.quran_menu, menu);
      return true;
   }

   @Override
   public boolean onPrepareOptionsMenu(Menu menu) {
      super.onPrepareOptionsMenu(menu);
//      MenuItem item = menu.findItem(R.id.favorite_item);
//      if (item != null){
//         int page = Constants.PAGES_LAST - mViewPager.getCurrentItem();
//         boolean bookmarked = false;
//         if (mBookmarksCache.get(page) != null){
//            bookmarked = mBookmarksCache.get(page);
//         }
//         if (bookmarked){ item.setIcon(R.drawable.favorite); }
//         else { item.setIcon(R.drawable.not_favorite); }
//      }

      MenuItem quran = menu.findItem(R.id.goto_quran);
      MenuItem translation = menu.findItem(R.id.goto_translation);
      if (!mShowingTranslation){
         quran.setVisible(false);
         translation.setVisible(true);
      }
      else {
         quran.setVisible(true);
         translation.setVisible(false);
      }
      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == R.id.favorite_item){
         int page = Constants.PAGES_LAST - mViewPager.getCurrentItem();
         bookmarkPage(page);
         return true;
      }
      else if (item.getItemId() == R.id.goto_quran){
         mPagerAdapter.setQuranMode();
         mShowingTranslation = false;
         invalidateOptionsMenu();
         return true;
      }
      else if (item.getItemId() == R.id.goto_translation){
         String activeDatabase = mPrefs.getString(
                 Constants.PREF_ACTIVE_TRANSLATION, null);
         if (activeDatabase == null){
            Intent i = new Intent(this, TranslationManagerActivity.class);
            startActivity(i);
         }
         else {
            mPagerAdapter.setTranslationMode();
            mShowingTranslation = true;
            invalidateOptionsMenu();
         }
         return true;
      }
      else if (item.getItemId() == R.id.settings){
         Intent i = new Intent(this, QuranPreferenceActivity.class);
         startActivity(i);
         return true;
      }
      else if (item.getItemId() == R.id.help) {
         Intent i = new Intent(this, HelpActivity.class);
         startActivity(i);
         return true;
      }
      else if (item.getItemId() == R.id.search){
         return onSearchRequested();
      }
      else if (item.getItemId() == android.R.id.home){
         finish();
         return true;
      }
      else if (item.getItemId() == R.id.jump){
         FragmentManager fm = getSupportFragmentManager();
         JumpFragment jumpDialog = new JumpFragment();
         jumpDialog.show(fm, "jumpDialogTag");
         return true;
      }
      return super.onOptionsItemSelected(item);
   }

   public void setLoading(boolean isLoading){
      setSupportProgressBarIndeterminateVisibility(isLoading);
   }

   public void setLoadingIfPage(int page){
      int position = mViewPager.getCurrentItem();
      int currentPage = Constants.PAGES_LAST - position;
      if (currentPage == page){
         setLoading(true);
      }
   }

   public void bookmarkPage(final int page){
      ShowBookmarkListTask task = new ShowBookmarkListTask(this, new OnBookmarkSelectedListener() {
         public void onBookmarkSelected(Bookmark bookmark) {
            new BookmarkPageTask(page, bookmark.id).execute();
         }
      });
      task.execute(page);
   }

   private void updateActionBarTitle(int page){
      String sura = QuranInfo.getSuraNameFromPage(this, page, true);
      ActionBar actionBar = getSupportActionBar();
      actionBar.setTitle(sura);
      String desc = QuranInfo.getPageSubtitle(this, page);
      actionBar.setSubtitle(desc);
   }

   BroadcastReceiver mAudioReceiver = new BroadcastReceiver(){
      @Override
      public void onReceive(Context context, Intent intent){
         if (intent != null){
            int state = intent.getIntExtra(
                    AudioService.AudioUpdateIntent.STATUS, -1);
            int sura = intent.getIntExtra(
                    AudioService.AudioUpdateIntent.SURA, -1);
            int ayah = intent.getIntExtra(
                    AudioService.AudioUpdateIntent.AYAH, -1);
            if (state == AudioService.AudioUpdateIntent.PLAYING){
               mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
               highlightAyah(sura, ayah);
            }
            else if (state == AudioService.AudioUpdateIntent.PAUSED){
               mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
               highlightAyah(sura, ayah);
            }
            else if (state == AudioService.AudioUpdateIntent.STOPPED){
               mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
               unhighlightAyah();

               Serializable qi = intent.getSerializableExtra(
                       AudioService.EXTRA_PLAY_INFO);
               if (qi != null){
                  // this means we stopped due to missing audio
               }
            }
         }
      }
   };

   @Override
   public void updateDownloadProgress(int progress,
                                      long downloadedSize, long totalSize){
      mAudioStatusBar.switchMode(
              AudioStatusBar.DOWNLOADING_MODE);
      mAudioStatusBar.setProgress(progress);
   }

   @Override
   public void updateProcessingProgress(int progress,
                                        int processFiles, int totalFiles){
      mAudioStatusBar.setProgressText(
              getString(R.string.extracting_title), false);
      mAudioStatusBar.setProgress(-1);
   }

   @Override
   public void handleDownloadTemporaryError(int errorId){
      mAudioStatusBar.setProgressText(getString(errorId), false);
   }

   @Override
   public void handleDownloadSuccess(){
      playAudioRequest(mLastAudioDownloadRequest);
   }

   @Override
   public void handleDownloadFailure(int errId){
      String s = getString(errId);
      mAudioStatusBar.setProgressText(s, true);
   }

   public void toggleActionBar(){
      if (mIsActionBarHidden){
         getWindow().addFlags(
                 WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
         getWindow().clearFlags(
                 WindowManager.LayoutParams.FLAG_FULLSCREEN);
         getSupportActionBar().show();
         mAudioStatusBar.updateSelectedItem();
         mAudioStatusBar.setVisibility(View.VISIBLE);
         mIsActionBarHidden = false;
      }
      else {
         getWindow().addFlags(
               WindowManager.LayoutParams.FLAG_FULLSCREEN);
         getWindow().clearFlags(
               WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
         getSupportActionBar().hide();
         mAudioStatusBar.setVisibility(View.GONE);
         mIsActionBarHidden = true;
      }
   }
   
   public QuranPageWorker getQuranPageWorker(){
      return mWorker;
   }

   public void highlightAyah(int sura, int ayah){
      highlightAyah(sura, ayah, true);
   }

   public void highlightAyah(int sura, int ayah, boolean force){
      Log.d(TAG, "highlightAyah() - " + sura + ":" + ayah);
      int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
      if (page < Constants.PAGES_FIRST ||
              Constants.PAGES_LAST < page){ return; }

      int position = Constants.PAGES_LAST - page;
      if (position != mViewPager.getCurrentItem() && force){
         unhighlightAyah();
         mViewPager.setCurrentItem(position);
      }

      Fragment f = mPagerAdapter.getFragmentIfExists(position);
      if (f != null && f instanceof QuranPageFragment){
         QuranPageFragment qpf = (QuranPageFragment)f;
         qpf.highlightAyah(sura, ayah);
      }
   }

   public void unhighlightAyah(){
      int position = mViewPager.getCurrentItem();
      Fragment f = mPagerAdapter.getFragmentIfExists(position);
      if (f!= null && f instanceof QuranPageFragment){
         QuranPageFragment qpf = (QuranPageFragment)f;
         qpf.unhighlightAyah();
      }
   }
/*
   class IsPageBookmarkedTask extends AsyncTask<Integer, Void, Boolean> {
      private int mPage;

      @Override
      protected Boolean doInBackground(Integer... params) {
         Boolean bookmarked = null;
         mPage = params[0];

         BookmarksDBAdapter dba = new BookmarksDBAdapter(PagerActivity.this);
         dba.open();
         bookmarked = dba.isPageBookmarked(mPage);
         dba.close();

         return bookmarked;
      }

      @Override
      protected void onPostExecute(Boolean result) {
         if (result != null){
            mBookmarksCache.put(mPage, result);
            invalidateOptionsMenu();
         }
      }
   }
*/
   class BookmarkPageTask extends AsyncTask<Void, Void, Long> {
      private int page;
      private long bookmarkId;
      
      public BookmarkPageTask(int page, long bookmarkId) {
         this.page = page;
         this.bookmarkId = bookmarkId;
      }
      
      @Override
      protected Long doInBackground(Void... params) {
         BookmarksDBAdapter dba = new BookmarksDBAdapter(PagerActivity.this);
         dba.open();
         long result = dba.bookmarkPage(page, bookmarkId);
         dba.close();
         
         return result;
      }
      
      @Override
      protected void onPostExecute(Long result) {
//         if (result != null && result >= 0){
//            mBookmarksCache.put(page, true);
//            invalidateOptionsMenu();
//         }
      }
   }

   @Override
   public void onPlayPressed() {
      if (mAudioStatusBar.getCurrentMode() == AudioStatusBar.PAUSED_MODE){
         // if we are "paused," just un-pause.
         play(null);
         return;
      }

      int position = mViewPager.getCurrentItem();
      int page = Constants.PAGES_LAST - position;

      int startSura = QuranInfo.PAGE_SURA_START[page - 1];
      int startAyah = QuranInfo.PAGE_AYAH_START[page - 1];
      playFromAyah(page, startSura, startAyah, false);
   }

   public void playFromAyah(int page, int sura, int ayah){
      playFromAyah(page, sura, ayah, true);
   }

   private void playFromAyah(int page, int startSura,
                             int startAyah, boolean force){
      if (force){ mShouldOverridePlaying = true; }
      int currentQari = mAudioStatusBar.getCurrentQari();

      QuranAyah ayah = new QuranAyah(startSura, startAyah);
      if (QuranSettings.shouldStream(this)){
         playStreaming(ayah, page, currentQari);
      }
      else { downloadAndPlayAudio(ayah, page, currentQari); }
   }

   private void playStreaming(QuranAyah ayah, int page, int qari){
      String qariUrl = AudioUtils.getQariUrl(this, qari, true);
      String dbFile = AudioUtils.getQariDatabasePathIfGapless(
              this, qari);
      if (!TextUtils.isEmpty(dbFile)){
         // gapless audio is "download only"
         downloadAndPlayAudio(ayah, page, qari);
         return;
      }

      AudioRequest request = new AudioRequest(qariUrl, ayah);
      request.setGaplessDatabaseFilePath(dbFile);
      play(request);

      mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
   }

   private void downloadAndPlayAudio(QuranAyah ayah, int page, int qari){
      QuranAyah endAyah = AudioUtils.getLastAyahToPlay(ayah, page,
              QuranSettings.getPreferredDownloadAmount(this));
      String baseUri = AudioUtils.getLocalQariUrl(this, qari);
      if (endAyah == null || baseUri == null){ return; }
      String dbFile = AudioUtils.getQariDatabasePathIfGapless(this, qari);

      String fileUrl = "";
      if (TextUtils.isEmpty(dbFile)){
         fileUrl = baseUri + File.separator + "%d" + File.separator +
              "%d" + AudioUtils.AUDIO_EXTENSION;
      }
      else {
         fileUrl = baseUri + File.separator + "%03d" +
                 AudioUtils.AUDIO_EXTENSION;
      }

      DownloadAudioRequest request =
              new DownloadAudioRequest(fileUrl, ayah, qari, baseUri);
      request.setGaplessDatabaseFilePath(dbFile);
      request.setPlayBounds(ayah, endAyah);
      mLastAudioDownloadRequest = request;
      playAudioRequest(request);
   }

   private void playAudioRequest(DownloadAudioRequest request){
      if (request == null){
         mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
         return;
      }

      boolean needsPermission = mNeedsPermissionToDownloadOver3g;
      if (needsPermission){
         if (QuranUtils.isOnWifiNetwork(this)){
            Log.d(TAG, "on wifi, don't need permission for download...");
            needsPermission = false;
         }
      }

      Log.d(TAG, "seeing if we can play audio request...");
      if (!QuranFileUtils.haveAyaPositionFile()){
         if (needsPermission){
            mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
            return;
         }

         if (mIsActionBarHidden){ toggleActionBar(); }
         mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
         String url = QuranFileUtils.getAyaPositionFileUrl();
         String destination = QuranFileUtils.getQuranDatabaseDirectory();
         // start the download
         String notificationTitle = getString(R.string.highlighting_database);
         Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
                 destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
                 QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
         startService(intent);
      }
      else if (AudioUtils.shouldDownloadGaplessDatabase(this, request)){
         Log.d(TAG, "need to download gapless database...");
         if (needsPermission){
            mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
            return;
         }

         if (mIsActionBarHidden){ toggleActionBar(); }
         mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
         String url = AudioUtils.getGaplessDatabaseUrl(this, request);
         String destination = request.getLocalPath();
         // start the download
         String notificationTitle = getString(R.string.timing_database);
         Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
                 destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
                 QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
         startService(intent);
      }
      else if (AudioUtils.haveAllFiles(request)){
         if (!AudioUtils.shouldDownloadBasmallah(this, request)){
            android.util.Log.d(TAG, "have all files, playing!");
            request.removePlayBounds();
            play(request);
            mLastAudioDownloadRequest = null;
         }
         else {
            android.util.Log.d(TAG, "should download basmalla...");
            if (needsPermission){
               mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
               return;
            }

            QuranAyah firstAyah = new QuranAyah(1, 1);
            String qariUrl = AudioUtils.getQariUrl(this,
                    request.getQariId(), true);
            mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);

            if (mIsActionBarHidden){ toggleActionBar(); }
            String notificationTitle =
                    QuranInfo.getNotificationTitle(this, firstAyah, firstAyah);
            Intent intent = ServiceIntentHelper.getDownloadIntent(this, qariUrl,
                    request.getLocalPath(), notificationTitle,
                    AUDIO_DOWNLOAD_KEY,
                    QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
            intent.putExtra(QuranDownloadService.EXTRA_START_VERSE, firstAyah);
            intent.putExtra(QuranDownloadService.EXTRA_END_VERSE, firstAyah);
            startService(intent);
         }
      }
      else {
         if (needsPermission){
            mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
            return;
         }

         if (mIsActionBarHidden){ toggleActionBar(); }
         mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);

         String notificationTitle = QuranInfo.getNotificationTitle(this,
                 request.getMinAyah(),request.getMaxAyah());
         String qariUrl = AudioUtils.getQariUrl(this,
                 request.getQariId(), true);
         android.util.Log.d(TAG, "need to start download: " + qariUrl);

         // start service
         Intent intent = ServiceIntentHelper.getDownloadIntent(this, qariUrl,
                 request.getLocalPath(), notificationTitle, AUDIO_DOWNLOAD_KEY,
                 QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
         intent.putExtra(QuranDownloadService.EXTRA_START_VERSE,
                 request.getMinAyah());
         intent.putExtra(QuranDownloadService.EXTRA_END_VERSE,
                 request.getMaxAyah());
         intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS,
                 request.isGapless());
         startService(intent);
      }
   }

   private void play(AudioRequest request){
      mNeedsPermissionToDownloadOver3g = true;
      Intent i = new Intent(AudioService.ACTION_PLAYBACK);
      if (request != null){
         i.putExtra(AudioService.EXTRA_PLAY_INFO, request);
      }

      if (mShouldOverridePlaying){
         // force the current audio to stop and start playing new request
         i.putExtra(AudioService.EXTRA_STOP_IF_PLAYING, true);
         mShouldOverridePlaying = false;
      }
      // just a playback request, so tell audio service to just continue
      // playing (and don't store new audio data) if it was already playing
      else { i.putExtra(AudioService.EXTRA_IGNORE_IF_PLAYING, true); }
      startService(i);
   }

   @Override
   public void onPausePressed() {
      startService(new Intent(AudioService.ACTION_PAUSE));
      mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
   }

   @Override
   public void onNextPressed() {
      startService(new Intent(AudioService.ACTION_SKIP));
   }

   @Override
   public void onPreviousPressed() {
      startService(new Intent(AudioService.ACTION_REWIND));
   }

   @Override
   public void setRepeatCount(int repeatCount){
      Intent i = new Intent(AudioService.ACTION_UPDATE_REPEAT);
      i.putExtra(AudioService.EXTRA_REPEAT_INFO, new RepeatInfo(repeatCount));
      startService(i);
   }

   @Override
   public void onStopPressed() {
      startService(new Intent(AudioService.ACTION_STOP));
      mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
      unhighlightAyah();
   }

   @Override
   public void onCancelPressed(boolean cancelDownload){
      if (cancelDownload){
         mNeedsPermissionToDownloadOver3g = true;

         int resId = R.string.canceling;
         mAudioStatusBar.setProgressText(getString(resId), true);
         Intent i = new Intent(this, QuranDownloadService.class);
         i.setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS);
         startService(i);
      }
      else { mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE); }
   }

   @Override
   public void onAcceptPressed(){
      if (mLastAudioDownloadRequest != null){
         mNeedsPermissionToDownloadOver3g = false;
         playAudioRequest(mLastAudioDownloadRequest);
      }
   }
}