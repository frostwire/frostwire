/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.gudy.azureus2.platform.dummy;

import java.io.File;
import java.net.InetAddress;

import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerFactory;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;

//import com.aelitis.azureus.core.AzureusCore;



/**
 * @version 1.0
 */

public class PlatformManagerImpl implements PlatformManager
{

    private static PlatformManager singleton;

    static
    {
        singleton = new PlatformManagerImpl();
    }
    
    public static PlatformManager getSingleton()
    {
        return singleton;
    }

    private PlatformManagerImpl() {}

    /**
     * {@inheritDoc}
     */
    public int getPlatformType()
    {
        return( PlatformManagerFactory.getPlatformType());
    }

    /**
     * {@inheritDoc}
     */
    public String getUserDataDirectory()

        throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

    /**
     * {@inheritDoc}
     */
    public boolean isApplicationRegistered()

        throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

	public String
	getApplicationCommandLine()
	
		throws PlatformManagerException
	{
        throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
	public String 
	getComputerName() 
	{
		return null;
	}
	
	public File
	getLocation(
		long	location_id )
	
		throws PlatformManagerException
	{
	    return( null );
	}
	
	public File 
	getVMOptionFile() 
	
		throws PlatformManagerException 
	{
        throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
	public String[]
   	getExplicitVMOptions()
	          	
   		throws PlatformManagerException
	{
        throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	 
	public boolean
	getRunAtLogin()
	          	
	 	throws PlatformManagerException
	{
		 throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	public void
	setRunAtLogin(
		boolean		run )
	          	
	 	throws PlatformManagerException
	{
		 throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
//	public void
//	startup(
//		AzureusCore		azureus_core )
//
//		throws PlatformManagerException
//	{
//	}
	
	public int
	getShutdownTypes()
	{
		return( 0 );
	}
	
	public void
	shutdown(
		int			type )
	
		throws PlatformManagerException
	{	
		 throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
	public void
	setPreventComputerSleep(
		boolean			b )
	
		throws PlatformManagerException
	{	
		 throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
	public boolean
	getPreventComputerSleep()
	{
		return( false );
	}
	
	public void
	setExplicitVMOptions(
		String[]		options )
	          	
		throws PlatformManagerException
	{
        throw new PlatformManagerException("Unsupported capability called on platform manager");	
	}
	
	public boolean
	isAdditionalFileTypeRegistered(
		String		name,				// e.g. "BitTorrent"
		String		type )				// e.g. ".torrent"
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");
	}	
	
	public void
	unregisterAdditionalFileType(
			String		name,				// e.g. "BitTorrent"
			String		type )				// e.g. ".torrent"
		
		throws PlatformManagerException
	{
		throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

    /**
     * {@inheritDoc}
     */
    public void registerApplication()

        throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

	public void
	registerAdditionalFileType(
		String		name,				// e.g. "BitTorrent"
		String		description,		// e.g. "BitTorrent File"
		String		type,				// e.g. ".torrent"
		String		content_type )		// e.g. "application/x-bittorrent"
	
		throws PlatformManagerException
	{
	       throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
    /**
     * {@inheritDoc}
     */
    public void createProcess(String command_line, boolean inherit_handles)

        throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

    /**
     * {@inheritDoc}
     */
    public void performRecoverableFileDelete(String file_name)

        throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

    /**
     * {@inheritDoc}
     */
    public String getVersion()

    	throws PlatformManagerException
	{
    	throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

    /**
     * {@inheritDoc}
     */
	public void
	setTCPTOSEnabled(
		boolean		enabled )
		
		throws PlatformManagerException
	{
        throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	public void
    copyFilePermissions(
		String	from_file_name,
		String	to_file_name )
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");		
	}
	
    /**
     * {@inheritDoc}
     */
    public void showFile(String file_name)

            throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

	public boolean
	testNativeAvailability(
		String	name )
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");		
	}
	
	public int
	getMaxOpenFiles()
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");		
	}

    /**
     * Does nothing
     */
    public void dispose()
    {
    }

    // @see org.gudy.azureus2.platform.PlatformManager#getAzComputerID()
    public String getAzComputerID() throws PlatformManagerException {
    	throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

    public void requestUserAttention(int type, Object data) throws PlatformManagerException {
    	throw new PlatformManagerException("Unsupported capability called on platform manager");
    }
    
	public Class<?>
	loadClass(
		ClassLoader	loader,
		String		class_name )
		
		throws PlatformManagerException
	{
		try{
			return( loader.loadClass( class_name ));
			
		}catch( Throwable e ){
			
			throw( new PlatformManagerException( "load of '" + class_name + "' failed", e ));
		}
	}
}
