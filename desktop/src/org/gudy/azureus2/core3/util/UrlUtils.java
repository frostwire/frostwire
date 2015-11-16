/*
 * Created on Mar 21, 2006 3:09:00 PM
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
package org.gudy.azureus2.core3.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author TuxPaper
 * @created Mar 21, 2006
 *
 */
public class UrlUtils
{
	private static final ThreadPool	connect_pool = new ThreadPool( "URLConnectWithTimeout", 8, true );
	
	static{
		connect_pool.setWarnWhenFull();
	}
	
	private static final String[] prefixes = new String[] {
			"http://",
			"https://",
			"ftp://",
			"dht://",
			"magnet:?",
			"magnet://?",
			"maggot://" };

	private static int MAGNETURL_STARTS_AT = 3;	// dht:// is a form of magnet URL
	
	private static final Object[] XMLescapes = new Object[] {
		new String[] { "&", "&amp;" },
		new String[] { ">", "&gt;" },
		new String[] { "<", "&lt;" },
		new String[] { "\"", "&quot;" },
		new String[] { "'", "&apos;" },
	};

	public static String getMagnetURI( byte[] hash ) {
		return( "magnet:?xt=urn:btih:" + Base32.encode( hash ));
	}

	/*public static String
	getMagnetURI(
		byte[]		hash,
		String		name,
		String[]	networks )
	{
		String magnet_uri = getMagnetURI( hash );
					
		magnet_uri += encodeName( name );
					
		magnet_uri += encodeNetworks( networks );
		
		return( magnet_uri );
	}*/

	private static String
	encodeName(
		String	name )
	{
		if ( name == null ){
			
			return( "" );
			
		}else{
			
			return( "&dn=" + UrlUtils.encode(name));
		}
	}

    /*private static String
	encodeNetworks(
		String[]	networks )
	{
		String	net_str = "";

		if ( networks != null && networks.length > 0 ){
						
			for ( String net: networks ){
				
				if ( net == AENetworkClassifier.AT_PUBLIC && networks.length == 1 ){
						
					break;
				}
				
				net_str += "&net=" + net;
			}
		}
		
		return( net_str );
	}
	
	/*public static String
	getMagnetURI(
		Download		download )
	{
		return( getMagnetURI( PluginCoreUtils.unwrap(download)));
	}*/
	
	/*public static String
	getMagnetURI(
		DownloadManager		dm )
	{
		if ( dm == null ){
			
			return( null );
		}
		
		TOTorrent to_torrent = dm.getTorrent();
		
		if ( to_torrent == null ){
		
			return( null );
		}
		
		String name = dm.getDisplayName();
		
		String magnet_uri = getMagnetURI( name, PluginCoreUtils.wrap( to_torrent ));
		
		String[]	networks = dm.getDownloadState().getNetworks();
		
		magnet_uri += encodeNetworks( networks );
		
		return( magnet_uri );
	}*/
	
