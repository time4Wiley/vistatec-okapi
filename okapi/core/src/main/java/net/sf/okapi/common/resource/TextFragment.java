/*===========================================================================
  Copyright (C) 2008-2013 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

package net.sf.okapi.common.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.okapi.common.StringUtil;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiIllegalFilterOperationException;

/**
 * Implements the methods for creating and manipulating a pre-parsed
 * flat representation of a content with in-line codes.
 * 
 * <p>The model uses two objects to store the data:
 * <ul><li>a coded text string
 * <li>a list of {@link Code} object.</ul>
 * 
 * <p>The coded text string is composed of normal characters and <b>markers</b>.
 * 
 * <p>A marker is a sequence of two special characters (in the Unicode PUA)
 * that indicate the type of underlying code (opening, closing, isolated), and an index
 * pointing to its corresponding Code object where more information can be found.
 * The value of the index is encoded as a Unicode PUA character. You can use the
 * {@link #toChar(int)} and {@link #toIndex(char)} methods to encoded and decode
 * the index value.
 * 
 * <p>To get the coded text of a TextFragment object use {@link #getCodedText()}, and
 * to get its list of codes use {@link #getCodes()}.
 * 
 * <p>You can modify directly the coded text or the codes and re-apply them to the
 * TextFragment object using {@link #setCodedText(String)} and
 * {@link #setCodedText(String, List)}.
 *
 * <p>Adding a code to the coded text can be done by:
 * <ul><li>appending the code with {@link #append(TagType, String, String)}
 * <li>changing a section of existing text to code with
 * {@link #changeToCode(int, int, TagType, String)}</ul>
 */
public class TextFragment implements Appendable, CharSequence, Comparable<TextFragment> {
	
	/**
	 * Special character marker for a opening inline code.
	 */
	public static final int MARKER_OPENING  = 0xE101;
	
	/**
	 * Special character marker for a closing inline code.
	 */
	public static final int MARKER_CLOSING  = 0xE102;
	
	/**
	 * Special character marker for an isolated inline code.
	 */
	public static final int MARKER_ISOLATED = 0xE103;
	
	/**
	 * Special value used as the base of inline code indices. 
	 */
	public static final int CHARBASE        = 0xE110;

	/**
	 * Marker for start of reference. 
	 */
	public static final String REFMARKER_START = "[#$";
	
	/**
	 * Marker for end of reference.
	 */
	public static final String REFMARKER_END   = "]";
	
	/**
	 * Marker for reference separator.
	 */
	public static final String REFMARKER_SEP   = "@%";

	/*
	 * Compiled regex for all TextFragment markers
	 */
	public static final Pattern MARKERS_REGEX = Pattern.compile("[\uE101\uE102\uE103\uE104].");

	/*
	 * Typical whitespace space (U+0020) tab (U+0009) form feed (U+000C) line feed
	 * (U+000A) carriage return (U+000D) zero-width space (U+200B) (IE6 does not
	 * recognize these, they are treated as unprintable characters)
	 */
	private static final String WHITESPACE_REGEX = "[ \t\r\n\f\u200B]+";
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile(WHITESPACE_REGEX);

	/**
	 * List of the types of tag usable for in-line codes.
	 */
	public static enum TagType {
		OPENING,
		CLOSING,
		PLACEHOLDER
	};
	
	/**
	 * List of the marker types as an {@link Enum}. Makes it easy to manipulate
	 * Markers as an {@link EnumSet}
	 * FIXME: should convert all integer contants to enums at some point
	 */
	public static enum Marker {
		OPENING(TextFragment.MARKER_OPENING),
		CLOSING(TextFragment.MARKER_CLOSING),
		ISOLATED(TextFragment.MARKER_ISOLATED),
		UNKOWN(-1);
		
		private int markerType;

		private Marker(int markerType)
		{
			this.markerType = markerType;
		}
		
		public int getMarkerType()
		{
			return this.markerType;
		}
		
		public static Marker asEnum(int markerType)
		{
			switch (markerType) {
			case TextFragment.MARKER_OPENING:
				return Marker.OPENING;
			case TextFragment.MARKER_CLOSING:
				return Marker.CLOSING;
			case TextFragment.MARKER_ISOLATED:
				return Marker.ISOLATED;
			default:
				return Marker.UNKOWN;
			}
		}		
	};
	
	/**
	 * Coded text buffer of this fragment.
	 */	
	// FIXME - why is this protected?
	protected StringBuilder text;
	
	/**
	 * List of the inline codes for this fragment.  
	 */
	// FIXME - why is this protected?
	protected List<Code> codes;
	
	/**
	 * Flag indicating if the opening/closing inline codes of this fragment
	 * have been balanced or not. 
	 */
	// FIXME - why is this protected?
	protected boolean isBalanced;
	
	/**
	 * Value of the last inline code ID in this fragment.
	 */
	// FIXME - why is this protected?
	protected int lastCodeID;

	/**
	 * Helper method to convert a marker index to its character value in the
	 * coded text string.
	 * @param index the index value to encode.
	 * @return the corresponding character value.
	 */
	public static char toChar (int index) {
		return (char)(index+CHARBASE);
	}

	/**
	 * Helper method to convert the index-coded-as-character part of a marker into 
	 * its index value.
	 * @param index the character to decode.
	 * @return the corresponding index value.
	 */
	public static int toIndex (char index) {
		return ((int)index)-CHARBASE;
	}
	
	/**
	 * Helper method to build a reference marker string from a given identifier.
	 * @param id the identifier to use.
	 * @return the reference marker constructed from the ID.
	 */
	public static String makeRefMarker (String id) {
		return REFMARKER_START+id+REFMARKER_END;
	}
	
	/**
	 * Helper method to build a reference marker string from a given identifier
	 * and a property name.
	 * @param id The identifier to use.
	 * @param propertyName the name of the property to use.
	 * @return the reference marker constructed from the identifier and the property name.
	 */
	public static String makeRefMarker (String id,
		String propertyName)
	{
		return REFMARKER_START+id+REFMARKER_SEP+propertyName+REFMARKER_END;
	}

	/**
	 * Helper method to retrieve a reference marker from a string.  
	 * @param text the text to search for a reference marker. 
	 * @return null if no reference marker has been found. An array of four 
	 * objects if a reference marker has been found:
	 * <ul><li>Object 0: The identifier of the reference.
	 * <li>Object 1: The start position of the reference marker in the string.
	 * <li>Object 2: The end position of the reference marker in the string.
	 * <li>Object 3: The name of the property if there is one, null otherwise. 
         * </ul>
	 */
	public static Object[] getRefMarker (StringBuilder text) {
		int start = text.indexOf(REFMARKER_START);
		if ( start == -1 ) return null; // No marker
		int end = text.indexOf(REFMARKER_END, start);
		if ( end == -1 ) return null; // No ending found, we assume it's not a marker
		String id = text.substring(start+REFMARKER_START.length(), end);
		Object[] result = new Object[4];
		result[1] = start;
		result[2] = end+REFMARKER_END.length();
		// Check for property name
		int sep = id.indexOf(REFMARKER_SEP);
		if ( sep > -1 ) {
			String propName = id.substring(sep+REFMARKER_SEP.length());
			id = id.substring(0, sep);
			result[3] = propName;
		}
		// Else: result[3] is null: it's not a property marker
		result[0] = id;
		return result;
	}

	/**
	 * Gets the position in the string representation of a fragment of a given
	 * position in that fragment.
	 * <p>For example if you find a match in a coded text string, use this method to
	 * convert the boundaries of the match into character position in the string 
	 * representing the fragment (4 in "xxyyMATCHyyxx" -&gt; 6 in "{b}{i}MATCH{/i}{/b}")
	 * @param frag the fragment where the position is located.
	 * @param pos the position.
	 * @return the same position, but in the string representation of the fragment.
	 */
	public static int fromFragmentToString (TextFragment frag,
		int pos)
	{
		// No codes means no correction
		if ( !frag.hasCode() ) return pos;

		// Else: correct the position
		int len = 0;
		String text = frag.getCodedText();
		for ( int i=0; i<text.length(); i++ ) {
			if ( i >= pos ) {
				return len;
			}
			if ( TextFragment.isMarker(text.charAt(i)) ) {
				Code code = frag.getCode(text.charAt(++i));
				len += code.getData().length();
				continue;
			}
			else {
				len++;
			}
		}
		return len;
	}

