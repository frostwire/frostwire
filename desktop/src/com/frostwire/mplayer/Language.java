package com.frostwire.mplayer;

import java.util.Locale;

public class Language {
	
	String id;
	String name;
	LanguageSource source;
	String sourceInfo;


	Locale language;
	
	public Language(LanguageSource source, String id) {
		this.source = source;
		this.id = id;
	}
	
	

	public void setName(String name) {
		this.name = name;
	}
	
	public void setLanguage(String isoCode) {
		language = ISO639.getLocaleFromISO639_2(isoCode);
	}
	
	public void setLanguage(Locale locale) {
		language = locale;
	}
	
	public Locale getLanguage() {
		return language;
	}
	
	public String getName() {
		return name;
	}
	
	public String getId() {
		return id;
	}
	
	public LanguageSource getSource() {
		return source;
	}
	
	public String getSourceInfo() {
		return sourceInfo;
	}



	public void setSourceInfo(String sourceInfo) {
		this.sourceInfo = sourceInfo;
	}
}
