/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.basistech.lucene.tools;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LuceneQueryToolTest {
    private static DirectoryReader reader;
    private static boolean showOutput;

    @BeforeClass
    public static void oneTimeSetup() throws IOException, ParseException {
        LuceneQueryToolTest.showOutput = false;  // for debugging tests
        Directory dir = new RAMDirectory();
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_45);
        IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_45, analyzer);
        IndexWriter writer = new IndexWriter(dir, config);
        Document doc = new Document();
        doc.add(new Field("longest-mention", "Bill Clinton", StringField.TYPE_STORED));
        doc.add(new Field("context", "Hillary Clinton Arkansas", TextField.TYPE_NOT_STORED));
        writer.addDocument(doc);
        doc = new Document();
        doc.add(new Field("longest-mention", "George W. Bush", StringField.TYPE_STORED));
        doc.add(new Field("context", "Texas Laura Bush", TextField.TYPE_NOT_STORED));
        writer.addDocument(doc);
        doc = new Document();
        doc.add(new Field("longest-mention", "George H. W. Bush", StringField.TYPE_STORED));
        doc.add(new Field("context", "Barbara Bush Texas", TextField.TYPE_NOT_STORED));
        writer.addDocument(doc);
        doc = new Document();
        doc.add(new Field("bbb", "foo", StringField.TYPE_STORED));
        doc.add(new Field("bbb", "bar", StringField.TYPE_STORED));
        doc.add(new Field("aaa", "foo", StringField.TYPE_STORED));
        FieldType typeUnindexed = new FieldType(StringField.TYPE_STORED);
        typeUnindexed.setIndexed(false);
        doc.add(new Field("zzz", "foo", typeUnindexed));
        writer.addDocument(doc);
        writer.close();
        reader = DirectoryReader.open(dir);
    }

    private List<String> getOutput(ByteArrayOutputStream bytes) throws UnsupportedEncodingException {
        List<String> lines = Lists.newArrayList();
        for (String line : bytes.toString("UTF-8").split(System.getProperty("line.separator"))) {
            if (showOutput) {
                System.out.println(line);
            }
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private boolean hasLineStartingWith(String prefix, List<String> lines) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testQueryAll() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader);
        lqt.run(new String[]{"%all"});
        List<String> lines = getOutput(bytes);
        assertTrue(lines.contains("longest-mention: Bill Clinton"));
        assertTrue(lines.contains("longest-mention: George W. Bush"));
        assertTrue(lines.contains("longest-mention: George H. W. Bush"));
    }

    @Test
    public void testQueryStoredStringField() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"longest-mention:Bill\\ Clinton"});
        List<String> lines = getOutput(bytes);
        assertTrue(lines.contains("longest-mention: Bill Clinton"));
    }

    @Test
    public void testQueryStoredStringFieldWrongCase() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"longest-mention:bill\\ clinton"});
        List<String> lines = getOutput(bytes);
        assertEquals(0, lines.size());
    }

    @Test
    public void testQueryAnalyzedField() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"context:bush"});
        List<String> lines = getOutput(bytes);
        assertEquals(2, lines.size());
        assertTrue(lines.contains("longest-mention: George W. Bush"));
        assertTrue(lines.contains("longest-mention: George H. W. Bush"));
    }

    @Test
    public void testDefaultField() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setDefaultField("context");
        lqt.run(new String[]{"bush"});
        List<String> lines = getOutput(bytes);
        assertEquals(2, lines.size());
        assertTrue(lines.contains("longest-mention: George W. Bush"));
        assertTrue(lines.contains("longest-mention: George H. W. Bush"));
    }

    @Test
    public void testQueryLimit() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setShowHits(true);
        lqt.setQueryLimit(1);
        lqt.run(new String[]{"context:bush"});
        List<String> lines = getOutput(bytes);
        assertEquals(2, lines.size());
        assertTrue(lines.contains("totalHits: 2"));
        assertTrue(lines.contains("longest-mention: George W. Bush"));
    }

    @Test
    public void testOutputLimit() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setOutputLimit(1);
        lqt.setShowId(true);
        lqt.setRegex("aaa", Pattern.compile("foo"));
        lqt.run(new String[]{"%all"});
        List<String> lines = getOutput(bytes);
        assertTrue(lines.contains("<id>: 3"));
    }

    @Test
    public void testSortFields() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setSortFields(true);
        lqt.run(new String[]{"%ids", "3"});
        List<String> lines = getOutput(bytes);
        assertEquals("aaa: foo", lines.get(0));
        assertEquals("bbb: bar", lines.get(1));
        assertEquals("bbb: foo", lines.get(2));
    }

    @Test
    public void testShowId() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setShowId(true);
        lqt.run(new String[]{"%all"});
        List<String> lines = getOutput(bytes);
        assertTrue(hasLineStartingWith("<id>:", lines));
    }

    @Test
    public void testShowHits() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setShowHits(true);
        lqt.run(new String[]{"%all"});
        List<String> lines = getOutput(bytes);
        assertTrue(hasLineStartingWith("totalHits:", lines));
    }

    @Test
    public void testShowScore() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setShowScore(true);
        lqt.run(new String[]{"%all"});
        List<String> lines = getOutput(bytes);
        assertTrue(hasLineStartingWith("<score>:", lines));
    }

    @Test
    public void testQueryIds() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setShowId(true);
        lqt.run(new String[]{"%ids", "0", "1", "2"});
        List<String> lines = getOutput(bytes);
        assertTrue(lines.contains("<id>: 0"));
        assertTrue(lines.contains("<id>: 1"));
        assertTrue(lines.contains("<id>: 2"));
    }

    @Test
    public void testRegex() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setRegex("longest-mention", Pattern.compile(".*Clint.*"));
        lqt.run(new String[]{"%all"});
        List<String> lines = getOutput(bytes);
        assertTrue(lines.contains("longest-mention: Bill Clinton"));
    }

    @Test
    public void testQueryCasedLuceneRegex() throws IOException, ParseException {
        // Requires queryParser.setLowercaseExpandedTerms(false), otherwise
        // can't match uppercase with regex on unanalyzed field!
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"longest-mention:/Bill C.*/"});
        List<String> lines = getOutput(bytes);
        assertTrue(lines.contains("longest-mention: Bill Clinton"));
    }

    @Test
    public void testFields() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setFieldNames(Lists.newArrayList("bbb"));
        lqt.run(new String[]{"%ids", "3"});
        List<String> lines = getOutput(bytes);
        assertEquals(2, lines.size());
        assertTrue(lines.contains("bbb: foo"));
        assertTrue(lines.contains("bbb: bar"));
        // "aaa: foo" should be missing
    }

    @Test
    public void testTabular() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setFieldNames(Lists.newArrayList("longest-mention"));
        lqt.setTabular(true);
        lqt.run(new String[]{"%ids", "0"});
        List<String> lines = getOutput(bytes);
        assertEquals(2, lines.size());
        assertEquals("longest-mention", lines.get(0));
        assertEquals("Bill Clinton", lines.get(1));
    }

    @Test
    public void testSuppressNames() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setFieldNames(Lists.newArrayList("longest-mention"));
        lqt.setTabular(true);
        lqt.setSuppressNames(true);
        lqt.run(new String[]{"%ids", "0"});
        List<String> lines = getOutput(bytes);
        assertEquals(1, lines.size());
        assertEquals("Bill Clinton", lines.get(0));
    }

    @Test
    public void testEnumerateFields() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"%enumerate-fields"});
        List<String> lines = getOutput(bytes);
        assertEquals(5, lines.size());
        assertTrue(lines.contains("aaa"));
        assertTrue(lines.contains("bbb"));
        assertTrue(lines.contains("context"));
        assertTrue(lines.contains("longest-mention"));
        assertTrue(lines.contains("zzz"));
    }

    @Test
    public void testCountFields() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"%count-fields"});
        List<String> lines = getOutput(bytes);
        assertEquals(5, lines.size());
        assertTrue(lines.contains("aaa: 1"));
        assertTrue(lines.contains("bbb: 1"));
        assertTrue(lines.contains("context: 3"));
        assertTrue(lines.contains("longest-mention: 3"));
        assertTrue(lines.contains("zzz: 0"));  // 0 because not indexed
    }

    @Test
    public void testEnumerateTerms() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"%enumerate-terms", "context"});
        List<String> lines = getOutput(bytes);
        assertEquals(7, lines.size());
        assertTrue(lines.contains("arkansas (1)"));
        assertTrue(lines.contains("barbara (1)"));
        assertTrue(lines.contains("bush (2)"));
        assertTrue(lines.contains("clinton (1)"));
        assertTrue(lines.contains("hillary (1)"));
        assertTrue(lines.contains("laura (1)"));
        assertTrue(lines.contains("texas (2)"));
    }

    @Test
    public void testSetOutput() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader);
        lqt.setOutputStream(out);
        lqt.run(new String[]{"%all"});
        assertTrue(getOutput(bytes).size() > 0);
    }

    @Test
    public void testScript() throws Exception {
        List<String> lines = Lists.newArrayList(
            "-q context:bush -o target/out1",
            "-q context:clinton -o target/out2");
        FileUtils.writeLines(new File("target/script.txt"), lines);
        LuceneQueryTool lqt = new LuceneQueryTool(reader);
        lqt.run(new String[]{"%script", "target/script.txt"});
        lines = FileUtils.readLines(new File("target/out1"), "UTF-8");
        assertTrue(Joiner.on(",").join(lines).contains("George"));
        lines = FileUtils.readLines(new File("target/out2"), "UTF-8");
        assertTrue(Joiner.on(",").join(lines).contains("Bill"));
    }

    @Test(expected = RuntimeException.class)
    public void testTabularMultivalued() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setFieldNames(Lists.newArrayList("bbb"));
        lqt.setTabular(true);
        lqt.run(new String[]{"%ids", "3"});
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidAnalyzer() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setAnalyzer("InvalidAnalyzer");
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidDefaultField() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setDefaultField("longest-mentionn");
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidFields() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setFieldNames(Lists.newArrayList("longest-mentionn"));
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidQuery() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"longest-mentionn:Bill\\ Clinton"});
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidRegex() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setRegex("longest-mentionn", Pattern.compile(".*Clint"));
    }

    @Test(expected = RuntimeException.class)
    public void testRegexWithMissingField() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.setFieldNames(Lists.newArrayList("context"));
        lqt.setRegex("longest-mention", Pattern.compile(".*Clint.*"));
    }

    @Test(expected = RuntimeException.class)
    public void testUnindexedEnumerateTerms() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"%enumerate-terms", "zzz"});
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidEnumerateTerms() throws IOException, ParseException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        LuceneQueryTool lqt = new LuceneQueryTool(reader, out);
        lqt.run(new String[]{"%enumerate-terms", "longest-mentionn"});
    }
}
