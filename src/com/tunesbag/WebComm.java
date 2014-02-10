package com.tunesbag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

public class WebComm {
	private static String jsonpllist = "";
	private static Playlist playlist;
	private Trackinfo[] ts;
	private String username;
	private String remotekey;
	
	public void playPlaylist(String json, int index) {

		if (!jsonpllist.equals(json)) {
			jsonpllist = json;
			try {
				MainGUI.setPlaylist(makePlaylist(json), index);
				Log.i(getClass().getName(), "Playlist set");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			MainGUI.setIndex(index);
		}
		Log.i(getClass().getName(), "Index: "+ index);
	}
	
	public String demo() {
		return "Hallo";
	}
	
	public void showMessage(String message) {
		Toast.makeText(MainGUI.con, message, Toast.LENGTH_SHORT).show();
	}
	
	public void downloadPlaylist(String json) {
		Log.i(getClass().getName(), "test");
	}
	
	public void downloadPlaylists(String json) {
		JSONObject jobj;
		ArrayList<Playlist> playlists = new ArrayList<Playlist>();
		try {
			jobj = new JSONObject(json);
			JSONArray items = jobj.getJSONArray("plists");
			for(int i=0; i < items.length(); i++) {
				playlists.add(makePlaylist(items.get(i).toString()));
			}
			MainGUI.setDownloadList(playlists);
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	public void savePreferences(String json) {
		Boolean savefile, playonstart; 
		int bitrate;
		Log.i(getClass().getName(), json);
		JSONObject jobj;
		try {
			jobj = new JSONObject(json);
			savefile = jobj.getBoolean("savefile");
			playonstart = jobj.getBoolean("playonstart");
			bitrate = jobj.getInt("bitrate");
			
			MainGUI.setSettings(savefile, playonstart, bitrate);
			Log.i(getClass().getName(), savefile+" "+ playonstart+" "+ bitrate);
			
			ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream("data/data/com.tunesbag/settings.ser"));
			os.writeObject(savefile+"");
			os.writeObject(playonstart+"");
			os.writeObject(bitrate+"");
			os.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		/*
		SharedPreferences.Editor editor = MainGUI.getWWSharedPreferences().edit();
		editor.putString(key, value);
		editor.commit();
		*/
	}
	
	public static String getJsonPlaylist() {
		return jsonpllist;
	}
	
	public void talkToBrowser(String action, String arguments) {
        MainGUI.browser.loadUrl("javascript:message(\""+action+"\",\""+arguments+"\")");
	}
	
	public void setUserName(String name) {
		username = name;
	}
	
	public void setRemoteKey(String key) {
		remotekey = key;
	}
	
	public String getTrafficStatistics() {
		File dir = new File("data/data/com.tunesbag/traffic");			
		if (!dir.exists())
			dir.mkdir();
		
		Calendar c = Calendar.getInstance();
		File trafficfile = new File(dir, c.get(Calendar.MONTH)+"_"+c.get(Calendar.YEAR)+".dat");
		long trafficinkb = 0;
		
		try {
			FileReader freader = new FileReader(trafficfile);
			BufferedReader bfreader = new BufferedReader(freader);
			String line = bfreader.readLine();
			bfreader.close();
			trafficinkb = Long.parseLong(line);
		} catch (Exception e) {
			Log.e(getClass().getName(), "Trafficfile does not exists", e);
		}
		
		File trafficfilem = new File(dir, c.get(Calendar.MONTH)+"_"+c.get(Calendar.YEAR)+"_Mobilen.dat");
		long trafficinkbm = 0;
		
		try {
			FileReader freader = new FileReader(trafficfilem);
			BufferedReader bfreader = new BufferedReader(freader);
			String line = bfreader.readLine();
			bfreader.close();			
			trafficinkbm = Long.parseLong(line);			
		} catch (Exception e) {
			Log.e(getClass().getName(), "Trafficfile does not exists", e);
		}
		
		JSONObject json = new JSONObject();
		String jsonurlenc = null;
		try {
			json.put("total_traffic", trafficinkb);
			json.put("nonwifi_traffic", trafficinkbm);
			jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Log.i(getClass().getName(), json.toString());
		return jsonurlenc;			
	}
	
	public Playlist makePlaylist(String json) throws JSONException {
		//ArrayList<Trackinfo> tilist = new ArrayList<Trackinfo>();
		JSONObject jobj = new JSONObject(json);
		//JSONArray items = jobj.getJSONArray("ITEMS");
		/*
		for(int i=0; i < items.length(); i++) {		
			String url = items.getJSONObject(i).getString("url");//String url = items.getJSONObject(i).getString("URL");
			String name = items.getJSONObject(i).getString("NAME");
			int id = items.getJSONObject(i).getInt("ID");
			int mb_artistid = items.getJSONObject(i).getInt("MB_ARTISTID");
			int mb_trackid = items.getJSONObject(i).getInt("MB_TRACKID");
			String album = items.getJSONObject(i).getString("ALBUM");
			int duration = items.getJSONObject(i).getInt("DURATION");
			String artist = items.getJSONObject(i).getString("ARTIST");
			String entrykey = items.getJSONObject(i).getString("ENTRYKEY"); 
			int mb_albumid = items.getJSONObject(i).getInt("MB_ALBUMID");
			int bitrate = items.getJSONObject(i).getInt("BITRATE");
			
			Trackinfo ti = new Trackinfo(artist, album, name, url, mb_artistid, mb_albumid, mb_trackid,
					entrykey, bitrate, "mp3", duration, id);
			 
			tilist.add(ti);	
			
			if(i == items.length() -1 ) {
				tilist.trimToSize();
			}
		}
		*/
		String tags = jobj.getString("TAGS");
		
		String name = jobj.getString("NAME");
		boolean istemp = jobj.getBoolean("ISTEMPORARY");
		String description = jobj.getString("DESCRIPTION");
		int id = jobj.getInt("ID");
		String entryKey = jobj.getString("ENTRYKEY");
					
		return new Playlist(makeList(json), tags, name, istemp, description, id, entryKey);
		/*public Playlist(ArrayList<Trackinfo> _tracks, String _tags,String _name, boolean _istemporary, String _description, int _id)*/				
	}
	
	private ArrayList<Trackinfo> makeList(String json) throws JSONException {
		ArrayList<Trackinfo> tilist = new ArrayList<Trackinfo>();
		JSONObject jobj = new JSONObject(json);
		JSONArray items = jobj.getJSONArray("ITEMS");
		
		for(int i=0; i < items.length(); i++) {		
			String url = items.getJSONObject(i).getString("url");//String url = items.getJSONObject(i).getString("URL");
			String name = items.getJSONObject(i).getString("NAME");
			int id = items.getJSONObject(i).getInt("ID");
			int mb_artistid = items.getJSONObject(i).getInt("MB_ARTISTID");
			int mb_trackid = items.getJSONObject(i).getInt("MB_TRACKID");
			String album = items.getJSONObject(i).getString("ALBUM");
			int duration = items.getJSONObject(i).getInt("DURATION");
			String artist = items.getJSONObject(i).getString("ARTIST");
			String entrykey = items.getJSONObject(i).getString("ENTRYKEY"); 
			int mb_albumid = items.getJSONObject(i).getInt("MB_ALBUMID");
			int bitrate = items.getJSONObject(i).getInt("BITRATE");
			
			Trackinfo ti = new Trackinfo(artist, album, name, url, mb_artistid, mb_albumid, mb_trackid,
					entrykey, bitrate, "mp3", duration, id);
			 
			tilist.add(ti);	
			
			if(i == items.length() -1 ) {
				tilist.trimToSize();
			}
			
		}
		return tilist;
		
	}
}
