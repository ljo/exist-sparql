package org.exist.xquery.modules.rdf;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.dom.QName;
import org.exist.indexing.rdf.TDBIndexWorker;
import org.exist.indexing.rdf.TDBRDFIndex;
import org.exist.xquery.*;
import org.exist.xquery.value.EmptySequence;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author <a href="mailto:abc2386@gmail.com">Anton Olsson</a> for friprogramvarusyndikatet.se
 * @author ljo
 */
public class FunSparql extends BasicFunction {

    protected static Logger LOG = LogManager.getLogger(FunSparql.class);

    public final static FunctionSignature[] signatures = {
	new FunctionSignature(
            new QName("query", SparqlModule.NAMESPACE_URI, null),
            "Returns the solution set to the SPARQL query $sparql-query from the RDF index. Not all query types are supported.",
            new SequenceType[]{
                new FunctionParameterSequenceType("sparql-query", Type.STRING, Cardinality.EXACTLY_ONE, "SPARQL query string")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "Solution set of query $sparql-query"))
    };

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        TDBIndexWorker worker = (TDBIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(TDBRDFIndex.ID);
	Sequence result = EmptySequence.EMPTY_SEQUENCE;

	if (worker == null) {
	    LOG.error("Unable to access SPARQL index worker");
	} else {
	    String query = "";
	    if (!args[0].isEmpty()) {
		query = args[0].getStringValue();
	    }
	    result = worker.query(context, query);
	}
        return result;
    }

    public FunSparql(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

}
