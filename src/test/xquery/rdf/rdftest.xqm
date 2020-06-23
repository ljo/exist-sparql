xquery version "3.0";

module namespace rdftest="http://exist-db.org/xquery/rdf/test/";

import module namespace test="http://exist-db.org/xquery/xqsuite"
    at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

import module namespace sparql="http://exist-db.org/xquery/sparql"
     at "java:org.exist.xquery.rdf.SparqlModule";

declare namespace rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
declare namespace myhouse = "myhouse://";


declare variable $rdftest:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <rdf />
        </index>
    </collection>;


declare variable $rdftest:XML1 :=
    <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:myhouse="myhouse://">

        <rdf:Description rdf:about="myhouse://table">
            <rdf:type rdf:resource="myhouse://furniture" />
            <myhouse:room rdf:resource="kitchen" />
            <myhouse:count>1</myhouse:count>
        </rdf:Description>
    </rdf:RDF>;

declare variable $rdftest:XML2 :=
    <rdf:RDF
        xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        xmlns:myhouse="myhouse://">
        <rdf:Description rdf:about="myhouse://chair">
            <rdf:type rdf:resource="myhouse://furniture" />
            <myhouse:room rdf:resource="kitchen" />
            <myhouse:count>2</myhouse:count>
        </rdf:Description>
        <rdf:Description rdf:about="myhouse://window">
            <myhouse:count>3</myhouse:count>
        </rdf:Description>
    </rdf:RDF>;


declare
    %test:setUp
function rdftest:setup() {
    let $testCol := xmldb:create-collection("/db", "rdftest")
    let $confCol := xmldb:create-collection("/db/system/config/db", "rdftest")
    return (
        xmldb:store($confCol, "collection.xconf", $rdftest:XCONF1),
        xmldb:store($testCol, "myhouse1.rdf", $rdftest:XML1),
        xmldb:store($testCol, "myhouse2.rdf", $rdftest:XML2)
    )
};

declare
    %test:tearDown
function rdftest:tearDown() {
    xmldb:remove("/db/rdftest"),
    xmldb:remove("/db/system/config/db/rdftest")
};



declare
    %test:name("sparql query test")

    %test:args("PREFIX myhouse: <myhouse://>
                SELECT ?x
                WHERE { ?x a myhouse:furniture }
                ORDER BY ?x")
    %test:assertEquals("myhouse://chair", "myhouse://table")

    %test:args("PREFIX myhouse: <myhouse://>
                SELECT ?x ?c
                WHERE { ?x myhouse:count ?c ; a myhouse:furniture }
                ORDER BY ASC(?c)")
    %test:assertEquals("myhouse://table", "1", "myhouse://chair", "2")

    %test:args("PREFIX myhouse: <myhouse://>
                SELECT ?x ?r
                WHERE { ?x myhouse:room ?r }
                ORDER BY ASC(?c)")
    %test:assertEquals("myhouse://chair", "kitchen",
                       "myhouse://table", "kitchen")

function rdftest:query($query as xs:string) {
    sparql:query($query)//text()
};


declare
    %test:name('update literal value')

    %test:assertEquals('3', '4')

function rdftest:updateNode() {
    let $query := "PREFIX myhouse:<myhouse://> SELECT ?c WHERE { myhouse:window myhouse:count ?c }"
    let $node := /rdf:RDF/*[@rdf:about='myhouse://window']/myhouse:count
    return (
        sparql:query($query)//text(),
        update value $node with '4',
        sparql:query($query)//text()
    )
};

