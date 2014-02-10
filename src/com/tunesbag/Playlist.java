package com.tunesbag;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Playlist implements Serializable {
	private List<Trackinfo> tracks = new ArrayList<Trackinfo>();
	private int duration; // Wie lange ist die Abspieldauer?
	private int position = 0; 
	private int itemcount; // Wieviele Items sind in der Playlist
	private String tags;
	private String name;
	private boolean istemporary;
	private String description;
	private int id;
	private String entryKey;
	
	public Playlist(ArrayList<Trackinfo> _tracks, String _tags,String _name, boolean _istemporary, String _description, int _id, String _entryKey) {
		tracks = _tracks;
		itemcount = _tracks.size();		
		for(int i = 0; i < itemcount; i++) {
			duration = duration + _tracks.get(i).getDuration();
		}
		tags = _tags;
		name = _name;
		istemporary = _istemporary;
		description = _description;
		id = _id;
		entryKey = _entryKey;
	}
	
	public Trackinfo getElement(int index) {
		return tracks.get(index);
	}
	
	public List<Trackinfo> getList() {
		return tracks;
	}
	
	public String getentryKey() {
		return entryKey;
	}
	
	public int size() {
		return tracks.size();
	}
	
}
