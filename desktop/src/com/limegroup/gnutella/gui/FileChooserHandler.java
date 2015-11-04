package com.limegroup.gnutella.gui;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.FilenameUtils;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import com.limegroup.gnutella.settings.ApplicationSettings;

/**
 * This is a utility class that displays a file chooser dialog to the user,
 * automatically selecting the appropriate dialog based on the operating system,
 * the current theme, etc. For example, if the user is on OS X and is not using
 * the default theme, this displays the standard <tt>MetalLookAndFeel</tt>
 * file chooser, as that is the only one that will appear with themes.
 */
public final class FileChooserHandler {
    
    private FileChooserHandler() {}

    /**
     * Returns the last directory that was used in a FileChooser.
     * 
     * @return
     */
    public static File getLastInputDirectory() {
        File dir = ApplicationSettings.LAST_FILECHOOSER_DIRECTORY.getValue();
        if(dir == null || dir.getPath().equals("") || !dir.exists() || !dir.isDirectory())
            return CommonUtils.getCurrentDirectory();
        else 
            return dir;
    }
    
    /** Sets the last directory that was used for the FileChooser. */
    public static void setLastInputDirectory(File file) {
        if(file != null) {
            if(!file.exists() || !file.isDirectory())
                file = file.getParentFile();
            if(file != null) {
                if(file.exists() && file.isDirectory())
                    ApplicationSettings.LAST_FILECHOOSER_DIRECTORY.setValue(file);
            }
        }
    }
    

	/**
     * Displays a directory chooser to the user and returns the selected
     * <tt>File</tt>. This uses the main application frame as the parent
     * component.
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         directory was not selected correctly
     */
	public static File getInputDirectory() {
		return getInputDirectory(GUIMediator.getAppFrame());
	}

