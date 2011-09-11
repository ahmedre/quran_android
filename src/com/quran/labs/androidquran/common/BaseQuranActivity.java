package com.quran.labs.androidquran.common;

import java.text.NumberFormat;
import java.util.Locale;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;

import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.BookmarksActivity;
import com.quran.labs.androidquran.DownloadActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranJumpDialog;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.QuranViewActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.TranslationActivity;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.service.QuranDataService;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

public abstract class BaseQuranActivity extends Activity {

	protected SharedPreferences prefs;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);	  
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
			case ApplicationConstants.BOOKMARKS_CODE:
				if (resultCode == Activity.RESULT_OK) {
					Integer lastPage = data.getIntExtra("page", ApplicationConstants.PAGES_FIRST);
					jumpTo(lastPage);
				} 
			break;
			case ApplicationConstants.TRANSLATION_VIEW_CODE:
				if (resultCode == Activity.RESULT_OK) {
					Integer lastPage = data.getIntExtra("page", ApplicationConstants.PAGES_FIRST);
					jumpTo(lastPage);
				} 
			break;
		}
	}
	
	@Override
    protected Dialog onCreateDialog(int id){
		if (id == ApplicationConstants.JUMP_DIALOG){
    		Dialog dialog = new QuranJumpDialog(this);
    		dialog.setOnCancelListener(new OnCancelListener(){
    			public void onCancel(DialogInterface dialog) {
    				QuranJumpDialog dlg = (QuranJumpDialog)dialog;
    				Integer page = dlg.getPage();
    				removeDialog(ApplicationConstants.JUMP_DIALOG);
    				if (page != null) jumpTo(page);
    			}

    		});
    		return dialog;
    	}
    	return null;
    }
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		Intent intent;
		switch (item.getItemId()){
			case R.id.menu_item_jump:
				showDialog(ApplicationConstants.JUMP_DIALOG);
			break;
			case R.id.menu_item_about_us:
		    	intent = new Intent(getApplicationContext(), AboutUsActivity.class);
		    	startActivity(intent);
		    break;
			case R.id.menu_item_help:
				intent = new Intent(getApplicationContext(), HelpActivity.class);
				startActivity(intent);
			break;
			case R.id.menu_item_settings:
				//intent = new Intent(getApplicationContext(), SettingsActivity.class);
				intent = new Intent(getApplicationContext(), QuranPreferenceActivity.class);
				startActivityForResult(intent, ApplicationConstants.SETTINGS_CODE);
			break;
			case R.id.menu_item_bookmarks:
		    	intent = new Intent(getApplicationContext(), BookmarksActivity.class);
		    	startActivityForResult(intent, ApplicationConstants.BOOKMARKS_CODE);
			break;
			case R.id.menu_item_translations:
				Intent i = new Intent(this, TranslationActivity.class);
				i.putExtra("page", QuranSettings.getInstance().getLastPage());
				startActivityForResult(i, ApplicationConstants.TRANSLATION_VIEW_CODE);
			break;
			case R.id.menu_item_get_translations:
				intent = new Intent(getApplicationContext(), DownloadActivity.class);
				startActivity(intent);
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	public void jumpTo(int page) {
		Intent i = new Intent(this, QuranViewActivity.class);
		i.putExtra("page", page);
		startActivityForResult(i, ApplicationConstants.QURAN_VIEW_CODE);
	}
	
	public String getPageFileName(int p) {
		NumberFormat nf = NumberFormat.getInstance(Locale.US);
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(p) + ".png";
	}
	
	@Override
    protected void onResume() {
    	super.onResume();
    	QuranSettings.load(prefs);
    }
	
	@Override
	protected void onPause() {
		super.onPause();
		QuranSettings.save(prefs);
	}

	protected void initializeQuranScreen() {
	    // get the screen size
	    WindowManager w = getWindowManager();
	    Display d = w.getDefaultDisplay();
	    int width = d.getWidth();
	    int height = d.getHeight();
	    Log.d("quran", "screen size: width [" + width + "], height: [" + height + "]");
	    QuranScreenInfo.initialize(width, height);
	    QuranDataService.qsi = QuranScreenInfo.getInstance();
	}
	
}
