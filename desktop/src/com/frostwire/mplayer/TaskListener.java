package com.frostwire.mplayer;

public interface TaskListener {
	
	void taskStarted(String taskName);
	
	void taskProgress(String taskName, int percent);
	
	void taskEnded(String taskName);

}
