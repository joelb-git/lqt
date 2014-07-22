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

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.LineIterator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.apache.tools.ant.types.Commandline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <code>LuceneQueryTool</code> is a command line tool for executing Lucene
 * queries and formatting the results.  The usage summary is shown below.
 * Please refer to README.md for complete documentation.
 *
 * <pre>
 * usage: LuceneQueryTool [options]
 *     --analyzer <arg>       for query, (KeywordAnalyzer | StandardAnalyzer)
 *                            (defaults to KeywordAnalyzer)
 *     --fields <arg>         fields to include in output (defaults to all)
 *  -i,--index <arg>          index (required)
 *     --output-limit <arg>   max number of docs to output
 *  -q,--query <arg>          (query | %all | %enumerate-fields |
 *                            %count-fields | %enumerate-terms field | %ids
 *                            id [id ...] | %id-file file) (required)
 *     --query-field <arg>    default field for query
 *     --query-limit <arg>    max number of query hits to process
 *     --regex <arg>          filter query by regex, syntax is field:/regex/
 *     --show-hits            show total hit count
 *     --show-id              show Lucene document id in results
 *     --show-score           show score in results
 *     --sort-fields          sort fields within document
 *     --suppress-names       suppress printing of field names
 *     --tabular              print tabular output (requires --fields with no
 *                            multivalued fields)
 * </pre>
 *
 */
public final class LuceneQueryTool {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private List<String> fieldNames;
    private Set<String> allFieldNames;
    private int queryLimit;
    private int outputLimit;
    private String regexField;
    private Pattern regex;
    private boolean showId;
    private boolean showHits;
    private boolean showScore;
    private boolean sortFields;
    private Analyzer analyzer;
    private String defaultField;
    private boolean tabular;
    private boolean suppressNames;
    private IndexReader indexReader;
    private PrintStream defaultOut;
    private int docsPrinted;

    LuceneQueryTool(IndexReader reader, PrintStream out) throws IOException {
        this.indexReader = reader;
        this.queryLimit = Integer.MAX_VALUE;
        this.outputLimit = Integer.MAX_VALUE;
        this.analyzer = new KeywordAnalyzer();
        this.fieldNames = Lists.newArrayList();
        this.defaultOut = out;
        allFieldNames = Sets.newTreeSet();
        for (FieldInfo fieldInfo : SlowCompositeReaderWrapper.wrap(reader).getFieldInfos()) {
            allFieldNames.add(fieldInfo.name);
        }
    }

    LuceneQueryTool(IndexReader reader) throws IOException {
        this(reader, System.out);
    }

    void setFieldNames(List<String> fieldNames) {
        List<String> invalidFieldNames = Lists.newArrayList();
        for (String field : fieldNames) {
            if (!allFieldNames.contains(field)) {
                invalidFieldNames.add(field);
            }
        }
        if (!invalidFieldNames.isEmpty()) {
            throw new RuntimeException("Invalid field names: " + invalidFieldNames);
        }
        this.fieldNames.addAll(fieldNames);
    }

    void setAnalyzer(String analyzerString) {
        if ("KeywordAnalyzer".equals(analyzerString)) {
            this.analyzer = new KeywordAnalyzer();
        } else if ("StandardAnalyzer".equals(analyzerString)) {
            this.analyzer = new StandardAnalyzer(Version.LUCENE_45);
        } else {
            throw new RuntimeException(
                    String.format("Invalid analyzer %s: %s",
                            analyzerString,
                            "Only KeywordAnalyzer and StandardAnalyzer currently supported"));
        }
    }

    void setQueryLimit(int queryLimit) {
        this.queryLimit = queryLimit;
    }

    void setOutputLimit(int outputLimit) {
        this.outputLimit = outputLimit;
    }

    void setOutputStream(PrintStream out) {
        this.defaultOut = out;
    }

    void setRegex(String regexField, Pattern regex) {
        if (!allFieldNames.contains(regexField)) {
            throw new RuntimeException("Invalid field name: " + regexField);
        }
        if (!fieldNames.isEmpty() && !fieldNames.contains(regexField)) {
            throw new RuntimeException("Attempted to apply regex to field not in results: " + regexField);
        }
        this.regexField = regexField;
        this.regex = regex;
    }

