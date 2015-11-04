package com.limegroup.gnutella.gui.bugs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;
import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.FileUtils;
import org.limewire.util.StringUtils;
import org.limewire.util.Version;
import org.limewire.util.VersionFormatException;

import com.frostwire.logging.Logger;
import com.frostwire.util.HttpClientFactory;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LimeWireModule;
import com.limegroup.gnutella.gui.LocalClientInfoFactory;
import com.limegroup.gnutella.gui.MessageService;
import com.limegroup.gnutella.gui.MultiLineLabel;
import com.limegroup.gnutella.settings.BugSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

/**
 * Interface for reporting bugs.
 * This can do any of the following:
 *  - Send the bug directly to the servlet
 *  - Allow the bug to be reviewed before sending
 *  - Allow the user to copy the bug & email it if sending fails.
 *  - Suppress the bug entirely
 */
public final class BugManager {
    
    private static final Logger LOG = Logger.getLogger(BugManager.class);

    private final LocalClientInfoFactory localClientInfoFactory;

    /**
     * The instance of BugManager -- follows a singleton pattern.
     */
    private static BugManager INSTANCE;
    
    /**
     * The error title
     */
    private final String TITLE =
        I18n.tr("Internal Error");
    
    //the width and the height of the internal error dialog box
    private static final int DIALOG_BOX_WIDTH = 300;
    private static final int DIALOG_BOX_HEIGHT = 100;
        