	/**
     * Same as <tt>getInputDirectory</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputDirectory(Component parent) {
		return getInputDirectory(parent, 
								 I18n.tr("Select Folder"), 
								 getLastInputDirectory());
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser as well
     * as other options.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param directory the directory to open the dialog to
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputDirectory(Component parent, 
										 File directory) {
		return getInputDirectory(parent, 
								 I18n.tr("Select Folder"), 
								 I18n.tr("Select"),
								 directory);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser as well
     * as other options.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param directory the directory to open the dialog to
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputDirectory(Component parent, 
										 File directory,
										 FileFilter filter) {
		return getInputDirectory(parent, 
								 I18n.tr("Select Folder"),  
								 I18n.tr("Select"),
								 directory,
								 filter);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser as well
     * as other options.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param directory the directory to open the dialog to
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputDirectory(Component parent, String titleKey,
										 File directory) {
		return getInputDirectory(parent, 
								 titleKey, 
								 I18n.tr("Select"),
								 directory);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser as well
     * as other options.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param directory the directory to open the dialog to
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputDirectory(Component parent, String titleKey,
										 File directory, FileFilter filter) {
		return getInputDirectory(parent, 
								 titleKey, 
								 I18n.tr("Select"),
								 directory,
								 filter);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser as well
     * as other options.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputDirectory(Component parent, String titleKey,
										 String approveKey, File directory) {
		return getInputDirectory(parent, 
								 titleKey, 
								 approveKey,
								 directory,
								 null);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser as well
     * as other options.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static File getInputDirectory(Component parent, String titleKey,
            String approveKey, File directory, FileFilter filter) {
        
        List<File> dirs = getInput(parent, titleKey, approveKey, directory,
                JFileChooser.DIRECTORIES_ONLY, JFileChooser.APPROVE_OPTION,
                false, filter);
        
        assert (dirs == null || dirs.size() <= 1) 
            : "selected more than one folder: " + dirs;
            
        if (dirs != null && dirs.size() == 1) {
            return dirs.get(0);
        } else {
            return null;
        }
    }

	/**
         * Displays a file chooser to the user and returns the selected
         * <tt>File</tt>. This uses the main application frame as the parent
         * component.
         * 
         * @return the selected <tt>File</tt> instance, or <tt>null</tt> if
         *         a file was not selected correctly
         */
	public static File getInputFile() {
		return getInputFile(GUIMediator.getAppFrame());
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputFile(Component parent) {
		return getInputFile(parent, 
							I18n.tr("Select Folder"), 
							I18n.tr("Select"),
							getLastInputDirectory());
	}


	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputFile(Component parent, FileFilter filter) {
		return getInputFile(parent, 
							I18n.tr("Select Folder"), 
							I18n.tr("Select"),
                            getLastInputDirectory(),
							filter);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param directory the directory to open the dialog to
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputFile(Component parent, String titleKey,
									File directory) {
		return getInputFile(parent, 
							titleKey, 
							I18n.tr("Select"),
							directory);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param directory the directory to open the dialog to
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputFile(Component parent, String titleKey,
									File directory, FileFilter filter) {
		return getInputFile(parent, 
							titleKey, 
							I18n.tr("Select"),
							directory,
							filter);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static File getInputFile(Component parent, String titleKey,
									String approveKey, File directory) {
		return getInput(parent, 
						titleKey, 
						approveKey,
						directory,
						JFileChooser.FILES_ONLY,
						JFileChooser.APPROVE_OPTION);
	}

	/**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static File getInputFile(Component parent, String titleKey,
            String approveKey, File directory, FileFilter filter) {
        
        List<File> files = getInput(parent, titleKey, approveKey, directory,
                JFileChooser.FILES_ONLY, JFileChooser.APPROVE_OPTION, false,
                filter);

        assert (files == null || files.size() <= 1) 
            : "selected more than one folder: " + files;

        if (files != null && files.size() == 1) {
            return files.get(0);
        } else {
            return null;
        }
    }


	/**
         * The implementation that the other methods delegate to. This provides
         * the caller with all available options for customizing the
         * <tt>JFileChooser</tt> instance. If a <tt>FileDialog</tt> is
         * displayed instead of a <tt>JFileChooser</tt> (on OS X, for
         * example), most or all of these options have no effect.
         * 
         * @param parent
         *                the <tt>Component</tt> that should be the dialog's
         *                parent
         * @param titleKey
         *                the key for the locale-specific string to use for the
         *                file dialog title
         * @param approveKey
         *                the key for the locale-specific string to use for the
         *                approve button text
         * @param directory
         *                the directory to open the dialog to
         * @param mode
         *                the "mode" to open the <tt>JFileChooser</tt> in from
         *                the <tt>JFileChooser</tt> class, such as
         *                <tt>JFileChooser.DIRECTORIES_ONLY</tt>
         * @param option
         *                the option to look for in the return code, such as
         *                <tt>JFileChooser.APPROVE_OPTION</tt>
         * 
         * @return the selected <tt>File</tt> instance, or <tt>null</tt> if
         *         a file was not selected correctly
         */
    public static File getInput(Component parent, String titleKey,
            String approveKey, File directory, int mode, int option) {
	    
        List<File> files = getInput(parent, titleKey, approveKey, directory,
                mode, option, false, null);
        
        assert (files == null || files.size() <= 1) 
            : "selected more than one folder: " + files;

        if (files != null && files.size() == 1) {
            return files.get(0);
        } else {
            return null;
        }
    }
	

    /**
     * Displays a file chooser to the user and returns the selected
     * <tt>File</tt>. This uses the main application frame as the parent
     * component.
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInputFile() {
        return getMultiInputFile(GUIMediator.getAppFrame());
    }

    /**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInputFile(Component parent) {
        return getMultiInputFile(parent, 
                                 I18n.tr("Select Folder"), 
                                 I18n.tr("Select"),
                                 getLastInputDirectory());
    }


    /**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInputFile(Component parent, FileFilter filter) {
        return getMultiInputFile(parent, 
                                 I18n.tr("Select Folder"), 
                                 I18n.tr("Select"),
                                 getLastInputDirectory(),
                                 filter);
    }

    /**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param directory the directory to open the dialog to
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInputFile(Component parent, String titleKey,
                                    File directory) {
        return getMultiInputFile(parent, 
                                 titleKey, 
                                 I18n.tr("Select"),
                                 directory);
    }

    /**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param directory the directory to open the dialog to
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInputFile(Component parent, String titleKey,
                                    File directory, FileFilter filter) {
        return getMultiInputFile(parent, 
                                 titleKey, 
                                 I18n.tr("Select"),
                                 directory,
                                 filter);
    }

    /**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInputFile(Component parent, String titleKey,
                                    String approveKey, File directory) {
        return getMultiInput(parent, 
                             titleKey, 
                             approveKey,
                             directory,
                             JFileChooser.FILES_ONLY,
                             JFileChooser.APPROVE_OPTION);
    }

    /**
     * Same as <tt>getInputFile</tt> that takes no arguments, except this
     * allows the caller to specify the parent component of the chooser.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInputFile(Component parent,
                                    String titleKey,
                                    String approveKey,
                                    File directory,
                                    FileFilter filter) {
       return getInput(parent, 
                       titleKey, 
                       approveKey,
                       directory,
                       JFileChooser.FILES_ONLY,
                       JFileChooser.APPROVE_OPTION,
                       true,
                       filter);
    }


    /**
     * The implementation that the other methods delegate to. This provides the
     * caller with all available options for customizing the
     * <tt>JFileChooser</tt> instance. If a <tt>FileDialog</tt> is displayed
     * instead of a <tt>JFileChooser</tt> (on OS X, for example), most or all
     * of these options have no effect.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * @param mode the "mode" to open the <tt>JFileChooser</tt> in from the
     *        <tt>JFileChooser</tt> class, such as
     *        <tt>JFileChooser.DIRECTORIES_ONLY</tt>
     * @param option the option to look for in the return code, such as
     *        <tt>JFileChooser.APPROVE_OPTION</tt>
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
    public static List<File> getMultiInput(Component parent,
                                String titleKey,
                                String approveKey,
                                File directory,
                                int mode,
                                int option) {
        return getInput(parent,
                        titleKey, 
                        approveKey,
                        directory,
                        mode,
                        option,
                        true,
                        null);
    }
	
	
	/**
     * Opens a dialog asking the user to choose a file which is used for
     * saving to.
     * 
     * @param parent the parent component the dialog is centered on
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param suggestedFile the suggested file for saving
     * @return the file or <code>null</code> when the user cancelled the
     *         dialog
     */
	public static File getSaveAsFile(Component parent, String titleKey, File suggestedFile) {
	    return getSaveAsFile(parent, titleKey, suggestedFile, null);
    }
	
	/**
     * Opens a dialog asking the user to choose a file which is used for
     * saving to.
     * 
     * @param parent the parent component the dialog is centered on
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param suggestedFile the suggested file for saving
     * @param filter to use for what's shown.
     * @return the file or <code>null</code> when the user cancelled the
     *         dialog
     */
    public static File getSaveAsFile(Component parent, String titleKey,
	                                 File suggestedFile, final FileFilter filter) {
        FileDialog dialog = new FileDialog(GUIMediator.getAppFrame(),
                I18n.tr(titleKey),
                FileDialog.SAVE);
        dialog.setDirectory(suggestedFile.getParent());
        dialog.setFile(suggestedFile.getName());
        if(filter != null) {
            FilenameFilter f = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return filter.accept(new File(dir, name));
                }
            };
            dialog.setFilenameFilter(f);
        }

        dialog.setVisible(true);
        String dir = dialog.getDirectory();
        String file = dialog.getFile();
        if(dir != null && file != null) {

            if (suggestedFile!=null ) {
                String suggestedFileExtension = FilenameUtils
                        .getExtension(suggestedFile.getName());

                String newFileExtension = FilenameUtils.getExtension(file);

                if (newFileExtension == null && suggestedFileExtension!=null) {
                    file = file + "." + suggestedFileExtension;
                }
            }

            File f = new File(dir, file);
            if(filter != null && !filter.accept(f)) {
                return null;
            } else {
                setLastInputDirectory(new File(dir));
                return f;
            }
        } else {
            return null;
        }
	}
    
    public static File getSaveAsDir(Component parent, String titleKey, File suggestedFile) {
	    return getSaveAsDir(parent, titleKey, suggestedFile, null);
    }
    
    public static File getSaveAsDir(Component parent, String titleKey,
			File suggestedFile, final FileFilter filter) {
		if (OSUtils.isAnyMac()) {
			FileDialog dialog = new FileDialog(GUIMediator.getAppFrame(), I18n
					.tr(titleKey), FileDialog.SAVE);
			dialog.setDirectory(suggestedFile.getParent());
			dialog.setFile(suggestedFile.getName());

			if (filter != null) {
				FilenameFilter f = new FilenameFilter() {
					public boolean accept(File dir, String name) {
						return filter.accept(new File(dir, name));
					}
				};
				dialog.setFilenameFilter(f);
			}

			dialog.setVisible(true);
			String dir = dialog.getDirectory();
			setLastInputDirectory(new File(dir));

            System.setProperty("apple.awt.fileDialogForDirectories", "false");
			if (dir != null) {
				File f = new File(dir);
				if (filter != null && !filter.accept(f)) {
					return null;
				}
				else {
					return f;
				}
			} else {
				return null;
			}
		} else {
			JFileChooser chooser = getDirectoryChooser(titleKey, null, null,
					JFileChooser.DIRECTORIES_ONLY, filter);
			chooser.setSelectedFile(suggestedFile);
			int ret = chooser.showSaveDialog(parent);
			File file = chooser.getSelectedFile();
			setLastInputDirectory(file);
			return ret != JFileChooser.APPROVE_OPTION ? null : file;
		}
	}

	/**
     * The implementation that the other methods delegate to. This provides the
     * caller with all available options for customizing the
     * <tt>JFileChooser</tt> instance. If a <tt>FileDialog</tt> is displayed
     * instead of a <tt>JFileChooser</tt> (on OS X, for example), most or all
     * of these options have no effect.
     * 
     * @param parent the <tt>Component</tt> that should be the dialog's parent
     * @param titleKey the key for the locale-specific string to use for the
     *        file dialog title
     * @param approveKey the key for the locale-specific string to use for the
     *        approve button text
     * @param directory the directory to open the dialog to
     * @param mode the "mode" to open the <tt>JFileChooser</tt> in from the
     *        <tt>JFileChooser</tt> class, such as
     *        <tt>JFileChooser.DIRECTORIES_ONLY</tt>
     * @param option the option to look for in the return code, such as
     *        <tt>JFileChooser.APPROVE_OPTION</tt>
     * @param allowMultiSelect true if the chooser allows multiple files to be
     *        chosen
     * @param filter the <tt>FileFilter</tt> instance for customizing the
     *        files that are displayed -- if this is null, no filter is used
     * 
     * @return the selected <tt>File</tt> instance, or <tt>null</tt> if a
     *         file was not selected correctly
     */
	public static List<File> getInput(Component parent, String titleKey,
								String approveKey,
								File directory,
								final int mode,
								int option,
								boolean allowMultiSelect,
								final FileFilter filter) {
            if(mode == JFileChooser.DIRECTORIES_ONLY && !OSUtils.isAnyMac()) {
                JFileChooser fileChooser = getDirectoryChooser(titleKey, approveKey, directory, mode, filter);
                fileChooser.setMultiSelectionEnabled(allowMultiSelect);
                try {
                    if(fileChooser.showOpenDialog(parent) != option)
                        return null;
                } catch(NullPointerException npe) {
                    // ignore NPE.  can't do anything with it ...
                    return null;
                }
                
                if(allowMultiSelect) {
                    File[] chosen = fileChooser.getSelectedFiles();
                    if(chosen.length > 0)
                        setLastInputDirectory(chosen[0]);
                    return Arrays.asList(chosen);
                } else {
                    File chosen = fileChooser.getSelectedFile();
                    setLastInputDirectory(chosen);
                    return Collections.singletonList(chosen);
                }
                
            } else {
                if (mode == JFileChooser.DIRECTORIES_ONLY) {
                    System.setProperty("apple.awt.fileDialogForDirectories", "true");
                }

                FileDialog dialog = new FileDialog(GUIMediator.getAppFrame(), "");
                dialog.setTitle(I18n.tr(titleKey));

                if(filter != null) {
                    FilenameFilter f = new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            if (mode == JFileChooser.DIRECTORIES_ONLY) {
                                return  new File(dir,name).isDirectory();
                            }
                            return filter.accept(new File(dir, name));
                        }
                    };
                    dialog.setFilenameFilter(f);
                }

                if (directory != null) {
                    dialog.setDirectory(directory.getAbsolutePath());
                }

                dialog.setVisible(true);
                String dirStr = dialog.getDirectory();
                String fileStr = dialog.getFile();

                if (mode == JFileChooser.DIRECTORIES_ONLY) {
                    //revert
                    System.setProperty("apple.awt.fileDialogForDirectories", "false");
                }

                if((dirStr==null) || (fileStr==null))
                    return null;
                setLastInputDirectory(new File(dirStr));
                // if the filter didn't work, pretend that the person picked
                // nothing
                File f = new File(dirStr, fileStr);
                if(filter != null && !filter.accept(f))
                    return null;
                
                return Collections.singletonList(f);
            }
	}