    void setShowId(boolean showId) {
        this.showId = showId;
    }

    void setSortFields(boolean sortFields) {
        this.sortFields = sortFields;
    }

    void setShowHits(boolean showHits) {
        this.showHits = showHits;
    }

    void setShowScore(boolean showScore) {
        this.showScore = showScore;
    }

    void setDefaultField(String defaultField) {
        if (!allFieldNames.contains(defaultField)) {
            throw new RuntimeException("Invalid field name: " + defaultField);
        }
        this.defaultField = defaultField;
    }

    void setSuppressNames(boolean suppressNames) {
        this.suppressNames = suppressNames;
    }

    void setTabular(boolean tabular) {
        this.tabular = tabular;
    }

    void run(String[] queryOpts) throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        run(queryOpts, defaultOut);
    }

    void run(String[] queryOpts, PrintStream out) throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        if (tabular && fieldNames == null) {
            // Unlike a SQL result set, Lucene docs from a single query (or %all) may
            // have different fields, so a tabular format won't make sense unless we
            // know the exact fields beforehand.  Also note that multivalued fields
            // may have a different number of values in each doc, which also won't
            // make sense with tabular output.  We detect that at runtime.
            throw new RuntimeException("--tabular requires --fields to be passed");
        }
        if (sortFields) {
            Collections.sort(fieldNames);
        }
        String opt = queryOpts[0]; 
        if ("%ids".equals(opt)) {
            List<String> ids = Lists.newArrayList(Arrays.copyOfRange(queryOpts, 1, queryOpts.length));
            dumpIds(ids.iterator());
        } else if ("%id-file".equals(opt)) {
            Iterator<String> iterator = new LineIterator(new BufferedReader(
                    new FileReader(queryOpts[1])));
            dumpIds(iterator);
        } else if ("%all".equals(opt)) {
            runQuery(null, out);
        } else if ("%enumerate-fields".equals(opt)) {
            for (String fieldName : allFieldNames) {
                out.println(fieldName);
            }
        } else if ("%count-fields".equals(opt)) {
            countFields();
        } else if ("%enumerate-terms".equals(opt)) {
            if (queryOpts.length != 2) {
                throw new RuntimeException("%enumerate-terms requires exactly one field.");
            }
            enumerateTerms(queryOpts[1]);
        } else if ("%script".equals(opt)) {
            if (queryOpts.length != 2) {
                throw new RuntimeException("%script requires exactly one arg.");
            }
            runScript(queryOpts[1]);
        } else {
            runQuery(queryOpts[0], out);
        }
    }

    // For now, script supports only -q (only simple, no % queries) and -o.
    // Might be nicer, eventually, to have all the other command line opts
    // apply for each script line, overriding the default command line level
    // setting.  But that can come later if we think it's useful.
    void runScript(String scriptPath) throws IOException, ParseException {
        // Sorry, I'm skipping try/finally until I can move to java 1.7 to get
        // try-with-resources.  I think we can move to 1.7 soon.
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(scriptPath), Charsets.UTF_8));
        int lineno = 0;
        String line;
        while ((line = in.readLine()) != null) {
            lineno++;
            if (line.trim().isEmpty()) {
                continue;
            }
            Commandline cl = new Commandline(line);
            PrintStream out = defaultOut;
            String query = null;
            String[] args = cl.getCommandline();
            int i = 0;
            while (i < args.length) {
                String arg = args[i];
                if ("-o".equals(arg) || "-output".equals(arg) || "--output".equals(arg)) {
                    i++;
                    out = new PrintStream(new FileOutputStream(new File(args[i])), true);
                } else if ("-q".equals(arg) || "-query".equals(arg) || "--query".equals(arg)) {
                    i++;
                    query = args[i];
                    if (query.startsWith("%")) {
                        throw new RuntimeException(String.format(
                            "%s:%d: script does not support %% queries", scriptPath, lineno));
                    }
                } else {
                    throw new RuntimeException(String.format(
                        "%s:%d: script supports only -q and -o", scriptPath, lineno));
                }
                i++;
            }
            if (query == null || query.startsWith("%")) {
                throw new RuntimeException(String.format(
                    "%s:%d: script line requires -q", scriptPath, lineno));
            }
            runQuery(query, out);
            if (out != defaultOut) {
                out.close();
            }
        }
        in.close();
    }

    private void dumpIds(Iterator<String> ids) throws IOException {
        docsPrinted = 0;
        while (ids.hasNext()) {
            for (String s : ids.next().split("\\s+")) {
                int id = Integer.parseInt(s);
                Document doc = indexReader.document(id);
                printDocument(doc, id, 1.0f, defaultOut);
            }
        }
    }

    private void enumerateTerms(String field) throws IOException {
        if (!allFieldNames.contains(field)) {
            throw new RuntimeException("Invalid field name: " + field);
        }
        List<AtomicReaderContext> leaves = indexReader.leaves();
        TermsEnum termsEnum = null;
        boolean unindexedField = true;
        Map<String, Integer> termCountMap = new TreeMap<String, Integer>();
        for (AtomicReaderContext leaf : leaves) {
            Terms terms = leaf.reader().terms(field);
            if (terms == null) {
                continue;
            }
            unindexedField = false;
            termsEnum = terms.iterator(termsEnum);
            BytesRef bytesRef;
            while ((bytesRef = termsEnum.next()) != null) {
                String term = bytesRef.utf8ToString();
                if (termCountMap.containsKey(term)) {
                    termCountMap.put(term, termsEnum.docFreq() + termCountMap.get(term));
                } else {
                    termCountMap.put(term, termsEnum.docFreq());
                }
            }
        }
        if (unindexedField) {
            throw new RuntimeException("Unindexed field: " + field);
        }
        for (Map.Entry<String, Integer> entry : termCountMap.entrySet()) {
            defaultOut.println(entry.getKey() + " (" + entry.getValue() + ")");
        }
    }

    private void countFields() throws IOException {
        for (String field : allFieldNames) {
            List<AtomicReaderContext> leaves = indexReader.leaves();
            Map<String, Integer> fieldCounts = new TreeMap<String, Integer>();
            int count = 0;
            for (AtomicReaderContext leaf : leaves) {
                Terms terms = leaf.reader().terms(field);
                if (terms == null) {
                    continue;
                }
                count += terms.getDocCount();
            }
            fieldCounts.put(field, count);
            for (Map.Entry<String, Integer> entry : fieldCounts.entrySet()) {
                defaultOut.println(entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    private void runQuery(String queryString, PrintStream out) throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        docsPrinted = 0;
        Query query;
        if (queryString == null) {
            query = new MatchAllDocsQuery();
        } else {
            if (!queryString.contains(":") && defaultField == null) {
                throw new RuntimeException("query has no ':' and no query-field defined");
            }
            QueryParser queryParser = new QueryParser(Version.LUCENE_45, defaultField, analyzer);
            queryParser.setLowercaseExpandedTerms(false);
            query = queryParser.parse(queryString).rewrite(indexReader);
            Set<Term> terms = Sets.newHashSet();
            query.extractTerms(terms);
            List<String> invalidFieldNames = Lists.newArrayList();
            for (Term term : terms) {
                if (!allFieldNames.contains(term.field())) {
                    invalidFieldNames.add(term.field());
                }
            }
            if (!invalidFieldNames.isEmpty()) {
                throw new RuntimeException("Invalid field names: " + invalidFieldNames);
            }
        }

        IndexSearcher searcher = new IndexSearcher(indexReader);
        TopDocs topDocs = searcher.search(query, queryLimit);
        if (showHits) {
            out.println("totalHits: " + topDocs.totalHits);
            out.println();
        }
        Set<String> fieldSet = Sets.newHashSet(fieldNames);
        for (int i = 0; i < topDocs.scoreDocs.length && docsPrinted < outputLimit; i++) {
            int id = topDocs.scoreDocs[i].doc;
            float score = topDocs.scoreDocs[i].score;
            Document doc = fieldSet.isEmpty() ? searcher.doc(id) : searcher.doc(id, fieldSet);
            boolean passedFilter = regexField == null;
            if (regexField != null) {
                String value = doc.get(regexField);
                if (value != null && regex.matcher(value).matches()) {
                    passedFilter = true;
                }
            }
            if (passedFilter) {
                printDocument(doc, id, score, out);
            }
        }
    }

    private void printDocument(Document doc, int id, float score, PrintStream out) {
        List<IndexableField> fields = doc.getFields();
        if (sortFields) {
            Collections.sort(fields, new Comparator<IndexableField>() {
                @Override
                public int compare(IndexableField o1, IndexableField o2) {
                    int ret = o1.name().compareTo(o2.name());
                    if (ret == 0) {
                        ret = o1.stringValue().compareTo(o2.stringValue());
                    }
                    return ret;
                }
            });
        }

        List<String> names = Lists.newArrayList();
        List<String> values = Lists.newArrayList();
        if (showId) {
            names.add("<id>");
            values.add(Integer.toString(id));
        }
        if (showScore) {
            names.add("<score>");
            values.add(Double.toString(score));
        }
        if (fieldNames.isEmpty()) {
            for (IndexableField f : fields) {
                names.add(f.name());
                values.add(f.stringValue());
            }
        } else {
            for (String name : fieldNames) {
                String[] fieldValues = doc.getValues(name);
                if (fieldValues != null && fieldValues.length != 0) {
                    if (tabular && fieldValues.length > 1) {
                        throw new RuntimeException(
                                String.format("Multivalued field '%s' not allowed with tabular format", name));
                    }
                    for (String value : fieldValues) {
                        names.add(name);
                        values.add(value);
                    }
                } else {
                    names.add(name);
                    values.add("null");
                }
            }
        }

        if (docsPrinted == 0 && tabular && !suppressNames) {
            out.println(Joiner.on('\t').join(names));
        }

        String formatted;
        if (tabular) {
            formatted = Joiner.on('\t').join(values);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (!suppressNames) {
                    sb.append(names.get(i));
                    sb.append(": ");
                }
                sb.append(values.get(i));
                if (i != values.size() - 1) {
                    sb.append(LINE_SEPARATOR);
                }
            }
            formatted = sb.toString();
        }
        if (!formatted.isEmpty()) {
            if (docsPrinted > 0 && !tabular) {
                out.println();
            }
            out.println(formatted);
            ++docsPrinted;
        }
    }

    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("LuceneQueryTool [options]", options);
        System.out.println();
    }

    private static Options createOptions() {
        Options options = new Options();
        Option option;

        option = new Option("i", "index", true, "index (required, multiple -i searches multiple indexes)");
        option.setRequired(true);
        options.addOption(option);

        option = new Option("q", "query", true,
            "(query | %all | %enumerate-fields "
                + "| %count-fields "
                + "| %enumerate-terms field "
                + "| %script scriptFile "
                + "| %ids id [id ...] | %id-file file) (required, scriptFile may contain -q and -o)");
        option.setRequired(true);
        option.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(option);

        option = new Option(null, "regex", true, "filter query by regex, syntax is field:/regex/");
        option.setArgs(1);
        options.addOption(option);

        option = new Option(null, "fields", true, "fields to include in output (defaults to all)");
        option.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(option);

        option = new Option(null, "sort-fields", false, "sort fields within document");
        options.addOption(option);

        option = new Option(null, "query-limit", true, "max number of query hits to process");
        option.setArgs(1);
        options.addOption(option);

        option = new Option(null, "output-limit", true, "max number of docs to output");
        option.setArgs(1);
        options.addOption(option);

        option = new Option(null, "analyzer", true,
                "for query, (KeywordAnalyzer | StandardAnalyzer) (defaults to KeywordAnalyzer)");
        option.setArgs(1);
        options.addOption(option);

        option = new Option(null, "query-field", true, "default field for query");
        option.setArgs(1);
        options.addOption(option);

        option = new Option(null, "show-id", false, "show Lucene document id in results");
        options.addOption(option);

        option = new Option(null, "show-score", false, "show score in results");
        options.addOption(option);

        option = new Option(null, "show-hits", false, "show total hit count");
        options.addOption(option);

        option = new Option(null, "suppress-names", false, "suppress printing of field names");
        options.addOption(option);

        option = new Option(null, "tabular", false, "print tabular output "
            + "(requires --fields with no multivalued fields)");
        options.addOption(option);

        option = new Option("o", "output", true, "output file (defaults to standard output)");
        option.setArgs(1);
        options.addOption(option);

        return options;
    }

    // Workaround an apparent bug in commons-cli:  If an unknown option comes
    // after an option that accepts unlimited values, no error is produced.
    private static void validateOptions(Options options, String[] args) throws org.apache.commons.cli.ParseException {
        Set<String> optionNames = Sets.newHashSet();

        // non-generic forced by commons.cli api
        for (Object o : options.getOptions()) {
            Option option = (Option) o;
            optionNames.add(option.getLongOpt());
            String shortOpt = option.getOpt();
            if (shortOpt != null) {
                optionNames.add(shortOpt);
            }
        }
        for (String arg : args) {
            if (arg.startsWith("-")) {
                String argName = arg.replaceFirst("-+", "");
                if (!optionNames.contains(argName)) {
                    throw new org.apache.commons.cli.ParseException("Unrecognized option: " + arg);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        String charsetName = Charset.defaultCharset().name();
        if (!"UTF-8".equals(charsetName)) {
            // Really only a problem on mac, where the default charset is MacRoman,
            // and it cannot be changed via the system Locale.
            System.err.println(String.format("defaultCharset is %s, but we require UTF-8.", charsetName));
            System.err.println("Set -Dfile.encoding=UTF-8 on the Java command line, or");
            System.err.println("set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 in the environment.");
            System.exit(1);
        }

        Options options = LuceneQueryTool.createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cmdline = null;
        try {
            cmdline = parser.parse(options, args);
            validateOptions(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println(e.getMessage());
            usage(options);
            System.exit(1);
        }
        String[] remaining = cmdline.getArgs();
        if (remaining != null && remaining.length > 0) {
            System.err.println("unknown extra args found: " + Lists.newArrayList(remaining));
            usage(options);
            System.exit(1);
        }

        String[] indexPaths = cmdline.getOptionValues("index");
        IndexReader[] readers = new IndexReader[indexPaths.length];
        for (int i = 0; i < indexPaths.length; i++) {
            readers[i] = DirectoryReader.open(FSDirectory.open(new File(indexPaths[i])));

        }
        IndexReader reader = new MultiReader(readers, true);

        LuceneQueryTool that = new LuceneQueryTool(reader);

        String opt;
        opt = cmdline.getOptionValue("query-limit");
        if (opt != null) {
            that.setQueryLimit(Integer.parseInt(opt));
        }
        opt = cmdline.getOptionValue("output-limit");
        if (opt != null) {
            that.setOutputLimit(Integer.parseInt(opt));
        }
        opt = cmdline.getOptionValue("analyzer");
        if (opt != null) {
            that.setAnalyzer(opt);
        }
        opt = cmdline.getOptionValue("query-field");
        if (opt != null) {
            that.setDefaultField(opt);
        }
        opt = cmdline.getOptionValue("output");
        PrintStream out = null;
        if (opt != null) {
            out = new PrintStream(new FileOutputStream(new File(opt)), true);
            that.setOutputStream(out);
        }
        if (cmdline.hasOption("show-id")) {
            that.setShowId(true);
        }
        if (cmdline.hasOption("show-hits")) {
            that.setShowHits(true);
        }
        if (cmdline.hasOption("show-score")) {
            that.setShowScore(true);
        }
        if (cmdline.hasOption("sort-fields")) {
            that.setSortFields(true);
        }
        if (cmdline.hasOption("suppress-names")) {
            that.setSuppressNames(true);
        }
        if (cmdline.hasOption("tabular")) {
            that.setTabular(true);
        }

        String[] opts;
        opts = cmdline.getOptionValues("fields");
        if (opts != null) {
            that.setFieldNames(Lists.newArrayList(opts));
        }
        opt = cmdline.getOptionValue("regex");
        if (opt != null) {
            Pattern p = Pattern.compile("^(.*?):/(.*)/$");
            Matcher m = p.matcher(opt);
            if (m.matches()) {
                that.setRegex(m.group(1), Pattern.compile(m.group(2)));
            } else {
                System.err.println("Invalid regex, should be field:/regex/");
                usage(options);
                System.exit(1);
            }
        }
        opts = cmdline.getOptionValues("query");
        that.run(opts);
        if (out != null) {
            out.close();
        }
        reader.close();
    }
}