	/**
	 * Helper method to find, from the back, the first non-whitespace character
	 * of a coded text, starting at a given position and no farther than another
	 * given position.
	 * @param codedText the coded text to process.
	 * @param fromIndex the first position to check (must be greater or equal to
	 * untilIndex). Use -1 to point to the last position of the text.
	 * @param untilIndex The last position to check (must be lesser or equal to
	 * fromIndex).
	 * @param openingMarkerIsWS indicates if opening markers count as whitespace.
	 * @param closingMarkerIsWS indicates if closing markers count as whitespace.
	 * @param isolatedMarkerIsWS indicates if isolated markers count as whitespace.
	 * @param whitespaceIsWS indicates if whitespace characters count as whitespace.
	 * @return the first non-whitespace character position from the back, given the 
	 * parameters, or -1 if the text in null, empty or if no non-whitespace has been
	 * found after the character at the position untilIndex has been checked.
	 * If the last non-whitespace found is a code, the position returned is the index
	 * of the second special character marker for that code.
	 */
	public static int indexOfLastNonWhitespace (String codedText,
		int fromIndex,
		int untilIndex,
		boolean openingMarkerIsWS,
		boolean closingMarkerIsWS,
		boolean isolatedMarkerIsWS,
		boolean whitespaceIsWS)
	{
		// Empty text
		if (( codedText == null ) || ( codedText.length() == 0 )) return -1;
		
		// Set variables
		if ( fromIndex == -1 ) fromIndex = codedText.length()-1;
		int textEnd = fromIndex;
		boolean done = false;

		while ( !done ) {
			switch ( codedText.charAt(textEnd) ) {
			case TextFragment.MARKER_OPENING:
				if ( !openingMarkerIsWS ) {
					textEnd++; // was += 2;
					done = true;
				}
				break;
			case TextFragment.MARKER_CLOSING:
				if ( !closingMarkerIsWS ) {
					textEnd++; // was += 2;
					done = true;
				}
				break;
			case TextFragment.MARKER_ISOLATED:
				if ( !isolatedMarkerIsWS ) {
					textEnd++; //was += 2;
					done = true;
				}
				break;
			default:
				if ( whitespaceIsWS && Character.isWhitespace(codedText.charAt(textEnd)) ) break;
				done = true; // Else: Probably done
				// But check if it's the index of a marker
				if ( textEnd > 1 ) {
					switch ( codedText.charAt(textEnd-1) ) {
					case TextFragment.MARKER_OPENING:
					case TextFragment.MARKER_CLOSING:
					case TextFragment.MARKER_ISOLATED:
						done = false; // Not done yet
						break;
					}
				}
				break;
			}
			if ( !done ) {
				if ( textEnd-1 < untilIndex ) {
					textEnd = -1;
					break;
				}
				textEnd--;
			}
		}
		return textEnd;
	}

	/**
	 * Helper method to find the first non-whitespace character
	 * of a coded text, starting at a given position and no farther than another
	 * given position.
	 * @param codedText the coded text to process.
	 * @param fromIndex the first position to check (must be lesser or equal to
	 * untilIndex).
	 * @param untilIndex the last position to check (must be greater or equal to
	 * fromIndex). Use -1 to point to the last position of the text.
	 * @param openingMarkerIsWS indicates if opening markers count as whitespace.
	 * @param closingMarkerIsWS indicates if closing markers count as whitespace.
	 * @param isolatedMarkerIsWS indicates if isolated markers count as whitespace.
	 * @param whitespaceIsWS indicates if whitespace characters count as whitespace.
	 * @return the first non-whitespace character position, given the parameters,
	 * or -1 if the text is null or empty, or no non-whitespace has been found
	 * after the character at the position untilIndex has been checked. 
	 */
	public static int indexOfFirstNonWhitespace (String codedText,
		int fromIndex,
		int untilIndex,
		boolean openingMarkerIsWS,
		boolean closingMarkerIsWS,
		boolean isolatedMarkerIsWS,
		boolean whitespaceIsWS)
	{
		// Empty text
		if (( codedText == null ) || ( codedText.length() == 0 )) return -1;
		
		// Set variables
		if ( untilIndex == -1 ) untilIndex = codedText.length()-1;
		int textStart = fromIndex;
		boolean done = false;

		while ( !done ) {
			switch ( codedText.charAt(textStart) ) {
			case TextFragment.MARKER_OPENING:
				if ( openingMarkerIsWS ) textStart++;
				else done = true;
				break;
			case TextFragment.MARKER_CLOSING:
				if ( closingMarkerIsWS ) textStart++;
				else done = true;
				break;
			case TextFragment.MARKER_ISOLATED:
				if ( isolatedMarkerIsWS ) textStart++;
				else done = true;
				break;
			default:
				if ( whitespaceIsWS
					&& Character.isWhitespace(codedText.charAt(textStart)) ) break;
				done = true;
				break;
			}
			if ( !done ) {
				if ( textStart == untilIndex ) {
					textStart = -1;
					break;
				}
				else textStart++;
			}
		}
		return textStart;
	}

