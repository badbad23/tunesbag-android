package com.tunesbag;

interface PlayerInterface {
    void setTrack( in int index);
    void preparePlayer( in List playlist, in String pathtocachedir, in int index);
    void pauseresumeTrack();
    void nextTrack();
    void previousTrack();
    //void setVolume();
    void seekTo( in int positioninpc );
    void beforeseekTo();
    void downloadTracks( in List playlist, in String pathtocachedir);
    void stopService();
    void setWidgetInfo();
    String getSendTrack();
    void updateGUI();
    void cancelPlaylistDownload();
    void preparePlayerNonPlay( in List playlist, in String pathtocachedir, in int index);
    boolean getPlayerState();
    void onCall(in boolean calling);
    void setINTIAL_KB_BUFFER(in int buffer);
}