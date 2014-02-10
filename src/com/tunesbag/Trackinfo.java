package com.tunesbag;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class Trackinfo implements Serializable {
	private String artist;
	private String album;
	private String name;
	private String url;
	private int MB_ArtistID;
	private int MB_AlbumID;
	private int MB_TrackID;
	private String entryKey; // MD5 bei Localtracks
	private int bitrate;
	private String format = ".aac";
	private int duration; 
	private int id;
	private String cacheFileName; // Verzeichniss cache anlegen // entryKey.bitrate.format
	private boolean exists = false;
	private boolean localtrack = false;
	
	public Trackinfo(String _artist, String _album, String _name, String _url, int _MB_ArtistID,
			int _MB_AlbumID, int _MB_TrackID, String _entryKey, int _Bitrate, String _Format, int _duration, int _id) {
		artist = _artist;
		album =_album;
		name = _name;
		url = _url;
		MB_ArtistID = _MB_ArtistID;
		MB_AlbumID = _MB_AlbumID;
		MB_TrackID = _MB_TrackID;
		entryKey = _entryKey;
		bitrate = _Bitrate;
		format = _Format;
		duration = _duration;
		id = _id;
		cacheFileName = entryKey;
	}
	
	public Trackinfo(String _artist, String _album, String _name, String mdfivesum, int _Bitrate, String _Format,
			int _duration, boolean _exists, String filename) {
		artist = _artist;
		album =_album;
		name = _name;
		entryKey = mdfivesum;
		bitrate = _Bitrate;
		format = _Format;
		duration = _duration;
		exists = _exists;
		cacheFileName = filename;
		url = filename;
	}
	
	public String getAlbum() {
		return album;
	}
	
	public int getBitrate() {
		return bitrate;
	}
	
	public String getFormat() {
		return format;
	}
	
	public boolean getExists() {
		return exists;
	}
	
	public String getCacheFileName() {
		return cacheFileName;
	}
	
	public Trackinfo(String _url) {
		url = _url;
	}
	
	public int getDuration() {
		return duration;		
	}
	
	public String getName() {
		return name;
	}
	
	public void setTrackExists(boolean e) {
		exists = e;
	}
	
	public boolean getTrackExists() {
		return exists;		
	}
	
	public String getURL() {
		return url;
	}
	
	public int getSize() {
		Log.i(getClass().getName(), "Bitrate: "+bitrate);
		int size = (duration * bitrate)/8;
		return size;
	}
	
	public String getArtist() {
		return artist;
	}
	
	public String getentryKey() {
		return entryKey;
	}
}