	/**
	 * Unwraps the content of a TextFragment. All sequences of consecutive white spaces
	 * are replaced by a single space characters, and any white spaces at the head or
	 * the end of the text is trimmed out. White spaces here are: space, tab, CR and LF.
	 * Existing segments are not unwrapped. 
	 * @param frag the text fragment to unwrap.
	 */
	public static void unwrap (TextFragment frag) {
		String text = frag.getCodedText();
		StringBuilder tmp = new StringBuilder(text.length());
		boolean wasWS = true; // Removes leading white-spaces
		// Process the text
		for ( int i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				tmp.append(text.charAt(i));
				tmp.append(text.charAt(++i));
				wasWS = false;
				//TODO: Do we need to do something for inline between WS?
				break;
			case ' ':
			case '\t':
			case '\r':
			case '\n':
				if ( wasWS ) continue;
				wasWS = true;
				tmp.append(' ');
				break;
			default:
				wasWS = false;
				tmp.append(text.charAt(i));
				break;
			}
		}
		frag.setCodedText(tmp.toString().trim());
	}
	
	/**
	 * Helper method that checks if a given character is an inline code marker.
	 * @param ch the character to check.
	 * @return true if the character is a code marker, false if it is not.
	 */
	public static boolean isMarker (char ch) {
		return (( ch == MARKER_OPENING )
			|| ( ch == MARKER_CLOSING )
			|| ( ch == MARKER_ISOLATED ));
	}
	
	/**
	 * Creates an empty TextFragment.
	 */
	public TextFragment () {
		text = new StringBuilder();
		isBalanced = true;
	}

	/**
	 * Creates a TextFragment with a given text.
	 * @param text the text to use.
	 */
	public TextFragment (String text) {
		this.text = new StringBuilder((text==null) ? "" : text);
		isBalanced = true;
	}

	/**
	 * Creates a TextFragment with a given text and an initial id value for codes.
	 * This constructor can be used to create fragments that will be appended to an existing one.
	 * @param text the text to use.
	 * @param lastCodeId value to use to start the code id. The first new code will have for id this value+1.
	 * The value should be -1 or a positive number. Values below -1 will be automatically reset to -1.
	 */
	public TextFragment (String text,
		int lastCodeId)
	{
		this(text);
		if ( lastCodeId < -1 ) lastCodeId = -1;
		this.lastCodeID = lastCodeId;
	}

	/**
	 * Creates a TextFragment with the content of a given TextFragment.
	 * @param fragment the content to use.
	 */
	public TextFragment (TextFragment fragment) {
		this.setCodedText(fragment.getCodedText(), fragment.getCodes(), false);
		this.lastCodeID = fragment.lastCodeID;
	}
	
	/**
	 * Creates a TextFragment with the content made of a given coded text
	 * and a list of codes.
	 * @param newCodedText the new coded text.
	 * @param newCodes the list of codes.
	 */
	public TextFragment (String newCodedText,
		List<Code> newCodes)
	{
		setCodedText(newCodedText, newCodes, false);
	}
	
	/**
	 * Clones this TextFragment.
	 * @return a new TextFragment that is a copy of this one.
	 */
	@Override
	public TextFragment clone () {
		TextFragment tf = new TextFragment(this.getCodedText(), this.getClonedCodes());
		tf.lastCodeID = this.getLastCodeId();
		return tf;
	}
	
	/**
	 * Indicates if this TextFragment contains any in-line code with a reference.
	 * @return true if there is one or more in-line codes with a reference,
	 * false if there is no reference.
	 */
	public boolean hasReference () {
		if ( !hasCode() ) return false;
		for ( Code code : codes ) {
			if ( code.hasReference() ) return true;
		}
		return false;
	}
	
	/**
	 * Appends a string to the fragment. If the string is null, it is ignored.
	 * @param text the string to append.
	 */
	public TextFragment append (String text) {
		if ( text == null ) return this;
		this.text.append(text);
		return this;
	}

	/**
	 * Appends a TextFragment object to this fragment. If the fragment is null,
	 * it is ignored.
	 * @param fragment the TextFragment to append.
	 * @return this fragment.
	 */
	public TextFragment append (TextFragment fragment) {
		return append(fragment, false);
	}
	
	public TextFragment append (TextFragment fragment, boolean keepCodeIds) {
		insert(-1, fragment, keepCodeIds);
		return this;
	}
	
	/**
	 * Appends an existing code to this fragment.
	 * @param code the existing code to append.
	 * @return a reference to this fragment
	 */
	public TextFragment append (Code code) {
		if ( codes == null ) codes = new ArrayList<Code>();
		if (codes.size() >= TextFragmentUtil.MAX_INLINE_CODES) {
			throw new OkapiIllegalFilterOperationException("TextFragment has more than allowed maximum of " +
						TextFragmentUtil.MAX_INLINE_CODES + " codes");
		}
		// Append the code marker
		switch ( code.tagType ) {
		case OPENING:
			append(""+((char)MARKER_OPENING)+toChar(codes.size()));
			break;
		case CLOSING:
			append(""+((char)MARKER_CLOSING)+toChar(codes.size()));
			break;
		case PLACEHOLDER:
			append(""+((char)MARKER_ISOLATED)+toChar(codes.size()));
			break;
		}
		// Flag it as needing balancing, if needed
		if ( code.tagType != TagType.PLACEHOLDER ) {
			// Set as not balanced only if no id is defined
			if ( code.id == -1) isBalanced = false;
		}
		// Add the code
		codes.add(code);
		if ( code.tagType != TagType.CLOSING ) {
			if ( codes.get(codes.size()-1).id == -1 ) {
				codes.get(codes.size()-1).id = ++lastCodeID;
			}
			else { // Make sure the last ID is up to date
				if ( codes.get(codes.size()-1).id > lastCodeID ) {
					lastCodeID = codes.get(codes.size()-1).id;
				}
			}
		}
		return this;
	}

	/**
	 * Appends an annotation-type code to this text.
	 * @param tagType the tag type of the code (e.g. TagType.OPENING).
	 * @param type the type of the annotation (e.g. "protected").
	 * @param annotation the annotation to add (can be null).
	 * @return the new code that was added to this text.
	 */
	public Code append (TagType tagType,
		String type,
		InlineAnnotation annotation)
	{
		Code code = append(tagType, type, "");
		code.setAnnotation(type, annotation);
		return code;
	}
	
	/**
	 * Appends a new code to the text.
	 * @param tagType the tag type of the code (e.g. TagType.OPENING).
	 * @param type the type of the code (e.g. "bold").
	 * @param data the raw code itself. (e.g. "&lt;b&gt;").
	 * @return the new code that was added to the text.
	 */
	public Code append (TagType tagType,
		String type,
		String data)
	{
		Code code = new Code(tagType, type, data);
		append(code);
		return code;
	}

	/**
	 * Appends a new code to the text, when the code has a defined identifier.
	 * @param tagType the tag type of the code (e.g. TagType.OPENING).
	 * @param type the type of the code (e.g. "bold").
	 * @param data the raw code itself. (e.g. "&lt;b&gt;").
	 * @param id the identifier to use for this code.
	 * @return the new code that was added to the text.
	 */
	public Code append (TagType tagType,
		String type,
		String data,
		int id)
	{
		// Create the code
		Code code = new Code(tagType, type, data);
		code.id = id;
		append(code);
		return code;
	}
	
	/**
	 * Inserts a {@link String} object to this fragment.
	 * @param offset position in the coded text where to insert the new String.
	 * You can use -1 to append at the end of the current content.
	 * @param str String to insert.
	 * @throws InvalidPositionException when offset points inside a marker.
	 */
	public void insert(int offset, String str) {
		TextFragment tf = new TextFragment();
		tf.append(str);
		insert(offset, tf, false);
	}
	
	/**
	 * Inserts a {@link Code} object to this fragment.
	 * @param offset position in the coded text where to insert the new Code.
	 * You can use -1 to append at the end of the current content.
	 * @param code {@link Code} to insert.
	 * @throws InvalidPositionException when offset points inside a marker.
	 */
	public void insert(int offset, Code code) {
		TextFragment tf = new TextFragment();
		tf.append(code);
		insert(offset, tf, true);
	}

	/**
	 * Inserts a TextFragment object to this fragment.
	 * @param offset position in the coded text where to insert the new fragment.
	 * You can use -1 to append at the end of the current content.
	 * @param fragment the TextFragment to insert.
	 * @throws InvalidPositionException when offset points inside a marker.
	 */
	public void insert (int offset,
		TextFragment fragment) {
		insert(offset, fragment, false);
	}
	
	/**
	 * Inserts a TextFragment object to this fragment.
	 * @param offset position in the coded text where to insert the new fragment.
	 * You can use -1 to append at the end of the current content.
	 * @param fragment the TextFragment to insert.
	 * @param keepCodeIds true to not change Ids of the codes of the inserted TextFragment. 
	 */
	public void insert (int offset,
		TextFragment fragment,
		boolean keepCodeIds)
	{
		if ( fragment == null ) return;
		checkPositionForMarker(offset);
		StringBuilder tmp = new StringBuilder(fragment.getCodedText());
		List<Code> newCodes = fragment.getCodes();

		// Note: This code assumes the lastCodeID is up-to-date and no code ID is higher than it.
		
		// If we have new codes, we need to deal with them: 
		if ( !newCodes.isEmpty() ) {
			
			// make sure we have a list of codes available
			if ( codes == null ) {
				codes = new ArrayList<Code>();
			}
			
			int newLastId = lastCodeID;
			int idOffset = lastCodeID;
			int negId = fragment.getLastCodeId()+idOffset;
			boolean doit = false;
			// Update the coded text to use new code indices and possibly new IDs
			for ( int i=0; i<tmp.length(); i++ ) {
				switch ( tmp.charAt(i) ) {
				case MARKER_OPENING:
				case MARKER_CLOSING:
				case MARKER_ISOLATED:
					Code c = newCodes.get(toIndex(tmp.charAt(++i))).clone();
					int cid = c.getId();
					if ( !keepCodeIds ) {
						if ( c.getTagType() != TagType.CLOSING ) {
							if ( cid < 0 ) {
								c.setId(++negId);
								if ( c.getId() > newLastId ) newLastId = c.getId();
							}
							else if ( doit || cid <= lastCodeID ) {
								c.setId(cid+idOffset);
								if ( c.getId() > newLastId ) newLastId = c.getId();
								doit = true;
							}
						}
						else { // Allow re-balancing for closing codes
							c.setId(-1);
						}
					}
					codes.add(c);
					tmp.setCharAt(i, toChar(codes.size()-1));
					break;
				}
			}
			lastCodeID = newLastId;

		}
		
		// Insert the new text in one chunk
		if ( offset < 0 ) text.append(tmp);
		else text.insert(offset, tmp);
		
		// If there was new codes we will need to re-balance
		// We keep the current balance state otherwise
		if ( !newCodes.isEmpty() ) {
			this.isBalanced = false;
		}
	}

	

	// Code with YS solution (not good enough: using offest would be better)