    /**
     * Returns a new <tt>JFileChooser</tt> instance for selecting directories
     * and with internationalized strings for the caption and the selection
     * button.
     * 
     * @param approveKey can be <code>null</code>
     * @param directory can be <code>null</code>
     * @param filter can be <code>null</code>
     * @return a new <tt>JFileChooser</tt> instance for selecting directories.
     */
    private static JFileChooser getDirectoryChooser(String titleKey,
            String approveKey, File directory, int mode, FileFilter filter) {
        JFileChooser chooser = null;
        if (directory == null)
            directory = getLastInputDirectory();
        

        if(directory == null) {
            chooser = new JFileChooser();
        } else {
            try {
                chooser = new JFileChooser(directory);
            } catch (NullPointerException e) {
                // Workaround for JRE bug 4711700. A NullPointer is thrown
                // sometimes on the first construction under XP look and feel,
                // but construction succeeds on successive attempts.
                try {
                    chooser = new JFileChooser(directory);
                } catch (NullPointerException npe) {
                    // ok, now we use the metal file chooser, takes a long time to load
                    // but the user can still use the program
                    UIManager.getDefaults().put("FileChooserUI", "javax.swing.plaf.metal.MetalFileChooserUI");
                    chooser = new JFileChooser(directory);
                }
            } catch (ArrayIndexOutOfBoundsException ie) {
            	// workaround for Windows XP, not sure if second try succeeds
            	// then
            	chooser = new JFileChooser(directory);
            }
        }

        prepareForWindowEvents(chooser);

        if (filter != null) {
            chooser.setFileFilter(filter);
        } else {
			if (mode == JFileChooser.DIRECTORIES_ONLY) {
				chooser.setFileFilter(new FileFilter() {
					public boolean accept(File file) {
						return true;
					}
					public String getDescription() {
						return I18n.tr("All Folders");
					}
				});
			}
        }
        chooser.setFileSelectionMode(mode);
        String title = I18n.tr(titleKey);
        chooser.setDialogTitle(title);

        if (approveKey != null) {
			String approveButtonText = I18n.tr(approveKey);
			chooser.setApproveButtonText(approveButtonText);
		}
        return chooser;
    }

