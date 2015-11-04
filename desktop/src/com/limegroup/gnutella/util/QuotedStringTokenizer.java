package com.limegroup.gnutella.util;

import java.util.NoSuchElementException;

/**
 * Provides a string tokenizer that respects quotes. If a separator is followed
 * by a quote the next token will extend to the next quote even if there are
 * separators in between. Quote characters will not be returned.
 * 
 * Based on code from the XNap-Commons (http;//xnap-commons.sf.net)
 */
public class QuotedStringTokenizer {

	public static final char QUOTE_CHAR = '"';

	private final boolean returnSeparators;

	private final String text;

	private final int maxIndex;

	private String separators;

	private int index;

	/**
	 * Constructs a <code>QuotedStringTokenizer</code>.
	 * 
	 * @see java.util.StringTokenizer
	 */
	public QuotedStringTokenizer(String text, String separators,
			boolean returnSeparators) {
		this.text = text;
		this.maxIndex = text.length() - 1;
		this.separators = separators;
		this.returnSeparators = returnSeparators;
	}

	/**
	 * Constructs a <code>QuotedStringTokenizer</code>.
	 * 
	 * @see java.util.StringTokenizer
	 */
	public QuotedStringTokenizer(String text, String separators) {
		this(text, separators, false);
	}

	/**
	 * Constructs a <code>QuotedStringTokenizer</code>.
	 * 
	 * @see java.util.StringTokenizer
	 */
	public QuotedStringTokenizer(String text) {
		this(text, " ", false);
	}

	public int countTokens() {
		int count = 0;
		int i = index;
		Token token;
		while ((token = nextToken(i)) != null) {
			count++;
			i = token.nextIndex;
		}
		return count;
	}

	public boolean hasMoreTokens() {
		return nextToken(index) != null;
	}

	public String nextToken(String separators) {
		this.separators = separators;
		return nextToken();
	}

	public String nextToken() {
		Token token = nextToken(index);
		if (token != null) {
			index = token.nextIndex;
			return token.text;
		}
		throw new NoSuchElementException();
	}

	protected Token nextToken(int index) {
		if (index > maxIndex) {
			return null;
		}

		if (returnSeparators && separators.indexOf(text.charAt(index)) != -1) {
			return new Token(text.substring(index, index + 1), index + 1);
		}

		// lets get started
		int i = index;
		boolean inQuotes = false;
		StringBuilder token = new StringBuilder();

		// eat separators
		while (i <= maxIndex && separators.indexOf(text.charAt(i)) != -1) {
			i++;
		}

		if (i > maxIndex) {
			return null;
		}

		while (i <= maxIndex) {
			char c = text.charAt(i);
			if (separators.indexOf(c) != -1) {
				if (inQuotes) {
					token.append(c);
				} else {
					return new Token(token.toString(), i);
				}
			} else if (c == QUOTE_CHAR) {
				// TODO add support for escaping of quote characters
				inQuotes = !inQuotes;
			} else {
				token.append(c);
			}
			i++;
		}

		return new Token(token.toString(), i);
	}

	private class Token {

		Token(String text, int nextIndex) {
			this.text = text;
			this.nextIndex = nextIndex;
		}

		String text;

		int nextIndex;
	}

}
