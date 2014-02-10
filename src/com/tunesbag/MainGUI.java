package com.tunesbag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.lang.Enum;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entagged.audioformats.AudioFile;
import entagged.audioformats.AudioFileIO;
import entagged.audioformats.Tag;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.graphics.PorterDuff;

public class MainGUI extends Activity {
	static final int PROGRESS_DIALOG = 0;
	
	static WebView browser;
	static ImageButton prevButton;
	static ImageButton playButton;
	static ImageButton nextButton;
	static SeekBar pbar;
	static TextView precedingtime;
	static TextView remainingtime;
	private static RelativeLayout playerbuttons;
	private static RelativeLayout actualplaybackbutton;
	private Button actualpButton;
	private ImageButton shufflebutton;
	private ImageButton repeatbutton;
	private static PlayerInterface control;
	private static Playlist playlist = null;
	private static String pathtocachedir;
	private static ArrayList<Playlist> downloadlists;
	private static int actualdownloadlistsindex = 0;
	private static int index=0;
	private Intent playIntent;
	private static SharedPreferences wwprefs;
	private static String WEBVIEW_PREFS = "WEBVIEW_PREFS";
	private static SharedPreferences fileprefs;
	private static String FILE_PREFS = "WEBVIEW_PREFS";
	private static SharedPreferences appprefs;
	private static String APP_PREFS = "APP_PREFS";
	private String state = Environment.getExternalStorageState();
	private TelephonyManager tm; 
	private WifiManager wm;
	static Context con;
	private boolean loadData = true;
	private static int repeatmode = 0;
	private static boolean shuffle = false;
	public static final int REPEATMODE_NONE = 0;
	public static final int REPEATMODE_ALL = 1;
	public static final int REPEATMODE_ONE = 2;
	public static final boolean SHUFFLE_OFF = false;
	public static final boolean SHUFFLE_ON = true;
	
	private final static Handler handler = new Handler();
	
	private static boolean savefile = false;
	private static boolean playonstart = false;
	private static int bitrate = 48;
	
