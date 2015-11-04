package com.limegroup.gnutella.gui.tables;




/**
 * Wrapper class that acts as a comparable for the attempted / completed upload info.
 * @author sam berlin
 */
public final class UploadCountHolder implements Comparable<UploadCountHolder> {
	
	/**
	 * Variable for the string representation of the file size.
	 */
	private String _string;

	/**
	 * Variable for the info.
	 */
	private int _attempted, _completed;

	/**
	 * The constructor sets attempted / completed
	 *
	 */
	public UploadCountHolder(int attempted, int completed) {
		_string = Integer.toString(completed) + " / " + Integer.toString(attempted);
		_attempted = attempted;
		_completed = completed;
	}
	
	/**
	 * This one is larger if it has had more completed downloads.
	 * If the two have completed the same, it's larger if it 
	 * had more attempted downloads
	 */
	public int compareTo(UploadCountHolder other) {
	    if ( other.getCompleted() == _completed ) 
	        return _attempted - other.getAttempted();
	    return _completed - other.getCompleted();
	}

	/**
	 *
	 * @return the formatted string
	 */
	public String toString() {
		return _string;
	}
    
    public int getAttempted() { return _attempted; }
    public int getCompleted() { return _completed; }
}