    private static void prepareForWindowEvents(JFileChooser fileChooser) {
        fileChooser.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                onFileChooserResized(e.getComponent().getSize().width,
                        e.getComponent().getSize().height);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                onFileChooserMoved(e.getComponent().getX(), e.getComponent().getY());
            }
        });

        if (ApplicationSettings.FILECHOOSER_X_POS.getValue() == -1) {
            //first time ever. calculate centered x,y offset. (Big-Small)/2.
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            ApplicationSettings.FILECHOOSER_X_POS.setValue((screenSize.width - ApplicationSettings.FILECHOOSER_WIDTH.getValue()) >> 1);
            ApplicationSettings.FILECHOOSER_Y_POS.setValue((screenSize.height - ApplicationSettings.FILECHOOSER_HEIGHT.getValue()) >> 1);
        }

        fileChooser.setLocation(ApplicationSettings.FILECHOOSER_X_POS.getValue(),
                ApplicationSettings.FILECHOOSER_Y_POS.getValue());

        fileChooser.setPreferredSize(new Dimension(ApplicationSettings.FILECHOOSER_WIDTH.getValue(),
                ApplicationSettings.FILECHOOSER_HEIGHT.getValue()));

    }

    private static void onFileChooserResized(int width, int height) {
        ApplicationSettings.FILECHOOSER_WIDTH.setValue(width);
        ApplicationSettings.FILECHOOSER_HEIGHT.setValue(height);
    }

    private static void onFileChooserMoved(int x, int y) {
        ApplicationSettings.FILECHOOSER_X_POS.setValue(x);
        ApplicationSettings.FILECHOOSER_Y_POS.setValue(y);
    }
}
