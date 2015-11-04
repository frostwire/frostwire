package com.frostwire.mplayer;

public interface MetaDataListener {
	
	public void receivedVideoResolution(int width,int height);
	
	public void receivedDisplayResolution(int width, int height);
	
	public void receivedDuration(float durationInSecs);
	
	public void foundAudioTrack(Language language);
	
	public void foundSubtitle(Language language);
	
	public void activeAudioTrackChanged(String audioTrackId);
	
	public void activeSubtitleChanged(String subtitleId,LanguageSource source);

}
