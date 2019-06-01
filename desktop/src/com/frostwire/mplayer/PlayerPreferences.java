package com.frostwire.mplayer;

import java.awt.*;

public interface PlayerPreferences {
	
	void setVolume(int volume);
	int getVolume();
	
	void setWindowPosition(Point p);
	Point getWindowPosition();
	
	void setPositionForFile(String file, float position);
	float getPositionForFile(String file);

}
