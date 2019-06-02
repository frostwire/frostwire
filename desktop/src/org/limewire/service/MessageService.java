package org.limewire.service;

/**
 * Forwards messages to a {@link MessageCallback}. <code>MessageService</code> 
 * includes static methods to set the <code>MessageCallback</code>, and to send 
 * formatted and unformatted error messages (an unformatted message 
 * accepts arbitrary parameters). 
 */
public class MessageService {

    /**
     * Variable for the <tt>MessageCallback</tt> implementation to use for 
     * displaying messages.
     */
    private volatile static MessageCallback _callback = new ShellMessageService();
    
    /**
     * Private constructor to ensure that this class cannot be instantiated.
     */
    private MessageService() {}

    /**
     * Sets the class to use for making callbacks to the user.
     * 
     * @param callback the <tt>MessageCallback</tt> instance to use
     */
    public static void setCallback(MessageCallback callback) {
        _callback = callback;
    }
    
    public static MessageCallback getCallback() {
        return _callback;
    }
    
    /**
     * Shows a locale-specific message to the user using the specified key to
     * look up the message in the resource bundles.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showError(String messageKey) {
        _callback.showError(messageKey);  
    }

    /**
     * Shows a locale-specific message to the user using the specified key to
     * look up the message in the resource bundles if the Switch
     * indicates to do so.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showError(String messageKey, Switch ignore) {
        _callback.showError(messageKey, ignore);
    }

    /**
     * Shows a locale-specific message to the user using the specified key to
     * look up the message in the resource bundles.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showMessage(String messageKey) {
        _callback.showMessage(messageKey);
    }

    /**
     * Shows a locale-specific message to the user using the specified key to
     * look up the message in the resource bundles if the Switch
     * indicates to do so.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showMessage(String messageKey, Switch ignore) {
        _callback.showMessage(messageKey, ignore);
    }
    
    /**
     * Shows a locale-specific formatted message to the user using the specified key to
     * look up the message in the resource bundles.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showFormattedMessage(String messageKey, Object... args) {
        _callback.showFormattedMessage(messageKey, args);
    }
    
    /**
     * Shows a locale-specific formatted message to the user using the specified key to
     * look up the message in the resource bundles if the Switch
     * indicates to do so.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showFormattedMessage(String messageKey, Switch ignore, Object... args) {
        _callback.showFormattedMessage(messageKey, ignore, args);
    }
    
    /**
     * Shows a locale-specific formatted error to the user using the specified key to
     * look up the message in the resource bundles.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showFormattedError(String errorKey, Object... args) {
        _callback.showFormattedError(errorKey, args);
    }    
    
    /**
     * Shows a locale-specific formatted error to the user using the specified key to
     * look up the message in the resource bundles if the Switch
     * indicates to do so.
     * 
     * @param messageKey the key for looking up the message to display in the
     *  resource bundles
     */
    public static void showFormattedError(String errorKey, Switch ignore, Object... args) {
        _callback.showFormattedError(errorKey, ignore, args);
    }
    
    /**
     * Default messaging class that simply displays messages in the console.
     */
    private static final class ShellMessageService implements MessageCallback {

        // Inherit doc comment.
        public void showError(String messageKey) {
            System.out.println("error key: "+messageKey);
        }

        // Inherit doc comment.        
        public void showError(String messageKey, Switch ignore) {
            showError(messageKey);
        }

        // Inherit doc comment.
        public void showMessage(String messageKey) {
            System.out.println("message key: " + messageKey); 
        }

        // Inherit doc comment.
        public void showMessage(String messageKey, Switch ignore) {
            showMessage(messageKey); 
        }

        public void showFormattedMessage(String messageKey, Object... args) {
            StringBuilder sb = new StringBuilder("message key: " + messageKey + " ");
            for(int i = 0; i < args.length; i++) {
                sb.append("arg[").append(i).append("]: ").append(args[i]);
                if(i < args.length - 1)
                    sb.append(", ");
            }
            System.out.println(sb);
        }

        public void showFormattedMessage(String messageKey, Switch ignore, Object... args) {
            showFormattedMessage(messageKey, args);
        }

        public void showFormattedError(String errorKey, Object... args) {
            StringBuilder sb = new StringBuilder("error key: " + errorKey + " ");
            for(int i = 0; i < args.length; i++) {
                sb.append("arg[").append(i).append("]: ").append(args[i]);
                if(i < args.length - 1)
                    sb.append(", ");
            }
            System.out.println(sb); 
        }

        public void showFormattedError(String errorKey, Switch ignore, Object... args) {
            showFormattedError(errorKey, args);
        }
        
    }

}
