package org.limewire.service;

import java.text.MessageFormat;

/**
 * Defines the interface for a class to handle formatted and unformatted 
 * messages. This class contains methods to handle errors and messages with a 
 * message key. Additionally, <code>MessageCallback</code> has methods with a 
 * flag to receive future messages of the same type. Messages can be displayed 
 * in different ways, in a dialog box, the "standard" output stream, a file, etc.
 * <p>
 * <code>MessageCallback</code> includes methods to handle a fixed message 
 * (<code>showError</code>) or a variable length message (<code>showFormattedError</code>). 
 * A formatted message accepts arbitrary parameters you format when 
 * you implement <MessageCallback>. For example, one partial 
 * implementation of <code>MessageCallback</code> using {@link MessageFormat}
 * could be:
 * <p>
 * <pre>
    void showError(String messageKey, String message){
        System.out.println(messageKey + message);      
    }
    void showFormattedError(String messageKey, String... args){
        System.out.println(MessageFormat.format(messageKey, args));
    }
    
 Call:
    myMessageCallback.showError("File, directory, was moved to -> ", 
        "temp.dat, c:\\temp\\, c:\\documents and settings\\all users\\");
     
    myMessageCallback.showFormattedError(
            "File {0} in directory {1} was moved to {2}.", "temp.dat", 
            "c:\\temp\\", "c:\\documents and settings\\all users\\");

 Output: 
    File, directory, was moved to -> temp.dat, c:\temp\, 
        c:\documents and settings\all users\

    File temp.dat in directory c:\temp\ was moved to 
        c:\documents and settings\all users\.

</pre>
 */
public interface MessageCallback {

    /**
     * Displays an error to the user based on the provided message key.  This
     * appends the locale-specific string with another non-locale-specific
     * string, such as a file name.
     * 
     * @param messageKey the key for the locale-specific message to display
     */
    void showError(String messageKey);
    
    /**
     * Displays an error to the user based on the provided message key.  This
     * appends the locale-specific string with another non-locale-specific
     * string, such as a file name.
     * The message is only displayed if the Switch indicates the user
     * has chosen to display the message.
     * 
     * @param messageKey the key for the locale-specific message to display
     * @param ignore the BooleanSetting that stores whether or not the user
     *        has chosen to receive future warnings of this message.
     */
    void showError(String messageKey, Switch ignore);
    
    /**
     * Shows a locale-specific error message to the user, using the
     * given message key & the arguments for that key.
     */
    void showFormattedError(String errorKey, Object... args);
    
    /**
     * Shows a locale-specific formatted error to the user, using the
     * given message key & the arguments for that key. 
     * The message is only displayed if the Switch indicates
     * the user had chosen to display the message.
     * @param ignore the BooleanSetting that stores whether or not the user
     *        has chosen to receive future warnings of this message.
     */
    void showFormattedError(String errorKey, Switch ignore, Object... args);    

    
    /**
     * Shows a locale-specific message to the user using the given message key.
     * 
     * @param messageKey the key for looking up the locale-specific message
     *  in the resource bundles
     */
    void showMessage(String messageKey);
    
    /**
     * Shows a locale-specific message to the user using the given message key.
     * The message is only displayed if the Switch indicates the user
     * has chosen to display the message.
     * 
     * @param messageKey the key for looking up the locale-specific message
     *  in the resource bundles
     * @param ignore the BooleanSetting that stores whether or not the user
     *        has chosen to receive future warnings of this message.
     */
    void showMessage(String messageKey, Switch ignore);
    
    /**
     * Shows a locale-specific formatted message to the user, using the
     * given message key & the arguments for that key.
     * @param messageKey the key for looking up the locale-specific message
     *  in the resource bundles
     */
    void showFormattedMessage(String messageKey, Object... args);
    
    /**
     * Shows a locale-specific formatted message to the user, using the
     * given message key & the arguments for that key. 
     * The message is only displayed if the Switch indicates
     * the user had chosen to display the message.
     * @param messageKey the key for looking up the locale-specific message
     *  in the resource bundles
     * @param ignore the BooleanSetting that stores whether or not the user
     *        has chosen to receive future warnings of this message.
     */
    void showFormattedMessage(String messageKey, Switch ignore, Object... args);
}
