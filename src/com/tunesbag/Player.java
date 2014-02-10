
package com.tunesbag;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RemoteViews;
import android.widget.Toast;

public class Player extends Service {
	private static int INTIAL_KB_BUFFER = MainGUI.getBitrate()*20/8; //assume 128kbps*10secs/8bits per byte
	private static MediaPlayer player;
	private List<Trackinfo> tracklist;
	private List<Trackinfo> downloadlist;
	private static int index;
	private Trackinfo actualMediaFile;
	private String pathtocachedir;
	private boolean StreamingInterrupted;
	private boolean DownloadingInterrupted;
	private boolean StreamingPaused = false;
	private boolean StreamingActive = false;
	
	private final Handler handler = new Handler();
	private int curposition;
	
	private NotificationManager notificationManager;
	private Notification downloadNotification;
	private Notification playNotification;
	public static final int DOWNLOADNOTIFICATION_ID = 1;
	public static final int PLAYNOTIFICATION_ID = 2;
	public static final String REFRESH_TRACKINFO = "com.tunesbag.REFRESH_TRACKINFO";
	public static final String SET_PAUSE = "com.tunesbag.SET_PAUSE";
	public static final String SET_NEXT = "com.tunesbag.SET_NEXT";
	public static final int PLAYBUTTON=1;
	private boolean widgetnextvisible;
	private boolean widgetpause;
	private boolean widgetplaypausevisible;
	static String widgettitle;
	static String widgetartist = "";
	
	private static String streamingbasicURL;
	private static String ApplicationKey;
	private StreamTask playerthread;
	private static File downloadingMediaFile;
	private boolean paused = false;
	private String state = Environment.getExternalStorageState();
	
	private WebComm webc = new WebComm();
	
	private ReentrantLock playlock = new ReentrantLock(); 	
	private ReentrantLock downloadlock = new ReentrantLock(); 
	private ReentrantLock streamlock = new ReentrantLock();
	private ReentrantLock actualMediaFilesetlock = new ReentrantLock();
	
