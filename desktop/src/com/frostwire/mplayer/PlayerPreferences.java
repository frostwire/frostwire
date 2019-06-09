package com.frostwire.mplayer;

public interface PlayerPreferences {
	
	void setVolume(int volume);
	int getVolume();

	void setPositionForFile(String file, float position);
	float getPositionForFile(String file);

}
