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

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author parg
 *
 */
public class 
AEDiagnosticsLogger 
{
	private static final int	MAX_PENDING = 8*1024;
	
	private String 			name;
	private int				max_size;
	private File			debug_dir;
	private boolean			force;
	
	private boolean			first_file				= true;
	private boolean			first_write			 	= true;
	private PrintWriter		current_writer;
	
	private LinkedList<StringBuilder>	pending;
	private int							pending_size;
	private boolean						direct_writes;
	
	private final boolean		close_pws = false;
	
	private static final String	start_date;
	private static final long	timezone_offset;
	
	static{
		long		now = System.currentTimeMillis();
		
		start_date = new SimpleDateFormat().format( new Date(now));
		
		timezone_offset = TimeZone.getDefault().getOffset(now);
	}
	
	AEDiagnosticsLogger(
			File _debug_dir,
			String _name,
			int _max_size,
			boolean _direct_writes)
	{
		debug_dir		= _debug_dir;
		name			= _name;
		max_size		= _max_size;
		direct_writes	= _direct_writes;
		
		try{
			File	f1 = getLogFile();
			
			first_file = false;
			
			File	f2 = getLogFile();
			
			first_file = true;

				// if we were writing to the second file, carry on from there
			
			if ( f1.exists() && f2.exists()){
	
				if ( f1.lastModified() < f2.lastModified()){
					
					first_file = false;
				}
			}
		}catch( Throwable ignore ){
			
		}
	}
	
	public void
	setForced(
		boolean		_force )
	{
		force = _force;
	}

	private String
	getName()
	{
		return( name );
	}

	private void
	log(
			Throwable e)
	{
		try{
			ByteArrayOutputStream	baos = new ByteArrayOutputStream();
			
			PrintWriter	pw = new PrintWriter( new OutputStreamWriter( baos ));
			
			e.printStackTrace( pw );
			
			pw.close();
			
			log( baos.toString());
			
		}catch( Throwable ignore ){
			
		}
	}

	public void
	logAndOut(
		String		str,
		boolean		stderr )
	{
		if ( stderr ){
			
			System.err.println( str );

			// Logger dumps the stderr, but if it's not setup, do it outselves
			if (Logger.getOldStdErr() == null) {
				log( str );
			}

		}else{
			
			System.out.println( str );
			log( str );
		}
		
	}
	
	public void
	logAndOut(
		Throwable 	e )
	{
		e.printStackTrace();
		
		log( e );
	}
	
	/*
	public static String
	getTimestamp()
	{
		Calendar now = GregorianCalendar.getInstance();

		String timeStamp = "[" + format(now.get(Calendar.DAY_OF_MONTH))+format(now.get(Calendar.MONTH)+1) + " " + 
						format(now.get(Calendar.HOUR_OF_DAY))+ ":" + format(now.get(Calendar.MINUTE)) + ":" + format(now.get(Calendar.SECOND)) + "] ";        
		
		return( timeStamp );
	}
	*/
	
	private static String
	getTimestamp()
	{
		long time = SystemTime.getCurrentTime();
			
		time += timezone_offset;		// we'll live with this changing...
			
		time /= 1000;
			
	    int secs = (int)time % 60;
	    int mins = (int)(time / 60) % 60;
	    int hours = (int)(time /3600) % 24;
	
	    char[]	chars = new char[11];
	    
	    chars[0] = '[';
	    format( hours, chars, 1 );
	    chars[3] = ':';
	    format( mins, chars, 4 );
	    chars[6] = ':';
	    format( secs, chars, 7 );
	    chars[9] = ']';
	    chars[10] = ' ';
	    
		return( new String( chars ));
	}
	
	private static void
	format(
		int		num,
		char[]	chars,
		int		pos )
	{
		if ( num < 10 ){
			chars[pos] = '0';
			chars[pos+1] =(char)( '0' + num );
		}else{
			chars[pos] 		= (char)('0' + (num/10));
			chars[pos+1]	= (char)('0' + (num%10));
		}
	}
	
	public void
	log(
		String	_str )
	{
		if ( !AEDiagnostics.loggers_enabled ){
			
			if ( !force ){
			
				return;
			}
		}
		
		StringBuilder str = new StringBuilder( _str.length() + 20 );
		
		final String timeStamp;

		boolean timestamp_enable = true;
		if (timestamp_enable){
			
			timeStamp = getTimestamp();
			
		}else{
			
			timeStamp = null;
		}
		
		synchronized( this ){

			if ( first_write ){
				
				first_write = false;

				str.append( "\r\n[" );
				str.append( start_date );
				str.append( "] Log File Opened for " );
				str.append(  Constants.APP_NAME );
				str.append( " " );
				str.append(  Constants.AZUREUS_VERSION );
				str.append( "\r\n" );
			}
			
			if ( timeStamp != null ){
				
				str.append( timeStamp );
			}
			
			str.append( _str );
		
			if ( !direct_writes ){
				
				if ( pending == null ){
					
					pending = new LinkedList<StringBuilder>();
				}
				
				pending.add( str );
				
				pending_size += str.length();
				
				if ( pending_size > MAX_PENDING ){
					
					writePending();
				}
				
				return;
			}
				
			write( str );
		}
	}
	
	private void
	write(
		StringBuilder		str )
	{
		try{	
			File	log_file	= getLogFile();

			if ( log_file.length() >= max_size ){
				
				if ( current_writer != null ){
					
					current_writer.close();
					
					current_writer = null;
				}
				
				first_file = !first_file;
				
				log_file	= getLogFile();
			
					// If the file doesn't exist, this will just return false.
				
				log_file.delete();
			}
				
			if ( current_writer == null ){
			
				current_writer = new PrintWriter(new FileWriter( log_file, true ));
			}
			
			current_writer.println( str );
			
			current_writer.flush();
			
		}catch( Throwable e ){
			
		}finally{
			
			if ( current_writer != null && close_pws ){
									
				current_writer.close();
				
				current_writer = null;
			}
		}
	}
	
	private void
	writePending()
	{
		synchronized( this ){
			
			if ( pending == null ){
				
				return;
			}
			
			// System.out.println( getName() + ": flushing " + pending_size );
						
			try{	
				File	log_file	= getLogFile();

				if ( log_file.length() >= max_size ){
					
					if ( current_writer != null ){
						
						current_writer.close();
						
						current_writer = null;
					}
					
					first_file = !first_file;
					
					log_file	= getLogFile();
				
						// If the file doesn't exist, this will just return false.
					
					log_file.delete();
				}
					
				if ( current_writer == null ){
					
					current_writer = new PrintWriter(new FileWriter( log_file, true ));
				}
				
				for ( StringBuilder str: pending ){
				
					current_writer.println( str );
				}
				
				current_writer.flush();
				
			}catch( Throwable e ){
				
			}finally{
				
				direct_writes 	= true;
				pending			= null;
				
				if ( current_writer != null && close_pws ){
					
					current_writer.close();
					
					current_writer = null;
				}
			}
		}
	}
	
	private File
	getLogFile()
	{
		return( new File( debug_dir, getName() + "_" + (first_file?"1":"2") + ".log" ));
	}

/*
	private static String 
	format(
		int 	n ) 
	{
		if (n < 10){
	   	
			return( "0" + n );
	   }
		
	   return( String.valueOf(n));
	}
*/

}
