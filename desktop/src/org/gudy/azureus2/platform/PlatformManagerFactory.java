/*
 * Created on 18-Apr-2004
 * Created by Paul Gardner
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
 *
 */

package org.gudy.azureus2.platform;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;

/**
 * @author parg
 *
 */
public class 
PlatformManagerFactory 
{
	protected static PlatformManager		platform_manager;
	protected static AEMonitor				class_mon	= new AEMonitor( "PlatformManagerFactory");
	
	public static PlatformManager
	getPlatformManager()
	{
		try{
			boolean force_dummy = System.getProperty( "azureus.platform.manager.disable", "false" ).equals( "true" );
			
			class_mon.enter();
		
			if ( platform_manager == null && !force_dummy ){
										  
				/*try{
					String cla = System.getProperty( "az.factory.platformmanager.impl", "" );

					if ( cla.length()> 0 ){
						
						platform_manager = (PlatformManager)Class.forName( cla ).newInstance();
						
					}else{
						if ( getPlatformType() == PlatformManager.PT_WINDOWS ){
							
							platform_manager = org.gudy.azureus2.platform.win32.PlatformManagerImpl.getSingleton();
							
						}else if( getPlatformType() == PlatformManager.PT_MACOSX ){
							
		                    platform_manager = org.gudy.azureus2.platform.macosx.PlatformManagerImpl.getSingleton();
		                    
						}else if( getPlatformType() == PlatformManager.PT_UNIX ){
							
							platform_manager = org.gudy.azureus2.platform.unix.PlatformManagerImpl.getSingleton();
						}
					}
				}catch( PlatformManagerException e ){
					
						// exception will already have been logged
					
				}catch( Throwable e ){
					
					Debug.printStackTrace(e);
				}*/
			}
			
			if ( platform_manager == null ){
				
				platform_manager = org.gudy.azureus2.platform.dummy.PlatformManagerImpl.getSingleton();
			}
			
			return( platform_manager );
			
		}finally{
			
			class_mon.exit();
		}
	}
	
	public static int
	getPlatformType()
	{
		if (Constants.isWindows) {

			return (PlatformManager.PT_WINDOWS );

		} else if (Constants.isOSX) {

			return (PlatformManager.PT_MACOSX );

		} else if (Constants.isUnix) {

			return (PlatformManager.PT_UNIX );

		} else {
			return (PlatformManager.PT_OTHER );
		}
	}
}
