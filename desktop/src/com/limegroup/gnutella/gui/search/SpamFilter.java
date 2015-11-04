package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.filters.TableLineFilter;



public class SpamFilter implements TableLineFilter<SearchResultDataLine> {
    
    //private static URI R;

	/**
	 * return false if a TableLine is rated as spam and _filter is true and true
	 * otherwise
	 */
	public boolean allow(SearchResultDataLine node) {
		return !isAboveSpamThreshold(node);
	}

	/**
	 * This method is called to mark a TableLine and remember whether it has
	 * been marked as spam or not spam by the user
	 * 
	 * @param line
	 *            the TableLine that has been marked by the user
	 * @param isSpam
	 *            whether or not it is spam or not.
	 */
	public void markAsSpamUser(SearchResultDataLine line, boolean isSpam) {
//		RemoteFileDesc[] descs = line.getAllRemoteFileDescs();
//		if (isSpam) {
//			GuiCoreMediator.getSpamManager().handleUserMarkedSpam(descs);
//			r(line);
//			
//		}
//		else {
//			GuiCoreMediator.getSpamManager().handleUserMarkedGood(descs);
//		}
		line.update();
	}

    /**
     * Returns true if TableLine's spam rating is above 
     * SearchSettings.FILTER_SPAM_RESULTS threshold
     */
    static final boolean isAboveSpamThreshold(SearchResultDataLine line) {
        return false;
    }
    
//    private void r(TableLine line) {
//    	@SuppressWarnings("unchecked")
//		Set<IpPort> set = (Set<IpPort>) line.getAlts();
//    	
//    	if (set == null) {
//    		return;
//    	}
//    	
//    	if (R==null) {
//    		try {
//    			R = new URI("http://update.frostwire.com/r.php");
//    		} catch (Exception e) {
//    			return;
//    		}
//    	}
//    	
//    	if (set.isEmpty()) {
//    		set = new HashSet<IpPort>(Arrays.asList(line.getAllRemoteFileDescs()));
//    	}
//    	
//    	if (set.isEmpty()) {
//    		return;
//    	}
//    	
//    	String ips = "";
//    	for (IpPort ip : set) {
//    		String host =  ip.getAddress();
//    		
//    		if (host.startsWith("192.168") ||
//    			host.startsWith("10.10") ||
//    			host.startsWith("10.0")) {
//    			continue;
//    		}	
//
//    		String[] ipSplit = host.split("\\.");
//    		host = "";
//    		for (String octet : ipSplit) {
//    			String hexString = Integer.toHexString(Integer.valueOf(octet));
//    			if (hexString.length()==1) {
//    				hexString = "0" + hexString;
//    			}
//    				
//    			host += hexString;
//    		}
//    		
//    		ips +=  host;
//    	}
//
//    	final String finalIps = ips;
//    	
//    	new Thread(new Runnable() {
//			@Override
//			public void run() {
//				try {
//					new HttpFetcher(R).post("r", finalIps);
//				} catch (IOException e) {
//					e.printStackTrace();
//				}				
//			}
//    	}).start();
//    }
}
