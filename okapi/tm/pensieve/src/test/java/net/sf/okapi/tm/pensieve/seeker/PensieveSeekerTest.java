/*===========================================================================
Copyright (C) 2008-2009 by the Okapi Framework contributors
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
package net.sf.okapi.tm.pensieve.seeker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.exceptions.OkapiIOException;
import net.sf.okapi.common.resource.TextFragment;
import net.sf.okapi.common.resource.TextFragment.TagType;
import net.sf.okapi.tm.pensieve.Helper;
import net.sf.okapi.tm.pensieve.common.Metadata;
import net.sf.okapi.tm.pensieve.common.MetadataType;
import net.sf.okapi.tm.pensieve.common.TmHit;
import net.sf.okapi.tm.pensieve.common.TranslationUnit;
import net.sf.okapi.tm.pensieve.common.TranslationUnitField;
import net.sf.okapi.tm.pensieve.common.TranslationUnitVariant;
import net.sf.okapi.tm.pensieve.writer.PensieveWriter;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * User: Christian Hargraves
 * Date: Aug 17, 2009
 * Time: 1:04:24 PM
 * 
 * @author HARGRAVEJE
 */
@RunWith(JUnit4.class)
public class PensieveSeekerTest {

    static final Directory DIR = new RAMDirectory();
    static final TranslationUnitVariant TARGET = new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("target text"));
    static final String STR = "watch out for the killer rabbit";
    PensieveSeeker seeker;
    List<TmHit> tmhits;

    @Before
    public void setUp() throws FileNotFoundException {
        seeker = new PensieveSeeker(DIR);
    }

    @After
    public void tearDown() {
        seeker.close();
    }

    @Test
    public void shortEntries () throws Exception {
        PensieveWriter writer = getWriter();
        writer.indexTranslationUnit(new TranslationUnit(
           	new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("abcd")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(
           	new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("abc")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(
            new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("am")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(
                new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("zq")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(
                new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("zqq")), TARGET));
        writer.close();

        List<TmHit> list;
        list = seeker.searchFuzzy(new TextFragment("abcd"), 100, 1, null);
        assertEquals("number of docs found", 1, list.size());
        
        list = seeker.searchFuzzy(new TextFragment("abc"), 100, 1, null);
        assertEquals("number of docs found", 1, list.size());
        
        list = seeker.searchFuzzy(new TextFragment("zqq"), 100, 1, null);
        assertEquals("number of docs found", 1, list.size());

        list = seeker.searchFuzzy(new TextFragment("am"), 100, 1, null);
        assertEquals("number of docs found", 1, list.size());
        
        list = seeker.searchFuzzy(new TextFragment("zq"), 100, 1, null);
        assertEquals("number of docs found", 1, list.size());        
    }

    @Test
    public void penaltyDifferentSpaces () throws Exception {
        PensieveWriter writer = getWriter();
        writer.indexTranslationUnit(new TranslationUnit(
           	new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("abcdef")), TARGET));
        writer.close();

        List<TmHit> list = seeker.searchFuzzy(new TextFragment("abCdef"), 100, 1, null);
        assertEquals("number of docs found", 0, list.size());
    }

    @Test
    public void translationUnitIterator() throws Exception {
        PensieveWriter writer = getWriter();
        populateIndex(writer, 12, "patents are evil", "unittest");
        writer.close();

        Iterator<TranslationUnit> tuIterator = seeker.iterator();
        List<TranslationUnit> tus = new ArrayList<TranslationUnit>();
        while (tuIterator.hasNext()) {
            tus.add(tuIterator.next());
        }
        assertEquals("number of tus", 13, tus.size());
        assertEquals("first document", "patents are evil0", tus.get(0).getSource().getContent().toText());
        assertEquals("second document", "patents are evil1", tus.get(1).getSource().getContent().toText());
    }

    @Test
    public void translationUnitIteratorNextCallOnEmpty() throws Exception {
        PensieveWriter writer = getWriter();
        populateIndex(writer, 1, "patents are evil", "unittest");
        writer.close();

        Iterator<TranslationUnit> tuIterator = seeker.iterator();
        TranslationUnit tu;
        tuIterator.next();
        tu = tuIterator.next();
        assertNotNull(tu);
        assertFalse(tuIterator.hasNext());
        assertNull(tuIterator.next());
    }

    @Test(expected = OkapiIOException.class)
    public void iteratorInstantiationHandleIOException() throws IOException {
        PensieveSeeker spy = spy(seeker);
        doThrow(new IOException("some exception")).when(spy).openIndexReader();
        spy.iterator();
    }

    @Test(expected = OkapiIOException.class)
    public void iteratorInstantiationHandleCorruptedIndexException() throws IOException {
        PensieveSeeker spy = spy(seeker);
        doThrow(new CorruptIndexException("some exception")).when(spy).openIndexReader();
        spy.iterator();
    }

    @Test(expected = OkapiIOException.class)
    public void iteratorNextIOException() throws Exception {
        PensieveWriter writer = getWriter();
        populateIndex(writer, 1, "patents are evil", "unittest");
        writer.close();

        Iterator<TranslationUnit> iterator = seeker.iterator();

        IndexReader mockIndexReader = mock(IndexReader.class);
        doThrow(new IOException("some exception")).when(mockIndexReader).document(anyInt());
        Helper.setPrivateMember(iterator, "ir", mockIndexReader);

        iterator.next();
    }

    @Test(expected = OkapiIOException.class)
    public void iteratorNextCorruptedIndexException() throws Exception {
        PensieveWriter writer = getWriter();
        populateIndex(writer, 1, "patents are evil", "unittest");
        writer.close();

        Iterator<TranslationUnit> iterator = seeker.iterator();

        IndexReader mockIndexReader = mock(IndexReader.class);
        doThrow(new CorruptIndexException("some exception")).when(mockIndexReader).document(anyInt());
        Helper.setPrivateMember(iterator, "ir", mockIndexReader);

        iterator.next();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void iteratorUnsupportedRemove() throws IOException {
        seeker.iterator().remove();
    }

    @Test
    public void getDirectory() {
        assertSame("directory", DIR, seeker.getIndexDir());
    }

    @Test
    public void getFieldValueNoField() {
        Document doc = new Document();
        assertNull("Null should be returned for an empty field", seeker.getFieldValue(doc, TranslationUnitField.SOURCE));
    }

    @Test
    public void getFieldValue() {
        Document doc = new Document();
        doc.add(new Field(TranslationUnitField.SOURCE.name(), "lk", Field.Store.NO, Field.Index.NOT_ANALYZED));
        assertEquals("source field", "lk", seeker.getFieldValue(doc, TranslationUnitField.SOURCE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorNullIndexDir() {
        new PensieveSeeker((Directory) null);
    }    

    @Test
    public void searchFuzzyMiddleMatch() throws Exception {
        PensieveWriter writer = getWriter();


        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(STR)), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch for the killer rabbit")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch out the killer rabbit")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch rabbit")), TARGET));

        writer.close();
        tmhits = seeker.searchFuzzy(new TextFragment(STR), 80, 10, null);
        assertEquals("number of docs found", 3, tmhits.size());
    }

    @Test
    public void searchOnInlineCodes () throws Exception {
        PensieveWriter writer = getWriter();
        TextFragment tf = new TextFragment("ABC");
        tf.append(TagType.PLACEHOLDER, "br", "[br/]");
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), tf), TARGET));
        writer.close();
        tmhits = seeker.searchFuzzy(tf, 99, 10, null);
        assertEquals(1, tmhits.size());
    }

    @Test
    public void searchOnNoiseAndShortWords () throws Exception {
        PensieveWriter writer = getWriter();
        String WORD = "from";
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"),
        	new TextFragment(WORD)), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"),
        	new TextFragment("help")), TARGET));
        writer.close();

        tmhits = seeker.searchFuzzy(new TextFragment("Help"), 80, 10, null);
        assertEquals(1, tmhits.size());
        assertEquals("help", tmhits.get(0).getTu().getSource().getContent().toText());
        tmhits = seeker.searchFuzzy(new TextFragment(WORD), 80, 10, null);
        assertEquals(1, tmhits.size());
        assertEquals("from", tmhits.get(0).getTu().getSource().getContent().toText());
    }

