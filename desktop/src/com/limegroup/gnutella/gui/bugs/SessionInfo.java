package com.limegroup.gnutella.gui.bugs;

public interface SessionInfo {

    int getNumberOfPendingTimeouts();

    /**
     * Returns the number of downloads waiting to be started.
     */
    int getNumWaitingDownloads();

    /**
     * Returns the number of individual downloaders.
     */
    int getNumIndividualDownloaders();

    /**
     * Returns the current uptime.
     */
    long getCurrentUptime();

    /**
     * Returns the number of active ultrapeer -> leaf connections.
     */
    int getNumUltrapeerToLeafConnections();

    /**
     * Returns the number of leaf -> ultrapeer connections.
     */
    int getNumLeafToUltrapeerConnections();

    /**
     * Returns the number of ultrapeer -> ultrapeer connections.
     */
    int getNumUltrapeerToUltrapeerConnections();

    /**
     * Returns the number of old unrouted connections.
     */
    int getNumOldConnections();

    long getContentResponsesSize();

    long getCreationCacheSize();

    long getDiskControllerByteCacheSize();

    long getDiskControllerVerifyingCacheSize();

    int getDiskControllerQueueSize();

    long getByteBufferCacheSize();

    int getNumberOfWaitingSockets();
    
    /**
     * Returns whether or not this node is capable of sending its own
     * GUESS queries.  This would not be the case only if this node
     * has not successfully received an incoming UDP packet.
     *
     * @return <tt>true</tt> if this node is capable of running its own
     *  GUESS queries, <tt>false</tt> otherwise
     */
    boolean isGUESSCapable();
    
    boolean canReceiveSolicited();
    
    boolean acceptedIncomingConnection();
    
    int getPort();

}