    /**
     * The queue that processes the bugs.
     */
    private final ExecutorService BUGS_QUEUE = ExecutorsHelper.newProcessingQueue(
                new ThreadFactory() {
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "BugProcessor");
                        t.setDaemon(true);
                        return t;
                    }
                });
	
	/**
	 * A mapping of stack traces (String) to next allowed time (long)
	 * that the bug can be reported.
	 *
	 * Used only if reporting the bug to the servlet.
	 */
	private final Map<String, Long> BUG_TIMES = Collections.synchronizedMap(new HashMap<String, Long>());
	
	/**
	 * A lock to be used when writing to the logfile, if the log is to be
	 * recorded locally.
	 */
	private final Object WRITE_LOCK = new Object();
	
	/**
	 * A separator between bug reports.
	 */
	private final byte[] SEPARATOR = "-----------------\n".getBytes();
	
	/**
	 * The next time we're allowed to send any bug.
	 *
	 * Used only if reporting the bug to the servlet.
	 */
	private volatile long _nextAllowedTime = 0;
	
	/**
	 * The number of bug dialogs currently showing.
	 */
	private int _dialogsShowing = 0;
	
	/**
	 * The maximum number of dialogs we're allowed to show.
	 */
	private final int MAX_DIALOGS = 3;
	
	public static synchronized BugManager instance() {
	    if(INSTANCE == null)
	        INSTANCE = new BugManager();
	    return INSTANCE;
	}
    
    /**
     * Private to ensure that only this class can construct a 
     * <tt>BugManager</tt>, thereby ensuring that only one instance is created.
     */
    private BugManager() {
        localClientInfoFactory = LimeWireModule.instance().getLimeWireGUIModule().getLimeWireGUI().getLocalClientInfoFactory();
    }
    
    /**
     * Shuts down the BugManager.
     */
    public void shutdown() {
    }
	
	/**
	 * Handles a single bug report.
	 * If bug is a ThreadDeath, rethrows it.
	 * If the user wants to ignore all bugs, this effectively does nothing.
	 * The the server told us to stop reporting this (or any) bug(s) for
	 * awhile, this effectively does nothing.
	 * Otherwise, it will either send the bug directly to the servlet
	 * or ask the user to review it before sending.
	 */
	public void handleBug(Throwable bug, String threadName, String detail) {
        if( bug instanceof ThreadDeath ) { // must rethrow.
	        throw (ThreadDeath)bug;
        }
	        
        // Try to dispatch the bug to a friendly handler.
        if(bug instanceof IOException && 
           handleException((IOException)bug, ErrorType.GENERIC)) {
           return; // handled already.
        }
        
        //Get the classpath
        String classPath = "";
        ClassLoader sysClassLoader = ClassLoader.getSystemClassLoader();
        URL[] urls = ((URLClassLoader)sysClassLoader).getURLs();
        for(int i=0; i< urls.length; i++) {
            classPath += "  " + urls[i].getFile() + "\n";
        }       
          
        //Add CLASSPATH to the report
        detail = detail + "\nCLASSPATH:\n" + classPath;
	    
        bug.printStackTrace();
        
        // Build the LocalClientInfo out of the info ...
        final LocalClientInfo info = localClientInfoFactory.createLocalClientInfo(bug, threadName, detail, false);

        if( BugSettings.LOG_BUGS_LOCALLY.getValue() ) {
            logBugLocally(info);
        }
                    
        boolean sent = false;
        if (BugSettings.IGNORE_ALL_BUGS.getValue()) {
            return; // ignore.
        }

        // If we have already sent information about this bug, leave.
        if (!shouldInform(info)) {
            return; // ignore.
        }

        // If the user wants to automatically send to the servlet, do so.
        // Otherwise, display it for review.
        if (isSendableVersion()) {
            if (BugSettings.USE_AUTOMATIC_BUG.getValue()) {
                sent = true;
            }
        }

        if (sent) {
            sendToServlet(info);
        }
        
        if (!sent &&  _dialogsShowing < MAX_DIALOGS ) {
            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    reviewBug(info);
                }
            });
        }
    }
    
    /**
     * Logs the bug report to a local file.
     * If the file reaches a certain size it is erased.
     */
    private void logBugLocally(LocalClientInfo info) {
        File f = BugSettings.BUG_LOG_FILE.getValue();
        FileUtils.setWriteable(f);
        OutputStream os = null;
        try {
            synchronized(WRITE_LOCK) {
                if ( f.length() > BugSettings.MAX_BUGFILE_SIZE.getValue() )
                    f.delete();
                os = new BufferedOutputStream(
                        new FileOutputStream(f.getPath(), true));
                os.write((new Date().toString() + "\n").getBytes());
                os.write(info.toBugReport().getBytes());
                os.write(SEPARATOR);
                os.flush();
            }
        } catch(IOException ignored) {
        } finally {
            IOUtils.closeQuietly(os);
        }
    }
    
    /**
     * Determines if the bug has already been reported enough.
     * If it has, this returns false.  Otherwise (if the bug should
     * be reported) this returns true.
     */
    private boolean shouldInform(LocalClientInfo info) {
        long now = System.currentTimeMillis();
        
        // If we aren't allowed to report a bug, exit.
        if( now < _nextAllowedTime )
            return false;

        Long allowed = BUG_TIMES.get(info.getParsedBug());
        return allowed == null || now >= allowed.longValue();
    }
    
    /**
     * Determines if we're allowed to send a bug report.
     */
    private boolean isSendableVersion() {
        Version myVersion;
        Version lastVersion;
        try {
            myVersion = new Version(FrostWireUtils.getFrostWireVersion());
            lastVersion = new Version(BugSettings.LAST_ACCEPTABLE_VERSION.getValue());
        } catch(VersionFormatException vfe) {
            return false;
        }
        
        return myVersion.compareTo(lastVersion) >= 0;
    }
    
    private static String warning() {
        //String msg = "Ui" + "jt!j" + "t!Mjn" + "fXjs" + "f/!U" + "if!pg"+
        //             "gjdjbm!xfc" + "tjuf!j" + "t!xx" + "x/mj" + "nfxjs" + "f/d" + "pn/";
        //StringBuilder ret = new StringBuilder(msg.length());
        //for(int i = 0; i < msg.length(); i++) {
        //    ret.append((char)(msg.charAt(i) - 1));
   	//
        //}
	//System.out.println("Bug Manager message: "+ ret.toString());	
        //return ret.toString();
	return "You are using FrostWire. www.frostwire.com";
    }
    
    /**
     * Displays a message to the user informing them an internal error
     * has occurred.  The user is asked to click 'send' to send the bug
     * report to the servlet and has the option to review the bug
     * before it is sent.
     */
    private void reviewBug(final LocalClientInfo info) {
        _dialogsShowing++;

		final JDialog DIALOG =
		    new JDialog(GUIMediator.getAppFrame(), TITLE, true);
		final Dimension DIALOG_DIMENSION = new Dimension(DIALOG_BOX_WIDTH, DIALOG_BOX_HEIGHT);
		DIALOG.setSize(DIALOG_DIMENSION);

        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        mainPanel.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
		
		boolean sendable = isSendableVersion();

        String msg;
        if(sendable)
            msg = I18n.tr("FrostWire has encountered an internal error. It is possible for FrostWire to recover and continue running normally. To aid with debugging, please click \'Send\' to notify FrostWire about the problem. If desired, you can click \'Review\' to look at the information that will be sent. Thank you.");
        else
            msg = I18n.tr("FrostWire has encountered an internal error. It is possible for FrostWire to recover and continue running normally. To continue using FrostWire, click \'Discard\'. If desired, you can click \'Review\' to look at the information about the error.");
        
        msg = warning() + "\n\n" + msg;
       
        MultiLineLabel label = new MultiLineLabel(msg, 400);        
        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new GridBagLayout());
        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.gridheight = GridBagConstraints.REMAINDER;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;        
        labelPanel.add(label, constraints); 
                
        String textAreaDescription;
        if(sendable)
            textAreaDescription = I18n.tr("Please add any comments you may have (e.g what caused the error).\nThank you and please use English.");
        else
            textAreaDescription = " ";
        
        final JTextArea userCommentsTextArea = new JTextArea(textAreaDescription);        
        userCommentsTextArea.setLineWrap(true);
        userCommentsTextArea.setWrapStyleWord(true);                        
        
        // When the user clicks anywhere in the text field, it highlights the whole text
        // so that user could just type over it without having to delete it manually
        userCommentsTextArea.addFocusListener(new FocusAdapter() {
             public void focusGained(FocusEvent e) {                                               
                 userCommentsTextArea.selectAll();                 
             }
        });
        JScrollPane userCommentsScrollPane = new JScrollPane(userCommentsTextArea);
        userCommentsScrollPane.setBorder(BorderFactory.createEtchedBorder());
        userCommentsScrollPane.setPreferredSize( new Dimension(400, 80) );        
        
        JPanel buttonPanel = new JPanel();
        JButton sendButton = new JButton(I18n.tr("Send"));
        sendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    info.addUserComments(userCommentsTextArea.getText());
			    sendToServlet(info);
				DIALOG.dispose();
				_dialogsShowing--;
			}
		});
        JButton reviewButton = new JButton(I18n.tr("Review"));
        reviewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {			    
			    info.addUserComments(userCommentsTextArea.getText());
		        JTextArea textArea = new JTextArea(info.toBugReport());
                textArea.setColumns(50);
                textArea.setEditable(false);
                textArea.setCaretPosition(0);
                JScrollPane scroller = new JScrollPane(textArea);
                scroller.setBorder(BorderFactory.createEtchedBorder());
                scroller.setPreferredSize( new Dimension(500, 200) );
                MessageService.instance().showMessage(scroller);
			}
		});
		JButton discardButton = new JButton(I18n.tr("Discard"));
		discardButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        DIALOG.dispose();
		        _dialogsShowing--;
		    }
		});
		if(sendable)
            buttonPanel.add(sendButton);
        buttonPanel.add(reviewButton);
        buttonPanel.add(discardButton);
        
        JPanel optionsPanel = new JPanel();
        JPanel innerPanel = new JPanel();
        ButtonGroup bg = new ButtonGroup();
        innerPanel.setLayout( new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
        optionsPanel.setLayout(new BorderLayout());
        final JRadioButton alwaysSend = new JRadioButton(I18n.tr("Always Send Immediately"));
        final JRadioButton alwaysReview = new JRadioButton(I18n.tr("Always Ask For Review"));
        final JRadioButton alwaysDiscard = new JRadioButton(I18n.tr("Always Discard All Errors"));
		innerPanel.add(Box.createVerticalStrut(6));        
        if (sendable) {
            innerPanel.add(alwaysSend);
        }
        innerPanel.add(alwaysReview);
        innerPanel.add(alwaysDiscard);
        innerPanel.add(Box.createVerticalStrut(6));        
        optionsPanel.add( innerPanel, BorderLayout.WEST );
        bg.add(alwaysSend);
        bg.add(alwaysReview);
        bg.add(alwaysDiscard);
        bg.setSelected(alwaysReview.getModel(), true);
        ActionListener alwaysListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if( e.getSource() == alwaysSend ) {
                    BugSettings.IGNORE_ALL_BUGS.setValue(false);
                    BugSettings.USE_AUTOMATIC_BUG.setValue(true);
                } else if (e.getSource() == alwaysReview ) {
                    BugSettings.IGNORE_ALL_BUGS.setValue(false);
                    BugSettings.USE_AUTOMATIC_BUG.setValue(false);
                } else if( e.getSource() == alwaysDiscard ) {                    
                    BugSettings.IGNORE_ALL_BUGS.setValue(true);
                }
            }
        };
        alwaysSend.addActionListener(alwaysListener);
        alwaysReview.addActionListener(alwaysListener);
        alwaysDiscard.addActionListener(alwaysListener);
        
        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        mainPanel.add(labelPanel, constraints);              
        
        if(sendable)
        {
            constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 1;
            constraints.fill = GridBagConstraints.BOTH;
            constraints.weightx = 1.0;            
            constraints.weighty = 1.0;
            constraints.insets = new Insets(20, 0, 6, 0);
            mainPanel.add(userCommentsScrollPane, constraints);
        }
            
        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 2;

        mainPanel.add(optionsPanel, constraints);
        
        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 3;
        
        mainPanel.add(buttonPanel, constraints);   
        
        DIALOG.getContentPane().add(mainPanel);
		DIALOG.pack();

		if(GUIMediator.isAppVisible())
			DIALOG.setLocationRelativeTo(MessageService.getParentComponent());
		else {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    		Dimension dialogSize = DIALOG.getSize();
    		DIALOG.setLocation((screenSize.width - dialogSize.width)/2,
    						   (screenSize.height - dialogSize.height)/2);
		}

		try {
		    DIALOG.setVisible(true);
        } catch(InternalError ie) {
            //happens occasionally, ignore.
        } catch(ArrayIndexOutOfBoundsException npe) {
            //happens occasionally, ignore.
        }
    }
    
    /**
     * Displays a message to the user informing them an internal error
     * has occurred and the send to the servlet has failed, asking
     * the user to email the bug to us.
     */
    private void servletSendFailed(final LocalClientInfo info) {
        _dialogsShowing++;

		final JDialog DIALOG =
		    new JDialog(GUIMediator.getAppFrame(), TITLE, true);
		final Dimension DIALOG_DIMENSION = new Dimension(350, 300);
		final Dimension ERROR_DIMENSION = new Dimension(300, 200);
		DIALOG.setSize(DIALOG_DIMENSION);

        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        MultiLineLabel label = new MultiLineLabel(I18n.tr("FrostWire was unable to connect to the bug server in order to send the below bug report. For further help and to aid with debugging, please visit www.frostwire.com and click \'Support\'. Thank you."), 400);
		JPanel labelPanel = new JPanel();
		JPanel innerPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
		innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
		innerPanel.add(label);
		innerPanel.add(Box.createVerticalStrut(6));
		labelPanel.add(innerPanel);
		labelPanel.add(Box.createHorizontalGlue());
		
		// Add 'FILES IN CURRENT DIRECTORY [text]
		//      SIZE: 0'
		// So that the script processing the emails still
		// works correctly.  [It uses the info as markers
		// of when to stop reading -- if it wasn't present
		// it failed processing the email correctly.]
		String bugInfo = info.toBugReport().trim() + "\n\n" + 
		                 "FILES IN CURRENT DIRECTORY NOT LISTED.\n" +
		                 "SIZE: 0";
        final JTextArea textArea = new JTextArea(bugInfo);
        textArea.selectAll();
        textArea.copy();        
        textArea.setColumns(50);
        textArea.setEditable(false);
        JScrollPane scroller = new JScrollPane(textArea);
        scroller.setBorder(BorderFactory.createEtchedBorder());
        scroller.setPreferredSize(ERROR_DIMENSION);		

        JPanel buttonPanel = new JPanel();
        JButton copyButton = new JButton(I18n.tr("Copy Report"));
        copyButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			    textArea.selectAll();
				textArea.copy();
				textArea.setCaretPosition(0);
			}
		});
        JButton quitButton = new JButton(I18n.tr("OK"));
        quitButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e){
				DIALOG.dispose();
				_dialogsShowing--;
			}
		});
        buttonPanel.add(copyButton);
        buttonPanel.add(quitButton);

        mainPanel.add(labelPanel);
        mainPanel.add(scroller);
        mainPanel.add(buttonPanel);

        DIALOG.getContentPane().add(mainPanel);
        try {
		    DIALOG.pack();
        } catch(OutOfMemoryError oome) {
            // we couldn't put this dialog together, discard it entirely.
            return;
        }

		if(GUIMediator.isAppVisible())
			DIALOG.setLocationRelativeTo(MessageService.getParentComponent());
		else {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    		Dimension dialogSize = DIALOG.getSize();
    		DIALOG.setLocation((screenSize.width - dialogSize.width)/2,
    						   (screenSize.height - dialogSize.height)/2);
		}

		DIALOG.setVisible(true);
    }    
    
    /**
     * Sends the bug to the servlet and updates the next allowed times
     * that this bug (or any bug) can be sent.
     * This is done in another thread so the current thread does not block
     * while connecting and transferring information to/from the servlet.
     * If the send failed, displays another message asking the user to email
     * the error.
     */
    private void sendToServlet(final LocalClientInfo info) {
        BUGS_QUEUE.execute(new ServletSender(info));
    }
    
    /**
     * Sends a single bug report.
     */
    private class ServletSender implements Runnable {
        final LocalClientInfo INFO;

        ServletSender(LocalClientInfo info) {
            INFO = info;
        }

        public void run() {
            String response = null;
            try {
                response = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).post(BugSettings.BUG_REPORT_SERVER.getValue(), 6000, "FrostWire-"+FrostWireUtils.getFrostWireVersion(), INFO.toBugReport(), "text/plain", false);
            } catch (Exception e) {
                LOG.error("Error sending bug report", e);
            }

            if (response == null) { // could not connect
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        servletSendFailed(INFO);
                    }
                });
                return;
            }

            long now = System.currentTimeMillis();
            long thisNextTime = 1000;

            synchronized (WRITE_LOCK) {
                _nextAllowedTime = now + thisNextTime;

                if (thisNextTime != 0) {
                    BUG_TIMES.put(INFO.getParsedBug(), new Long(now + 10 * thisNextTime));
                }
            }
        }
    }
    
    public static enum ErrorType {
        GENERIC, DOWNLOAD;
    }
    
    private static enum DetailErrorType {
        DISK_FULL, FILE_LOCKED, NO_PRIVS, BAD_CHARS;
    }
    
    private static final EnumMap<ErrorType, EnumMap<DetailErrorType, String>> errorDescs;
    
    static {
        errorDescs = new EnumMap<ErrorType, EnumMap<DetailErrorType,String>>(ErrorType.class);
        for(ErrorType type : ErrorType.values())
            errorDescs.put(type, new EnumMap<DetailErrorType, String>(DetailErrorType.class));
        
        errorDescs.get(ErrorType.GENERIC).put(DetailErrorType.DISK_FULL, 
            I18n.tr("FrostWire was unable to write a necessary file because your hard drive is full. To continue using FrostWire you must free up space on your hard drive."));
        errorDescs.get(ErrorType.GENERIC).put(DetailErrorType.FILE_LOCKED,
            I18n.tr("FrostWire was unable to open a necessary file because another program has locked the file. FrostWire may act unexpectedly until this file is released."));
        errorDescs.get(ErrorType.GENERIC).put(DetailErrorType.NO_PRIVS,
            I18n.tr("FrostWire was unable to write a necessary file because you do not have the necessary permissions. Your preferences may not be maintained the next time you start FrostWire, or FrostWire may behave in unexpected ways."));
        errorDescs.get(ErrorType.GENERIC).put(DetailErrorType.BAD_CHARS,
            I18n.tr("FrostWire cannot open a necessary file because the filename contains characters which are not supported by your operating system. FrostWire may behave in unexpected ways."));        


        errorDescs.get(ErrorType.DOWNLOAD).put(DetailErrorType.DISK_FULL,
            I18n.tr("FrostWire cannot download the selected file because your hard drive is full. To download more files, you must free up space on your hard drive."));
        errorDescs.get(ErrorType.DOWNLOAD).put(DetailErrorType.FILE_LOCKED,
            I18n.tr("FrostWire was unable to download the selected file because another program is using the file. Please close the other program and retry the download."));
        errorDescs.get(ErrorType.DOWNLOAD).put(DetailErrorType.NO_PRIVS,
            I18n.tr("FrostWire was unable to create or continue writing an incomplete file for the selected download because you do not have permission to write files to the incomplete folder. To continue using FrostWire, please choose a different Save Folder."));
        errorDescs.get(ErrorType.DOWNLOAD).put(DetailErrorType.BAD_CHARS,
            I18n.tr("FrostWire was unable to open the incomplete file for the selected download because the filename contains characters which are not supported by your operating system."));
        
        // just verify it was all setup right.
        for(ErrorType type : ErrorType.values()) {
            assert errorDescs.get(type) != null;
            assert errorDescs.get(type).size() == DetailErrorType.values().length;
        }
     
    }
    
    /**
     * Attempts to handle an IOException. If we know expect the problem,
     * we can either ignore it or display a friendly error (both returning
     * true, for handled) or expect the outer-world to handle it (and
     * return false).
     *
     * @return true if we could handle the error.
     */
    public static boolean handleException(IOException ioe, ErrorType errorType) {
        Throwable e = ioe;
        
        while(e != null) {
            String msg = e.getMessage();
            
            if(msg != null) {
                msg = msg.toLowerCase();
                DetailErrorType detailType = null;
                // If the user's disk is full, let them know.
                if(StringUtils.contains(msg, "no space left") || 
                   StringUtils.contains(msg, "not enough space")) {
                    detailType = DetailErrorType.DISK_FULL;
                }
                // If the file is locked, let them know.
                else if(StringUtils.contains(msg, "being used by another process") ||
                   StringUtils.contains(msg, "with a user-mapped section open")) {
                    detailType = DetailErrorType.FILE_LOCKED;
                }
                // If we don't have permissions to write, let them know.
                else if(StringUtils.contains(msg, "access is denied") || 
                   StringUtils.contains(msg, "permission denied") ) {
                    detailType = DetailErrorType.NO_PRIVS;
                }
                // If characterset is faulty...
                else if(StringUtils.contains(msg, "invalid argument")) {
                    detailType = DetailErrorType.BAD_CHARS;
                }
                
                if(detailType != null) {
                    MessageService.instance().showError(errorDescs.get(errorType).get(detailType));
                    return true;
                }
            }
            
            e = e.getCause();
        }

        // dunno what to do, let the outer world handle it.
        return false;
    }
}
