package com.limegroup.gnutella.gui.bugs;

public interface SessionInfo {

    public int getNumberOfPendingTimeouts();

    /**
     * Returns the number of downloads waiting to be started.
     */
    public int getNumWaitingDownloads();

    /**
     * Returns the number of individual downloaders.
     */
    public int getNumIndividualDownloaders();

    /**
     * Returns the current uptime.
     */
    public long getCurrentUptime();

    /**
     * Returns the number of active ultrapeer -> leaf connections.
     */
    public int getNumUltrapeerToLeafConnections();

    /**
     * Returns the number of leaf -> ultrapeer connections.
     */
    public int getNumLeafToUltrapeerConnections();

    /**
     * Returns the number of ultrapeer -> ultrapeer connections.
     */
    public int getNumUltrapeerToUltrapeerConnections();

    /**
     * Returns the number of old unrouted connections.
     */
    public int getNumOldConnections();

    public long getContentResponsesSize();

    public long getCreationCacheSize();

    public long getDiskControllerByteCacheSize();

    public long getDiskControllerVerifyingCacheSize();

    public int getDiskControllerQueueSize();

    public long getByteBufferCacheSize();

    public int getNumberOfWaitingSockets();
    
    /**
     * Returns whether or not this node is capable of sending its own
     * GUESS queries.  This would not be the case only if this node
     * has not successfully received an incoming UDP packet.
     *
     * @return <tt>true</tt> if this node is capable of running its own
     *  GUESS queries, <tt>false</tt> otherwise
     */
    public boolean isGUESSCapable();
    
    public boolean canReceiveSolicited();
    
    public boolean acceptedIncomingConnection();
    
    public int getPort();

}