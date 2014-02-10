package com.tunesbag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

public class PlayerWidget extends AppWidgetProvider {
	private String name;
	private String artist;
	private boolean pausebutton = false;
	private boolean nextvisible = false;
	private boolean playpausevisible = false;
	
	public final static String ACTION_WIDGET_PLAY_PAUSE = "com.tunesbag.ACTION_WIDGET_PLAY_PAUSE";
	public final static String ACTION_WIDGET_NEXT = "com.tunesbag.ACTION_WIDGET_NEXT";
	
	private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds, boolean formIntent) {
		final int N = appWidgetIds.length;
		
        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];

            // Create an Intent to launch ExampleActivity
            Intent intentpp = new Intent(ACTION_WIDGET_PLAY_PAUSE);
            PendingIntent pendingIntentpp = PendingIntent.getBroadcast(context, 0, intentpp, 0);
            
            Intent intentn = new Intent(ACTION_WIDGET_NEXT);
            PendingIntent pendingIntentn = PendingIntent.getBroadcast(context, 0, intentn, 0);
            
            Intent intent = new Intent(context, MainGUI.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

            // Get the layout for the App Widget and attach an on-click listener to the button
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.playerwidget);
            views.setOnClickPendingIntent(R.id.widget_ppbutton, pendingIntentpp);
            views.setOnClickPendingIntent(R.id.widget_nbutton, pendingIntentn);
            views.setOnClickPendingIntent(R.id.widget_widget, pendingIntent);
                       
            if (nextvisible)
            	views.setViewVisibility(R.id.widget_nbutton, View.VISIBLE);
            else
            	views.setViewVisibility(R.id.widget_nbutton, View.INVISIBLE);
            
            if (playpausevisible) {
            	views.setViewVisibility(R.id.widget_ppbutton, View.VISIBLE);
            	if (pausebutton)
                	views.setImageViewResource(R.id.widget_ppbutton, R.drawable.pause);
                else
                	views.setImageViewResource(R.id.widget_ppbutton, R.drawable.play); 
            } else
            	views.setViewVisibility(R.id.widget_ppbutton, View.INVISIBLE);
            
            if (formIntent) {
	            views.setTextViewText(R.id.widget_title, name);
	            views.setTextViewText(R.id.widget_artist, artist);

				try {
					ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream("data/data/com.tunesbag/widget.ser"));
					os.writeObject(name);
					os.writeObject(artist);
					os.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}						
	                        	            	         
            } 
            else {
            	try {
            		if(new File("data/data/com.tunesbag/widget.ser").exists()) {
                    	ObjectInputStream is = new ObjectInputStream(new FileInputStream("data/data/com.tunesbag/widget.ser"));		            	
                    	views.setTextViewText(R.id.widget_title, (String) is.readObject());
        	            views.setTextViewText(R.id.widget_artist, (String) is.readObject());
        	            is.close();
            		}
            	} catch (Exception e) {
        			// TODO Auto-generated catch block
        			e.printStackTrace();
        		}	
            }
            
            // Tell the AppWidgetManager to perform an update on the current App Widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
	}
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) { 
		update(context, appWidgetManager, appWidgetIds, false);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
		if (intent.getAction().equals(Player.REFRESH_TRACKINFO)) {
			name = (String) intent.getCharSequenceExtra("name");
			artist = (String) intent.getCharSequenceExtra("artist");
			nextvisible = intent.getBooleanExtra("nextvisible", false);
			pausebutton = intent.getBooleanExtra("pausebutton", false);
			playpausevisible = intent.getBooleanExtra("playpausevisible", false);
			ComponentName thisWidget = new ComponentName(context, 
		    PlayerWidget.class); 
		    AppWidgetManager appWidgetManager = 
		    AppWidgetManager.getInstance(context); 
		    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget); 
		    update(context, appWidgetManager, appWidgetIds, true); 
		}
	}
}
