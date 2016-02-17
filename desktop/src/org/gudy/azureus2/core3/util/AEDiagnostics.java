/*
 * Created on 22-Sep-2004
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

package org.gudy.azureus2.core3.util;

//import org.gudy.azureus2.core3.config.COConfigurationManager;
//import org.gudy.azureus2.core3.config.ParameterListener;
//import org.gudy.azureus2.platform.PlatformManager;
//import org.gudy.azureus2.platform.PlatformManagerCapabilities;
//import org.gudy.azureus2.platform.PlatformManagerFactory;


/**
 * @author parg
 *
 */

import java.io.*;
import java.util.*;

public class 
AEDiagnostics 
{
	// these can not be set true and have a usable AZ!
	public static final boolean	ALWAYS_PASS_HASH_CHECKS			= false;
	public static final boolean	USE_DUMMY_FILE_DATA				= false;
	public static final boolean	CHECK_DUMMY_FILE_DATA			= false;

	// these can safely be set true, things will work just slower
	public static final boolean	DEBUG_MONITOR_SEM_USAGE			= false;
    public static final boolean DEBUG_THREADS			        = true; // Leave this on by default for the moment

	public static final boolean	TRACE_DIRECT_BYTE_BUFFERS		= false;
	public static final boolean	TRACE_DBB_POOL_USAGE			= false;
	public static final boolean	PRINT_DBB_POOL_USAGE			= false;
  
    public static final boolean TRACE_TCP_TRANSPORT_STATS       = false;
    public static final boolean TRACE_CONNECTION_DROPS          = false;
    
	
	static{
		if ( ALWAYS_PASS_HASH_CHECKS ){
			System.out.println( "**** Always passing hash checks ****" );
		}
		if ( USE_DUMMY_FILE_DATA ){
			System.out.println( "**** Using dummy file data ****" );
		}
		if ( CHECK_DUMMY_FILE_DATA ){
			System.out.println( "**** Checking dummy file data ****" );
		}
		if ( DEBUG_MONITOR_SEM_USAGE ){
			System.out.println( "**** AEMonitor/AESemaphore debug on ****" );
		}
		if ( TRACE_DIRECT_BYTE_BUFFERS ){
			System.out.println( "**** DirectByteBuffer tracing on ****" );
		}
		if ( TRACE_DBB_POOL_USAGE ){
			System.out.println( "**** DirectByteBufferPool tracing on ****" );
		}
		if ( PRINT_DBB_POOL_USAGE ){
			System.out.println( "**** DirectByteBufferPool printing on ****" );
		}
		if ( TRACE_TCP_TRANSPORT_STATS ){
		  System.out.println( "**** TCP_TRANSPORT_STATS tracing on ****" );
		}
		
		int maxFileSize = 256 * 1024;
		try {
			String logSize = System.getProperty("diag.logsize", null);
			if (logSize != null) {
				if (logSize.toLowerCase().endsWith("m")) {
					maxFileSize = Integer.parseInt(logSize.substring(0,
							logSize.length() - 1)) * 1024 * 1024;
				} else {
					maxFileSize = Integer.parseInt(logSize);
				}
			}
		} catch (Throwable t) {
		}
		MAX_FILE_SIZE = maxFileSize;
	}
	
	private static final int	MAX_FILE_SIZE;	// get two of these per logger type
	
	private static final String	CONFIG_KEY	= "diagnostics.tidy_close";
	
	private static File	debug_dir;

	private static File	debug_save_dir;
	
	private static boolean	started_up;
	private static volatile boolean	startup_complete;
	private static boolean	enable_pending_writes;
	
	private static Map<String,AEDiagnosticsLogger>		loggers	= new HashMap<String, AEDiagnosticsLogger>();
	
	protected static boolean	logging_enabled;
	protected static boolean	loggers_enabled;
	
	private static List<AEDiagnosticsEvidenceGenerator>		evidence_generators	= new ArrayList<AEDiagnosticsEvidenceGenerator>();
	
	/**
	 * 
	 */
	private static synchronized void cleanOldLogs() {
		try {
			long now = SystemTime.getCurrentTime();

			// clear out any really old files in the save-dir

			File[] files = debug_save_dir.listFiles();

			if (files != null) {

				for (int i = 0; i < files.length; i++) {

					File file = files[i];

					if (!file.isDirectory()) {

						long last_modified = file.lastModified();

						if (now - last_modified > 10 * 24 * 60 * 60 * 1000L) {

							file.delete();
						}
					}
				}
			}

		} catch (Exception e) {
		}
	}

	public static boolean
	isStartupComplete()
	{
		return( startup_complete );
	}
	
	public static synchronized void
	flushPendingLogs()
	{
		for ( AEDiagnosticsLogger logger: loggers.values()){
			
			logger.writePending();
		}
		
		enable_pending_writes = false;
	}
	
	public static synchronized AEDiagnosticsLogger
	getLogger(
		String		name )
	{
		AEDiagnosticsLogger	logger = loggers.get(name);

		if ( logger == null ){

			logger	= new AEDiagnosticsLogger( debug_dir, name, MAX_FILE_SIZE, !enable_pending_writes );

			loggers.put( name, logger );
		}

		return( logger );
	}

	public static void
	logWithStack(
		String	logger_name,
		String	str )
	{
		log( logger_name, str + ": " + Debug.getCompressedStackTrace());
	}
	
	public static void
	log(
		String	logger_name,
		String	str )
	{
		getLogger( logger_name ).log( str );
	}
	
	public static void
	markDirty()
	{
		try{

			//COConfigurationManager.setParameter( CONFIG_KEY, false );
		
			//COConfigurationManager.save();
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
		}
	}

	public static boolean
	isDirty()
	{
		return false;//return( !COConfigurationManager.getBooleanParameter( CONFIG_KEY ));
	}
	
	public static void
	markClean()
	{
		try{
			//COConfigurationManager.setParameter( CONFIG_KEY, true );
			
			//COConfigurationManager.save();
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
		}
	}
	
	private static final String[][]
	   	bad_dlls = { 
			{	"niphk", 			"y", },
	   		{	"nvappfilter", 		"y", },
	   		{	"netdog", 			"y", },
	   		{	"vlsp", 			"y", },
	   		{	"imon", 			"y", },
	   		{	"sarah", 			"y", },
	   		{	"MxAVLsp", 			"y", },
	   		{	"mclsp", 			"y", },
	   		{	"radhslib", 		"y", },
	   		{	"winsflt",			"y", },
	   		{	"nl_lsp",			"y", },
	   		{	"AxShlex",			"y", },
	   		{	"iFW_Xfilter",		"y", },
	   		{	"gapsp",			"y", },
	   		{	"WSOCKHK",			"n", },
	   		{	"InjHook12",		"n", },
	   		{	"FPServiceProvider","n", },
	   		{	"SBLSP.dll",		"y"  },
	   		{	"nvLsp.dll",		"y"	 },
	};

	public static void
	checkDumpsAndNatives()
	{/*
		try{
			PlatformManager	p_man = PlatformManagerFactory.getPlatformManager();
			
			if ( 	p_man.getPlatformType() == PlatformManager.PT_WINDOWS &&
					p_man.hasCapability( PlatformManagerCapabilities.TestNativeAvailability )){	

				for (int i=0;i<bad_dlls.length;i++){
					
					String	dll 	= bad_dlls[i][0];
					String	load	= bad_dlls[i][1];
					
					if ( load.equalsIgnoreCase( "n" )){
						
						continue;
					}
					
					if ( !COConfigurationManager.getBooleanParameter( "platform.win32.dll_found." + dll, false )){
								
						try{
							if ( p_man.testNativeAvailability( dll + ".dll" )){
								
								COConfigurationManager.setParameter( "platform.win32.dll_found." + dll, true );
	
								String	detail = MessageText.getString( "platform.win32.baddll." + dll );
								
								Logger.logTextResource(
										new LogAlert(
												LogAlert.REPEATABLE, 
												LogAlert.AT_WARNING,
												"platform.win32.baddll.info" ),	
										new String[]{ dll + ".dll", detail });
							}
				
						}catch( Throwable e ){
							
							Debug.printStackTrace(e);
						}
					}
				}
			}
			
			File app_dir = new File( SystemProperties.getApplicationPath());
			
			if ( app_dir.canRead()){
				
				File[]	files = app_dir.listFiles();
				
				File	most_recent_dump 	= null;
				long	most_recent_time	= 0;
				
				long	now = SystemTime.getCurrentTime();
				
				long	one_week_ago = now - 7*24*60*60*1000;
				
				for (int i=0;i<files.length;i++){
					
					File	f = files[i];
					
					String	name = f.getName();
					
					if ( name.startsWith( "hs_err_pid" )){
						
						long	last_mod = f.lastModified();
						
						if ( last_mod > most_recent_time && last_mod > one_week_ago){
							
							most_recent_dump 	= f;
							most_recent_time	= last_mod;
						}
					}
				}
				
				if ( most_recent_dump!= null ){
					
					long	last_done = 
						COConfigurationManager.getLongParameter( "diagnostics.dump.lasttime", 0 ); 
					
					if ( last_done < most_recent_time ){
						
						COConfigurationManager.setParameter( "diagnostics.dump.lasttime", most_recent_time );
						
						analyseDump( most_recent_dump );
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
		}*/
	}
	
	protected static void
	analyseDump(
		File	file )
	{
		System.out.println( "Analysing " + file );
		
		try{
			LineNumberReader lnr = new LineNumberReader( new FileReader( file ));
			
			try{
				boolean	float_excep	= false;
				
				String[]	bad_dlls_uc = new String[bad_dlls.length];
				
				for (int i=0;i<bad_dlls.length;i++){
					
					String	dll 	= bad_dlls[i][0];

					bad_dlls_uc[i] = (dll + ".dll" ).toUpperCase();
				}
				
				String	alcohol_dll = "AxShlex";
				
				List<String>	matches = new ArrayList<String>();
				
				while( true ){
					
					String	line = lnr.readLine();
					
					if ( line == null ){
						
						break;
					}
					
					line = line.toUpperCase();
					
					if (line.indexOf( "EXCEPTION_FLT") != -1 ){
						
						float_excep	= true;
						
					}else{
						
						for (int i=0;i<bad_dlls_uc.length;i++){
							
							String b_uc = bad_dlls_uc[i];
							
							if ( line.indexOf( b_uc ) != -1 ){
								
								String	dll = bad_dlls[i][0];
								
								if ( dll.equals( alcohol_dll )){
									
									if ( float_excep ){
										
										matches.add( dll );
									}
									
								}else{
									
									matches.add( dll );
								}
							}
						}
					}
				}
				
				for (int i=0;i<matches.size();i++){
					
					String	dll = matches.get(i);
					
					String	detail = MessageText.getString( "platform.win32.baddll." + dll );
					
					Logger.logTextResource(
							new LogAlert(
									LogAlert.REPEATABLE, 
									LogAlert.AT_WARNING,
									"platform.win32.baddll.info" ),	
							new String[]{ dll + ".dll", detail });
				}
			}finally{
				
				lnr.close();
			}
		}catch( Throwable e){
			
			Debug.printStackTrace( e );
		}
	}
	
	public static void
	addEvidenceGenerator(
		AEDiagnosticsEvidenceGenerator	gen )
	{
		synchronized( evidence_generators ){
			
			evidence_generators.add( gen );
		}
	}
	
	public static void
	removeEvidenceGenerator(
		AEDiagnosticsEvidenceGenerator	gen )
	{
		synchronized( evidence_generators ){
			
			evidence_generators.remove( gen );
		}
	}
	
	public static void
	generateEvidence(
		PrintWriter		_writer )
	{
		IndentWriter	writer = new IndentWriter( _writer );
		
		synchronized( evidence_generators ){

			for (int i=0;i<evidence_generators.size();i++){
				
				try{
					evidence_generators.get(i).generate( writer );
					
				}catch( Throwable e ){
					
					e.printStackTrace( _writer );
				}
			}
		}
		
		writer.println( "Memory" );
		
		try{
			writer.indent();
			
			Runtime rt = Runtime.getRuntime();
			
			writer.println( "max=" + rt.maxMemory() + ",total=" + rt.totalMemory() + ",free=" + rt.freeMemory());
			
		}finally{
			
			writer.exdent();
		}
	}
}