//			int newLastId = Math.max(lastCodeID, fragment.getLastCodeId());
//			// Update the coded text to use new code indices
//			for ( int i=0; i<tmp.length(); i++ ) {
//				switch ( tmp.charAt(i) ) {
//				case MARKER_OPENING:
//				case MARKER_CLOSING:
//				case MARKER_ISOLATED:
//					Code c = newCodes.get(toIndex(tmp.charAt(++i))).clone();
//					if ( !keepCodeIds && (c.getTagType() != TagType.CLOSING ) && ( c.getId() <= lastCodeID )) {
//						// If the code is not a closing one (Closing codes will be handled when re-balancing)
//						// and if its id is less than the lastCodeID for this fragment: make that id the next one
//						// using the highest of the two possible
//						c.setId(++newLastId);
//						// 
//					}
//					codes.add(c);
//					tmp.setCharAt(i, toChar(codes.size()-1));
//					break;
//				}
//			}
//			lastCodeID = newLastId;

	
	/**
	 * Clears the fragment of all content. The parent is not modified.
	 */
	public void clear () {
		text = new StringBuilder();
		codes = null;
		lastCodeID = 0;
		isBalanced = true;
	}
	
	/**
	 * Trims white-spaces from the beginning and the end of this fragment.
	 */
	public void trim () {
		text = new StringBuilder(text.toString().trim());
	}
	
	 /**
	  *  Remove leading whitespace from this fragment
	  */
    public void ltrim() {
    	text = new StringBuilder(text.toString().replaceAll("^\\s+", ""));
    }

    /**
     *  Remove trailing whitespace from this fragment
     */
    public void rtrim() {
    	text = new StringBuilder(text.toString().replaceAll("\\s+$", ""));
    }
    
    /**
     *  Collapse all whitespace to a single space character.
     */
    public void collapseWhitespace() {
    	text = new StringBuilder(WHITESPACE_PATTERN.matcher(text).replaceAll(" "));
    }
	
	/**
	 * Get the text of the fragment (all codes are removed)
	 * @return the content of fragment without codes
	 */
	public String getText () {		
		if ( !hasCode() ) {
			return text.toString();
		}
		Matcher m = MARKERS_REGEX.matcher(new String(text));
		return m.replaceAll(""); 
	}
	
	/**
	 * Helper method that will take a coded string and return a text only version.
	 * @param codedText string with possible TextFragment codes.
	 * @return the given string stripped out of any codes.
	 */
	public static String getText (String codedText) {
		Matcher m = MARKERS_REGEX.matcher(codedText);
		return m.replaceAll(""); 
	}

	/**
	 * Gets the coded text representation of the fragment.
	 * @return the coded text for the fragment.
	 */
	public String getCodedText () {
		if ( !isBalanced ) balanceMarkers();
		return text.toString();
	}

	/**
	 * Gets the portion of coded text for a given section of the coded text.
	 * @param start the position of the first character or marker of the section
	 * (in the coded text representation).
	 * @param end The position just after the last character or marker of the section
	 * (in the coded text representation).
	 * You can use -1 for ending the section at the end of the fragment.
	 * @return the portion of coded text for the given range. It can be 
	 * empty but never null.
	 * @throws InvalidPositionException when start or end points inside a marker.
	 */
	public String getCodedText (int start,
		int end)
	{
		if ( end == -1 ) end = text.length();
		checkPositionForMarker(start);
		checkPositionForMarker(end);
		if ( !isBalanced ) balanceMarkers();
		return text.substring(start, end);
	}

	/**
	 * Gets the code for a given index formatted as character (the second
	 * special character in a marker in a coded text string).
	 * @param indexAsChar the index value coded as character.
	 * @return the corresponding code.
	 */
	public Code getCode (char indexAsChar) {
		if ( !isBalanced ) balanceMarkers();
		return codes.get(toIndex(indexAsChar)); 
	}
	
	/**
	 * Gets the code for a given index.
	 * @param index the index of the code.
	 * @return the code for the given index.
	 */
	public Code getCode (int index) {
		if ( !isBalanced ) balanceMarkers();
		return codes.get(index);
	}
		
	/**
	 * Gets the list of all codes for the fragment.
	 * @return the list of all codes for the fragment. If there is no code, an empty
	 * list is returned.
	 */
	public List<Code> getCodes () {
		if ( codes == null ) codes = new ArrayList<Code>();
		if ( !isBalanced ) balanceMarkers();
		return Collections.unmodifiableList(codes);
	}
	
	/*
	 * For serialization
	 */
	protected void setCodes(List<Code> codes) {
		this.codes = codes;
	}
	
	/**
	 * Gets a list of the copy of the codes for this fragment.
	 * @return the list of the copy of the codes for this fragment. If there is no code, an empty list is returned.
	 */
	public List<Code> getClonedCodes () {
		ArrayList<Code> clones = new ArrayList<Code>();
		for ( Code code : getCodes() ) {
			clones.add(code.clone());
		}
		return clones;
	}
	

	/**
	 * Gets a copy of the list of the codes that are within a given section of
	 * coded text.
	 * @param start the position of the first character or marker of the section
	 * (in the coded text representation).
	 * @param end the position just after the last character or marker of the section
	 * (in the coded text representation).
	 * @return a new list of all codes within the given range.
	 * @throws InvalidPositionException when start or end points inside a marker.
	 */
	public List<Code> getCodes (int start,
		int end)
	{
		ArrayList<Code> tmpCodes = new ArrayList<Code>();
		if ( codes == null ) return tmpCodes;
		if ( codes.isEmpty() ) return tmpCodes;
		if ( !isBalanced ) balanceMarkers();
		checkPositionForMarker(start);
		checkPositionForMarker(end);

		for ( int i=start; i<end; i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				Code ori = codes.get(toIndex(text.charAt(++i)));
				tmpCodes.add(ori.clone());
				break;
			}
		}
	
		return tmpCodes;
	}
	
	/**
	 * Gets the index value for the first in-line code (in the codes list)
	 * with a given identifier.
	 * @param id the identifier to look for.
	 * @return the index of the found code, or -1 if none is found. 
	 */
	public int getIndex (int id) {
		if ( codes == null ) return -1;
		if ( !isBalanced ) balanceMarkers();
		int i = 0;
		for ( Code code : codes ) {
			if ( code.id == id ) return i;
			i++;
		}
		return -1;
	}
	
	/**
	 * Gets the index value for the closing in-line code (in the codes list)
	 * with a given identifier.
	 * @param id the identifier of the closing tag to look for.
	 * @return the index of the found closing code, or -1 if none is found. 
	 */
	public int getIndexForClosing (int id) {
		if ( codes == null ) return -1;
		if ( !isBalanced ) balanceMarkers();
		int i = 0;
		for ( Code code : codes ) {
			if (( code.tagType == TagType.CLOSING ) && ( code.id == id )) {
				return i;
			}
			i++;
		}
		return -1;
	}

	/**
	 * Indicates if the fragment is empty (no text and no codes).
	 * @return true if the fragment is empty.
	 */
	public boolean isEmpty () {
		return (text == null || text.length()==0);
	}
	
	/**
	 * Indicates if this fragment contains at least one character other than a whitespace.
	 * (inline codes and other markers do not count as characters).
	 * @return true if this fragment contains at least one character, excluding whitespace.
	 */
	public boolean hasText () {
		return hasText(false);
	}
	
	/**
	 * Indicates if this fragment contains at least one character
	 * (inline codes, segment markers, and annotation markers do not count as characters).
	 * @param whiteSpacesAreText indicates if whitespaces should be considered 
	 * characters or not for the purpose of checking if this fragment is empty.
	 * @return true if this fragment contains at least one character (that character could
	 * be a whitespace if whiteSpacesAreText is set to true).
	 */
	public boolean hasText (boolean whiteSpacesAreText) {
		for ( int i=0; i<text.length(); i++ ) {
			switch (text.charAt(i)) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				i++; // Skip over the marker, they are not text
				continue;
			}
			// Not a marker
			// If we count whitespaces as text, then we have text
			if ( whiteSpacesAreText ) return true;
			// Otherwise we have text if it's not a whitespace
			if ( !Character.isWhitespace(text.charAt(i)) ) return true;
		}
		return false;
	}
	
	/**
	 * Indicates if the fragment contains at least one code.
	 * @return true if the fragment contains at least one code.
	 */
	public boolean hasCode () {
		if ( codes == null ) return false;
		return (codes.size()>0);
	}

	/**
	 * Removes a section of the fragment (including its codes).
	 * @param start the position of the first character or marker of the section
	 * (in the coded text representation).
	 * @param end the position just after the last character or marker of the section
	 * (in the coded text representation). You can use -1 to indicate the end of the fragment.
	 * @throws InvalidPositionException when start or end points inside a marker.
	 */
	public void remove (int start,
		int end)
	{
		if ( end == -1 ) end = text.length();
		// TODO: Check if there is a better way to do this,
		// as this is quite expensive.
		checkPositionForMarker(start);
		checkPositionForMarker(end);
		// Remove the coded text to delete
		text.replace(start, end, "");
		if (( codes == null ) || ( codes.size()==0 )) return;
		// Make a list of all remaining codes
		ArrayList<Code> remaining = new ArrayList<Code>();
		for ( int i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				// Copy the remaining codes into the new list
				remaining.add(codes.get(toIndex(text.charAt(++i))));
				// And update the index in the coded text
				text.setCharAt(i, toChar(remaining.size()-1));
				break;
			}
		}
		codes.clear();
		codes = remaining; // The new list is the remaining codes
		isBalanced = false;
	}

	/**
	 * Gets a copy of a sub-sequence of this object.
	 * @param start the position of the first character or marker of the section
	 * (in the coded text representation).
	 * @param end the position just after the last character or marker of the section
	 * (in the coded text representation).
	 * You can use -1 for ending the section at the end of the fragment.
	 * @return a new TextFragment object with a copy of the given sub-sequence.
	 */
	@Override
	public TextFragment subSequence (int start,
		int end)
	{
		TextFragment sub = new TextFragment();
		if ( isEmpty() ) return sub;
		StringBuilder tmpText = new StringBuilder(getCodedText(start, end));
		ArrayList<Code> tmpCodes = null;
	
		// Get the codes and adjust indices if needed
		if (( codes != null ) && ( codes.size() > 0 )) {
			tmpCodes = new ArrayList<Code>(); 
			for ( int i=0; i<tmpText.length(); i++ ) {
				switch ( tmpText.charAt(i) ) {
				case MARKER_OPENING:
				case MARKER_CLOSING:
				case MARKER_ISOLATED:
					tmpCodes.add(codes.get(toIndex(tmpText.charAt(++i))).clone());
					tmpText.setCharAt(i, toChar(tmpCodes.size()-1));
					if ( sub.lastCodeID < tmpCodes.get(tmpCodes.size()-1).id ) {
						sub.lastCodeID = tmpCodes.get(tmpCodes.size()-1).id;
					}
					break;
				}
			}
		}
		sub.setCodedText(tmpText.toString(), tmpCodes, false);
//		sub.lastCodeID = lastCodeID;
		return sub;
	}
	
	/**
	 * Sets the coded text of the fragment, using its the existing codes. The coded
	 * text must be valid for the existing codes.
	 * @param newCodedText the coded text to apply.
	 * @throws InvalidContentException when the coded text is not valid, or does
	 * not correspond to the existing codes.
	 */
	public void setCodedText (String newCodedText)
	{
		setCodedText(newCodedText, codes, false);
	}

	/**
	 * Sets the coded text of the fragment, using its the existing codes. The coded
	 * text must be valid for the existing codes.
	 * @param newCodedText The coded text to apply.
	 * @param allowCodeDeletion True when missing in-line codes in the coded text
	 * means the corresponding codes should be deleted from the fragment.
	 * @throws InvalidContentException When the coded text is not valid, or does
	 * not correspond to the existing codes.
	 */
	public void setCodedText (String newCodedText,
		boolean allowCodeDeletion)
	{
		setCodedText(newCodedText, codes, allowCodeDeletion);
	}

	/**
	 * Sets the coded text of the fragment and its corresponding codes.
	 * @param newCodedText the coded text to apply.
	 * @param newCodes the list of the corresponding codes.
	 * @throws InvalidContentException when the coded text is not valid or does 
	 * not correspond to the new codes.
	 */
	public void setCodedText (String newCodedText,
		List<Code> newCodes)
	{
		setCodedText(newCodedText, newCodes, false);
	}
	
	/**
	 * Sets the coded text of the fragment and its corresponding codes.
	 * @param newCodedText the coded text to apply.
	 * @param newCodes the list of the corresponding codes.
	 * @param allowCodeDeletion True when missing in-line codes in the coded text
	 * means the corresponding codes should be deleted from the fragment.
	 * @throws InvalidContentException when the coded text is not valid or does 
	 * not correspond to the new codes.
	 */
	public void setCodedText (String newCodedText,
		List<Code> newCodes,
		boolean allowCodeDeletion)
	{
		isBalanced = false;
		text = new StringBuilder(newCodedText);
		if ( newCodes == null ) {
			if (( codes != null ) && !allowCodeDeletion ) {
				throw new InvalidContentException("Missing codes in the new list: "+codes.size());
			} // Else: OK to delete all codes
			codes = null;
		}
		else codes = new ArrayList<Code>(newCodes);
		if (( codes == null ) || ( codes.size() == 0 )) {
			lastCodeID = 0;
			return; // No codes, all done.
		}
		//TODO: do we need to reset the lastCodeID?
		ArrayList<Code> activeCodes = new ArrayList<Code>();
		
		// Validate the codes and coded text
		int j = 0;
		for ( int i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				j++;
				if ( codes == null ) {
					throw new InvalidContentException("Invalid index for marker "+j);
				}
				try {
					// Just try to access the code
					activeCodes.add(codes.get(toIndex(text.charAt(++i))));
				}
				catch ( IndexOutOfBoundsException e ) {
					throw new InvalidContentException("Invalid index for marker "+j);
				}
				break;
			}
		}
		
		if ( allowCodeDeletion ) {
			codes.retainAll(activeCodes);
		}
		else { // No deletion allowed: check the numbers
			if ( j > 0 ) {
				if (( codes == null ) || ( j < codes.size() )) {
					throw new InvalidContentException(
						String.format("Markers in coded text: %d. Listed codes: %d. New text=\"%s\" ",
							j, codes.size(), newCodedText));
				}
			}
		}
	}

	/**
	 * Gets the coded text for this fragment. This method returns the same data as {@link #getCodedText()}.
	 * <p>Each code is represented by a placeholder made of two special characters.
	 * To get the content with the codes expanded as their original data use {@link #toText()}.
	 * @return the coded text for this fragment.
	 */
	@Override
	public String toString () {
		return text.toString();
	}
	
	/**
	 * Returns the content of this fragment, including the original codes whenever
	 * possible. To get the coded text for this fragment use {@link #getCodedText()}
	 * or {@link #toString()}.
	 * @return the content of this fragment.
	 */
	public String toText () {
		if (( codes == null ) || ( codes.size() == 0 )) return text.toString();
		if ( !isBalanced ) balanceMarkers();
		StringBuilder tmp = new StringBuilder();
		Code code;
		for ( int i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				code = codes.get(toIndex(text.charAt(++i)));
				tmp.append(code.data);
				break;
			default:
				tmp.append(text.charAt(i));
				break;
			}
		}
		return tmp.toString();
	}

	/**
	 * Compares an object with this TextFragment. If the object is also a TextFragment,
	 * the method returns the same results as <code>compareTo(fragment, false)</code>
	 * (Note that inline codes are not compared with this method).
	 * @param tf the object to compare with this TextFragment.
	 * @return a value 0 if the objects are equals.
	 */
	public int compareTo (TextFragment tf) {
		if ( tf == null ) return -1;
		return compareTo(tf, false);
	}

	/**
	 * Compares a TextFragment with this one. The method returns the comparison between the text parts of both text
	 * fragments, and if specified, between their inline codes.
	 * 
	 * @param frag
	 *            the TextFragment to compare with this one.
	 * @param codeSensitive
	 *            true if the codes need to be compared as well.
	 * @return a value 0 if the objects are equals.
	 */
	public int compareTo(TextFragment frag, boolean codeSensitive) {
		if (frag == null) {
			return -1;
		}

		int textOnlyCompare = getText().compareTo(frag.getText());
//		String t1 = getText();
//		String t2 = frag.getText();
		if (codeSensitive) {
			if (hasCode() && textOnlyCompare == 0) {
				if (!frag.hasCode()) {
					return 1;
				}
				String otherCodes = frag.getCodes().toString();
				return otherCodes.compareTo(codes.toString());
			} else {
				if (frag.hasCode()) {
					return -1;
				}
			}
		}
		return textOnlyCompare;
	}

	@Override
	public boolean equals (Object object) {
		if ( object == null ) return false;
		if (object == this) return true;
		if (!(object instanceof TextFragment)) return false;
		return (compareTo((TextFragment)object, true) == 0);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getText(), getCodes());
	}

	/**
	 * Changes a section of the coded text into a single code. Any code already
	 * existing that is within the range will become part of the new code.
	 * @param start The position of the first character or marker of the section
	 * (in the coded text representation).
	 * @param end the position just after the last character or marker of the section
	 * (in the coded text representation).
	 * @param tagType the tag type of the new code.
	 * @param type the type of the new code.
	 * @return the difference between the coded text length before and after 
	 * the operation. This value can be used to adjust further start and end positions
	 * that have been calculated on the coded text before the changes are applied.
	 * @throws InvalidPositionException when start or end points inside a marker.
	 */
	public int changeToCode (int start,
			int end,
			TagType tagType,
			String type) {
		return changeToCode(start, end, tagType, type, false);
	}
	

	/**
	 * Changes a section of the coded text into a single code. Any code already
	 * existing that is within the range will become part of the new code.
	 * @param start The position of the first character or marker of the section
	 * (in the coded text representation).
	 * @param end the position just after the last character or marker of the section
	 * (in the coded text representation).
	 * @param tagType the tag type of the new code.
	 * @param type the type of the new code.
	 * @param setDisplayText if true set the subsequence (sub) as the displayText of the code
	 * @return the difference between the coded text length before and after 
	 * the operation. This value can be used to adjust further start and end positions
	 * that have been calculated on the coded text before the changes are applied.
	 * @throws InvalidPositionException when start or end points inside a marker.
	 */
	public int changeToCode (int start,
		int end,
		TagType tagType,
		String type, boolean setDisplayText)
	{
		// Get the subsequence
		TextFragment sub = subSequence(start, end);
		// Store the length of the coded text before the operation
		int before = text.length();
		// Create the new code, using the text of the subsequence as the data
		Code code = new Code(tagType, type, sub.toText());
		if ( codes == null ) codes = new ArrayList<Code>();
		// Remove the section that will be code, this takes care of the codes too
		remove(start, end);
		// Create the new marker
		String marker = null;
		switch ( tagType ) {
		case OPENING:
			marker = ""+((char)MARKER_OPENING)+toChar(codes.size());
			code.id = ++lastCodeID;
			break;
		case CLOSING:
			marker = ""+((char)MARKER_CLOSING)+toChar(codes.size());
			// The id stays -1
			break;
		case PLACEHOLDER:
			marker = ""+((char)MARKER_ISOLATED)+toChar(codes.size());
			code.id = ++lastCodeID;
			break;
		}
		// Insert the new marker into the coded text
		text.insert(start, marker);
		if (code.data.toString().contains(TextFragment.REFMARKER_START)) {
			code.setReferenceFlag(true);
		}
		
		// add original substring (sub) as GenericAnnotationType.DISPLAYTEXT
		if (setDisplayText) {
			code.setDisplayText(sub.toText());
		}
		
		// Add the new code
		codes.add(code);
		isBalanced = false;
		return text.length()-before;
	}

	/**
	 * Finds the position in this coded text of the closing code for a give
	 * opening code.
	 * @param id identifier of the opening code.
	 * @param indexOfOpening index of the opening code.
	 * @return the position in this text of the closing code for the given
	 * opening code, or -1 if it could not be found. 
	 */
	private int findClosingCodePosition(int id, int indexOfOpening) {
		for ( int i=indexOfOpening+1; i<codes.size(); i++ ) {
			if ( codes.get(i).id == id ) {
				if ( codes.get(i).type.equals(codes.get(indexOfOpening).type) ) {
					char ci = toChar(i); // Convert found index to char
					for ( int j=0; j<text.length(); j++ ) {
						if ( text.charAt(j) == MARKER_CLOSING ) {
							if ( text.charAt(j+1) == ci ) {
								return j;
							}
						}
					}
					return -1; // Found the code in the list but not in the text
				}
			}
		}
		return -1; // Cannot find the code
	}

	/**
	 * Annotates a section of this text.
	 * @param start the position of the first character or marker of the section 
	 * to annotate (in the coded text representation).
	 * @param end the position just after the last character or marker of the section
	 * to annotate (in the coded text representation).
	 * @param type the type of annotation to set.
	 * @param annotation the annotation to set (can be null).
	 * @return the difference between the coded text length before and after 
	 * the operation. This value can be used to adjust further start and end positions
	 * that have been calculated on the coded text before the changes are applied.
	 * @throws InvalidPositionException when start or end points inside a marker.
	 */
	public int annotate (int start,
		int end,
		String type,
		InlineAnnotation annotation)
	{
		checkPositionForMarker(start);
		checkPositionForMarker(end);
		if ( !isBalanced ) balanceMarkers();
		//TODO: Handle all the cases (overlapping, interrupted range, etc.
		// cases:
		// a<1>|bc|</1>d = a<1n>bc</1>d
		// a|<1>bc|</1>d = a<1n>bc</1>d
		// a<1>|bc</1>|d = a<1n>bc</1>d
		// a|<1>bc</1>|d = a<1n>bc</1>d
		// a<1>|b|c</1>d = a<1><2n>b</2>c</1>d
		// a<1>b|c|</1>d = a<1>b<2n>c</2></1>d
		// a<1>|bc</1>d| = a<1n>bc</1><2n>d</2>
		// a<1>b|c</1>d| = a<1>b<2n>c</2></1><3n>d</3>
		// |a<1>bc</1>d| = <2n>a<1>bc</1>d</2>

		// Store the length of the coded text before the operation
		int before = text.length();
		Code startCode = null;
		Code endCode = null;
		
		// Make sure we have a codes array or check if we can re-use codes
		if ( codes == null ) {
			codes = new ArrayList<Code>();
		}
		else { // If we have other codes, maybe we can use them...
			if (( start > 1 ) && (( text.charAt(start-2) == MARKER_OPENING ))) {
				// start is just before an opening code
				int sci = toIndex(text.charAt(start-1));
				int ccp = findClosingCodePosition(codes.get(sci).id, sci);
				if ( ccp != -1 ) {
					if ( ccp == end ) {
						startCode = codes.get(sci);
						endCode = codes.get(toIndex(text.charAt(ccp+1)));
					}
				}
			}
		}

		String startBuf = ""; // Empty by default
		if ( startCode == null ) {
			// Create the new start code
			startCode = new Code(TagType.OPENING, Code.TYPE_ANNOTATION_ONLY);
			startBuf = ""+((char)MARKER_OPENING)+toChar(codes.size());
			startCode.id = ++lastCodeID;
			// Insert the start marker
			text.insert(start, startBuf);
			codes.add(startCode);
		}
		startCode.setAnnotation(type, annotation);

		if ( endCode == null ) {
			// Create the new end code
			endCode = new Code(TagType.CLOSING, Code.TYPE_ANNOTATION_ONLY);
			String endBuf = ""+((char)MARKER_CLOSING)+toChar(codes.size());
			endCode.id = startCode.id;
			// Insert the end code, startBuf length is 0 or 2
			text.insert(end+startBuf.length(), endBuf);
			codes.add(endCode);
		}
		//TODO: How to avoid the actual annotation on the closing code??
		//TODO: could it be linked somehow to the starting code??
		endCode.setAnnotation(type, annotation);
		
		// No need to change isBalance since we did balance those codes
		// return the difference
		return text.length()-before;
	}

	/**
	 * Removes all annotations in this text. This also removes any code that
	 * is or was there only for holding an annotation.
	 */
	public void removeAnnotations () {
		if ( !hasCode() ) return;
		boolean clean = false;
		for ( Code code : codes ) {
			if ( code.annotations != null ) {
				code.annotations.clear();
				code.annotations = null;
				clean = true;
			}
		}
		if ( clean ) cleanUnusedCodes();
	}
	
	/**
	 * Removes all annotations of a given type in this text. This also removes any
	 * code that is there only for holding an annotation of the given type, or
	 * any code that has no annotation and no data either. 
	 * @param type the type of annotation to remove.
	 */
	public void removeAnnotations (String type) {
		if ( !hasCode() ) return;
		boolean clean = false;
		for ( Code code : codes ) {
			code.removeAnnotation(type);
			clean = true;
		}
		if ( clean ) cleanUnusedCodes();
	}
	
	/**
	 * Indicates if this text has at least one annotation.
	 * @return true if there is at least one annotation, false otherwise.
	 */
	public boolean hasAnnotation () {
		if ( !hasCode() ) return false;
		for ( Code code : codes ) {
			if ( code.hasAnnotation() ) return true;
		}
		return false;
	}
	
	/**
	 * Indicates if this text has at least one annotation of a given type.
	 * @param type the type of annotation to look for.
	 * @return true if there is at least one annotation of the given type, false otherwise.
	 */
	public boolean hasAnnotation (String type) {
		if ( !hasCode() ) return false;
		for ( Code code : codes ) {
			if ( code.hasAnnotation(type) ) return true;
		}
		return false;
	}
	
	/**
	 * Removes all codes that have no data and no annotation.
	 */
	public void cleanUnusedCodes () {
		// We cannot use hasCode() because it may be wrong at this point.
		Code code;
		int before = text.length();
		for ( int i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				code = codes.get(toIndex(text.charAt(++i)));
				if (( !code.hasData() ) && ( !code.hasAnnotation() )) {
					text = text.delete(i-1, i+1);
					i--; // Correct  for loop
				}
				break;
			}
		}
		// No change, we're done.
		if ( text.length() == before ) return;
		// Else: We need to re-build the list of codes and adjust the indices
		// Make a list of all remaining codes
		ArrayList<Code> remaining = new ArrayList<Code>();
		for ( int i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				// Copy the remaining codes into the new list
				remaining.add(codes.get(toIndex(text.charAt(++i))));
				// And update the index in the coded text
				text.setCharAt(i, toChar(remaining.size()-1));
				break;
			}
		}
		codes.clear();
		codes = remaining; // The new list is the remaining codes
		isBalanced = false;
	}

	/**
	 * Removes all codes both in the Codes list and the markers.
	 * @return this {@link TextFragment}, with the codes removed
	 */
	public TextFragment cleanCodes() {
		// We cannot use hasCode() because it may be wrong at this point.
		Code code;
		for ( int i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				code = codes.get(toIndex(text.charAt(++i)));
				if (( !code.hasData() ) && ( !code.hasAnnotation() )) {
					text = text.delete(i-1, i+1);
					i--; // Correct  for loop
				}
				break;
			}
		}			
		// remove all listed codes
		setCodes(null);
		return this;
	}
	
	public int getCodePosition (int index) {
		int n = text.indexOf(String.valueOf(toChar(index)), 0);
		if ( n == -1 ) return -1; // Not found
		return n-1; // Position is the one of char before (open/close/placeholder) 
	}

	/**
	 * Gets the list of all spans of text annotated with a given type of annotation.
	 * @param type the type of annotation to look for.
	 * @return a list of annotated spans for the given type (it may be empty).
	 */
	public List<AnnotatedSpan> getAnnotatedSpans (String type) {
		// Always return a list, never null.
		ArrayList<AnnotatedSpan> list = new ArrayList<AnnotatedSpan>();
		if ( codes == null ) return list;
		for ( int i=0; i<codes.size(); i++ ) {
			if (( codes.get(i).tagType == TagType.OPENING ) && ( codes.get(i).hasAnnotation(type) )) {
				int start = getCodePosition(i)+2; // Span starts just after the marker
				int end = findClosingCodePosition(codes.get(i).id, i);
				if (( start == -1 ) || ( end == -1 )) continue; // Something is wrong
				list.add(new AnnotatedSpan(type, codes.get(i).getAnnotation(type),
					subSequence(start, end), start, end));
			}
		}
		return list;
	}
	
	/**
	 * Renumbers the IDs of the codes in the fragment.
	 * @return The last value used for code ID or 0 if this fragment has no codes. 
	 */
	public int renumberCodes () {		
		if ( codes == null ) return 0;
		
//		for ( Code code : codes ) {
//			if ( code.tagType != TagType.CLOSING ) code.id = ++lastCodeID;
//		}
//		// go ahead and balance as we usually 
//		//want full id assignment at this point
//		balanceMarkers();
		Map<Integer, Integer> lookup = new HashMap<Integer, Integer>();
		int curId = 1;
		
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (isMarker(ch)) {
				Code code = codes.get(toIndex(text.charAt(++i)));
				
				switch (code.tagType) {
				case OPENING:					
					if (code.id != curId) {
						lookup.put(code.id, curId); // store for the closing code with same id
						code.id = curId;						
					}
					lastCodeID = curId++;
					break;
					
				case CLOSING:
					if (lookup.containsKey(code.id)) {
						code.id = lookup.get(code.id); // set the stored id of the opening code
					}
					break;
					
				case PLACEHOLDER:
					code.id = curId;
					lastCodeID = curId++; 
					break;
				}
			}
		}
		return lastCodeID;
	}
	
	/**
	 * Re-assigns IDs of the codes in this fragment to be in a sequential order starting
	 * from a given base. 
	 * @param idBase The base from which code IDs start numbering.
	 * @return The last value used for code ID or -1 if this fragment has no codes.
	 */
	public int renumberCodes (int idBase) {
		return renumberCodes (idBase, true);
	}
	
	/**
	 * Re-assigns IDs of the codes in this fragment to be in a sequential order starting
	 * from a given base. 
	 * @param idBase The base from which code IDs start numbering.
	 * @param mindPosition If true, the codes with lesser positions in this text fragment will have lesser IDs.
	 * If false, the codes with lesser original IDs will be assigned lesser IDs.
	 * @return The last value used for code ID or idBase-1 if this fragment has no codes.
	 */
	public int renumberCodes (int idBase, boolean mindPosition) {
		if ( codes == null ) return idBase - 1;
		
		Map<Integer, Integer> map;
		if (mindPosition) {
			map = new LinkedHashMap<Integer, Integer>(); // guaranteed-order map
			for ( int i=0; i<text.length(); i++ ) {
				switch ( text.charAt(i) ) {
				case MARKER_OPENING:
				case MARKER_CLOSING:
				case MARKER_ISOLATED:
					int index = toIndex(text.charAt(++i));
					Code code = codes.get(index);
					map.put(code.getId(), -1);
				}
			}
		}
		else {
			map = new TreeMap<Integer, Integer>(); // sorted map
			for (Code code : this.codes) {
				map.put(code.getId(), -1);
			}
		}
		
		int i = idBase;
		for (int key : map.keySet()) {
			map.put(key, i++);
		}
		lastCodeID = i - 1;
		
		if (this.codes != null) {
			for (Code code : this.codes) {
				code.setId(map.get(code.getId()));
			}
		}
		
		return lastCodeID;
	}
	
	/**
	 * Remove the {@link Code} from this TextFragment 
	 * @param code - the {@link Code} to remove
	 */
