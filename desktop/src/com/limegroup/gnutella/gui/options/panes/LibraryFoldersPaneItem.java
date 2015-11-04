package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.gui.library.AddLibraryDirectoryAction;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.RecursiveLibraryDirectoryPanel;
import com.frostwire.gui.library.RemoveLibraryDirectoryAction;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.SharingSettings;
import org.limewire.util.CommonUtils;
import org.limewire.util.FileUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class defines the panel in the options window that allows the user
 * to change the directory that are shared.
 */
public final class LibraryFoldersPaneItem extends AbstractPaneItem {

    public final static String TITLE = I18n.tr("Library Included Folders");
    
    public final static String LABEL = I18n.tr("You can choose the folders for include files when browsing the library.");

    private final JButton buttonAddLibraryDirectory;
    private final JButton buttonRemoveLibraryDirectory;
    
	private final RecursiveLibraryDirectoryPanel directoryPanel = new RecursiveLibraryDirectoryPanel(true);

	private Set<File> initialFoldersToInclude;
	
	private Set<File> initialFoldersToExclude;
	
	private final boolean  isPortable;
	
	public LibraryFoldersPaneItem() {
	    super(TITLE, LABEL);
	    isPortable = CommonUtils.isPortable();
	    
	    buttonAddLibraryDirectory = new JButton(new AddLibraryDirectoryAction(directoryPanel, directoryPanel));
	    buttonRemoveLibraryDirectory = new JButton(new RemoveLibraryDirectoryAction(directoryPanel));
	    
		directoryPanel.getTree().setRootVisible(false);
		directoryPanel.getTree().setShowsRootHandles(true);
		
		if (!isPortable) {
            directoryPanel.getTree().addTreeSelectionListener(new TreeSelectionListener() {
                public void valueChanged(TreeSelectionEvent e) {
                    Object comp = e.getPath().getLastPathComponent();
                    if (comp instanceof File) {
                        if (comp.equals(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue())) {
                            buttonRemoveLibraryDirectory.setEnabled(false);
                            return;
                        }
                    }
                    buttonRemoveLibraryDirectory.setEnabled(true);
                }
            });
		} else {
		    removeTreeSelectionListeners();
		}
		
		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.setBorder(new EmptyBorder(0, 4, 0, 0));
		JPanel buttons = new JPanel(new BorderLayout());
		buttons.add(buttonAddLibraryDirectory, BorderLayout.NORTH);
		buttons.add(Box.createVerticalStrut(4), BorderLayout.CENTER);
		buttons.add(buttonRemoveLibraryDirectory, BorderLayout.SOUTH);
		buttonPanel.add(buttons, BorderLayout.NORTH);
		directoryPanel.addEastPanel(buttonPanel);		
		add(directoryPanel);
		
		directoryPanel.getTree().setEnabled(!isPortable);
        buttonAddLibraryDirectory.setEnabled(!isPortable);
        buttonRemoveLibraryDirectory.setEnabled(!isPortable);
	}

    private void removeTreeSelectionListeners() {
        TreeSelectionListener[] treeSelectionListeners = directoryPanel.getTree().getTreeSelectionListeners();
        for (TreeSelectionListener tsl : treeSelectionListeners) {
            directoryPanel.getTree().removeTreeSelectionListener(tsl);
        }
    }

    /**
     * Adds a directory to the internal list, resetting 'dirty' only if it wasn't
     * dirty already.
     */
    void addAndKeepDirtyStatus(Set<File> foldersToShare, Set<File> foldersToExclude) {
        for (File folder : foldersToShare) {
            directoryPanel.addRoot(folder);
        }
        directoryPanel.addFoldersToExclude(foldersToExclude);
    }
    
    boolean isAlreadyGoingToBeIncluded(File dir) {
        if (directoryPanel.getFoldersToExclude().contains(dir)) {
            return false;
        }
        for (File folder : directoryPanel.getRootsToInclude()) {
            if (FileUtils.isAncestor(folder, dir)) {
                return true;
            }
        }
        return false;
    }
        
	/**
	 * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
	 *
	 * Sets the options for the fields in this <tt>PaneItem</tt> when the 
	 * window is shown.
	 */
	public void initOptions() {
		initialFoldersToInclude = LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue();
		initialFoldersToExclude = LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue();
		
		List<File> roots = new ArrayList<File>(LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue());
		roots.addAll(LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
		directoryPanel.setRoots(roots.toArray(new File[0]));
		directoryPanel.setFoldersToExclude(initialFoldersToExclude);
	}

	/**
	 * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
	 *
     * This makes sure that the shared directories have, in fact, changed to
     * make sure that we don't load the <tt>FileManager</tt> twice.  This is
     * particularly relevant to the case where the save directory has changed,
     * in which case we only want to reload the <tt>FileManager</tt> once for 
     * any changes.<p>
     * 
	 * Applies the options currently set in this window, displaying an
	 * error message to the user if a setting could not be applied.
	 *
	 * @throws <tt>IOException</tt> if the options could not be applied 
     *  for some reason
	 */
	public boolean applyOptions() throws IOException {
	    
	    LibrarySettings.DIRECTORIES_TO_INCLUDE.setValue(new HashSet<File>());
	    LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.setValue(new HashSet<File>());
	    
	    for(File f : directoryPanel.getRootsToInclude()) {
	        if (f != null) {
	            LibrarySettings.DIRECTORIES_TO_INCLUDE.add(f);
	        }
	    }
	    for(File f : directoryPanel.getFoldersToExclude()) {
	        if (f != null) {
        	        if (f.equals(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue())) {
        	            LibrarySettings.DIRECTORIES_TO_INCLUDE.add(f);
        	        } else {
        	            System.out.println("Not including " + f.getAbsolutePath());
        	            LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.add(f);
        	        }
	        }
        }

	    LibraryMediator.instance().clearDirectoryHolderCaches();
        return false;
	}

	public boolean isDirty() {
	    return !initialFoldersToInclude.equals(directoryPanel.getRootsToInclude())
	    || !initialFoldersToExclude.equals(directoryPanel.getFoldersToExclude());
    }
}