//    @Test
//    public void searchOnNoiseAndVeryShortWords () throws Exception {
//        PensieveWriter writer = getWriter();
//        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"),
//        	new TextFragment("of")), TARGET));
//        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"),
//        	new TextFragment("a")), TARGET));
//        writer.close();
//
//        tmhits = seeker.searchFuzzy(new TextFragment("a"), 80, 10, null);
//        assertEquals(1, tmhits.size());
//        assertEquals("a", tmhits.get(0).getTu().getSource().getContent().toText());
//        tmhits = seeker.searchFuzzy(new TextFragment("of"), 80, 10, null);
//        assertEquals(1, tmhits.size());
//        assertEquals("of", tmhits.get(0).getTu().getSource().getContent().toText());
//    }

    @Test
    public void searchFuzzyWordOrder80Percent() throws Exception {
        PensieveWriter writer = getWriter();

        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch rabbit")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(STR)), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("rabbit killer the for out watch")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch for the killer rabbit")), TARGET));

        writer.close();
        tmhits = seeker.searchFuzzy(new TextFragment(STR), 80, 10, null);
        assertEquals("number of docs found", 2, tmhits.size());
        assertEquals("1st match", "watch out for the killer rabbit", tmhits.get(0).getTu().getSource().getContent().toText());
        assertEquals("2nd match", "watch for the killer rabbit", tmhits.get(1).getTu().getSource().getContent().toText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void searchFuzzyThresholdGreaterThan100() throws Exception {
        PensieveWriter writer = getWriter();

        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch rabbit")), TARGET));
        writer.close();
        seeker.searchFuzzy(new TextFragment(STR), 101, 10, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void searchFuzzyThresholdLessThan0() throws Exception {
        PensieveWriter writer = getWriter();

        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch rabbit")), TARGET));
        writer.close();
        seeker.searchFuzzy(new TextFragment(STR), -1, 10, null);
    }

    @Test
    public void searchFuzzyMiddleMatch80Percent() throws Exception {
        PensieveWriter writer = getWriter();

        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch rabbit")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(STR)), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch out the killer rabbit and some extra stuff")), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch for the killer rabbit")), TARGET));

        writer.close();
        tmhits = seeker.searchFuzzy(new TextFragment(STR), 80, 10, null);
        assertEquals("number of docs found", 2, tmhits.size());
        assertEquals("1st match", "watch out for the killer rabbit", tmhits.get(0).getTu().getSource().getContent().toText());
        assertEquals("2nd match", "watch for the killer rabbit", tmhits.get(1).getTu().getSource().getContent().toText());
    }

    @Test
    public void searchFuzzy80PercentWithMetadata() throws Exception {
        PensieveWriter writer = getWriter();

        writer.indexTranslationUnit(Helper.createTU(LocaleId.fromString("EN"), LocaleId.fromString("KR"), "watch rabbit", "something that is the same", "1", "some_file", "some_group", "nachotype"));
        writer.indexTranslationUnit(Helper.createTU(LocaleId.fromString("EN"), LocaleId.fromString("KR"), STR, "something that is the same", "2", "some_file", "some_group", "nachotype"));
        writer.indexTranslationUnit(Helper.createTU(LocaleId.fromString("EN"), LocaleId.fromString("KR"), "watch out the killer rabbit and some extra stuff", "something that is the same", "3", "some_file", "some_group", "nachotype"));
        writer.indexTranslationUnit(Helper.createTU(LocaleId.fromString("EN"), LocaleId.fromString("KR"), "watch for the killer rabbit", "something that is the same", "4", "some_file", "some_group", "nachotype"));
        writer.indexTranslationUnit(Helper.createTU(LocaleId.fromString("EN"), LocaleId.fromString("KR"), "watch for the killer rabbit", "something that is the same", "5", "nacho_file", "some_group", "nachotype"));

        writer.close();
        Metadata md = new Metadata();
        md.put(MetadataType.FILE_NAME, "some_file");
        md.put(MetadataType.GROUP_NAME, "some_group");
        md.put(MetadataType.TYPE, "nachotype");
        tmhits = seeker.searchFuzzy(new TextFragment(STR), 80, 10, md);
        assertEquals("number of docs found", 2, tmhits.size());
        assertEquals("1st match", "watch out for the killer rabbit", tmhits.get(0).getTu().getSource().getContent().toText());
        assertEquals("2nd match", "watch for the killer rabbit", tmhits.get(1).getTu().getSource().getContent().toText());
    }

    @Test
    public void searchFuzzyScoreSortNoFuzzyThreshold() throws Exception {
        PensieveWriter writer = getWriter();
        String[] testStrings = {STR,
            STR + " 1",
            STR + " 2 words",
            STR + " 3 words now"
        };

        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(testStrings[0])), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(testStrings[1])), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(testStrings[2])), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(testStrings[3])), TARGET));
        writer.close();
        //If you add a threshold it changes the sort order
        tmhits = seeker.searchFuzzy(new TextFragment(STR), 0, 10, null);

        assertEquals("number of docs found", 4, tmhits.size());
        assertEquals("first match", testStrings[0], tmhits.get(0).getTu().getSource().getContent().toText());

        //Verify sort order
        Float previous = tmhits.get(0).getScore();

        for (int i = 1; i < tmhits.size(); i++) {
            Float currentScore = tmhits.get(i).getScore();
            assertEquals(i + " match", testStrings[i], tmhits.get(i).getTu().getSource().getContent().toText());
            assertTrue("results should be sorted descending by score", currentScore < previous);
            previous = currentScore;
        }
    }

    @Test
    public void searchFuzzyEndMatch() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch out for the killer rabbit";

        final int numOfIndices = 9;

        populateIndex(writer, numOfIndices, str, "two");

        writer.close();
        tmhits = seeker.searchFuzzy(new TextFragment(str), 0, 10, null);
        assertEquals("number of docs found", 9, tmhits.size());
    }

    @Test
    public void searchExactSingleMatch() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch out for the killer rabbit";

        final int numOfIndices = 18;

        populateIndex(writer, numOfIndices, str, "two");

        writer.close();
        //Fuzzy or phrase matching would return "watch out for the killer rabbit1" & "watch out for the killer rabbit11"
        tmhits = seeker.searchExact(new TextFragment(str + 1), null);
        assertEquals("number of docs found", 1, tmhits.size());
    }

    @Test
    public void searchExactSingleMatchWithMetadata() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch out for the killer rabbit";

        final int numOfIndices = 18;
        populateIndex(writer, numOfIndices, str, "two", "ID", "FileORama", "groupie", "singletype");
        writer.close();
        Metadata metadata = new Metadata();
        metadata.put(MetadataType.ID, "ID1");
        tmhits = seeker.searchExact(new TextFragment(str), metadata);
        assertEquals("number of docs found", 1, tmhits.size());
    }

    @Test
    public void searchExactMultipleMatchesWithMetadata() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch out for the killer rabbit";

        final int numOfIndices = 18;
        populateIndex(writer, numOfIndices, str, "two", "ID", "FileORama", "groupie", "singletype");
        populateIndex(writer, 5, str, "two", "ID", "ORama", "groupx", "nachotype");
        writer.close();
        Metadata metadata = new Metadata();
        metadata.put(MetadataType.TYPE, "nachotype");
        tmhits = seeker.searchExact(new TextFragment(str), metadata);
        assertEquals("number of docs found", 1, tmhits.size());
    }

    @Test
    public void searchExactMultipleMatches() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch out for the killer rabbit";
        for (int i = 0; i < 5; i++) {
            writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str)), TARGET));
        }

        writer.close();
        tmhits = seeker.searchExact(new TextFragment(str), null);
        assertEquals("number of docs found", 1, tmhits.size());
    }

    @Test
    public void searchExactDifferentStopWords() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch out for the killer rabbit";
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str)), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch out for the the killer rabbit")), TARGET));

        writer.close();
        tmhits = seeker.searchExact(new TextFragment(str), null);
        assertEquals("number of docs found", 1, tmhits.size());
    }
    
    @Test
    public void searchNoHits() throws Exception {
    	 PensieveWriter writer = getWriter();
         String str = "watch out for the killer rabbit";
         writer.close();
         tmhits = seeker.searchExact(new TextFragment(str), null);
         assertNotNull(tmhits);
         assertEquals("number of docs found", 0, tmhits.size());
    }
    
    @Test public void searchNoScoreOver100() throws Exception {
    	 PensieveWriter writer = getWriter();
         String str = "Consistent with 48 C.F.R. \u00a712.212 or 48 C.F.R. \u00a7\u00a7227.7202-1 through 227.7202-4, as applicable, the Commercial Computer Software and Commercial Computer Software Documentation are being licensed to U.S. Government end users (a) only as Commercial Items and (b) with only those rights as are granted to all other end users pursuant to the terms and conditions herein.";
         writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str)), TARGET));
         writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch out for the the killer rabbit")), TARGET));

         writer.close();
         tmhits = seeker.searchExact(new TextFragment(str), null);
         assertEquals("number of docs found", 1, tmhits.size());
         assertTrue("score over 100%", tmhits.get(0).getScore() == 100);
    }

    @Test
    public void searchExactDifferentCases() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch Out for The killEr rabbit";
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str)), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch out for the the killer rabbit")), TARGET));

        writer.close();
        tmhits = seeker.searchExact(new TextFragment(str), null);
        assertEquals("number of docs found", 1, tmhits.size());
    }

    @Test
    public void searchExactDifferentOrder() throws Exception {
        PensieveWriter writer = getWriter();
        String str = "watch out for the killer rabbit";
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str)), TARGET));
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("watch out for the the killer rabbit")), TARGET));

        writer.close();
        tmhits = seeker.searchExact(new TextFragment("killer rabbit the for out watch"), null);
        assertEquals("number of docs found", 0, tmhits.size());
    }

    @Test
    public void searchExactWithCodes () throws Exception {
    	PensieveWriter writer = getWriter();
    	String str = "watch out for the killer rabbit";
    	TextFragment frag = new TextFragment("watch out for ");
    	frag.append(TagType.OPENING, "b", "<b>");
    	frag.append("the killer");
    	frag.append(TagType.CLOSING, "b", "</b>");
    	frag.append(" rabbit");
    	
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str)), TARGET));
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), frag), TARGET));
    	writer.close();
    	
    	tmhits = seeker.searchExact(frag, null);
    	assertEquals("number of docs found", 1, tmhits.size());
    	assertEquals("watch out for <b>the killer</b> rabbit", tmhits.get(0).getTu().getSource().getContent().toText());
    }
    
    @Test
    public void searchExactWithCodesQueryNoCodes () throws Exception {
    	PensieveWriter writer = getWriter();
    	String str = "watch out for the killer rabbit";
    	TextFragment frag = new TextFragment("watch out for ");
    	frag.append(TagType.OPENING, "b", "<b>");
    	frag.append("the killer");
    	frag.append(TagType.CLOSING, "b", "</b>");
    	frag.append(" rabbit");
    	
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str)), TARGET));
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), frag), TARGET));
    	writer.close();
    	
    	frag = new TextFragment("watch out for the killer rabbit");
    	tmhits = seeker.searchExact(frag, null);
    	assertEquals("number of docs found", 1, tmhits.size());
    	assertEquals("watch out for the killer rabbit", tmhits.get(0).getTu().getSource().getContent().toText());
    }
    
    @Test
    public void searchFuzzyWithCodes () throws Exception {
    	PensieveWriter writer = getWriter();
    	String str1 = "watch out for the killer rabbit";
    	String str2 = "something very different";
    	TextFragment frag = new TextFragment("watch out for ");
    	frag.append(TagType.OPENING, "b", "<b>");
    	frag.append("the killer");
    	frag.append(TagType.CLOSING, "b", "</b>");
    	frag.append(" rabbit");
    	
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str1)), TARGET));
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str2)), TARGET));
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), frag), TARGET));
    	writer.close();
    	
    	tmhits = seeker.searchFuzzy(frag, 5, 10, null);
    	assertEquals("number of docs found", 2, tmhits.size());
    	assertEquals("watch out for <b>the killer</b> rabbit", tmhits.get(0).getTu().getSource().getContent().toText());
    	assertEquals("watch out for the killer rabbit", tmhits.get(1).getTu().getSource().getContent().toText());
    }
    
    @Test
    public void searchSimpleConcordance () throws Exception {
    	PensieveWriter writer = getWriter();
    	String str1 = "watch out for the killer rabbit";
    	String str2 = "something very different about killer rabbits";
    	TextFragment frag = new TextFragment("watch out for ");
    	frag.append(TagType.OPENING, "b", "<b>");
    	frag.append("the killer");
    	frag.append(TagType.CLOSING, "b", "</b>");
    	frag.append(" rabbit");
    	
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str1)), TARGET));
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(str2)), TARGET));
    	writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), frag), TARGET));
    	writer.close();
    	
    	tmhits = seeker.searchSimpleConcordance(" killer rabbits ", 70, 10, null);
    	assertEquals("number of docs found", 3, tmhits.size());
    	assertEquals(str2, tmhits.get(0).getTu().getSource().getContent().toText());
    }
    
    //TODO support metadata
    @Test
    public void getTranslationUnitFields() throws Exception {
        final String source = "watch out for the killer rabbit";
        final String target = "j";
        final LocaleId targetLang = LocaleId.fromString("KR");
        final LocaleId sourceLang = LocaleId.fromString("EN");
        Document doc = new Document();
        doc.add(new Field(TranslationUnitField.SOURCE_EXACT.name(), source,
                Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field(TranslationUnitField.SOURCE.name(), source,
                Field.Store.YES, Field.Index.ANALYZED));
        doc.add(new Field(TranslationUnitField.SOURCE_LANG.name(), sourceLang.toString(),
                Field.Store.YES, Field.Index.ANALYZED));
        doc.add(new Field(TranslationUnitField.TARGET.name(), target,
                Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field(TranslationUnitField.TARGET_LANG.name(), targetLang.toString(),
                Field.Store.YES, Field.Index.ANALYZED));
        TranslationUnit tu = seeker.getTranslationUnit(doc);
        assertEquals("source field", source, tu.getSource().getContent().toText());
        assertEquals("source lang", sourceLang, tu.getSource().getLanguage());
        assertEquals("target field", target, tu.getTarget().getContent().toText());
        assertEquals("target lang", targetLang, tu.getTarget().getLanguage());
    }

    @Test
    public void getTranslationUnitMeta() throws Exception {
        final String source = "watch out for the killer rabbit";
        final String target = "j";
        final String id = "1";
        final String filename = "fname";
        final String groupname = "gname";
        final String type = "typeA";
        final String targetLang = "KR";
        final String sourceLang = "EN";
        Document doc = new Document();
        doc.add(new Field(TranslationUnitField.SOURCE.name(), source,
                Field.Store.YES, Field.Index.ANALYZED));
        doc.add(new Field(TranslationUnitField.SOURCE_LANG.name(), sourceLang,
                Field.Store.YES, Field.Index.ANALYZED));
        doc.add(new Field(TranslationUnitField.TARGET.name(), target,
                Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field(TranslationUnitField.TARGET_LANG.name(), targetLang,
                Field.Store.YES, Field.Index.ANALYZED));
        doc.add(new Field(MetadataType.ID.fieldName(), id,
                Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(MetadataType.FILE_NAME.fieldName(), filename,
                Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(MetadataType.GROUP_NAME.fieldName(), groupname,
                Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(MetadataType.TYPE.fieldName(), type,
                Field.Store.YES, Field.Index.NOT_ANALYZED));
        TranslationUnit tu = seeker.getTranslationUnit(doc);
        assertEquals("id field", id, tu.getMetadata().get(MetadataType.ID));
        assertEquals("filename field", filename, tu.getMetadata().get(MetadataType.FILE_NAME));
        assertEquals("groupname field", groupname, tu.getMetadata().get(MetadataType.GROUP_NAME));
        assertEquals("type field", type, tu.getMetadata().get(MetadataType.TYPE));
    }

    PensieveWriter getWriter() throws Exception {
        return new PensieveWriter(DIR, true);
    }

    void populateIndex(PensieveWriter writer, int numOfEntries, String source, String target) throws Exception {
        for (int i = 0; i < numOfEntries; i++) {
            writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(source + i)), new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment(target))));
        }
        writer.indexTranslationUnit(new TranslationUnit(new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("something that in no way should ever match")), new TranslationUnitVariant(LocaleId.fromString("EN"), new TextFragment("unittesttarget"))));
    }

    void populateIndex(PensieveWriter writer, int numOfEntries, String source, String target, String id,
            String filename, String groupname, String type) throws Exception {
        for (int i = 0; i < numOfEntries; i++) {
            writer.indexTranslationUnit(Helper.createTU(LocaleId.fromString("EN"), LocaleId.fromString("KR"), source, target, id+i, filename, groupname, type));
        }
    }
}