	/*public static String
	getMagnetURI(
		String		name,
		Torrent		torrent )
	{
		String	magnet_str = getMagnetURI( torrent.getHash());
		
		magnet_str += encodeName( name);

		List<String>	tracker_urls = new ArrayList<String>();
		
		URL announce_url = torrent.getAnnounceURL();
		
		if ( announce_url != null ){
			
			if ( !TorrentUtils.isDecentralised( announce_url )){
				
				tracker_urls.add( announce_url.toExternalForm());
			}
		}
		
		TorrentAnnounceURLList list = torrent.getAnnounceURLList();
		
		TorrentAnnounceURLListSet[] sets = list.getSets();
		
		for ( TorrentAnnounceURLListSet set: sets ){
			
			URL[] set_urls = set.getURLs();
			
			if ( set_urls.length > 0 ){
				
				URL set_url = set_urls[0];
				
				if ( !TorrentUtils.isDecentralised( set_url )){
					
					String str = set_url.toExternalForm();
					
					if ( !tracker_urls.contains( str )){
					
						tracker_urls.add( str );
					}
				}
			}
		}
		
		for ( String str: tracker_urls ){
			
			magnet_str += "&tr=" + UrlUtils.encode( str );
		}
		
		List<String>	ws_urls = new ArrayList<String>();

		Object obj = torrent.getAdditionalProperty( "url-list" );
							
		if ( obj instanceof byte[] ){
            
			try{
				ws_urls.add( new URL( new String((byte[])obj, "UTF-8" )).toExternalForm());
				
			}catch( Throwable e ){							
			}
		}else if ( obj instanceof List ){
			
			for ( Object o: (List)obj ){
				
				try{
					if (o instanceof byte[]) {
						ws_urls.add( new URL( new String((byte[])o, "UTF-8" )).toExternalForm());
					} else if (o instanceof String) {
						ws_urls.add( new URL((String) o).toExternalForm());
					}
					
				}catch( Throwable e ){							
				}
			}
		} else if ( obj instanceof String ) {
			try{
				ws_urls.add(new URL((String) obj).toExternalForm());
			}catch( Throwable e ){							
			}
		}
		
		for ( String str: ws_urls ){
			
			magnet_str += "&ws=" + UrlUtils.encode( str );
		}	
		
		return( magnet_str );
	}*/
		/**
		 * returns magnet uri if input is base 32 or base 16 encoded sha1 hash, null otherwise
		 * @param base_hash
		 * @return
		 */
	
	public static String normaliseMagnetURI( String	base_hash ) {
		byte[]	hash = decodeSHA1Hash( base_hash );

		if ( hash != null ){
			return getMagnetURI( hash );
		}

		return null;
	}

	public static byte[] decodeSHA1Hash( String	str ) {
		if ( str == null ) {
			return null;
		}

		str = str.trim();

		byte[] hash = null;

		try{
			if ( str.length() == 40 ){
				hash = ByteFormatter.decodeString( str );

			}else if ( str.length() == 32 ){
				hash = Base32.decode( str );
			}
		}catch( Throwable e ){
		}

		if ( hash != null && hash.length != 20 ){
				hash = null;
		}

		return hash;
	}

	private static String parseHTMLforURL(String text) {
		if (text == null) {
			return null;
		}

		// examples:
		// <A HREF=http://abc.om/moo>test</a>
		// <A style=cow HREF="http://abc.om/moo">test</a>
		// <a href="http://www.gnu.org/licenses/fdl.html" target="_top">moo</a>

		Pattern pat = Pattern.compile("<.*a\\s++.*href=\"?([^\\'\"\\s>]++).*",
				Pattern.CASE_INSENSITIVE);
		Matcher m = pat.matcher(text);
		if (m.find()) {
			String sURL = m.group(1);
			try {
				sURL = URLDecoder.decode(sURL);
			} catch (Exception e) {
				// sometimes fires a IllegalArgumentException
				// catch everything and ignore.
			}
			return sURL;
		}

		return null;
	}

