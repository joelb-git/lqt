Lucene Query Tool (lqt)
=======================

Introduction
------------

Lucene Query Tool (`lqt`) is a command line tool for executing Lucene
queries and formatting the results.
[Luke](https://code.google.com/p/luke/) is a great tool for ad-hoc
index inspection, but we were looking for something to help with:

* scripting and ad-hoc Unix pipelines

* recording queries and results in bug reports, emails, etc. without
  the need for screenshots

* working with non-ASCII characters on remote machines without dealing
  with X fonts

* avoid long Luke startup time for large indexes

`lqt` is simple - really just one Java file - but it has proven to be
very useful for our Lucene projects.

Usage
-----

Invoke `lqt` with the supplied driver script, after compiling the
project:

>     $ mvn compile
>     $ ./lqt
>     usage: LuceneQueryTool [options]
>         --analyzer <arg>       for query, (KeywordAnalyzer | StandardAnalyzer)
>                                (defaults to KeywordAnalyzer)
>         --fields <arg>         fields to include in output (defaults to all)
>      -i,--index <arg>          index (required)
>         --output-limit <arg>   max number of docs to output
>      -q,--query <arg>          (query | %all | %enumerate-fields |
>                                %count-fields | %enumerate-terms field | %ids
>                                id [id ...] | %id-file file) (required)
>         --query-field <arg>    default field for query
>         --query-limit <arg>    max number of query hits to process
>         --regex <arg>          filter query by regex, syntax is field:/regex/
>         --show-hits            show total hit count
>         --show-id              show Lucene document id in results
>         --show-score           show score in results
>         --sort-fields          sort fields within document
>         --suppress-names       suppress printing of field names
>         --tabular              print tabular output (requires --fields with no
>                                multivalued fields)

`lqt` currently targets Java 1.6 and Lucene 4.5.0, although I've used
it successfully against indexes built with Lucene 4.3.0.

Building
--------

>     $ git clone git@github.com:joelb-git/lqt.git
>     $ cd lqt
>     $ mvn compile

Examples
--------

The following examples use an index that contains a large portion of
the English and Chinese Wikipedia, used as part of an Entity
Resolution system we're developing at [Basis
Technology](http://www.basistech.com/).

* Enumerate the field names

>     $ ./lqt -i /tmp/index -q %enumerate-fields
>     ...
>     c-alias
>     c-cluster-id
>     c-entity-type
>     crossdoc-id
>     longest-mention
>     ...

* Count the fields

  For each field, this shows the number of documents where it occurs
  at least once.  Note that an unindexed field will show up with count
  0.

>     $ ./lqt -i /tmp/index -q %count-fields
>     ...
>     c-alias: 2992452
>     c-cluster-id: 3101383
>     c-entity-type: 3101383
>     crossdoc-id: 3765425
>     longest-mention: 3765425
>     ...

* Count all documents with a c-cluster-id field.

  Note that `-query-limit 0` will provoke a Lucene exception.  Use
  `output-limit 0` if you want no output except a count.

>     $ ./lqt -i /tmp/index -q c-cluster-id:/.*/ -show-hits -output-limit 0
>     totalHits: 1693260

* Print all documents

  The special query `%all` will return all documents.  The default
  format prints fields vertically.  Multivalued fields are printed one
  after another.

>     $ ./lqt -i /tmp/index -q %all | less
>     bt_rni_NameHRK_encodedName: STN
>     bt_rni_Name_NormalizedData: sutton
>     bt_rni_Name_FullnameOverrides: engsutton
>     bt_rni_Name_CompletedData: sutton
>     bt_rni_Name_TokenOverrides: engsutton
>     bt_rni_NameHRK_originalName: Sutton
>     bt_rni_NameHRK_keyBigrams: ST TN
>     bt_rni_NameHRK_initials: s
>     bt_rni_Name_UID:
>     bt_rni_Name_Language: eng
>     bt_rni_Name_Script: Latn
>     bt_rni_Name_LanguageOfOrigin: xxx
>     bt_rni_Name_EntityType: 196608
>     bt_rni_Name_LatnData: sutton
>     bt_rni_Name_TokenSpans: 1 1 0 6
>     name-token-count: 1
>     doc-id: 221/rlp-processed/en_124868
>     doc-language: eng
>     indoc-chain-id: 0
>     longest-mention: Sutton
>     ...
>     
>     bt_rni_NameHRK_encodedName: KR AJNS
>     bt_rni_Name_NormalizedData: crow agency
>     bt_rni_Name_FullnameOverrides: engcrow agency
>     bt_rni_Name_CompletedData: crow agency
>     ...

* Select specific fields and format as tab-delimited rows

>     $ ./lqt -i /tmp/index -q c-cluster-id:/.*/ \
>     -fields c-cluster-id c-cluster-label -query-limit 5 -tabular
>     c-cluster-id	c-cluster-label
>     en_1176874	Dan O'Keeffe
>     en_11768762	Ralph Felton
>     en_11768767	Greene County-Lewis A. Jackson Regional Airport
>     en_11768770	Jimmy Keegan
>     en_117688	Ionia Township

* Count documents by entity-type

>     $ ./lqt -i /tmp/index -q entity-type:/.*/ \
>     -fields entity-type -tabular -suppress-names \
>     | sort | uniq -c | sort -nr
>      903524 LOCATION
>      697399 PERSON
>      241269 ORGANIZATION

* Count Chinese PERSON documents

>     $ ./lqt -i /tmp/index -q "entity-type:PERSON && doc-language:zho"
>     -show-hits -output-limit 0
>     totalHits: 112395

* Show internal lucene doc id and score

>     $ ./lqt -i /tmp/index \
>     -q c-cluster-label:George \
>     -fields c-cluster-id c-cluster-label \
>     -query-limit 5 -tabular -show-id -show-score | column -s$'\t' -t
>     <id>     <score>            c-cluster-id  c-cluster-label
>     1209985  13.30576229095459  en_114019     George
>     1265605  13.30576229095459  en_2505331    George
>     1290287  13.30576229095459  en_302240     George
>     1460699  13.30576229095459  en_2303828    George
>     1960718  13.30576229095459  en_13141      George

* Filter with a complex regex

  `-query field:/.../` uses Lucene's built in regular expressions at
  query time.  This uses a very limited regex syntax.  `-regex
  field:/.../` applies a full Java regex to each returned document.
  This is much slower, but it can be useful when you need more
  powerful regexes.  Since this is applied to the results of the
  query, use `-output-limit` rather than `-query-limit`. For example,
  find documents with an ideograph in the `longest-mention` field:

>     $ ./lqt -i /tmp/index -q longest-mention:/.*/ \
>     -regex "longest-mention:/.*\p{InCJK_UNIFIED_IDEOGRAPHS}.*/" \
>     -fields longest-mention -output-limit 5
>     longest-mention: Red Leaves / 紅葉
>     
>     longest-mention: 努利虫疠霉
>     
>     longest-mention: 京特・马洛伊达
>     
>     longest-mention: 盖林卡亚
>     
>     longest-mention: 东升里

* Enumerate terms

  Enumerating terms is useful to see values in fields that are not
  stored.  For example, you could use this to show that stopwords are
  not being indexed or that your analyzer is really doing lowercasing.
  The number in parentheses is the occurrence count.

>     $ ./lqt -i /tmp/index -q %enumerate-terms text-context \
>     | grep -i george | head
>     contogeorge (1)
>     digeorge (1)
>     dršgeorge (1)
>     fitzgeorge (1)
>     george (6390)
>     george's (11)
>     georgeanna (1)

* Field validation

  Field names in queries are validated to catch typos.

>     $ ./lqt -i /tmp/index -q longest-mentioon:George
>     Exception in thread "main" java.lang.RuntimeException: Invalid field names: [longest-mentioon]
>     	at com.basistech.lucene.tools.LuceneQueryTool.runQuery(LuceneQueryTool.java:313)
>     	at com.basistech.lucene.tools.LuceneQueryTool.run(LuceneQueryTool.java:245)
>     	at com.basistech.lucene.tools.LuceneQueryTool.main(LuceneQueryTool.java:597)

Credits
-------

`lqt` was written by Joel Barry (joelb@basistech.com).  David Corbett
(corbett.dav@husky.neu.edu) added term enumeration and field
validation.

License
-------

`lqt` is released under the Apache License.
