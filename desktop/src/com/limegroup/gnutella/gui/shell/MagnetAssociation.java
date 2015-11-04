package com.limegroup.gnutella.gui.shell;

public class MagnetAssociation implements ShellAssociation {

	/** The extension for magnet: links, "magnet", without punctuation. */
	private static final String MAGNET_EXTENTION = "magnet";
	/** The name of the magnet: link protocol, "Magnet Protocol". */
	private static final String MAGNET_PROTOCOL = "Magnet Protocol";
	
	private final ShellAssociation protocol, handler;
	
	public MagnetAssociation(String program, String executable) {
		protocol = new WindowsProtocolShellAssociation(executable,
				MAGNET_EXTENTION,
				MAGNET_PROTOCOL);
		handler = new WindowsMagnetHandlerAssociation(program, executable);
	}
	
	public boolean isAvailable() {
		return protocol.isAvailable();
	}

	public boolean isRegistered() {
		return protocol.isRegistered();
	}

	public void register() {
		protocol.register();
		handler.register();
	}

	public void unregister() {
		protocol.unregister();
		if (handler.isRegistered())
			handler.unregister();
	}
}