	/*public static void main(String[] args) {
		
		MagnetURIHandler.getSingleton();
		byte[] infohash = ByteFormatter.decodeString("1234567890123456789012345678901234567890");
		String[] test = {
				"http://moo.com",
				"http%3A%2F/moo%2Ecom",
				"magnet:?moo",
				"magnet%3A%3Fxt=urn:btih:26",
				"magnet%3A//%3Fmooo",
				"magnet:?xt=urn:btih:" + Base32.encode(infohash),
				"aaaaaaaaaabbbbbbbbbbccccccccccdddddddddd",
				"magnet:?dn=OpenOffice.org_2.0.3_Win32Intel_install.exe&xt=urn:sha1:PEMIGLKMNFI4HZ4CCHZNPKZJNMAAORKN&xt=urn:tree:tiger:JMIJVWHCQUX47YYH7O4XIBCORNU2KYKHBBC6DHA&xt=urn:ed2k:1c0804541f34b6583a383bb8f2cec682&xl=96793015&xs=http://mirror.switch.ch/ftp/mirror/OpenOffice/stable/2.0.3/OOo_2.0.3_Win32Intel_install.exe",
				};
		for (int i = 0; i < test.length; i++) {
			System.out.println("URLDecoder.decode: " + test[i] + " -> " + URLDecoder.decode(test[i]));
			System.out.println("decode: " + test[i] + " -> " + decode(test[i]));
			System.out.println("isURL: " + test[i] + " -> " + isURL(test[i]));
			System.out.println("parse: " + test[i] + " -> " + parseTextForURL(test[i], true));
		}

		String[] testEncode = {
			"a b"
		};
		for (int i = 0; i < testEncode.length; i++) {
			String txt = testEncode[i];
			try {
				System.out.println("URLEncoder.encode: " + txt + " -> "
						+ URLEncoder.encode(txt, "UTF8"));
			} catch (UnsupportedEncodingException e) {
			}
			System.out.println("URLEncoder.encode: " + txt + " -> "
					+ URLEncoder.encode(txt));
			System.out.println("encode: " + txt + " -> " + encode(txt));
		}

	}*/

	/**
	 * Like URLEncoder.encode, except translates spaces into %20 instead of +
	 * @param s
	 * @return
	 */
	public static String encode(String s) {
		if (s == null) {
			return "";
		}
		try {
			return URLEncoder.encode(s, "UTF-8").replaceAll("\\+", "%20");
		} catch (UnsupportedEncodingException e) {
			return URLEncoder.encode(s).replaceAll("\\+", "%20");
		}
	}
	
