package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.markupartist.android.widget.ActionBar.IntentAction;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.AudioServiceBinder;
import com.quran.labs.androidquran.service.QuranAudioService;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranAudioLibrary;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.numberpicker.NumberPickerDialog;

public class QuranViewActivity extends PageViewQuranActivity implements
		AyahStateListener {

	protected static final String ACTION_GO_TO_PAGE = "ACTION_GO_TO_PAGE";
	protected static final String ACTION_PREVIOUS = "ACTION_PREVIOUS";
	protected static final String ACTION_NEXT = "ACTION_NEXT";
	protected static final String ACTION_PAUSE = "ACTION_PAUSE";
	protected static final String ACTION_PLAY = "ACTION_PLAY";
	protected static final String ACTION_STOP = "ACTION_STOP";
	protected static final String ACTION_CHANGE_READER = "ACTION_CHANGE_READER";
	protected static final String ACTION_JUMP_TO_AYAH = "ACTION_JUMP_TO_AYAH";
	protected static final String ACTION_REPEAT = "ACTION_REPEAT";
	
	private static final String TAG = "QuranViewActivity";
	private static final String IS_PLAYING = "IS_PLAYING";

	private boolean bounded = false;
	
	/* making this static because otherwise, when activity gets destroyed (ex on
	 * orientation change), we lose this reference and can't properly keep state.
	 * (stop, etc start dying).  mainly seen on 1.5. */
	private static AudioServiceBinder quranAudioPlayer = null;

	private AyahItem lastAyah;
	private int currentReaderId;
	private boolean playing = false;

	private LinkedHashMap<String, IntentAction> actionBarActions = new LinkedHashMap<String, IntentAction>();

	// private TextView textView;

	private ServiceConnection conn = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			unBindAudioService();
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			quranAudioPlayer = (AudioServiceBinder) service;
			quranAudioPlayer.setAyahCompleteListener(QuranViewActivity.this);
			if (quranAudioPlayer.isPlaying()) {
				onActionPlay();
				AyahItem a = quranAudioPlayer.getCurrentAyah();
				quranPageFeeder.highlightAyah(a.getSoura(), a.getAyah());
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null && savedInstanceState.containsKey(IS_PLAYING)){
			boolean shouldIPlay = savedInstanceState.getBoolean(IS_PLAYING);
			if (shouldIPlay){
				bindAudioService();
				onActionPlay();
			}
		}
	}

	protected void addActions() {
		super.addActions();
		if (actionBar != null) {
			// actionBar.setTitle("QuranAndroid");
			actionBar.setTitle("Quran");
			actionBarActions.put(ACTION_PLAY, getIntentAction(
					ACTION_PLAY, R.drawable.ab_play));
			actionBarActions.put(ACTION_PAUSE, getIntentAction(
					ACTION_PAUSE, R.drawable.ab_pause));
			actionBarActions.put(ACTION_NEXT, getIntentAction(
					ACTION_NEXT, R.drawable.ab_next));
			actionBarActions.put(ACTION_PREVIOUS, getIntentAction(
					ACTION_PREVIOUS, R.drawable.ab_prev));
			actionBarActions.put(ACTION_STOP, getIntentAction(
					ACTION_STOP, R.drawable.stop));
			actionBarActions.put(ACTION_CHANGE_READER,
					getIntentAction(ACTION_CHANGE_READER, R.drawable.mic));
			actionBarActions.put(ACTION_JUMP_TO_AYAH,
					getIntentAction(ACTION_JUMP_TO_AYAH, R.drawable.ab_jump));
			actionBarActions.put(ACTION_REPEAT,
					getIntentAction(ACTION_REPEAT, R.drawable.repeat));
			onActionStop();
		}
	}

	private IntentAction getIntentAction(String intentAction, int drawable) {
		Intent i = new Intent(this, QuranViewActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		i.setAction(intentAction);
		IntentAction action = new IntentAction(this, i, drawable);
		return action;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		String action = intent.getAction();
		if (action != null) {
			if (action.equalsIgnoreCase(ACTION_GO_TO_PAGE)) {
				int page = intent.getExtras().getInt("page", QuranSettings.getInstance().getLastPage());
				jumpTo(page);
			} else if (action.equalsIgnoreCase(ACTION_PLAY)) {
				bindAudioService();
				if (quranAudioPlayer != null && quranAudioPlayer.isPaused()) {
					quranAudioPlayer.resume();
					onActionPlay();
				} else {
					if (!QuranUtils.haveAyaPositionFile()){
						showDownloadAyahInfoDialog();
					}
					else showPlayDialog();
				}
			} else if (action.equalsIgnoreCase(ACTION_PAUSE) && quranAudioPlayer != null) {
				quranAudioPlayer.pause();
				onActionStop();
			} else if (action.equalsIgnoreCase(ACTION_NEXT) && quranAudioPlayer != null) {
				// Quick fix to switch actions 
				lastAyah = QuranAudioLibrary.getPreviousAyahAudioItem(this, getLastAyah());
				int page = QuranInfo.getPageFromSuraAyah(lastAyah.getSoura(), lastAyah.getAyah());
				if (page == (quranPageFeeder.getCurrentPagePosition() - 1)) 
					quranPageFeeder.goToPreviousPage();
				if (quranAudioPlayer != null && quranAudioPlayer.isPlaying())
					quranAudioPlayer.play(lastAyah);
			} else if (action.equalsIgnoreCase(ACTION_PREVIOUS) && quranAudioPlayer != null) {
				lastAyah = QuranAudioLibrary.getNextAyahAudioItem(this,
						getLastAyah());
				int page = QuranInfo.getPageFromSuraAyah(lastAyah.getSoura(), lastAyah.getAyah());
				if (page == (quranPageFeeder.getCurrentPagePosition() + 1)) 
					quranPageFeeder.goToNextpage();
				if (quranAudioPlayer != null && quranAudioPlayer.isPlaying())
					quranAudioPlayer.play(lastAyah);
			} else if (action.equalsIgnoreCase(ACTION_STOP) && quranAudioPlayer != null) {
				lastAyah = null;
				quranAudioPlayer.stop();
				onActionStop();
				unBindAudioService();
				quranPageFeeder.unHighlightAyah();
			} else if (action.equalsIgnoreCase(ACTION_CHANGE_READER)){
				showChangeReaderDialog();
			} else if (action.equalsIgnoreCase(ACTION_JUMP_TO_AYAH)) {
				showJumpToAyahDialog();
			} else if (action.equalsIgnoreCase(ACTION_REPEAT)) {
				showAudioRepeatsDialog();
			}
		} else { // no audio related tasks to just flip to requested page
			Bundle extras = intent.getExtras();
			int page = extras != null? extras.getInt("page") :
				QuranSettings.getInstance().getLastPage();
			
			// If still no page saved
			if (page == ApplicationConstants.NO_PAGE_SAVED) {
				page = ApplicationConstants.PAGES_FIRST;
			}
			
			quranPageFeeder.jumpToPage(page);
		}
	}

	private void onActionPlay() {
		actionBar.removeAllActions();
		for (String action : actionBarActions.keySet()) {
			if (ACTION_PLAY.equals(action))
				continue;
			actionBar.addAction(actionBarActions.get(action), 0);
		}
		playing =true;
	}

	private void onActionStop() {
		actionBar.removeAllActions();
		actionBar.addAction(actionBarActions.get(ACTION_PLAY), 0);
		actionBar.addAction(actionBarActions.get(ACTION_CHANGE_READER), 0);
		actionBar.addAction(actionBarActions.get(ACTION_JUMP_TO_AYAH), 0);
		actionBar.addAction(actionBarActions.get(ACTION_REPEAT), 0);
		playing = false;
	}

	private void showDownloadAyahInfoDialog(){
		AlertDialog.Builder dialog = new AlertDialog.Builder(QuranViewActivity.this);
		dialog.setTitle(R.string.downloadPrompt_title)
		.setMessage(R.string.downloadHighlightingData)
		.setPositiveButton(R.string.downloadPrompt_ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				downloadTranslation(QuranUtils.getAyaPositionFileUrl(), "ayahinfo.db.zip");
			}
		})
		.setNegativeButton(R.string.downloadPrompt_no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				showPlayDialog();
			}
		});
		
		dialog.show();
	}
	
	private void showPlayDialog() {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		LayoutInflater li = LayoutInflater.from(this);
		final View view = li.inflate(R.layout.dialog_play, null);
		dialog.setView(view);
		final Map<Integer, RadioButton> suraButtons = initPlayRadioButtons(view, quranPageFeeder.getCurrentPagePosition());
		dialog.setPositiveButton(getString(R.string.dialog_play), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				RadioGroup radio = (RadioGroup) view.findViewById(R.id.radioGroupPlay); 
				int checkedRbId = radio.getCheckedRadioButtonId();
				if (R.id.radioPlayPage == checkedRbId) {
					// TODO Should have a method to get first ayah on page -AF
					Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
					lastAyah = QuranAudioLibrary.getAyahItem(getApplicationContext(),
							pageBounds[0], pageBounds[1], getQuranReaderId());
				} else if (R.id.radioPlayLast == checkedRbId) {
					// TODO Method to return AyahItem instead of one for Ayah + one for sura -AF
					int lastPlayedSura = QuranSettings.getInstance().getLastPlayedSura();
					int lastPlayedAyah = QuranSettings.getInstance().getLastPlayedAyah();
					if (lastPlayedSura > 0 && lastPlayedAyah >= 0)
						lastAyah = QuranAudioLibrary.getAyahItem(getApplicationContext(),
								lastPlayedSura, lastPlayedAyah, getQuranReaderId());
				} else {
					for (Integer sura : suraButtons.keySet()) {
						if (radio.getCheckedRadioButtonId() == suraButtons.get(sura).getId()) {
							int ayah = sura == 1 || sura == 9 ? 1 : 0;
							lastAyah = QuranAudioLibrary.getAyahItem(getApplicationContext(),
									sura, ayah, getQuranReaderId());
							break;
						}
					}
				}
				onActionPlay();
				dialog.dismiss();
				quranAudioPlayer.enableRemotePlay(false);
				playAudio(lastAyah);
			}
		});
		dialog.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				unBindAudioService();
			}
		});
		
		AlertDialog diag = dialog.create();
		diag.show();
	}

	private void showDownloadDialog(final AyahItem i) {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		LayoutInflater li = LayoutInflater.from(this);
		final View view = li.inflate(R.layout.dialog_download, null);
		Spinner s = (Spinner) view.findViewById(R.id.spinner);
		if (s != null)
			s.setSelection(getReaderIndex(getQuranReaderId()));
		dialog.setView(view);
		initDownloadRadioButtons(view, getLastAyah());
		dialog.setPositiveButton(getString(R.string.dialog_download),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						// get reader id
						Spinner s = (Spinner) view.findViewById(R.id.spinner);
						lastAyah = i;
						if (s != null) {
							if (s.getSelectedItemPosition() != Spinner.INVALID_POSITION) {
								setReaderId(s.getSelectedItemPosition());
								// reader is not default reader
								if (getQuranReaderId() != i.getQuranReaderId()) {
									lastAyah = QuranAudioLibrary.getAyahItem(
											getApplicationContext(), i
													.getSoura(), i.getAyah(),
											getQuranReaderId());
								}
							}
						}
						RadioGroup radio = (RadioGroup) view.findViewById(R.id.radioGroupDownload); 
						
						switch (radio.getCheckedRadioButtonId()) {						
						case R.id.radioDownloadJuza:
							downloadJuza(getQuranReaderId(), 
									QuranInfo.getJuzFromPage(quranPageFeeder.getCurrentPagePosition()));
							break;
						case R.id.radioDownloadSura:
							downloadSura(getQuranReaderId(), lastAyah.getSoura());
							break;
						case R.id.radioDownloadPage:
							downloadPage(getQuranReaderId(), QuranInfo.getPageBounds(quranPageFeeder
									.getCurrentPagePosition()));
						default:
							break;
						}
						dialog.dismiss();
					}
				});
		dialog.setNeutralButton(getString(R.string.dialog_stream),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						// get reader id
						quranAudioPlayer.enableRemotePlay(true);
						Spinner s = (Spinner) view.findViewById(R.id.spinner);
						lastAyah = i;
						if (s != null) {
							if (s.getSelectedItemPosition() != Spinner.INVALID_POSITION) {								
								setReaderId(s.getSelectedItemPosition());
								// reader is not default reader
								if (getQuranReaderId() != i.getQuranReaderId()) {
									lastAyah = QuranAudioLibrary.getAyahItem(
											getApplicationContext(), i
													.getSoura(), i.getAyah(),
											getQuranReaderId());
								}
							}
						}
						if(lastAyah.getQuranReaderId() != getQuranReaderId())
							lastAyah.setReader(getQuranReaderId());
						quranAudioPlayer.play(lastAyah);
						dialog.dismiss();
					}
				});

		dialog.setNegativeButton(getString(R.string.dialog_cancel),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						onActionStop();
						dialog.dismiss();
						unBindAudioService();
					}
				});

		AlertDialog diag = dialog.create();
		diag.show();
	}


	private void showAudioRepeatsDialog(){
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		int numberOfRepeats = preferences.getInt("numberOfRepeats", 0);
		final NumberPickerDialog diag = new NumberPickerDialog(this, 1, numberOfRepeats);
		diag.setTitle(getString(R.string.dialog_repeat_title));
		diag.show();
		diag.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				int numberOfRepeats = diag.getSelectedNumber();
				if(quranAudioPlayer != null)
					quranAudioPlayer.setNumberOfRepeats(numberOfRepeats);
				// save repeats to preference file
				SharedPreferences preferences = getPreferences(MODE_PRIVATE);
				Editor editor = preferences.edit();
				editor.putInt("numberOfRepeats", numberOfRepeats);
				editor.commit();
			}
		});
	}
	
	// Returns a map of Sura Number to RadioButtons
	private Map<Integer, RadioButton> initPlayRadioButtons(View parent, int pageNum){
		RadioButton radioPage = (RadioButton) parent.findViewById(R.id.radioPlayPage);
		RadioButton radioLastAyah = (RadioButton) parent.findViewById(R.id.radioPlayLast);
		
		radioPage.setText(getString(R.string.play_dialog_page, new Object[]{pageNum}));
		int lastPlayedSura = QuranSettings.getInstance().getLastPlayedSura();
		int lastPlayedAyah = QuranSettings.getInstance().getLastPlayedAyah();
		if (lastPlayedSura > 0 && lastPlayedAyah >= 0)
			radioLastAyah.setText(ArabicStyle.reshape(getString(R.string.play_dialog_resume, 
					new Object[]{QuranInfo.getSuraName(lastPlayedSura - 1), lastPlayedAyah})));
		else
			radioLastAyah.setVisibility(View.GONE);
		
		Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
		RadioGroup rg = (RadioGroup)parent.findViewById(R.id.radioGroupPlay);
		Map<Integer, RadioButton> rbMap = new HashMap<Integer, RadioButton>();
		for (int i = pageBounds[0]; i <= pageBounds[2]; i++) {
			RadioButton rb = new RadioButton(getApplicationContext());
			rb.setText(ArabicStyle.reshape(QuranInfo.getSuraTitle() + " " + QuranInfo.getSuraName(i-1)));
			rg.addView(rb);
			rbMap.put(i, rb);
		}
		rg.invalidate();
		return rbMap;
	}

	private void initDownloadRadioButtons(View parent, AyahItem ayahItem){
		RadioButton radioSura = (RadioButton) parent.findViewById(R.id.radioDownloadSura);
		RadioButton radioJuz = (RadioButton) parent.findViewById(R.id.radioDownloadJuza);
		RadioButton radioPage = (RadioButton) parent.findViewById(R.id.radioDownloadPage);
		
		radioSura.setText(ArabicStyle.reshape(QuranInfo.getSuraTitle() + " " 
				+ QuranInfo.getSuraName(ayahItem.getSoura() - 1)));
		radioJuz.setText(ArabicStyle.reshape(QuranInfo.getJuzTitle() + " " + QuranInfo.getJuzFromPage(
				QuranInfo.getPageFromSuraAyah(ayahItem.getSoura(), ayahItem.getAyah()))));
		radioPage.setText(getString(R.string.download_dialog_page, 
				new Object[]{quranPageFeeder.getCurrentPagePosition()}));
		
	}

	private void showJumpToAyahDialog() {
		final Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder
				.getCurrentPagePosition());
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		LayoutInflater li = LayoutInflater.from(this);
		final View view = li.inflate(R.layout.dialog_jump_to_ayah, null);
		
		final Spinner ayatSpinner = (Spinner) view.findViewById(R.id.spinner_ayat);
		final CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox_whole_quran);
		int startAyah = pageBounds[1];
		int endAyah = pageBounds[0] == pageBounds[2]? pageBounds[3] : QuranInfo.SURA_NUM_AYAHS[pageBounds[0] - 1];
		initAyatSpinner(ayatSpinner, startAyah, endAyah);
		
		final Spinner surasSpinner = (Spinner) view.findViewById(R.id.spinner_suras);
		//initSurasSpinner(surasSpinner, pageBounds[0], pageBounds[2]);
		initSurasSpinner(surasSpinner, 1, 114);
		surasSpinner.setSelection(pageBounds[0] - 1);
		surasSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void onItemSelected(AdapterView<?> adapter, View view,
					int position, long id) {
				HashMap<String, String>map = (HashMap<String, String>) adapter.getItemAtPosition(position);
				int suraIndex = Integer.parseInt(map.get("suraId"));
				int startAyah = suraIndex == pageBounds[0] && checkbox.isChecked() ? pageBounds[1] : 1;
				int endAyah = suraIndex == pageBounds[2] && checkbox.isChecked() ? pageBounds[3] : QuranInfo.SURA_NUM_AYAHS[suraIndex - 1];
				initAyatSpinner(ayatSpinner, startAyah, endAyah);
				if (suraIndex == pageBounds[0]) {
					int selection = checkbox.isChecked()? 0 : pageBounds[1] - 1;
					ayatSpinner.setSelection(selection);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
		});
			
		checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(!isChecked){
					initSurasSpinner(surasSpinner, 1, 114);
					surasSpinner.setSelection(pageBounds[0] - 1);
				}
				else
					initSurasSpinner(surasSpinner, pageBounds[0], pageBounds[2]);
			}
		});
		dialogBuilder.setView(view);
		dialogBuilder.setMessage(getString(R.string.dialog_jump_ayah));
		dialogBuilder.setPositiveButton(getString(R.string.menu_jump),
				new DialogInterface.OnClickListener() {

					@SuppressWarnings("unchecked")
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// get sura
						HashMap<String, String> suraData =
							(HashMap<String, String>) surasSpinner.getSelectedItem();
						int sura = Integer.parseInt(suraData.get("suraId"));
						Log.d("Ayah", "Spinner ayay values " + ayatSpinner.getSelectedItem().toString());
						Integer ayah = (Integer) ayatSpinner.getSelectedItem();
						lastAyah = QuranAudioLibrary.getAyahItem(getApplicationContext(), 
								sura, ayah, getQuranReaderId());
						if (quranAudioPlayer != null && quranAudioPlayer.isPlaying()){
							quranAudioPlayer.stop();
							quranAudioPlayer.play(lastAyah);	
						}
						jumpTo(QuranInfo.getPageFromSuraAyah(sura, ayah));
					}
				});
		dialogBuilder.setNegativeButton(getString(R.string.dialog_cancel), null);
		dialogBuilder.show();
	}
	
	private void initSurasSpinner(final Spinner spinner, int startSura, int endSura){
		String[] from = new String[] {"suraName"};
		int[] to = new int[] {android.R.id.text1 };

		ArrayList<HashMap<String, String>> data = new ArrayList<HashMap<String, String>>();
		for (int i = startSura; i <= endSura; i++) {
			HashMap<String, String> hash = new HashMap<String, String>();
			hash.put("suraName", ArabicStyle.reshape(QuranInfo.getSuraName(i-1)));
			hash.put("suraId", ""+i);
			data.add(hash);
		}
		SimpleAdapter sa = new SimpleAdapter(this, data, 
				android.R.layout.simple_spinner_item, from, to);
		sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		SimpleAdapter.ViewBinder viewBinder = new SimpleAdapter.ViewBinder() {
			 
            public boolean setViewValue(View view, Object data,
                    String textRepresentation) {
                TextView textView = (TextView) view;
                textView.setText(textRepresentation);
                return true;
            }
        };
        sa.setViewBinder(viewBinder);
		spinner.setAdapter(sa);

	}
	
	
	private void initAyatSpinner(final Spinner spinner, int startAyah, int endAyah){
		ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item);
		for(int i = startAyah; i <= endAyah; i++)
			adapter.add(i);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
	}
	
	private void showChangeReaderDialog() {
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		LayoutInflater li = LayoutInflater.from(this);
		final View view = li.inflate(R.layout.dialog_download, null);
		Spinner s = (Spinner) view.findViewById(R.id.spinner);
		View v = view.findViewById(R.id.scrollViewDownload);
		v.setVisibility(View.GONE);
		s.setSelection(getReaderIndex(getQuranReaderId()));
		dialogBuilder.setView(view);
		dialogBuilder.setMessage(getString(R.string.dialog_select_reciter));
		dialogBuilder.setPositiveButton(getString(R.string.dialg_ok),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Spinner s = (Spinner) view.findViewById(R.id.spinner);
						if (s != null
								&& s.getSelectedItemPosition() != Spinner.INVALID_POSITION) {
							setReaderId(s.getSelectedItemPosition());
						}
					}
				});
		dialogBuilder.setNegativeButton(getString(R.string.dialog_cancel), null);
		dialogBuilder.show();
	}

	protected void initQuranPageFeeder() {
		if (quranPageFeeder == null) {
			Log.d(TAG, "Quran Feeder instantiated...");
			quranPageFeeder = new QuranPageFeeder(this, quranPageCurler,
					R.layout.quran_page_layout);
		} else {
			quranPageFeeder.setContext(this, quranPageCurler);
		}
	}

	private void unBindAudioService() {
		if (bounded) {
			// Detach our existing connection.
			unbindService(conn);
			if (quranAudioPlayer != null)
				quranAudioPlayer.setAyahCompleteListener(null);
			stopService(new Intent(getApplicationContext(), QuranAudioService.class));
			bounded = false;
		}
	}

	private void bindAudioService() {
		if (!bounded) {
			Intent serviceIntent = new Intent(getApplicationContext(),
					QuranAudioService.class);
			startService(serviceIntent);
			bounded = bindService(serviceIntent, conn, BIND_AUTO_CREATE);
			Log.d("QuranView", "Audio service bounded: " + bounded);
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putBoolean(IS_PLAYING, quranAudioPlayer != null && quranAudioPlayer.isPlaying());
	}
	
	@Override
	public void onDestroy(){
		if (bounded){
			unbindService(conn);
			quranAudioPlayer.setAyahCompleteListener(null);
		}
		super.onDestroy();
	}

	private void playAudio(AyahItem ayah) {
		if (quranAudioPlayer != null) {
			if (ayah == null) {
				Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder
						.getCurrentPagePosition());
				ayah = QuranAudioLibrary.getAyahItem(getApplicationContext(),
						pageBounds[0], pageBounds[1], getQuranReaderId());
			}
			quranAudioPlayer.play(ayah);
		}
	}

	@Override
	public boolean onAyahComplete(AyahItem ayah, AyahItem nextAyah) {
		lastAyah = ayah;
		if (nextAyah.getQuranReaderId() != getQuranReaderId()
				&& quranAudioPlayer != null && quranAudioPlayer.isPlaying()) {
			quranAudioPlayer.stop();
			lastAyah = QuranAudioLibrary.getAyahItem(this, nextAyah.getSoura(),
					nextAyah.getAyah(), getQuranReaderId());
			quranAudioPlayer.play(lastAyah);
			
			quranPageFeeder.unHighlightAyah();
			return false;
		}
		return true;
	}

	@Override
	public void onAyahNotFound(AyahItem ayah) {
		lastAyah = ayah;
		showDownloadDialog(ayah);
	}

	@Override
	protected void loadLastNonConfigurationInstance() {
		super.loadLastNonConfigurationInstance();
		Object[] saved = (Object[]) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			quranPageFeeder = (QuranPageFeeder) saved[0];
		}
	}

	@Override
	protected void onFinishDownload() {
		super.onFinishDownload();
		if (quranAudioPlayer != null) {
			quranAudioPlayer.enableRemotePlay(false);
			playAudio(getLastAyah());
		}
	}
	

	@Override
	protected void onDownloadCanceled() {
		super.onDownloadCanceled();
		if (quranAudioPlayer != null) {
			quranAudioPlayer.stop();
		}
		onActionStop();
	}

	private int getQuranReaderId() {
		return QuranSettings.getInstance().getLastReader();
	}

	private void setReaderId(int readerNamePosition) {
		currentReaderId = getResources().getIntArray(R.array.quran_readers_id)[readerNamePosition];
		QuranSettings.getInstance().setLastReader(currentReaderId);		
	}

	private int getReaderIndex(int readerId) {
		int[] ids = getResources().getIntArray(R.array.quran_readers_id);
		for (int i=0 ; i<ids.length ; i++) {
			if (ids[i] == readerId) {
				return i;
			}
		}
		return 0;
	}

	@Override
	public void onUnknownError(AyahItem ayah) {
		lastAyah = ayah;
		quranAudioPlayer.stop();
		onActionStop();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("An error occured");
		builder.setNegativeButton("Ok", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		builder.show();
	}
	
	private AyahItem getLastAyah() {
		AyahItem last = lastAyah; 
		if (quranAudioPlayer != null && quranAudioPlayer.isPlaying()) {
			last = quranAudioPlayer.getCurrentAyah();
		}
		
		// TODO Should have a method to get first ayah on page -AF
		if (last == null) {
			Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
			last = QuranAudioLibrary.getAyahItem(getApplicationContext(),
					pageBounds[0], pageBounds[1], getQuranReaderId());
		}
		
		lastAyah = last;
		return lastAyah;
	}

	@Override
	public void onConnectionLost(AyahItem ayah) {
		lastAyah = ayah;
		connect();
	}
	
	@Override
	protected void onConnectionSuccess() {
		super.onConnectionSuccess();
		if (lastAyah != null) {
			quranAudioPlayer.enableRemotePlay(true);
			quranAudioPlayer.play(getLastAyah());
		}
	}

	@Override
	public void onAyahPlay(AyahItem ayah) {
		int page = QuranInfo.getPageFromSuraAyah(ayah.getSoura(), ayah.getAyah());

		if (quranPageFeeder.getCurrentPagePosition() != page) {
			quranPageFeeder.jumpToPage(page);
			updatePageInfo(page);
		}
		try{
			quranPageFeeder.highlightAyah(ayah.getSoura(), ayah.getAyah());
		} catch(Exception e){
			Log.e("HighLight", "Exception while highlighting ayah " + e.toString() + e.getMessage());
		}
		
		QuranSettings.getInstance().setLastPlayedAyah(ayah.getSoura(), ayah.getAyah());
		
		if (!playing)
			onActionPlay();
	}
}