	private static boolean wifienabled = false; 
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.main);
	    browser=(WebView)findViewById(R.id.webkit);
		prevButton=(ImageButton)findViewById(R.id.prevButton);
		playButton=(ImageButton)findViewById(R.id.playpauseButton);
		nextButton=(ImageButton)findViewById(R.id.nextButton);
		precedingtime=(TextView)findViewById(R.id.precedingtime);
		remainingtime=(TextView)findViewById(R.id.remainingtime); 
		pbar=(SeekBar)findViewById(R.id.pbar);
		playerbuttons=(RelativeLayout)findViewById(R.id.playerbuttons);
		actualplaybackbutton=(RelativeLayout)findViewById(R.id.actualplaybackbutton);
		actualpButton=(Button)findViewById(R.id.actualpButton);
		shufflebutton=(ImageButton)findViewById(R.id.shufflebutton);
		repeatbutton=(ImageButton)findViewById(R.id.repeatbutton);
	    //requestWindowFeature(Window.FEATURE_NO_TITLE); 
		actualpButton.getBackground().setColorFilter(0xFF222222, PorterDuff.Mode.MULTIPLY);
		
	    con = getApplicationContext();
	    pathtocachedir = con.getCacheDir().getAbsolutePath();
	    wwprefs = getSharedPreferences(WEBVIEW_PREFS, Activity.MODE_PRIVATE);
	    fileprefs = getSharedPreferences(FILE_PREFS, Activity.MODE_PRIVATE);
	    appprefs = getSharedPreferences(APP_PREFS, Activity.MODE_PRIVATE);
	    loadSettings();
	    //pbar.setProgressDrawable(getResources().getDrawable(R.drawable.icon));
	    loadURL();
	    addListener();
	    
	    playIntent = new Intent(this, Player.class);
		bindService(playIntent, playConnection, Context.BIND_AUTO_CREATE);
		
		IntentFilter mediaFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
		mediaFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
	    registerReceiver(mediabuttonreceiver, mediaFilter);
		registerReceiver(playpausereceiver, new IntentFilter(PlayerWidget.ACTION_WIDGET_PLAY_PAUSE));
		registerReceiver(nextreceiver, new IntentFilter(PlayerWidget.ACTION_WIDGET_NEXT));
		wm = (WifiManager) getSystemService(WIFI_SERVICE);
		Log.i(getClass().getName(), "Wifi IP: "+ wm.getConnectionInfo().getIpAddress()+"");
		if(wm.getConnectionInfo().getIpAddress()!=0)
			wifienabled=true;
		//wifienabled = wm.isWifiEnabled();
		tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		
		
		tm.listen(callstatelistener, PhoneStateListener.LISTEN_CALL_STATE);
	    
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			File dir = new File(Environment.getExternalStorageDirectory()+"/tunesbag/tosend"); 
			if( dir.exists() ) {
				File[] files = dir.listFiles();
				for(int i=0; i<files.length; i++) {
					files[i].delete();
			        
				}
			}
		}
		musicScan();
	}

	
	
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	//notificationManager.cancel(Player.NOTIFICATION_ID);
    	unbindService(playConnection);
    	unregisterReceiver(playpausereceiver);
    	unregisterReceiver(nextreceiver);
    	try {
			control.stopService();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			File dir = new File(Environment.getExternalStorageDirectory()+"/tunesbag/tosend"); 
			if( dir.exists() ) {
				File[] files = dir.listFiles();
				for(int i=0; i<files.length; i++) {
					files[i].delete();
			        
				}
			}
		}			
    }
    
    public void onPause() {	
    	super.onPause();
    	if(!loadData&& playlist!=null) {
    		try {
				control.setWidgetInfo();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
     
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
		return true;   	
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	JSONObject json = new JSONObject();
		String jsonurlenc;
		
    	switch (item.getItemId()) {
    	case R.id.menu_preferences :
    		try {
				json.put("action", "preferences");
				jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
				Log.i(getClass().getName(), json.toString());						
				browser.loadUrl("javascript:message(\"menuclick\",\""+ jsonurlenc +"\")");
				setMediaButtons(false);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
			
    	case R.id.menu_createplist :
    		try {
				json.put("action", "createplist");
				jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
				Log.i(getClass().getName(), json.toString());						
				browser.loadUrl("javascript:message(\"menuclick\",\""+ jsonurlenc +"\")");
				setMediaButtons(false);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
			
    	case R.id.menu_offlinesync :
    		try {
				json.put("action", "offlinesync");
				jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
				Log.i(getClass().getName(), json.toString());						
				browser.loadUrl("javascript:message(\"menuclick\",\""+ jsonurlenc +"\")");
				setMediaButtons(false);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
			
    	case R.id.menu_mainscreen :
    		try {
				json.put("action", "mainscreen");
				jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
				Log.i(getClass().getName(), json.toString());						
				browser.loadUrl("javascript:message(\"menuclick\",\""+ jsonurlenc +"\")");
				setMediaButtons(false);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
			
    	case R.id.menu_about :
    		try {
				json.put("action", "about");
				jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
				Log.i(getClass().getName(), json.toString());						
				browser.loadUrl("javascript:message(\"menuclick\",\""+ jsonurlenc +"\")");
				setMediaButtons(false);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
			
    	case R.id.menu_sendtrack :
    		
    		try {
    			
				String filename = control.getSendTrack();
				if (filename == null) {
					Toast.makeText(con, getString(R.string.notrackselected), Toast.LENGTH_SHORT).show();
				} else if(filename.equals("filenotexists")) {
					Toast.makeText(con, getString(R.string.tracknotexists), Toast.LENGTH_SHORT).show();
				} else {
					Log.i(getClass().getName(), "file://"+Environment.getExternalStorageDirectory()+"/tunesbag/tosend/"+filename);
					Intent sendIntent = new Intent(Intent.ACTION_SEND); 
		            sendIntent.putExtra(Intent.EXTRA_TEXT, "email text"); 
		            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Subject"); 
		            
		            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse 
		            		("file://"+Environment.getExternalStorageDirectory()+"/tunesbag/tosend/"+filename)); 
		            /*		
		            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse 
		            		("file://"+Environment.getExternalStorageDirectory()+"/test.txt")); */
		            //sendIntent.setType("message/rfc822"); 
		            sendIntent.setType("audio/mp3"); 
		            startActivity(Intent.createChooser(sendIntent, getString(R.string.sharedialog_title))); 
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    		return true;
    	case R.id.menu_cancelplaylistdownload :
    		try {
				control.cancelPlaylistDownload();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
    		return true;
    	case R.id.menu_set_ringtone :
    		if (playlist!=null) {
	    		Trackinfo ringtrack = playlist.getElement(Player.getIndex());
	    		File k = new File("/sdcard/tunesbag", ringtrack.getCacheFileName() + ".cache"); // path is a file to /sdcard/media/ringtone
	    		if (k.exists()) {
		    		ContentValues values = new ContentValues();
		    		values.put(MediaStore.MediaColumns.DATA, k.getAbsolutePath());
		    		values.put(MediaStore.MediaColumns.TITLE, ringtrack.getName());
		    		values.put(MediaStore.MediaColumns.SIZE, ringtrack.getSize());
		    		values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/aac");
		    		values.put(MediaStore.Audio.Media.ARTIST, ringtrack.getArtist());
		    		values.put(MediaStore.Audio.Media.DURATION, ringtrack.getDuration());
		    		values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
		    		values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
		    		values.put(MediaStore.Audio.Media.IS_ALARM, false);
		    		values.put(MediaStore.Audio.Media.IS_MUSIC, false);
		
		    		//Insert it into the database
		    		Uri uri = MediaStore.Audio.Media.getContentUriForPath(k.getAbsolutePath());
		    		Uri newUri = this.getContentResolver().insert(uri, values);
		
		    		RingtoneManager.setActualDefaultRingtoneUri(
		    		  this,
		    		  RingtoneManager.TYPE_RINGTONE,
		    		  newUri
		    		);
	    		} else {
	    			Toast.makeText(con, getString(R.string.tracknotexists), Toast.LENGTH_SHORT).show();
	    		}
    		} else {
    			Toast.makeText(con, getString(R.string.notrackselected), Toast.LENGTH_SHORT).show();
    		}
    		return true;
		default:
			return super.onOptionsItemSelected(item);
    	}
    	
    	
    }
    
    private void loadSettings() {
    	if (new File("data/data/com.tunesbag/settings.ser").exists() && loadData) {
    		try {
				ObjectInputStream is = new ObjectInputStream(new FileInputStream("data/data/com.tunesbag/settings.ser"));
				savefile = Boolean.parseBoolean((String) is.readObject());
				playonstart = Boolean.parseBoolean((String) is.readObject());
				bitrate = Integer.parseInt((String) is.readObject());
				is.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
    	}
    }
    
    private void musicScan() {
    	new Thread (new Runnable() {

			public void run() {
				while(true) {
					try {
						Thread.sleep(1000*60*30);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					new ListMusicFiles().start();
				}
			}
    		
    	}).start();
    }
    
    
    private void loadURL() {
    	
    	browser.setWebViewClient(new BrowserClient());
    	browser.setWebChromeClient(new BrowserChromeClient());
    	WebSettings browserset = browser.getSettings();
    	browserset.setLoadsImagesAutomatically(true);
    	browserset.setJavaScriptEnabled(true);
    	browserset.setJavaScriptCanOpenWindowsAutomatically(true);
    	browserset.setDatabaseEnabled(true);
    	browserset.setDatabasePath("data/data/com.tunesbag/databases");
    	browserset.setDomStorageEnabled(true);
    	browserset.setRenderPriority(WebSettings.RenderPriority.HIGH);
    	browserset.setSupportZoom(false);
    	browserset.setUserAgentString( browserset.getUserAgentString() + " (tunesBag ClientApp)" );
    	browserset.setAllowFileAccess(true);
    	browserset.setSavePassword(false);
    	browserset.setSupportMultipleWindows(false);
    	browserset.setAppCacheEnabled(true);
    	browserset.setAppCachePath("");
    	browserset.setAppCacheMaxSize(5*1024*1024);
    	browserset.setCacheMode(browserset.LOAD_CACHE_ELSE_NETWORK);
    	    	
    	browser.addJavascriptInterface(new WebComm(), "webcomm");
    	//browser.loadUrl(getString(R.string.tunesBagURL));    	
    	
    	browser.setScrollbarFadingEnabled(true);
         //webkit.setVerticalScrollBarEnabled(false);
         //webkit.setHorizontalScrollBarEnabled(false);
         
    	browser.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY) ;
    	
    	// browser.loadUrl(appprefs.getString("lasturl", getString(R.string.tunesBagURL)));
    	   
    	browser.loadUrl("file:///android_asset/html/start.html?loading=true&");
    	
    	
    }
    
    private void addListener() {
    	
    	prevButton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {

				try {
	    			control.previousTrack();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
					
			}
		});
    	
    	playButton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {

				try {
					control.pauseresumeTrack();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
    	
    	nextButton.setOnClickListener(new View.OnClickListener() {
    		
			public void onClick(View v) {
				try {
	    			control.nextTrack();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}						
			}
		});
    	
    	actualpButton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				if (playlist!=null) {
					try {
						JSONObject json = new JSONObject();
						String jsonurlenc;
						json.put("entrykey", playlist.getElement(Player.getIndex()).getentryKey());
						jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
						Log.i(getClass().getName(), json.toString());
						MainGUI.browser.loadUrl("javascript:message(\"shownowplaying\",\""+ jsonurlenc +"\")");
						setMediaButtons(true);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} 
				} else {
					Toast.makeText(con, getString(R.string.notrackselected), Toast.LENGTH_SHORT).show();
				}
			}
		});
    	
    	shufflebutton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				shuffle = !shuffle;
				
				if (shuffle) {
					shufflebutton.setImageResource(R.drawable.shuffle_on);
					Toast.makeText(con, getString(R.string.shuffle_on), Toast.LENGTH_SHORT).show();
				} else {
					shufflebutton.setImageResource(R.drawable.shuffle_off);
					Toast.makeText(con, getString(R.string.shuffle_off), Toast.LENGTH_SHORT).show();
				}
				
				try {
					control.updateGUI();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
    		
    	});
    	
    	repeatbutton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				if (repeatmode==REPEATMODE_ONE) {
					repeatmode=REPEATMODE_NONE;
				} else {
					repeatmode++;
				}
					
				if(repeatmode==REPEATMODE_NONE) {
					repeatbutton.setImageResource(R.drawable.repeat_none);
					Toast.makeText(con, getString(R.string.repeat_none), Toast.LENGTH_SHORT).show();
				} else if(repeatmode==REPEATMODE_ALL) {
					repeatbutton.setImageResource(R.drawable.repeat_all);
					Toast.makeText(con, getString(R.string.repeat_all), Toast.LENGTH_SHORT).show();
				} else if(repeatmode==REPEATMODE_ONE) {
					repeatbutton.setImageResource(R.drawable.repeat_one);
					Toast.makeText(con, getString(R.string.repeat_one), Toast.LENGTH_SHORT).show();
				}
				
				try {
					control.updateGUI();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
    		
    	});
    	
    	pbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			int progress;
    		
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				try {
					control.seekTo(progress);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				try {
					control.beforeseekTo();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			public void onProgressChanged(SeekBar seekBar, int _progress,
					boolean fromUser) {
				
				if (fromUser) {
					progress = _progress;
				}
		
			}
		});
    }
    
    public static int getRepeatMode() {
    	return repeatmode;
    }
    
    public static boolean getShuffleMode() {
    	return shuffle;
    }
    
    public static void setSettings(boolean _savefile, boolean _playonstart, int _bitrate) {
    	savefile = _savefile;
    	playonstart = _playonstart;
    	bitrate = _bitrate;
    }
    
    public static boolean getSavefile() {
    	return savefile;
    }
    
    public static boolean getPlayonstart() {
    	return playonstart;
    }
    
    public static int getBitrate() {
    	return bitrate;
    }
    
    public static boolean getWifiState() {
    	return wifienabled;
    }
    
    public static void setMediaButtons(boolean visibility) {
    	if (visibility) {
    		playerbuttons.setVisibility(View.VISIBLE);
    		actualplaybackbutton.setVisibility(View.INVISIBLE);
    	} else {
    		actualplaybackbutton.setVisibility(View.VISIBLE);
    		playerbuttons.setVisibility(View.INVISIBLE);
    	}
    }
    
    public static void setPlaylist(Playlist l, int index) {
    	playlist =l;
    	try {
			control.preparePlayer(playlist.getList(), pathtocachedir, index);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Runnable updater = new Runnable() {
			public void run() {
				setMediaButtons(true);
			}
		};
		handler.post(updater);
    }
    
    public static Playlist getPlaylist() {
    	return playlist;
    }
    
    public static Playlist getDownloadlist() {
    	return downloadlists.get(actualdownloadlistsindex);
    }
    
    public static SharedPreferences getWWSharedPreferences() {
    	return wwprefs;
    }
    
    public static void setDownloadList(ArrayList<Playlist> ti){
    	downloadlists = ti;
    	try {
    		for (actualdownloadlistsindex = 0; actualdownloadlistsindex < downloadlists.size(); actualdownloadlistsindex++) {
    			control.downloadTracks(downloadlists.get(actualdownloadlistsindex).getList(), pathtocachedir);
    		}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public static void setIndex(int _index) {
    	index =_index;
    	try {
			control.setTrack(_index);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Runnable updater = new Runnable() {
			public void run() {
				setMediaButtons(true);
			}
		};
		handler.post(updater);
    }
    
    public void moveActivityBack() {
    	moveTaskToBack(false);
    }
    
    public void showMessageToast(String message) {
    	Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    final class BrowserChromeClient extends WebChromeClient {
  
        public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,  
                long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater)  
        {  
        	// updated to 20 MB
            quotaUpdater.updateQuota(20480100/*204801*/);  
        } 
    }
    
    private class BrowserClient extends WebViewClient {
    	
    	/** Loads a url in the webview */
    	@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
    		if (url.contains("tbopenextern")) {
    			return false;
    		} else {    		
				browser.loadUrl(url);
				return true ;
    		}
		}
    	
    	/** Called when a page finished loading */
    	@Override
    	public void onPageFinished (WebView view, String url) {
    		Log.i(getClass().getName(), "URL: " + url);
    		
    		if(url.contains("tb:nowplaying")) 
    			setMediaButtons(true);
    		
    		if(url.contains("savelocation=true")) {
	    		SharedPreferences.Editor editor = appprefs.edit();
				editor.putString("lasturl", url);
				editor.commit();
    		}
			
			if (new File("data/data/com.tunesbag/playerstate.ser").exists() && loadData) {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				new ListMusicFiles().start();
				tm.listen(phstatelistener, PhoneStateListener.LISTEN_SERVICE_STATE);
				try {
					
					ObjectInputStream is = new ObjectInputStream(new FileInputStream("data/data/com.tunesbag/playerstate.ser"));
					playlist = (Playlist) is.readObject();
		            index = Integer.parseInt((String) is.readObject());
		            repeatmode = Integer.parseInt((String) is.readObject());
		            shuffle = Boolean.parseBoolean((String) is.readObject());
		            is.close();
		            if (shuffle) {
						shufflebutton.setImageResource(R.drawable.shuffle_on);
						
					} else {
						shufflebutton.setImageResource(R.drawable.shuffle_off);
						
					}
		            
		            if(repeatmode==REPEATMODE_NONE) {
						repeatbutton.setImageResource(R.drawable.repeat_none);
						
					} else if(repeatmode==REPEATMODE_ALL) {
						repeatbutton.setImageResource(R.drawable.repeat_all);
						
					} else if(repeatmode==REPEATMODE_ONE) {
						repeatbutton.setImageResource(R.drawable.repeat_one);
						
					}
		            
		            Log.i(getClass().getName(), "repeatmode: " + repeatmode + " shuffle: " +shuffle);
		            setMediaButtons(true);
		            if (playonstart) {
		            	control.preparePlayer(playlist.getList(), pathtocachedir, index);
		            } else {
		            	control.preparePlayerNonPlay(playlist.getList(), pathtocachedir, index);
		            }
		            
        			
				} catch (Exception e) {
					Log.e(getClass().getName(), e.getMessage(), e);
				} 	finally {
					loadData = false;
				}
			}
			
    	}
	}
    
    /** Lets the browser go back */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {

			if (browser.canGoBack()) {
				// MainGUI.browser.loadUrl("javascript:message(\"goback\",\"\")");
				//browser.loadUrl("javascript:history.go(-1)");
				setMediaButtons(false);
				//TODO Testing
				browser.goBack();
				return true;
			} else {
				//return super.onKeyDown(KeyEvent.KEYCODE_HOME, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME));
				this.moveTaskToBack(false);
				return true;
			}
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}
    
    private ServiceConnection playConnection = new ServiceConnection() {
    	
    	/** Starts when Player is connected */
    	public void onServiceConnected(ComponentName className, IBinder service) {
    		control = PlayerInterface.Stub.asInterface(service);
    		
    		/*
    		String jsonplaylist = appprefs.getString("jsonplaylist", null);
        	int playlistindex = appprefs.getInt("playlistindex", -1);
        	
        	if (jsonplaylist != null || playlistindex != -1) {
        		WebComm webc = new WebComm();
        		try {
        			playlist = webc.makePlaylist(jsonplaylist);
        			index = playlistindex;
        			control.preparePlayer(playlist.getList(), pathtocachedir);
        			control.setTrack(playlistindex);
    			} catch (Exception e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
        	}
        	*/
		}
    	
    	/** Starts when Player is disconnected */
    	public void onServiceDisconnected(ComponentName className) {
			control = null;
		}
    };
    
    /** Called when play or pause is pressed on the widget */
    BroadcastReceiver playpausereceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				control.pauseresumeTrack();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	
    };
    
    /** Called when next is pressed on the widget */
    BroadcastReceiver nextreceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				control.nextTrack();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	
    };
    
    /** Called when a mediabutton is pressed */
    BroadcastReceiver mediabuttonreceiver = new BroadcastReceiver() {
 
   	    @Override
	    public void onReceive(Context context, Intent intent) {
	        String intentAction = intent.getAction();
  	        if (!Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {   	
	           return;    	
	        }
    	
	        KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);    	
	        if (event == null) {    	
	            return;    	
	        }
    	
	        int action = event.getAction();
	        if (action == KeyEvent.ACTION_DOWN) {
	        	event.getKeyCode();
	        	// do something
        		Log.i(getClass().getName(), "MediaButtonpressed: "+ event.getKeyCode());
        		try {
	        		switch (event.getKeyCode()) {
	        			case KeyEvent.KEYCODE_HEADSETHOOK:
	        			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
	        				control.pauseresumeTrack();
	        				break;
	        			case KeyEvent.KEYCODE_MEDIA_NEXT:
	        				control.nextTrack();
	        				break;
	        			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
	        				control.previousTrack();
	        				break;
	        		}
        		} catch(RemoteException e) {
        			Log.e(getClass().getName(), e.getMessage(), e);
        		}
	        }
	        abortBroadcast();
	    }    
	};
    
	/** Listens to the service state */
    PhoneStateListener phstatelistener = new PhoneStateListener() {
    	@Override
    	public void onServiceStateChanged (ServiceState serviceState) {
    		if (ServiceState.STATE_IN_SERVICE == serviceState.getState()) {
    			if(wm.getConnectionInfo().getIpAddress()!=0)
    				wifienabled=true;
    			int networkstatus = serviceState.getState();
    			int networktype = tm.getNetworkType();
    			Log.i(getClass().getName(), "Networkstate: " + networkstatus + ", Networktype: " 
    					+ networktype + ", Wifistate: "+wifienabled);

    			try {
					JSONObject json = new JSONObject();
					String jsonurlenc;
					json.put("network_type", networktype);
					json.put("network_status", networkstatus);
					json.put("wifi_active", wifienabled);
					jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
					Log.i(getClass().getName(), json.toString());				
					MainGUI.browser.loadUrl("javascript:message(\"connectionchanged\",\""+ jsonurlenc +"\")");
				} catch (Exception e) {
					Log.e(getClass().getName(), "Could not send message to browser", e);
				}
				if (networktype == TelephonyManager.NETWORK_TYPE_HSDPA || wifienabled)
				try {
					if (networktype == TelephonyManager.NETWORK_TYPE_HSDPA || wifienabled)
						control.setINTIAL_KB_BUFFER(MainGUI.getBitrate()*17/8);
					else if(networktype == TelephonyManager.NETWORK_TYPE_UMTS)
						control.setINTIAL_KB_BUFFER(MainGUI.getBitrate()*25/8);
					else
						control.setINTIAL_KB_BUFFER(MainGUI.getBitrate()*35/8);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
    		}
    	}
    };
    
    /** Listens to the call state */
    PhoneStateListener callstatelistener = new PhoneStateListener() {
    	@Override
    	public void onCallStateChanged(int state, String incomingNumber) {
    		if(control!=null && state==TelephonyManager.CALL_STATE_RINGING) {
    			/*
    			try {
					if(control!=null && control.getPlayerState()) {
						control.pauseresumeTrack();
					}
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				*/
    			try {
					control.onCall(true);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		} else if (control!=null && state==TelephonyManager.CALL_STATE_IDLE) {
    			/*
    			try {
					if(control!=null && !control.getPlayerState()) {
						control.pauseresumeTrack();
					}
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				*/
    			try {
					control.onCall(false);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}
    };
    
    /** Lists the music files of the sdcard and sends them to the webview */
    class ListMusicFiles extends Thread {
    	ArrayList<String> multimediaext = new ArrayList<String>();
    	
    	public void run() {
    		String filenamelist, oldfilenamelist, filenames[], oldfilenames[];
    		ArrayList<File> files = new ArrayList<File>(); 
    		ArrayList<File> oldfiles = new ArrayList<File>(); 
    		ArrayList<String> checksums = new ArrayList<String>();	
    		ArrayList<Trackinfo> offlineti = new ArrayList<Trackinfo>();
    		String[] mexts = {".aac", ".m4a", ".mp3", ".ogg"};
	        for (String mext : mexts) multimediaext.add(mext);
    		
    		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
    			JSONArray trackinfos = new JSONArray();
	    		filenamelist = scanDirectory(Environment.getExternalStorageDirectory());
	    		oldfilenamelist = fileprefs.getString("filenamelist", "");
	    		
	    		if(filenamelist!=null && !filenamelist.equals(oldfilenamelist)) {
		    		filenames = filenamelist.split("\\|");
		    		oldfilenames = oldfilenamelist.split("\\|");
		    		
		    		for (int i = 0; i < oldfilenames.length; i++) {
		    			File file = new File(oldfilenames[i]);
		    			if (isMusicFile(file)) {
		    				oldfiles.add(file);
		    			}
		    		}
		    		
		    		for (int i = 0; i < filenames.length; i++) {
		    			File file = new File(filenames[i]);
		    			if (isMusicFile(file)) {
		    				files.add(file);
		    				try {
								checksums.add(MD5Checksum.getMD5Checksum(file.getAbsolutePath()));
								Log.i(getClass().getName(), "checksum created");
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
		    				if (!oldfiles.contains(file)) {
		    					Trackinfo actualtrack = makeTrackinfo(file);
		    					offlineti.add(actualtrack);
		    					try {
		    						JSONObject tijson = new JSONObject();
		    						try {
		    							tijson.put("artist", actualtrack.getArtist());
		    						} catch (NullPointerException e) {
		    							tijson.put("artist", "");
		    						}
		    						try {
		    							tijson.put("album", actualtrack.getAlbum());
		    						} catch (NullPointerException e) {
		    							tijson.put("album", "");
		    						}
									try {
										tijson.put("name", actualtrack.getName());
									} catch (NullPointerException e) {
										tijson.put("name", "");
									}
			    					try {
			    						tijson.put("entryKey", actualtrack.getentryKey());
			    					} catch (NullPointerException e) {
			    						tijson.put("entryKey", "");
			    					}
			    					try {
			    						tijson.put("filename", actualtrack.getCacheFileName());
			    					} catch (NullPointerException e) {
			    						tijson.put("filename", "");
			    					}
			    					try {
			    						tijson.put("format", actualtrack.getFormat());
			    					} catch (NullPointerException e) {
			    						tijson.put("format", "");
			    					}
			    					try {
			    						tijson.put("bitrate", actualtrack.getBitrate());
			    					} catch (NullPointerException e) {
			    						tijson.put("bitrate", "");
			    					}
			    					try {
			    						tijson.put("duration", actualtrack.getDuration());
			    					} catch (NullPointerException e) {
			    						tijson.put("duration", "");
			    					}
			    					try {
			    						tijson.put("exits", actualtrack.getExists());
			    					} catch (NullPointerException e) {
			    						tijson.put("exits", "");
			    					}
			    					trackinfos.put(tijson);
								} catch (JSONException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
		    					
		    				}	    					
		    			}
		    		}
		    		
		    		try {
						JSONObject json = new JSONObject();
						//JSONArray fileentrykeys = new JSONArray(checksums);
						
						json.put("fileentrykeys", checksums);
						
						json.put("trackinfos", trackinfos);
						String jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
						Log.i(getClass().getName(), json.toString());						
						browser.loadUrl("javascript:message(\"submitlocalmetadata\",\""+ jsonurlenc +"\")");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		    		
		    		saveFilenamelist(filenamelist);	    		
	    		}
	    	}
    	}
    	
    	/** Saves the filenamelist */
    	private void saveFilenamelist(String filenamelist) {
    		SharedPreferences.Editor editor = fileprefs.edit();
    		editor.putString("filenamelist", filenamelist);
    		editor.commit();
    	}
    	
    	/** Scans a directory and its subdiectory for files */
		private String scanDirectory(File dir) {
	    	String result;
	    	File list[];

	        result = dir.getAbsolutePath();
	        try {
		        if (dir.isDirectory()) {
		        	list = dir.listFiles();
		        	
		            for (int i = 0; i < list.length; i++) {
	            		result = result + "|" + scanDirectory(list[i]);
		            }
		        	
	            }
		        return result;
	        } catch (NullPointerException e) {
        		Log.e(getClass().getName(), "Could not scan Directorys", e);
        		return null;
        	} 
	       	    	
	    }
	    
		/** returns true when the file is a music file */
	    private boolean isMusicFile(File in) {
	    	if (!in.isDirectory()) {
	    		try {
	    			String extension = in.getName().substring(in.getName().lastIndexOf(".")).toLowerCase();
	    			return multimediaext.contains(extension);  
	    		} catch (Exception e) {
	    			return false;
	    		} 

	    	} else
	    		return false;
	    }
	    
	    /** Reads the tag of a multimediafile */
	    private Trackinfo makeTrackinfo(File audiofile) {
	    	String artist, album, name, entryKey = null, filename = null, Format = null;
	    	int Bitrate = 0, duration = 0; 
	    	boolean exists;
	    	
	    	AudioFile f;
	    	Tag tag;
			try {
				f = AudioFileIO.read(audiofile);
				tag = f.getTag();
				try {
					artist = tag.getFirstArtist();
				} catch (Exception e) {
					artist = null;
				}
				
				try {
					album = tag.getFirstAlbum();
				} catch (Exception e) {
					album = null;
				}
				
				try {
					name = tag.getFirstTitle();
					if (name.equals("")) {
						name = audiofile.getName();
					}
				} catch (Exception e) {
					name = audiofile.getName();
				}
				
				try {
					entryKey = MD5Checksum.getMD5Checksum(audiofile.getAbsolutePath());
				} catch (Exception e) {
					entryKey = null;
				}
				
				try {
					filename = audiofile.getAbsolutePath();
				} catch (Exception e) {
					filename = null;
				}
				
				try {
					Format = audiofile.getName().substring(audiofile.getName().lastIndexOf(".")).toLowerCase();
				} catch (Exception e) {
					Format = null;
				}
				
				try {
					Bitrate = (int) f.getBitrate(); 
				} catch (Exception e) {
					Bitrate = 0;
				}
				
				try {
					duration = f.getLength();
				} catch (Exception e) {
					duration = 0;
				}
				Log.i(getClass().getName(),"Artist: "+ artist +", Album: "+ album +", Title: "+name);
				exists = true;
				return new Trackinfo(artist, album, name, entryKey, Bitrate, Format, duration, exists, filename);
				
			} catch (Exception e) {
				return null;
			}
					
	    }
	    
    }
}