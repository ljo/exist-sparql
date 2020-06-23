# exist-sparql

Integrates SPARQL and RDF indexing through the Jena TDB libraries into eXist-db.

## Compile and install
If you installed the app through the package manager you only need to do step 5 and restart.

1. clone the github repository: https://github.com/ljo/exist-sparql.git

2. To build with Apache Maven: `mvn clean compile package`

3. upload the xar file found in `target/` into eXist-db using the dashboard

4. enable the Index module in `$EXIST_HOME/conf.xml`, by adding one module declaration under "indexer/modules":
```xml
<module id="rdf-index" class="org.exist.indexing.rdf.TDBRDFIndex"/>
```

5. Restart eXist-db.


## Overview
There is currently only one function available with the signature:
```xquery
sparql:query($sparql-query as xs:string) as node()
```

See the usage example below...

## Usage example

```xquery
xquery version "3.0";
import module namespace sparql="http://exist-db.org/xquery/sparql" at
 "java:org.exist.xquery.modules.rdf.SparqlModule";

declare namespace rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#";
let $rdftest-coll := xmldb:create-collection("/db", "rdftest")
let $rdftest-conf-coll := xmldb:create-collection("/db/system/config/db", "rdftest")
let $rdf-conf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
       <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <rdf />
       </index>
    </collection>;
let $rdf-xml-1 :=
    <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:myhouse="myhouse://">
        <rdf:Description rdf:about="myhouse://table">
            <rdf:type rdf:resource="myhouse://furniture" />
            <myhouse:room rdf:resource="kitchen" />
            <myhouse:count>1</myhouse:count>
        </rdf:Description>
    </rdf:RDF>;
let $rdf-xml-2 :=
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
    </rdf:RDF>
let $dummy1 := xmldb:store($rdftest-conf-coll, "collection.xconf", $rdf-conf)
let $dummy2 := (xmldb:store($rdftest-coll, "myhouse1.rdf", $rdf-xml-1),
               	xmldb:store($rdftest-coll, "myhouse2.rdf", $rdf-xml-2))

let $query1 := ("PREFIX myhouse: <myhouse://>
                SELECT ?x
                WHERE { ?x a myhouse:furniture }
                ORDER BY ?x")

(: ("myhouse://chair", "myhouse://table") :)

let $query2 := ("PREFIX myhouse: <myhouse://>
                SELECT ?x ?c
                WHERE { ?x myhouse:count ?c ; a myhouse:furniture }
                ORDER BY ASC(?c)")
(: ("myhouse://table", "1", "myhouse://chair", "2") :)

let $query3 := ("PREFIX myhouse: <myhouse://>
                SELECT ?x ?r
                WHERE { ?x myhouse:room ?r }
                ORDER BY ASC(?c)")
(: ("myhouse://chair", "kitchen", "myhouse://table", "kitchen") :)

let $queries := ($query1, $query2, $query3)

return
  for $query in $queries
    (: return sparql:query($query)//text() :)
    return sparql:query($query)//sr:uri
```