//FIXME: This does not delete the code from the list of codes
// and does not re-index the markers in the coded-text
	public void removeCode(Code code) {
		if (code == null || 
				codes == null || 
				codes.isEmpty()) {
			return;
		}
		
		// TODO: should we balance the codes before removal?
		for (int i=0; i<text.length(); i++) {
			switch (text.charAt(i)) {
				case TextFragment.MARKER_OPENING:
				case TextFragment.MARKER_CLOSING:
				case TextFragment.MARKER_ISOLATED:
					int index = toIndex(text.charAt(i+1));
					if (codes.get(index).getId() == code.getId() &&
							codes.get(index).getTagType() == code.getTagType()) {
						remove(i, i+2);
						return;
					}
					i++; // skip index marker as well
					break;
				default:					
					break;
			}
		}	
	}
	
	/**
	 * Verifies if a given position in the coded text is on the second special
	 * character of a marker sequence.
	 * @param position the position to text.
	 * @throws InvalidPositionException when position points inside a marker.
	 */
	private void checkPositionForMarker (int position) {
		if ( position > 0 ) {
			switch ( text.charAt(position-1) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				throw new InvalidPositionException (
					String.format("Position %d is inside a marker.", position));
			}
		}
	}
	
	/**
	 * Balances the markers based on the tag type of the codes.
	 * Closing codes can have -1 as their ID, they will get the Id of their
	 * matching opening, or a new ID if they are isolated. Closing codes with
	 * and existing id that found themselves isolated keep the same id.
	 * This method also reset the last code id value to the highest code id found.
	 */
	public void balanceMarkers () {
		// FIXME: there are many ways to access codes
		// that do *not* balance them!!! How do we
		// make sure codes are balance in all cases?
		if ( codes == null ) return;
		lastCodeID = 0;
		int[] closingIds = new int[codes.size()];
		int i = 0;
		for ( Code item : codes ) {
			// Keep a copy of the original IDs of closing tags
			closingIds[i] = item.id;
			// And get the highest ID value used
			if ( item.id > lastCodeID ) lastCodeID = item.id;
			i++;
		}
		// Process the markers
		for ( i=0; i<text.length(); i++ ) {
			switch ( text.charAt(i) ) {
			case MARKER_OPENING:
			case MARKER_CLOSING:
			case MARKER_ISOLATED:
				int index = toIndex(text.charAt(i+1));
				//TODO: fix this: index is not codes sequence, so the loop to find the sibling close may not work
				// because it start after the code (e.g. if the code was inserted after)
				Code code = codes.get(index);
				switch ( code.tagType ) {
				case PLACEHOLDER:
					text.setCharAt(i, (char)MARKER_ISOLATED);
					break;
				case OPENING:
					// Search for corresponding closing code
					boolean found = false;
					// First look for a closing code with the same id/type
					for ( int j=index+1; j<codes.size(); j++ ) {
						if (( codes.get(j).tagType == TagType.CLOSING ) && codes.get(j).type.equals(code.type) ) {
							if ( codes.get(j).id == code.id && closingIds[j] != -9999) {
								// Found it
								found = true;
								// Mark this closing code as used (==-9999)
								closingIds[j] = -9999;
								text.setCharAt(i, (char)MARKER_OPENING);
								break;
							}
						}
					}
					if ( found ) break; // Done for this code
					//===== If not found: try to balance the opening
					boolean fixupMode = false;
					int candidate = -1;
					int stackElem = 1;
					int stackType = 1;
					for ( int j=index+1; j<codes.size(); j++ ) {
						if ( codes.get(j).type.equals(code.type) ) {
							if ( codes.get(j).tagType == TagType.OPENING ) {
								stackElem++;
								stackType++;
							}
							else if ( codes.get(j).tagType == TagType.CLOSING ) {
								stackElem--;
								stackType--;
								if ( fixupMode ) { // Searching for closing code after overlapping is detected
									//if (( candidate == -1 ) && ( closingIds[j] == -1 )) candidate = j;
									if (( stackType == 0 ) && ( closingIds[j] != -9999 )) {
										candidate = j;
										// Stop now, but do not set this as 'found' 
										break;
									}
									continue;
								}
								// Else: Normal process
								if ( stackElem == 0 ) {
									if (( stackType == 0 ) && ( closingIds[j] != -9999 )) {
										codes.get(j).id = code.id;
										// Mark this closing code as used (==-9999)
										closingIds[j] = -9999;
										found = true;
										break;
									}
									// Else: Not the proper element order
									fixupMode = true;
								}
								else if ( stackElem > 0 ) {
									// Remember possible closing code candidate in case of overlapping
									if (( stackType == 0 ) && ( closingIds[j] != -9999 )) {
										candidate = j;
									}
								}
								else { // stack < 0 (we are past the proper spot)
									// Do we have a candidate available?
									if ( candidate != -1 ) break; // Stop now, but do not set this as 'found'
									// Starting with this one
									if (( stackType == 0 ) && ( closingIds[j] != -9999 )) {
										candidate = j;
										break;
									}
									// Else: no candidate yet, try to find one
									fixupMode = true; 
								}
							}
						}
						else { // Different types
							if ( codes.get(j).tagType == TagType.OPENING ) stackElem++;
							else if ( codes.get(j).tagType == TagType.CLOSING ) stackElem--;
							if ( stackElem == 0 ) {
								fixupMode = true;
							}
						}
					}
					// Now set the result
					if ( found ) {
						text.setCharAt(i, (char)MARKER_OPENING);
					}
					else {
						if ( candidate != -1 ) {
							codes.get(candidate).id = code.id;
							closingIds[candidate] = -88;
						}
						text.setCharAt(i, (char)MARKER_ISOLATED);
					}
					break;
				case CLOSING:
					// If id in closingIds is -9999: it has been matched (and therefore has id)
					if ( closingIds[index] == -9999 ) {
						text.setCharAt(i, (char)MARKER_CLOSING);
					}
					// If id in closingIds is -1: it has not been matched and as no id
					else if ( closingIds[index] == -1 ) {
						text.setCharAt(i, (char)MARKER_ISOLATED);
						code.id = ++lastCodeID;
					}
					// otherwise: it has not been matched but it has an id
					else {
						text.setCharAt(i, (char)MARKER_ISOLATED);
					}
				}
				i++; // Skip index part of the code marker
				break;
			}
		}
		isBalanced = true;
	}

	/**
	 * Aligns the code IDs of this fragment with the ones of a given fragment.
	 * This method re-assigns the IDs of the in-line codes of this fragment based on the
	 * code data of the provided fragment. If there is a code with the same data, then 
	 * prefer the first code as this is the matching target code in the majority of cases.
	 * An example of usage is when source and target fragments have codes generated
	 * from regular expressions and not in the same order.
	 * For example if the source is <code>%d equals %s</code> and the target is
	 * <code>%s equals %d</code> and <code>%s</code> and <code>%d</code> are codes.
	 * You want their IDs to match for the code with the same content.
	 * @param base the fragment to use as the base for the synchronization.
	 */
	public void alignCodeIds (TextFragment base) {
		if ( !base.hasCode() ) return;
		if ( codes == null ) return; // No codes in this fragment
		List<Code> toUse = new ArrayList<Code>(base.getCodes());
		Code srcCode;
		Code candidate;
		isBalanced = false;		
		lastCodeID = 0;
		
		boolean needExtra = false;
		
		// FIXME: Do we need to balance the codes here?
		// Uncommenting seems to cause errors during some file merges
		//balanceMarkers();
		
		for ( Code trgCode : codes ) {
			// Closing codes are aligned by the balancing
			if ( trgCode.tagType == TagType.CLOSING ) continue;
			
			// Get the first non-closing source code
			srcCode = null;
			while ( !toUse.isEmpty() ) {
				srcCode = toUse.get(0); // First available is the normal match
				if ( srcCode.tagType == TagType.CLOSING ) {
					toUse.remove(0);
				}
				else break;
			}
			if ( srcCode == null ) continue; // No more source
			
			// some formats may drop the code data (e.g., xliff with placeHolderMode=true)
            // in that case we can't compare code data to align codes
			// we assume codes are already aligned and continue
            if (Util.isEmpty(srcCode.getData()) || Util.isEmpty(trgCode.getData())) {
                continue;
            }
            
            // FIXME: remove whitespace because the code data gotten from XLIFF
    		// may be normalized
    		String srcData = StringUtil.removeWhiteSpace(srcCode.getData());
    		String trgData = StringUtil.removeWhiteSpace(trgCode.getData());
			
			// Check if source is same content and same ID
			if (trgData.equals(srcData) && (trgCode.getId() == srcCode.getId()))
			{
				// First source code has the same content and ID
				toUse.remove(0); // Remove it from the list of available codes
				if (lastCodeID < trgCode.getId()) lastCodeID = trgCode.getId();
				continue;
			}
			
			// Else: Content or ID is different
			// Try to find the first same content
			boolean found = false;
			for ( int i=0; i<toUse.size(); i++ ) {
				candidate = toUse.get(i);
				String candidateData = StringUtil.removeWhiteSpace(candidate.getData());
				if (trgData.equals(candidateData)) {
					trgCode.setId(candidate.getId());
					toUse.remove(i);
					found = true;
					if ( lastCodeID < trgCode.getId() ) lastCodeID = trgCode.getId();
					break;
				}
			}
			if ( !found ) { // Extra target code
				if (!Util.isEmpty(trgCode.getData())) {
					trgCode.setId(-1);
					needExtra = true;
				}
			}
		}
		
		// If needed, set the IDs of non-closing non-assigned codes
		if ( needExtra ) {
			for ( Code trgCode : codes ) {
				if (Util.isEmpty(trgCode.getData())) {
					// If the target code has no data, and there is a source code with the same Id,
					// copy data from src code to target code
					Code code = base.findCode(trgCode.getId());
					if (code != null)
						trgCode.setData(code.getData());
				}
				else 
					if ( trgCode.tagType != TagType.CLOSING ) {
					if ( trgCode.getId() == -1 ) {
						trgCode.setId(++lastCodeID);
					}
				}
			}
		}
	}

	/**
	 * Appends a character to the fragment.
	 * @param value the character to append.
	 * @return a reference to this fragment.
	 */
	@Override
	public TextFragment append (char value) {
		text.append(value);
		return this;
	}

	/**
	 * Appends the specified character sequence to this fragment.
	 * @param csq the character sequence to append.
	 * If the parameter is null, the string "null" is appended.
	 * @return a reference to this fragment.
	 */
	@Override
	public TextFragment append (CharSequence csq) {
		text.append(csq);
		return this;
	}

	/**
	 * Appends a subsequence of the specified character sequence to this fragment.
	 * @param csq the character sequence to append.
	 * If csq is null, then characters will be appended as if csq contained the string "null".
	 * @param start the index of the first character in the subsequence.
	 * @param end the index of the character following the last character in the subsequence.
	 * @return a reference to this fragment.
	 */
	@Override
	public TextFragment append (CharSequence csq,
		int start,
		int end)
	{
		if ( csq == null ) csq = "null";
		return append(csq.subSequence(start, end));
	}

	/**
	 * Returns the character at the specified index in the coded text of this fragment.
	 * Each code in the coded text string take 2 characters, regardless of the size of the code. 
	 * <p>For example: If the fragment is "A[xy]B" and "[xy]" is a code, charAt(3) returns 'B' not 'x'.
	 * <p>If the specified index falls on a code placeholder, the character returned is either a marker
	 * (first character of the placeholder) or a special index to access the underlying code (second
	 * character of the placeholder). Markers can be identified using {@link #isMarker(char)}.
	 * @param index the index of the character to be returned.
	 * @return the specified character.
	 * @throws IndexOutOfBoundsException if the if the index argument is negative or not less than the length
	 * of the coded text.
	 * @see #isMarker(char)
	 */
	public char charAt (int index) {
		return text.charAt(index);
	}

	/**
	 * Returns the number of character in the coded text of this fragment.
	 * <p>This is not the length of the content with all its codes. In the coded text,
	 * each code is represented by a placeholder made of two characters regardless
	 * of the size of the code. For example: If the fragment is "A[xy]B" and "[xy]" is a 
	 * code, length() returns 4, not 6.
	 * <p>To get the length of the content including codes use <code>{@link #toText()}.length()</code>.
	 * Note that codes with referenced are not expanded by {@link #toText()}.
	 * @return the number of character in the coded text of this fragment.
	 */
	public int length () {
		return text.length();
	}

	/**
	 * Sets the fragment in a state where it has to be re-balanced before being used for output.
	 * This method is not harmful, but should preferably be used only when adding unbalanced paired codes. 
	 */
	public void invalidate () {
		isBalanced = false;
	}

	/**
	 * Gets the last value used for code id.
	 * @return the last value used for code id.
	 */
	public int getLastCodeId () {
		if ( !isBalanced ) balanceMarkers();
		return lastCodeID;
	}

	/**
	 * Return the last code appended to this fragment, or null if there are no codes.
	 * @param id
	 * @return code, or null
	 */
	public Code getLastCode() {
		return codes.isEmpty() ? null : codes.get(codes.size() - 1);
	}

	public Code findCode(int id) {
		for (Code code : codes) {
			if (code.getId() == id) {
				return code;
			}
		}
		return null;
	}
	
}