	@Override
	public void onCreate() {
		/** Creates a new player */
		super.onCreate();
		widgettitle = getString(R.string.noplaylist);

		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		
		downloadNotification = new Notification(R.drawable.icon, getString(R.string.downloadinfo), System.currentTimeMillis());
		downloadNotification.flags = downloadNotification.flags | Notification.FLAG_ONGOING_EVENT;
		downloadNotification.contentView = new RemoteViews(this.getPackageName(), R.layout.downloadnot);
		
		Intent intent = new Intent(this, MainGUI.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
		downloadNotification.contentIntent = pendingIntent;
		
		/*
		RemoteViews updateViews = new RemoteViews(this.getPackageName(),
				R.layout.playerwidget);
		
		ComponentName thisWidget = new ComponentName(this, PlayerWidget.class);
		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		manager.updateAppWidget(thisWidget, updateViews);
		*/
		ApplicationKey = getString(R.string.ApplicationKey); 
		
	}
	
	@Override
	public void onDestroy() {
		/** Stops the player */	
		super.onDestroy();
		
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return playBinder;
	}
	
	private void downloadFiles() {
		final String plentryKey = MainGUI.getDownloadlist().getentryKey();
		new Thread (new Runnable() {
			
			int dlsize;
			
			public void run() {
				if (downloadlock.isLocked()) {
					//TODO
				}
					
				downloadlock.lock();
				try {
					DownloadingInterrupted = false;
					
					/*
					Context context = getApplicationContext();				
					CharSequence contentTitle = "My notification";
					CharSequence contentText = "Hello World!";
					
					Intent startActivityIntent = new Intent(Player.this, MainGUI.class);
					PendingIntent launchIntent = PendingIntent.getActivity(context, 0, startActivityIntent, 0);
					*/
					//downloadNotification.setLatestEventInfo(context, contentTitle, contentText, launchIntent);
					downloadNotification.contentView.setImageViewResource(R.id.status_icon, R.drawable.icon);
					
					notificationManager.notify(DOWNLOADNOTIFICATION_ID, downloadNotification);
					getFiles();
				} finally {
					downloadlock.unlock();
				}
			}
			
			private void getFiles() {
				try {
					File dir;
					ArrayList<String> cachefilenames = new ArrayList<String>();
					if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
						dir = new File(Environment.getExternalStorageDirectory(), "tunesbag");
					
						if(dir.isDirectory()) {
							String[] filenames = dir.list();
							for (int i = 0; i < filenames.length; i++) {
								cachefilenames.add(filenames[i]);
							}
						}
					}
					
					for(int i=0; i < downloadlist.size(); i++) {
						if(!cachefilenames.contains(downloadlist.get(i).getCacheFileName())) {	
							downloadNotification.contentView.setTextViewText(R.id.status_text, getString(R.string.downloadn_downloadingfile)
									+" "+ (i+1) +" "+ getString(R.string.downloadn_of) +" "+ downloadlist.size());
							downloadNotification.contentView.setProgressBar(R.id.status_progress, downloadlist.size(), i+1, false);						
							notificationManager.notify(DOWNLOADNOTIFICATION_ID, downloadNotification);
							
							downloadFile(downloadlist.get(i).getURL(), downloadlist.get(i).getCacheFileName());
							
							try {
								JSONObject json = new JSONObject();
								String jsonurlenc;
								json.put("entrykey", downloadlist.get(i).getentryKey());
								json.put("plistkey", plentryKey);
								jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
								Log.i(getClass().getName(), json.toString());
								webc.talkToBrowser("trackdownloaded", jsonurlenc);						
								//MainGUI.browser.loadUrl("javascript:message(\"logplaystatus\",\""+ jsonurlenc +"\")");
							} catch (Exception e) {
								Log.e(getClass().getName(), "Could not send message to browser", e);
							}
							
							dlsize = downloadlist.get(i).getSize();
							saveDownloadTraffic(dlsize);
						}
					}
					try {
						JSONObject json = new JSONObject();
						String jsonurlenc;
						json.put("plistkey", plentryKey);
						jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
						Log.i(getClass().getName(), json.toString());
						webc.talkToBrowser("playlistdownloaded", jsonurlenc);						
						//MainGUI.browser.loadUrl("javascript:message(\"logplaystatus\",\""+ jsonurlenc +"\")");
					} catch (Exception e) {
						Log.e(getClass().getName(), "Could not send message to browser", e);
					}
					
					notificationManager.cancel(DOWNLOADNOTIFICATION_ID);
				} catch (Exception e) {
					Log.i(getClass().getName(), "Downloading stopped", e);
				}
			}
			
			private void downloadFile(String url, String cachefilename) throws Exception {
				try {
					BufferedInputStream in = new BufferedInputStream(new URL(url+"&synctodevice=true").openStream());
					File dl = new File(pathtocachedir, cachefilename+".tmp");
					FileOutputStream fos = new FileOutputStream(dl);
					BufferedOutputStream bout = new BufferedOutputStream(fos,16384);
					byte[] buf = new byte[16384];
					int totalBytesRead = 0, totalKbRead = 0;
					int numread = 0;
					
					do {		        		        	
			        	numread = in.read(buf);   
			            if (numread <= 0) {
			            	break;
			            } else if(DownloadingInterrupted) {
			            	throw new Exception();
			            } else {
				            bout.write(buf, 0, numread);
			            }
			            
			        } while (true);
					
					bout.close();
					in.close();
					Log.i(getClass().getName(), "File downloaded");
										
					if (Environment.MEDIA_MOUNTED.equals(state)) {
						File root = Environment.getExternalStorageDirectory();
						Log.i(getClass().getName(), "can write to sd card");
						File dir = new File(root, "tunesbag");						
						if (!dir.exists())
							dir.mkdir();
						
						try {
							File newLoc = new File(dir, cachefilename + ".cache");
							moveFile(dl, newLoc);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					} else {
						Log.e(getClass().getName(), "Could not write to sdcard");
					}
		        	
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch blockif (bufferFile)
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			
			}
		}).start();
		
	}

	/** Streams a file or URL */
	private void streamFile() {
		
		
		playerthread = new StreamTask();
        playerthread.start();
		
		//notificationManager.cancel(PLAYNOTIFICATION_ID);
		
		/*
		Intent intent = new Intent(REFRESH_TRACKINFO);
		intent.putExtra("name", name);
		intent.putExtra("artist", artist);
		sendBroadcast(intent);
		*/
		
        
	}
	
	private void informWebViewAboutPlaybackStarted() {
		try {
			JSONObject json = new JSONObject();
			String jsonurlenc;
			json.put("entrykey", actualMediaFile.getentryKey());
			json.put("plistkey", MainGUI.getPlaylist().getentryKey());
			jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
			Log.i(getClass().getName(), json.toString());
			webc.talkToBrowser("trackplaybackstarted", jsonurlenc);						
			//MainGUI.browser.loadUrl("javascript:message(\"logplaystatus\",\""+ jsonurlenc +"\")");
		} catch (Exception e) {
			Log.e(getClass().getName(), "Could not send message to browser", e);
		}
	}
	
	private void makePlayNotification(String name, String artist, boolean newtrack) {
		//playNotification = new Notification(R.drawable.icon, name +"\n by "+ artist, System.currentTimeMillis());
		playNotification = new Notification();
		if (newtrack)
			playNotification.tickerText = name +"\n by "+ artist;
		playNotification.icon = R.drawable.icon;
		playNotification.when = System.currentTimeMillis();
		playNotification.sound = Uri.parse("");
		playNotification.flags = playNotification.flags | Notification.FLAG_ONGOING_EVENT;
		
		Context context = getApplicationContext();
		Intent notificationIntent = new Intent(this, MainGUI.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		playNotification.setLatestEventInfo(context, name, artist, contentIntent);
		
		notificationManager.notify(PLAYNOTIFICATION_ID, playNotification);
	}
	
	private void savePlaylistAndIndex() {
		try {
			ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream("data/data/com.tunesbag/playerstate.ser"));
			os.writeObject(MainGUI.getPlaylist());
			os.writeObject(index+"");
			os.writeObject(MainGUI.getRepeatMode()+"");
			os.writeObject(MainGUI.getShuffleMode()+"");
			os.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}						
	}
	
	class StreamTask extends Thread {
		private int flag;
		private int totalKbRead;
		private int mediaLengthInKb;
		
		/** will be called when the thread starts */
		public void run() {			
			do {
				StreamingInterrupted = true;
			
				try {
					playerthread.interrupt();
				} catch (Exception e) {}
				
				if (player != null) {
					player.release();
				}
			
			} while (streamlock.isLocked());
			
			streamlock.lock();
			try {
				paused = false;
				StreamingInterrupted = false;
				curposition = 0;
				flag = 0;
				totalKbRead = 0;
				
				player = new MediaPlayer();
				//player.setOnPreparedListener(new PlayerPreparedListener());
				//player.setOnCompletionListener(new PlayerCompletionListener());
				
				MainGUI.pbar.setProgress(0);  
				boolean bufferFile=true;
				String filename;
				if (actualMediaFile.getCacheFileName().startsWith(File.separator)) 
					filename = actualMediaFile.getCacheFileName();
				else
					filename = actualMediaFile.getCacheFileName()+ ".cache";
				
				/*
				File dir;
				if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
					dir = new File(Environment.getExternalStorageDirectory(), "tunesbag");
				
					if(dir.isDirectory() && !StreamingActive) {
						String[] filenames = dir.list();
						for (int i = 0; i < filenames.length; i++) {
							if (filenames[i].equals(filename)) {
								bufferFile = false;
								break;
							}
						}
					}
				}
				if (new File(pathtocachedir, actualMediaFile.getCacheFileName()+".tmp").exists())
					bufferFile = false;
				*/
				new Thread( new Runnable() {

					public void run() {
						Message updateUImessage = new Message();
						updateUImessage.arg1=index;
						updateMainGUIHandler.sendMessage(updateUImessage);
						
						String name = actualMediaFile.getName();
						String artist = actualMediaFile.getArtist();
										
						informWebViewAboutPlaybackStarted();	
						makePlayNotification(name, artist, true);
						savePlaylistAndIndex();
						
					}
					
				}).start();
				
				/*								
				if (bufferFile) {
					downloadAudio();
					
				} else if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
					player.setOnCompletionListener(new TrackCompletionListener());

					if (filename.startsWith(File.separator)) {
						playAudio(filename);
					} else
						playAudio(Environment.getExternalStorageDirectory() + File.separator + "tunesbag" + File.separator + filename);
			
				}
				*/
				File extfile = new File(Environment.getExternalStorageDirectory() + File.separator + "tunesbag" + File.separator + filename);
				File chfile = new File(pathtocachedir, actualMediaFile.getCacheFileName()+".tmp");
				
				
				if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
					player.setOnCompletionListener(new TrackCompletionListener());

					if (filename.startsWith(File.separator))
						playAudio(filename);
					else if (extfile.exists())
						playAudio(extfile.getAbsolutePath());
					else if (chfile.exists()) 
						playAudio(chfile.getAbsolutePath());					
					else
						downloadAudio();
					
					if(index < (tracklist.size() - 1)) {
						Trackinfo nextMediaFile = tracklist.get(index+1);
						File nextextfile = new File(Environment.getExternalStorageDirectory() + File.separator + "tunesbag" + File.separator +  nextMediaFile.getCacheFileName()+ ".cache");
						File nextchfile = new File(pathtocachedir, nextMediaFile.getCacheFileName()+".tmp");
						if ((!new File(nextMediaFile.getCacheFileName()).exists() 
								|| !nextextfile.exists() || !nextchfile.exists()))
							downloadNextAudio();
					}
				}
			} finally {
				streamlock.unlock();
			}
		}
	
		/** Plays a file */
		private void playAudio(String filename) { // filename
			try {
				FileInputStream fis = new FileInputStream(new File(filename));
				//player = new MediaPlayer();
				MainGUI.pbar.setSecondaryProgress(100);
				player.setDataSource(fis.getFD());
				player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				player.prepare();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			/*
			if (!paused)
				player.start();
			*/
			startPlaying();
			new File(pathtocachedir, actualMediaFile.getCacheFileName()+".tmp").delete();
		}
		
		private void downloadAudio() {
			
			try {
				BufferedInputStream in;
				BufferedOutputStream bout;
				byte[] buf;
				int totalBytesRead = 0, incrementalBytesRead = 0;
				int numread = 0;
				actualMediaFilesetlock.lock();
				try {
					player.setOnCompletionListener(new TrackPartCompletionListener());
					in = new BufferedInputStream(new URL(actualMediaFile.getURL()).openStream());
					Log.i(getClass().getName(),"URL: "+ actualMediaFile.getURL());
					downloadingMediaFile = new File(pathtocachedir, actualMediaFile.getCacheFileName()+".tmp");
					Log.i(getClass().getName(),"File: "+ downloadingMediaFile);
					FileOutputStream fos = new FileOutputStream(downloadingMediaFile);
					bout = new BufferedOutputStream(fos,16384);
					buf = new byte[16384];
					mediaLengthInKb= actualMediaFile.getSize();					
					StreamingActive=true;
				} finally {
					actualMediaFilesetlock.unlock();
				}
				
				do {		        		        	
		        	numread = in.read(buf);  
		            if (numread <= 0) {	
		            	
						Log.i(getClass().getName(), "File: " +downloadingMediaFile.getName() +" Index: "+ index );
						FileInputStream fis;
						try {
							MediaPlayer player2 = new MediaPlayer();
							player2.setOnCompletionListener(new TrackCompletionListener());
							fis = new FileInputStream(downloadingMediaFile);
							player2.setDataSource(fis.getFD());
					        //player.setAudioStreamType(AudioManager.STREAM_MUSIC);
					        player2.prepare();
					        curposition = player.getCurrentPosition();
					        player2.seekTo(curposition);
					        if(!paused)
					        	player2.start();
					        	
					        // TODO Progressbar could stop if player stops
			            	player.stop();
			            	player = player2;
			            	if(!paused)
			            		startPlaying();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}		            	
		            	break;  
		            	
		            } else if (!validateNotInterrupted()) {
		            	/** Throws an exception to stop downloadAudio() */
		            	StreamingActive=false;
		            	boolean delted = downloadingMediaFile.delete();		            	
		            	Exception e = new Exception(); 
		            	throw e; 
		            	/*
		            	in.reset();
		            	bout.flush();
		            	break;
		            	*/
		            } else if (StreamingPaused) {
		            	try {
		            		wait();
		            	} catch (Exception e) {
		            		e.printStackTrace();
		            	}
		            } else {
			            bout.write(buf, 0, numread);
			            totalBytesRead += numread;
			            incrementalBytesRead += numread;
			            totalKbRead = totalBytesRead/1000;
	
		            	//MainGUI.pbar.setMax(mediaLengthInKb);
			            //MainGUI.pbar.setSecondaryProgress((int)(((float)totalKbRead*100)/(float)mediaLengthInKb));

			            Message secondaryprogressmessage = new Message();
			            secondaryprogressmessage.arg1 = totalKbRead;
			            secondaryprogressmessage.arg2 = mediaLengthInKb;
			            downloadprogressHandler.sendMessage(secondaryprogressmessage);		
			            
						testMediaBuffer(downloadingMediaFile);												
		            }
		        } while (true);
				Log.i(getClass().getName(), "Download finished");
				//player.setOnCompletionListener(new PlayerCompletionListener());
				
				bout.close();
				in.close();
				
		        if (validateNotInterrupted()) {		        	
		        	saveDownloadTraffic(actualMediaFile.getSize());
		        	
		        	if(MainGUI.getSavefile()) {
			        	new Thread(new Runnable() {
	
							public void run() {
								// TODO Auto-generated method stub
								actualMediaFilesetlock.lock();
								try {
									try {
										Thread.sleep(500);
									} catch (InterruptedException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
									
									if (Environment.MEDIA_MOUNTED.equals(state)) {
										File root = Environment.getExternalStorageDirectory();
										Log.i(getClass().getName(), "can write to sd card");
										File dir = new File(root, "tunesbag");						
										if (!dir.exists())
											dir.mkdir();
										
										
										try {
											File newLoc = new File(dir, actualMediaFile.getCacheFileName() + ".cache");
											moveFile(downloadingMediaFile, newLoc);
		
											JSONObject json = new JSONObject();
											String jsonurlenc;
											json.put("entrykey", actualMediaFile.getentryKey());
											json.put("plistkey", MainGUI.getPlaylist().getentryKey());
											jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
											Log.i(getClass().getName(), json.toString());
											webc.talkToBrowser("trackdownloaded", jsonurlenc);						
											//MainGUI.browser.loadUrl("javascript:message(\"logplaystatus\",\""+ jsonurlenc +"\")");
											
											boolean delted = downloadingMediaFile.delete();
										} catch (Exception e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										} 
										
									} else {
										Log.e(getClass().getName(), "Could not write to sdcard");
									}
								} finally {
									actualMediaFilesetlock.unlock();
								}
							}		        		
			        	}).start();   
		        	} else 
		        		downloadingMediaFile.delete();
		        	//StreamingActive=false;
					//downloadNextAudio();
		        } else {
		        	player.release();	
		        	boolean delted = downloadingMediaFile.delete();
		        }	        
			} catch (IOException e) {
				Log.e(getClass().getName(), "Error in streaming", e);
			} catch (Exception ex) {
				Log.i(getClass().getName(), "downloadAudio() has stopped", ex);
				boolean delted = downloadingMediaFile.delete();	
			}
			StreamingActive=false;
		}
		
		private void downloadNextAudio() {
			final Trackinfo nextMediaFile=tracklist.get(index+1);
			final File downloadingNextMediaFile = new File(pathtocachedir, nextMediaFile.getCacheFileName()+".tmp");
			try {
				BufferedInputStream in;
				BufferedOutputStream bout;
				byte[] buf;
				int totalBytesRead = 0, incrementalBytesRead = 0;
				int numread = 0;
				actualMediaFilesetlock.lock();
				try {
					in = new BufferedInputStream(new URL(nextMediaFile.getURL()).openStream());
					Log.i(getClass().getName(),"URL: "+ nextMediaFile.getURL());
					Log.i(getClass().getName(),"File: "+ downloadingNextMediaFile);
					FileOutputStream fos = new FileOutputStream(downloadingNextMediaFile);
					bout = new BufferedOutputStream(fos,16384);
					buf = new byte[16384];
					//mediaLengthInKb= nextMediaFile.getSize();					
				} finally {
					actualMediaFilesetlock.unlock();
				}
				
				do {		        		        	
		        	numread = in.read(buf);  
		            if (numread <= 0) {	  	
		            	break;  
		            	
		            } else if (!validateNotInterrupted()) {
		            	/** Throws an exception to stop downloadAudio() */   
		            	boolean delted = downloadingNextMediaFile.delete();		
		            	Exception e = new Exception(); 
		            	throw e; 
		            } else if (StreamingPaused) {
		            	try {
		            		wait();
		            	} catch (Exception e) {
		            		e.printStackTrace();
		            	}
		            } else {
			            bout.write(buf, 0, numread);
			            totalBytesRead += numread;
			            incrementalBytesRead += numread;
			            //totalKbRead = totalBytesRead/1000;
	
		            	//MainGUI.pbar.setMax(mediaLengthInKb);
			            //MainGUI.pbar.setSecondaryProgress((int)(((float)totalKbRead*100)/(float)mediaLengthInKb));
			            /*
			            Message secondaryprogressmessage = new Message();
			            secondaryprogressmessage.arg1 = totalKbRead;
			            secondaryprogressmessage.arg2 = mediaLengthInKb;
			            downloadprogressHandler.sendMessage(secondaryprogressmessage);		
			            
						testMediaBuffer(downloadingMediaFile);	
						*/											
		            }
		        } while (true);
				Log.i(getClass().getName(), "Download finished");
				//player.setOnCompletionListener(new PlayerCompletionListener());
				
				bout.close();
				in.close();
				
		        if (validateNotInterrupted()) {		        	
		        	saveDownloadTraffic(nextMediaFile.getSize());
		        	if(MainGUI.getSavefile()) {
			        	new Thread(new Runnable() {
	
							public void run() {
								// TODO Auto-generated method stub
								actualMediaFilesetlock.lock();
								try {
									try {
										Thread.sleep(500);
									} catch (InterruptedException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
									
									if (Environment.MEDIA_MOUNTED.equals(state)) {
										File root = Environment.getExternalStorageDirectory();
										Log.i(getClass().getName(), "can write to sd card");
										File dir = new File(root, "tunesbag");						
										if (!dir.exists())
											dir.mkdir();
										
										
										try {
											File newLoc = new File(dir, nextMediaFile.getCacheFileName() + ".cache");
											moveFile(downloadingNextMediaFile, newLoc);
		
											JSONObject json = new JSONObject();
											String jsonurlenc;
											json.put("entrykey", nextMediaFile.getentryKey());
											json.put("plistkey", MainGUI.getPlaylist().getentryKey());
											jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
											Log.i(getClass().getName(), json.toString());
											webc.talkToBrowser("trackdownloaded", jsonurlenc);						
											//MainGUI.browser.loadUrl("javascript:message(\"logplaystatus\",\""+ jsonurlenc +"\")");
											
											boolean delted = downloadingNextMediaFile.delete();
										} catch (Exception e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										} 
										
									} else {
										Log.e(getClass().getName(), "Could not write to sdcard");
									}
								} finally {
									actualMediaFilesetlock.unlock();
								}
							}		        		
			        	}).start();   
		        	}
		        }	        
		        
			} catch (IOException e) {
				Log.e(getClass().getName(), "Error in streaming", e);
			} catch (Exception ex) {
				Log.i(getClass().getName(), "downloadAudio() has stopped", ex);
				boolean delted = downloadingNextMediaFile.delete();	
			}
		}
		
		private boolean validateNotInterrupted() {
			if (StreamingInterrupted) {
				
				if (player != null) {
					player.release();
				}
				
				return false;
			} else {
				return true;
			}
	    }
		
		private void testMediaBuffer(final File file) { 
			
			
			Runnable updater = new Runnable() {
				public void run() {
				
					try {
						if (StreamingActive && ((int)(((float)INTIAL_KB_BUFFER*100)/(float)mediaLengthInKb) 
								>= MainGUI.pbar.getSecondaryProgress() - MainGUI.pbar.getProgress()) ) {
							
							flag = 0;
							if (player.isPlaying()) {
								curposition = player.getCurrentPosition();
								player.reset();
							}																			
						} else if (totalKbRead >= INTIAL_KB_BUFFER) {
							
							startPlayer(file);
							
						}
					} catch (Exception e) {
						Log.e(this.getClass().getName(), "Error in testMediaBuffer", e);
					}
				}
			};
			handler.post(updater);
		}
		
		private synchronized void startPlayer(File file) {
			//Log.i("Player",totalKbRead /*downloadingMediaFile.length()*/+" of " + mediaLengthInKb);
			/*
			if(player.isPlaying())
				flag =1;
			else
				flag =0;
			*/
			if (flag==0) {
				//player.reset();
	            //bufferedMediaFile = new File(pathtocachedir, actualMediaFile.getChacheFileName()+"."+ (counter) +".buf");
	            //moveFile(downloadingMediaFile,bufferedMediaFile);
				try {
					Log.i(getClass().getName(), "File: " +file.getName() +" Index: "+ index );
					FileInputStream fis = new FileInputStream(file);
					//player = new MediaPlayer();
			        player.setDataSource(fis.getFD());
			        //player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			        player.prepare();
					player.seekTo(curposition);		
					/*
					if(!paused)
						player.start();
					*/
					
					startPlaying();
				} catch (Exception e) {
					e.printStackTrace();
				}
	            flag = 1;
			}
		}
		
		/** Called when a track is really completed */
		class TrackCompletionListener implements OnCompletionListener {

			public void onCompletion(MediaPlayer mp) {
				
				trackplaybackeend();
				int eins = index;
				int zwei = tracklist.size() - 2;
				if((index == (tracklist.size() - 2)) && MainGUI.getShuffleMode()==MainGUI.SHUFFLE_OFF) {
					MainGUI.nextButton.setVisibility(View.INVISIBLE);
					widgetnextvisible = false;
					/*
					Intent intent = new Intent(SET_NEXT);
					intent.putExtra("nextvisible", false);
					sendBroadcast(intent);
					*/
				}	
				if (index < (tracklist.size() - 1)) {
					if (MainGUI.getRepeatMode()==MainGUI.REPEATMODE_ONE) {
						if(!actualMediaFilesetlock.isLocked()) {
							actualMediaFile = tracklist.get(index);
							streamFile();
						}
					} else {
						MainGUI.prevButton.setVisibility(View.VISIBLE);
						if(MainGUI.getShuffleMode()==MainGUI.SHUFFLE_ON)
							index = (int) (Math.random()*tracklist.size());
						else
							index++;
						if(!actualMediaFilesetlock.isLocked()) {
							actualMediaFile = tracklist.get(index);
							streamFile();
						}
					}
				} else {
					if(MainGUI.getRepeatMode()==MainGUI.REPEATMODE_NONE) {
						index = 0;
						MainGUI.prevButton.setVisibility(View.INVISIBLE);
						MainGUI.nextButton.setVisibility(View.VISIBLE);
						MainGUI.pbar.setSecondaryProgress(0);
						MainGUI.pbar.setProgress(0);
						MainGUI.playButton.setImageResource(R.drawable.play);
						MainGUI.precedingtime.setText("0: 0");
						MainGUI.remainingtime.setText((int)(actualMediaFile.getDuration()/60)+":"+(int)(actualMediaFile.getDuration()%60));
						informWebViewAboutPlaybackStarted();
						notificationManager.cancel(PLAYNOTIFICATION_ID);
						savePlaylistAndIndex();
						player=null;
					} else if(MainGUI.getRepeatMode()==MainGUI.REPEATMODE_ALL) {
						index = 0;
						MainGUI.prevButton.setVisibility(View.INVISIBLE);
						MainGUI.nextButton.setVisibility(View.VISIBLE);
						if(!actualMediaFilesetlock.isLocked()) {
							actualMediaFile = tracklist.get(index);
							refreshWidget();
							streamFile();
						}
					}
					
				}
			
			}
			
		}
		
		/** Called when a part of track is completed */
		class TrackPartCompletionListener implements OnCompletionListener {

			public void onCompletion(MediaPlayer arg0) {
				FileInputStream fis;
				try {
					MediaPlayer player2 = new MediaPlayer();
					player2.setOnCompletionListener(new TrackPartCompletionListener());
					fis = new FileInputStream(downloadingMediaFile);
					player2.setDataSource(fis.getFD());
			        //player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			        player2.prepare();
			        curposition = player.getCurrentPosition();
			        player2.seekTo(curposition);
			        if(!paused)
			        	player2.start();
			        	
			        // TODO Progressbar could stop if player stops
	            	player = player2;
	            	if(!paused)
	            		startPlaying();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		            	
				
			}
			
		}

	}
	
	public void moveFile(File	oldLocation, File	newLocation)
	throws IOException {

		if ( oldLocation.exists( )) {
			BufferedInputStream  reader = new BufferedInputStream( new FileInputStream(oldLocation) );
			BufferedOutputStream  writer = new BufferedOutputStream( new FileOutputStream(newLocation, false));
            try {
		        byte[]  buff = new byte[8192];
		        int numChars;
		        while ( (numChars = reader.read(  buff, 0, buff.length ) ) != -1) {
		        	writer.write( buff, 0, numChars );
      		    }
            } catch( IOException ex ) {
				throw new IOException("IOException when transferring " + oldLocation.getPath() + " to " + newLocation.getPath());
            } finally {
                try {
                    if ( reader != null ){
                    	writer.close();
                        reader.close();
                    }
                } catch( IOException ex ){
				    Log.e(getClass().getName(),"Error closing files when transferring " + oldLocation.getPath() + " to " + newLocation.getPath() ); 
				}
            }
        } else {
			throw new IOException("Old location does not exist when transferring " + oldLocation.getPath() + " to " + newLocation.getPath() );
        }
	}
	
	public static int getIndex() {
		return index;
	}
	
	private void saveDownloadTraffic(int size) {
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
		
		try {
			FileWriter fwriter = new FileWriter(trafficfile);
			fwriter.write(""+(trafficinkb+size));
			fwriter.close();
			Log.i(getClass().getName(), c.get(Calendar.MONTH)+"_"+c.get(Calendar.YEAR)+".dat: "+ (trafficinkb+size));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (!MainGUI.getWifiState()) {
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
			
			try {
				FileWriter fwriter = new FileWriter(trafficfilem);
				fwriter.write(""+(trafficinkbm+size));
				fwriter.close();
				Log.i(getClass().getName(), c.get(Calendar.MONTH)+"_"+c.get(Calendar.YEAR)+"_Mobilen.dat: "+ (trafficinkbm+size));
			} catch (IOException e) {
				e.printStackTrace();
			}
						
		}
		
		
	}
	
	private void startPlaying() {	
		new StartPlayer().start();
	}
	
	
	class StartPlayer extends Thread {	
		float duration = (float)actualMediaFile.getDuration();
		float position = (float)player.getCurrentPosition()/1000;
		
		@Override
		public void run() {
			
			while (playlock.isLocked() && player != null) {
				if (player.isPlaying()) {
					try {
						player.pause();
					} catch (IllegalStateException exc) {
							
					}
				}
			}

			
			playlock.lock();						
			try {
				if(!paused)
					player.start();
				
				setMainProgress();
			} catch (Exception e) {
				
			} finally {	
				playlock.unlock();
			}
		}
		
		private void setMainProgress() {
			int sleeped = 0;
			
			while (player.isPlaying()) {				
				
				Message progressMsg = new Message();
                progressMsg.arg1 = (int)duration;
                progressMsg.arg2 = (int)position;
				playprogressHandler.sendMessage(progressMsg);
				 
				try {
					Thread.sleep(1000);					
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				
				if (!player.isPlaying())
					break;

				position= position + 1;		// position und entry	
				
				//Log.i(getClass().getName(), "precedingtime "+(int)(position/60)+":"+(int)(position%60));
				
				if ((sleeped % 30) == 0 && sleeped >= 30) {
					JSONObject json = new JSONObject();
					String jsonurlenc;
					try {
						json.put("mediaitemkey", actualMediaFile.getentryKey());
						json.put("seconds", position);
						jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
						Log.i(getClass().getName(), json.toString());
						webc.talkToBrowser("logplaystatus", jsonurlenc);						
						//MainGUI.browser.loadUrl("javascript:message(\"logplaystatus\",\""+ jsonurlenc +"\")");
					} catch (Exception e) {
						Log.e(getClass().getName(), "Could not send message to browser", e);
					}
					
					
				}	
				sleeped++;	
			}
		}	

	}
	
	Handler playprogressHandler = new Handler() {
        public void handleMessage(Message msg) {
        	float duration = (float) msg.arg1;
        	float position = (float) msg.arg2;
        	float negposition = duration - position; 
        	MainGUI.pbar.setProgress((int)((position*100)/duration));
        	String prectime = String.format("%2d:%2d",(int)(position/60),(int)(position%60));
        	String remaintime = String.format("%2d:%2d",(int)(negposition/60),(int)(negposition%60));
			MainGUI.precedingtime.setText(prectime);
			MainGUI.remainingtime.setText(remaintime);
        }
    };
    
    Handler downloadprogressHandler = new Handler() {
        public void handleMessage(Message msg) {
        	float totalKbRead = (float) msg.arg1;
        	float mediaLengthInKb = (float) msg.arg2;
        	MainGUI.pbar.setSecondaryProgress((int)(((float)totalKbRead*100)/(float)mediaLengthInKb));
        	/*
        	if (player!=null && !paused) {
        		if (!player.isPlaying()) {
	        		MainGUI.pbar.setProgress(0);
	        		MainGUI.precedingtime.setText("Load");
	        		MainGUI.remainingtime.setText("");
        		}
        	}
        	*/
        }
    };
    
    Handler updateMainGUIHandler = new Handler() {
    	public void handleMessage(Message msg) {
    		int _index = msg.arg1;
    		int playbutton = msg.arg2;
    		MainGUI.playButton.setVisibility(View.VISIBLE); 

			widgetplaypausevisible = true;
			if (playbutton==Player.PLAYBUTTON || paused) {
				MainGUI.playButton.setImageResource(R.drawable.play);
				widgetpause = false;
			} else {
				MainGUI.playButton.setImageResource(R.drawable.pause);
				widgetpause = true;
			}
			/*
			Intent intent = new Intent(SET_PAUSE);
			intent.putExtra("pausebutton", true);
			sendBroadcast(intent);
			*/
			if ((_index == 0) && MainGUI.getShuffleMode()==MainGUI.SHUFFLE_OFF)
				MainGUI.prevButton.setVisibility(View.INVISIBLE);
			else
				MainGUI.prevButton.setVisibility(View.VISIBLE);
			
			int eins = _index;
			int zwei = tracklist.size() - 1;
			if((eins >= zwei) && MainGUI.getShuffleMode()==MainGUI.SHUFFLE_OFF) {
				MainGUI.nextButton.setVisibility(View.INVISIBLE);
				widgetnextvisible = false;
				/*
				Intent inten = new Intent(SET_NEXT);
				intent.putExtra("nextvisible", false);
				sendBroadcast(inten);
				*/
			}
			else {
				MainGUI.nextButton.setVisibility(View.VISIBLE);
				widgetnextvisible = true;
				/*
				Intent inten = new Intent(SET_NEXT);
				intent.putExtra("nextvisible", true);
				sendBroadcast(inten);
				*/
			}
			refreshWidget();
    	}
    };
	
	private void refreshWidget() {
		if (actualMediaFile!=null) {
			widgettitle = actualMediaFile.getName();
			widgetartist = actualMediaFile.getArtist();
		}
		Intent intent = new Intent(REFRESH_TRACKINFO);
		intent.putExtra("name", widgettitle);
		intent.putExtra("artist", widgetartist);
		intent.putExtra("nextvisible", widgetnextvisible);
		intent.putExtra("pausebutton", widgetpause);
		intent.putExtra("playpausevisible", widgetplaypausevisible);
		sendBroadcast(intent);
	}
	
	private void trackplaybackeend() {
		if (player != null || paused) {
			try {
				JSONObject json = new JSONObject();
				String jsonurlenc;
				json.put("entrykey", actualMediaFile.getentryKey());
				json.put("plistkey", MainGUI.getPlaylist().getentryKey());
				jsonurlenc = URLEncoder.encode(json.toString(), "UTF-8");
				Log.i(getClass().getName(), json.toString());
				webc.talkToBrowser("trackplaybackeend", jsonurlenc);						
				//MainGUI.browser.loadUrl("javascript:message(\"logplaystatus\",\""+ jsonurlenc +"\")");
			} catch (Exception e) {
				Log.e(getClass().getName(), "Could not send message to browser", e);
			}
		}
	}
	
	private final PlayerInterface.Stub playBinder = new PlayerInterface.Stub() {
		
		/** plays a Track from the Arraylist */
		public void setTrack(int _index) throws RemoteException {
			if(!actualMediaFilesetlock.isLocked()) {
				trackplaybackeend();
				/*
				Message updateUImessage = new Message();
				updateUImessage.arg1=_index;
				updateMainGUIHandler.sendMessage(updateUImessage);
				*/
				index = _index;	
				actualMediaFile = tracklist.get(index);
				streamFile();
			}
			
		}
		
		/** sets the Playlist. Only a list of Trackinfo objects is allowed */
		public void preparePlayer(List playlist, String _pathtocachedir, int _index) throws RemoteException {
			tracklist = playlist;
			pathtocachedir = _pathtocachedir;
			setTrack(_index);
		}

		public void nextTrack() throws RemoteException {
			paused=false;
			if(MainGUI.getShuffleMode()==MainGUI.SHUFFLE_ON)
				index = (int) (Math.random()*tracklist.size());
			else
				if(index <(tracklist.size()-1))
					index++;
			setTrack(index);
			/*
			int eins = index;
			int zwei = tracklist.size() - 2;
			if(eins == zwei) {
				MainGUI.nextButton.setVisibility(View.INVISIBLE);
				widgetnextvisible = false;

			}	
			MainGUI.prevButton.setVisibility(View.VISIBLE);
			MainGUI.playButton.setImageResource(R.drawable.pause);
			widgetpause = true;
			index++;
			
			if(!actualMediaFilesetlock.isLocked()) {
				actualMediaFile = tracklist.get(index);
				refreshWidget();
				streamFile();
			}
			*/
		}

		public void pauseresumeTrack() throws RemoteException {
			if (player!=null && player.isPlaying()) {
				//MainGUI.playButton.setBackgroundResource(R.drawable.play);
				widgetpause = false;
				
				notificationManager.cancel(PLAYNOTIFICATION_ID);
				paused = true;
				if (player != null) 
					player.pause();
			} else {
				//MainGUI.playButton.setBackgroundResource(R.drawable.pause);
				widgetpause = true;
				
				paused = false;
				if (player != null) {
					makePlayNotification(actualMediaFile.getName(), actualMediaFile.getArtist(), false);
					startPlaying();
				} else if (player == null && tracklist != null) {
					paused=false;
					widgetpause=true;
					streamFile();
				}
			}
			Message updateUImessage = new Message();
			updateUImessage.arg1=index;
			updateMainGUIHandler.sendMessage(updateUImessage);
		}

		public void previousTrack() throws RemoteException {
			paused=false;
			if(MainGUI.getShuffleMode()==MainGUI.SHUFFLE_ON)
				index = (int) (Math.random()*tracklist.size());
			else
				if (index>0)
					index--;
			setTrack(index);
			/*
			if(1 == index)
				MainGUI.prevButton.setVisibility(View.INVISIBLE);
			
			MainGUI.nextButton.setVisibility(View.VISIBLE);
			widgetnextvisible = true;
			
			
			MainGUI.playButton.setImageResource(R.drawable.pause);
			widgetpause = true;
			index--;

			if(!actualMediaFilesetlock.isLocked()) {
				actualMediaFile = tracklist.get(index);
				refreshWidget();
				streamFile();
			}
			*/
		}
		
		public void beforeseekTo() throws RemoteException {
			// TODO Auto-generated method stub
			if (!StreamingActive && player != null) {
				player.pause();
			}
		}

		public void seekTo(int position) throws RemoteException {
			// TODO Auto-generated method stub
			if (!StreamingActive && player != null) {
				/*
				synchronized (playerthread) {
					playerthread.pauseStreaming();
				}
				*/
				curposition = (int)(((float)position*(float)(actualMediaFile.getDuration()*1000))/100);
				//player.pause();
				player.seekTo(curposition);
				startPlaying();
				Log.i(getClass().getName(), "Position: " +position + " Seekposition: " + curposition);
				/*
				synchronized (playerthread) {
					playerthread.proceedStreaming();
				}
				*/
			}
		}

		public void downloadTracks(List _playlist, String _pathtocachedir)
				throws RemoteException {
			pathtocachedir = _pathtocachedir;
			downloadlist = _playlist;
			downloadFiles();
		}

		public void stopService() throws RemoteException {
			// TODO Auto-generated method stub
			StreamingInterrupted = true;
			DownloadingInterrupted = true;
			
			if (notificationManager != null) {
				notificationManager.cancel(DOWNLOADNOTIFICATION_ID);
				notificationManager.cancel(PLAYNOTIFICATION_ID);
			}
			
			if(player.isPlaying() || player != null)
				player.stop();
			
			widgetpause=false;
			widgetnextvisible = false;
			widgetplaypausevisible = false;
			refreshWidget();
				
		}

		public void setWidgetInfo() throws RemoteException {
			refreshWidget();
			
		}

		public String getSendTrack() throws RemoteException {
			if (Environment.MEDIA_MOUNTED.equals(state)) {
				File root = Environment.getExternalStorageDirectory();
				File tbdir = new File(root, "tunesbag");						
				if (!tbdir.exists())
					tbdir.mkdir();
				File dir = new File(tbdir, "tosend");						
				if (!dir.exists())
					dir.mkdir();
				
				try {
					String newFilename = actualMediaFile.getArtist()+ " - "+ actualMediaFile.getName() +actualMediaFile.getFormat();
					File oldLoc = new File(tbdir, actualMediaFile.getCacheFileName() + ".cache");
					if (oldLoc.exists()) {
						File newLoc = new File(dir, newFilename);
						moveFile(oldLoc, newLoc);
						return newFilename;
					} else 
						return "filenotexists";
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
				
			} else {
				Log.e(getClass().getName(), "Could not write to sdcard");
				return null;
			}
			
		}

		public void updateGUI() throws RemoteException {
			Message updateUImessage = new Message();
			updateUImessage.arg1=index;
			updateMainGUIHandler.sendMessage(updateUImessage);
			savePlaylistAndIndex();
		}

		public void cancelPlaylistDownload() throws RemoteException {
			DownloadingInterrupted = true;
			
			if (notificationManager != null) 
				notificationManager.cancel(DOWNLOADNOTIFICATION_ID);
						
		}

		public void preparePlayerNonPlay(List _playlist, String _pathtocachedir,
				int _index) throws RemoteException {
			tracklist = _playlist;
			pathtocachedir = _pathtocachedir;
			index = _index;
			actualMediaFile = tracklist.get(index);
			Message updateUImessage = new Message();
			updateUImessage.arg1=_index;
			updateUImessage.arg2=Player.PLAYBUTTON;
			updateMainGUIHandler.sendMessage(updateUImessage);
			//MainGUI.prevButton.setVisibility(View.INVISIBLE);
			//MainGUI.nextButton.setVisibility(View.INVISIBLE);
			//MainGUI.playButton.setVisibility(View.VISIBLE);
			MainGUI.pbar.setSecondaryProgress(0);
			MainGUI.pbar.setProgress(0);
			
			MainGUI.precedingtime.setText("0: 0");
			MainGUI.remainingtime.setText((int)(actualMediaFile.getDuration()/60)+":"+(int)(actualMediaFile.getDuration()%60));
			informWebViewAboutPlaybackStarted();
			notificationManager.cancel(PLAYNOTIFICATION_ID);
			savePlaylistAndIndex();
			player=null;
			MainGUI.playButton.setImageResource(R.drawable.play);
		}

		public boolean getPlayerState() throws RemoteException {
			if (paused) {
				return !paused;
			} else {
				if (player!=null)
					return player.isPlaying();
				else
					return false;
			}
		}

		public void onCall(boolean calling) throws RemoteException {
			if (player!=null && !paused) {
				if (calling)
					player.pause();
				else
					startPlaying();
			}
			
		}

		public void setINTIAL_KB_BUFFER(int buffer) throws RemoteException {
			INTIAL_KB_BUFFER = buffer;
			
		}		

	};
	
}