	public static String decode(String s) {
		if (s == null) {
			return "";
		}
		try {
			return( URLDecoder.decode(s, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			return( URLDecoder.decode(s));
		}
	}
	
	public static String escapeXML(String s) {
		if (s == null) {
			return "";
		}
		String ret = s;
		for (int i = 0; i < XMLescapes.length; i++) {
			String[] escapeEntry = (String[])XMLescapes[i];
			ret = ret.replaceAll(escapeEntry[0], escapeEntry[1]);
		}
		return ret;
	}

	public static String unescapeXML(String s) {
		if (s == null) {
			return "";
		}
		String ret = s;
		for (int i = 0; i < XMLescapes.length; i++) {
			String[] escapeEntry = (String[])XMLescapes[i];
			ret = ret.replaceAll(escapeEntry[1], escapeEntry[0]);
		}
		return ret;
	}
	
	public static String
	convertIPV6Host(
		String	host )
	{
		if ( host.indexOf(':') != -1 ){
			
			return( "[" + host + "]" );
		}
		
		return( host );
	}
	
	public static String
	expandIPV6Host(
		String	host )
	{
		if ( host.indexOf(':') != -1 ){
			
			try{
				return( InetAddress.getByAddress(InetAddress.getByName( host ).getAddress()).getHostAddress());
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
		
		return( host );
	}
	
	public static void
	connectWithTimeout(
		final URLConnection		connection,
		long					connect_timeout )
	
		throws IOException
	{
		connectWithTimeouts( connection, connect_timeout, -1 );
	}
	
	public static void
	connectWithTimeouts(
		final URLConnection		connection,
		long					connect_timeout,
		long					read_timeout )
	
		throws IOException
	{
		if ( connect_timeout != -1 ){
				
			connection.setConnectTimeout( (int)connect_timeout );	
		}
			
		if ( read_timeout != -1 ){
				
			connection.setReadTimeout( (int)read_timeout );	
		}
			
		connection.connect();
	}
	
	//private static String	last_headers = COConfigurationManager.getStringParameter( "metasearch.web.last.headers", null );
	
	// private static final String default_headers = "SG9zdDogbG9jYWxob3N0OjQ1MTAwClVzZXItQWdlbnQ6IE1vemlsbGEvNS4wIChXaW5kb3dzOyBVOyBXaW5kb3dzIE5UIDUuMTsgZW4tVVM7IHJ2OjEuOC4xLjE0KSBHZWNrby8yMDA4MDQwNCBGaXJlZm94LzIuMC4wLjE0CkFjY2VwdDogdGV4dC94bWwsYXBwbGljYXRpb24veG1sLGFwcGxpY2F0aW9uL3hodG1sK3htbCx0ZXh0L2h0bWw7cT0wLjksdGV4dC9wbGFpbjtxPTAuOCxpbWFnZS9wbmcsKi8qO3E9MC41CkFjY2VwdC1MYW5ndWFnZTogZW4tdXMsZW47cT0wLjUKQWNjZXB0LUVuY29kaW5nOiBnemlwLGRlZmxhdGUKQWNjZXB0LUNoYXJzZXQ6IElTTy04ODU5LTEsdXRmLTg7cT0wLjcsKjtxPTAuNwpLZWVwLUFsaXZlOiAzMDAKQ29ubmVjdGlvbjoga2VlcC1hbGl2ZQ==";
	private static final String default_headers = "QWNjZXB0OiB0ZXh0L2h0bWwsYXBwbGljYXRpb24veGh0bWwreG1sLGFwcGxpY2F0aW9uL3htbDtxPTAuOSwqLyo7cT0wLjgKQWNjZXB0LUNoYXJzZXQ6IElTTy04ODU5LTEsdXRmLTg7cT0wLjcsKjtxPTAuMwpBY2NlcHQtRW5jb2Rpbmc6IGd6aXAsZGVmbGF0ZQpBY2NlcHQtTGFuZ3VhZ2U6IGVuLVVTLGVuO3E9MC44CkNhY2hlLUNvbnRyb2w6IG1heC1hZ2U9MApDb25uZWN0aW9uOiBrZWVwLWFsaXZlClVzZXItQWdlbnQ6IE1vemlsbGEvNS4wIChXaW5kb3dzIE5UIDYuMTsgV09XNjQpIEFwcGxlV2ViS2l0LzUzNi4xMSAoS0hUTUwsIGxpa2UgR2Vja28pIENocm9tZS8yMC4wLjExMzIuNDcgU2FmYXJpLzUzNi4xMQ==";

	/*public static void
	setBrowserHeaders(
		ResourceDownloader		rd,
		String					referer )
	{
		setBrowserHeaders( rd, null, referer );
	}
	
	public static void
	setBrowserHeaders(
		ResourceDownloader		rd,
		String					encoded_headers,
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( encoded_headers );
		
		try{
			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );
		
			String[]	headers = header_string.split( "\n" );
			
			for (int i=0;i<headers.length;i++ ){
			
				String	header = headers[i];
				
				int	pos = header.indexOf( ':' );
				
				if ( pos != -1 ){
					
					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();
					
					if ( !( lhs.equalsIgnoreCase( "Host") || 
							lhs.equalsIgnoreCase( "Referer" ))){
						
						rd.setProperty( "URL_" + lhs, rhs );
					}
				}
			}
			
			if ( referer != null && referer.length() > 0 ){
				
				rd.setProperty( "URL_Referer", referer );
			}
		}catch( Throwable e ){	
		}
	}
	
	public static void
	setBrowserHeaders(
		ResourceUploader		ru,
		String					encoded_headers,
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( encoded_headers );
		
		try{
			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );
		
			String[]	headers = header_string.split( "\n" );
			
			for (int i=0;i<headers.length;i++ ){
			
				String	header = headers[i];
				
				int	pos = header.indexOf( ':' );
				
				if ( pos != -1 ){
					
					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();
					
					if ( !( lhs.equalsIgnoreCase( "Host") || 
							lhs.equalsIgnoreCase( "Referer" ))){
						
						ru.setProperty( "URL_" + lhs, rhs );
					}
				}
			}
			
			if ( referer != null && referer.length() > 0 ){
				
				ru.setProperty( "URL_Referer", referer );
			}
		}catch( Throwable e ){	
		}
	}
	
	public static void
	setBrowserHeaders(
		URLConnection			connection,
		String					referer )
	{
		setBrowserHeaders( connection, null, referer );
	}
	
	public static void
	setBrowserHeaders(
		URLConnection			connection,
		String					encoded_headers,
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( encoded_headers );
		
		try{
		
			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );
		
			String[]	headers = header_string.split( "\n" );
			
			for (int i=0;i<headers.length;i++ ){
			
				String	header = headers[i];
				
				int	pos = header.indexOf( ':' );
				
				if ( pos != -1 ){
					
					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();
					
					if ( !( lhs.equalsIgnoreCase( "Host") || 
							lhs.equalsIgnoreCase( "Referer" ))){
						
						connection.setRequestProperty( lhs, rhs );
					}
				}
			}
			
			if ( referer != null && referer.length() > 0 ){
				
				connection.setRequestProperty( "Referer", referer );
			}
		}catch( Throwable e ){		
		}
	}
	
	public static Map
	getBrowserHeaders(
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( null );
		
		Map	result = new HashMap();
		
		try{
		
			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );
		
			String[]	headers = header_string.split( "\n" );
			
			for (int i=0;i<headers.length;i++ ){
			
				String	header = headers[i];
				
				int	pos = header.indexOf( ':' );
				
				if ( pos != -1 ){
					
					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();
					
					if ( !( lhs.equalsIgnoreCase( "Host") || 
							lhs.equalsIgnoreCase( "Referer" ))){
						
						result.put( lhs, rhs );
					}
				}
			}
			
			if ( referer != null && referer.length() > 0){
				
				result.put( "Referer", referer );
			}
		}catch( Throwable e ){		
		}
		
		return( result );
	}
	
	private static String
	getBrowserHeadersToUse(
		String		encoded_headers )
	{
		String	headers_to_use = encoded_headers;
		
		synchronized( UrlUtils.class ){
			
			if ( headers_to_use == null ){
				
				if ( last_headers != null ){
					
					headers_to_use = last_headers;
					
				}else{
					
					headers_to_use = default_headers;
				}
			}else{
			
				if ( last_headers == null || !headers_to_use.equals( last_headers )){
					
					COConfigurationManager.setParameter( "metasearch.web.last.headers", headers_to_use );
				}
				
				last_headers = headers_to_use;
			}
		}
		
		return( headers_to_use );
	}*/
	
	public static boolean queryHasParameter(String query_string, String param_name, boolean case_sensitive) {
		if (!case_sensitive) {
			query_string = query_string.toLowerCase();
			param_name = param_name.toLowerCase();
		}
		if (query_string.charAt(0) == '?') {
			query_string = '&' + query_string.substring(1);
		}
		else if (query_string.charAt(0) != '&') {
			query_string = '&' + query_string;
		}
		
		return query_string.indexOf("&" + param_name + "=") != -1;
	}
	
	public static boolean 
	containsPasskey(
		URL		url )
	{
		String url_str = url.toExternalForm();
		
		return( url_str.matches(".*[0-9a-z]{20,40}.*"));
	}
	
	public static URL
	setPort(
		URL		u,
		int		port )
	{
		if ( port == -1 ){
			port = u.getDefaultPort();
		}
		StringBuffer result = new StringBuffer();
		result.append(u.getProtocol());
		result.append(":");
		String authority=u.getAuthority();
		if (authority != null && authority.length() > 0) {
			result.append("//");
			int pos = authority.indexOf( '@' );
			if ( pos != -1 ){
				result.append(authority.substring(0,pos+1));
				authority = authority.substring(pos+1);
			}
			pos = authority.lastIndexOf(':');
			if ( pos == -1 ){
				if ( port > 0 ){
					result.append(authority + ":" + port );
				}else{
					result.append(authority);
				}
			}else{
				if ( port > 0 ){
					result.append(authority.substring(0,pos+1) + port );
				}else{
					result.append(authority.substring(0,pos));
				}
			}
		}
		if (u.getPath() != null) {
			result.append(u.getPath());
		}
		if (u.getQuery() != null) {
			result.append('?');
			result.append(u.getQuery());
		}
		if (u.getRef() != null) {
			result.append("#");
			result.append(u.getRef());
		}
		try{
			return( new URL( result.toString()));
		}catch( Throwable e ){
			Debug.out(e);
			return(u);
		}
	}
	
	public static URL
	setHost(
		URL			u,
		String		host )
	{
		StringBuffer result = new StringBuffer();
		result.append(u.getProtocol());
		result.append(":");
		String authority=u.getAuthority();
		if (authority != null && authority.length() > 0) {
			result.append("//");
			int pos = authority.indexOf( '@' );
			if ( pos != -1 ){
				result.append(authority.substring(0,pos+1));
				authority = authority.substring(pos+1);
			}
			pos = authority.lastIndexOf(':');
			if ( pos == -1 ){
				result.append(host );
			}else{
				result.append(host + authority.substring(pos));				
			}
		}
		if (u.getPath() != null) {
			result.append(u.getPath());
		}
		if (u.getQuery() != null) {
			result.append('?');
			result.append(u.getQuery());
		}
		if (u.getRef() != null) {
			result.append("#");
			result.append(u.getRef());
		}
		try{
			return( new URL( result.toString()));
		}catch( Throwable e ){
			Debug.out(e);
			return(u);
		}
	}
	
	public static URL
	setProtocol(
		URL			u,
		String		protocol )
	{
		String str = u.toExternalForm();
		
		int pos = str.indexOf( ":" );
		
		try{
			return( new URL( protocol + str.substring( pos )));
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( u );
		}
	}
	
	public static URL
	getBaseURL(
		URL		u )
	{
		StringBuffer result = new StringBuffer();
		result.append(u.getProtocol());
		result.append(":");
		String authority=u.getAuthority();
		if (authority != null && authority.length() > 0) {
			result.append("//");
			int pos = authority.indexOf( '@' );
			if ( pos != -1 ){
				result.append(authority.substring(0,pos+1));
				authority = authority.substring(pos+1);
			}
			pos = authority.lastIndexOf(':');
			int	port = u.getPort();
			if ( port == -1 ){
				port = u.getDefaultPort();
			}
			if ( pos == -1 ){
				result.append(authority + ":" + port );
			}else{
				result.append(authority.substring(0,pos+1) + port );				
			}
		}

		try{
			return( new URL( result.toString()));
		}catch( Throwable e ){
			Debug.out(e);
			return(u);
		}
	}
	
	public static String
	getCanonicalString(
		URL		url )
	{
		String protocol = url.getProtocol();
		
		if ( !protocol.equals( protocol.toLowerCase( Locale.US ))){
			
			protocol = protocol.toLowerCase( Locale.US );
			
			url = UrlUtils.setProtocol( url, protocol ); 
		}
		
		int	port = url.getPort();
		
		if ( protocol.equals( "http" ) || protocol.equals( "https" )){
		
			if ( port == url.getDefaultPort()){
				
				url = UrlUtils.setPort( url, 0 );
			}
		}else{
			
			if ( port == -1 ){
				
				url = UrlUtils.setPort( url, url.getDefaultPort());
			}
		}
		
		return( url.toString());
	}
	
	public static long
	getContentLength(
		URLConnection	con )
	{
		long res = con.getContentLength();
		
		if ( res == -1 ){
			
			try{
				String	str = con.getHeaderField( "content-length" );
				
				if ( str != null ){
					
					res = Long.parseLong( str );
				}
			}catch( Throwable e ){
				
			}
		}
		
		return( res );
	}
}
