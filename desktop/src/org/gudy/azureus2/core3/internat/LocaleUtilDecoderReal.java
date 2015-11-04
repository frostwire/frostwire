/*
 * Created on 21-Jun-2004
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

package org.gudy.azureus2.core3.internat;

/**
 * @author parg
 *
 */

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;

import org.gudy.azureus2.core3.util.*;


public class 
LocaleUtilDecoderReal 
	implements LocaleUtilDecoder
{
	protected CharsetDecoder	decoder;
	protected int				index;
	
	protected
	LocaleUtilDecoderReal(
		int				_index,
		CharsetDecoder	_decoder )
	{
		index		= _index;
		decoder		= _decoder;
	}
	
	public String
	getName()
	{
		return( decoder.charset().name());
	}

	public int
	getIndex()
	{
		return( index );
	}
	
	public String
	tryDecode(
		byte[]		array,
		boolean		lax )
	{
		try{
			ByteBuffer bb = ByteBuffer.wrap(array);
				
			CharBuffer cb = CharBuffer.allocate(array.length);
				
			CoderResult cr = decoder.decode(bb,cb, true);
			
			if ( !cr.isError() ){
							
				cb.flip();
				
				String	str = cb.toString();
				
					// lax means that as long as the conversion works we consider it usable
					// as opposed to strict which requires reverse-conversion equivalence
				
				if ( lax ){
										
					return( str );
				}
				
				byte[]	b2 = str.getBytes( getName() );
				
					// make sure the conversion is symetric (there are cases where it appears
					// to work but in fact converting back to bytes leads to a different
					// result
					
				/*
				for (int k=0;k<str.length();k++){
					System.out.print( Integer.toHexString(str.charAt(k)));
				}
				System.out.println("");
				*/
				
				if ( Arrays.equals( array, b2 )){
				
					return( str );
				}
			}
			
			return( null );
			
		}catch( Throwable e ){
		
				// Throwable here as we can get "classdefnotfound" + others if the decoder
				// isn't available
			
			return( null );
		}
	}
	
	public String
	decodeString(
		byte[]		bytes )
		
		throws UnsupportedEncodingException
	{
		if ( bytes == null ){
			
			return( null );
		}
		
		try{
			ByteBuffer bb = ByteBuffer.wrap(bytes);
      		
			CharBuffer cb = CharBuffer.allocate(bytes.length);
	      		
			CoderResult cr = decoder.decode(bb,cb, true);
				
			if ( !cr.isError() ){
								
				cb.flip();
					
				String	str = cb.toString();
					
				byte[]	b2 = str.getBytes(decoder.charset().name());
					
					// make sure the conversion is symetric (there are cases where it appears
					// to work but in fact converting back to bytes leads to a different
					// result
						
				/*
				for (int k=0;k<str.length();k++){
					System.out.print( Integer.toHexString(str.charAt(k)));
				}
				System.out.println("");
				*/
					
				if ( Arrays.equals( bytes, b2 )){
					
					return( str );
				}
			}
		}catch( Throwable e ){
			
			// Throwable here as we can get "classdefnotfound" + others if the decoder
			// isn't available
			
			// ignore
		}
		
		try{
		
				// no joy, default
		
			return( new String( bytes, Constants.DEFAULT_ENCODING ));
			
		}catch( UnsupportedEncodingException e ){
			
			Debug.printStackTrace( e );
			
			return( new String( bytes ));
		}
	}
}
