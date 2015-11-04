package com.frostwire.mplayer;

public interface TaskListener {
	
	public void taskStarted(String taskName);
	
	public void taskProgress(String taskName,int percent);
	
	public void taskEnded(String taskName);

}
