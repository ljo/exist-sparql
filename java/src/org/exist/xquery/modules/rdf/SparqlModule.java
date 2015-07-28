package org.exist.xquery.modules.rdf;

import java.util.List;
import java.util.Map;
import org.exist.xquery.*;

/**
 *
 * @author Anton Olsson <abc2386@gmail.com> for friprogramvarusyndikatet.se
 * @author ljo
 */
public class SparqlModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/sparql";
    public static final String PREFIX = "sparql";
    public final static String INCLUSION_DATE = "";
    public final static String RELEASED_IN_VERSION = "";
    
    public static final FunctionDef[] functions = {
    	new FunctionDef(FunSparql.signatures[0], FunSparql.class),
    };
    
    public SparqlModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, false);
    }
    
    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "A SPARQL and RDF indexing module.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
