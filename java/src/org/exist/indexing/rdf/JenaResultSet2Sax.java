package org.exist.indexing.rdf;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.sparql.ARQConstants;
import com.hp.hpl.jena.sparql.resultset.ResultSetProcessor;
import com.hp.hpl.jena.sparql.resultset.XMLResults;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author Anton Olsson <abc2386@gmail.com> for friprogramvarusyndikatet.se
 */
public class JenaResultSet2Sax implements ResultSetProcessor, XMLResults {

    private final ContentHandler handler;
    private final AttributesImpl atts = new AttributesImpl();

    public JenaResultSet2Sax(ContentHandler handler) {
        this.handler = handler;
    }

    @Override
    public void start(ResultSet rs) {
        // start elements: document, root, header, results
        try {
            handler.startDocument();

            // start root element
            atts.clear();
            handler.startElement(dfNamespace, dfRootTag, dfRootTag, atts);

            // start header
            atts.clear();
            handler.startElement(dfNamespace, dfHead, dfHead, atts);

            for (String varName : rs.getResultVars()) {
                atts.clear();
                atts.addAttribute(dfNamespace, dfAttrVarName, dfAttrVarName, "CDATA", varName);
                handler.startElement(dfNamespace, dfVariable, dfVariable, atts);
                handler.endElement(dfNamespace, dfVariable, dfVariable);
            }

            // end header
            handler.endElement(dfNamespace, dfHead, dfHead);

            // start results
            atts.clear();
            handler.startElement(dfNamespace, dfResults, dfResults, atts);

        } catch (SAXException ex) {
        }
    }

    @Override
    public void finish(ResultSet rs) {
        // end results, root, document
        try {
            handler.endElement(dfNamespace, dfResults, dfResults);
            handler.endElement(dfNamespace, dfRootTag, dfRootTag);
            handler.endDocument();
        } catch (SAXException ex) {
        }
    }

    @Override
    public void start(QuerySolution qs) {
        // start a result element
        atts.clear();
        try {
            handler.startElement(dfNamespace, dfSolution, dfSolution, atts);
        } catch (SAXException ex) {
        }
    }

    @Override
    public void finish(QuerySolution qs) {
        // end a result element
        try {
            handler.endElement(dfNamespace, dfSolution, dfSolution);
        } catch (SAXException ex) {
        }
    }

    @Override
    public void binding(String varName, RDFNode value) {
        // If, for a particular solution, a variable is unbound, no binding element for that variable is included in the result element.
        if (value == null)
            return;

        try {
            // start binding element
            atts.clear();
            atts.addAttribute(dfNamespace, dfAttrVarName, dfAttrVarName, "CDATA", varName);
            handler.startElement(dfNamespace, dfBinding, dfBinding, atts);

            // binding value
            if (value.isLiteral())
                literal((Literal) value);
            else if (value.isResource())
                resource((Resource) value);

            // end binding element
            handler.endElement(dfNamespace, dfBinding, dfBinding);
        } catch (SAXException ex) {
        }
    }

    /* Literal binding to handler*/
    private void literal(Literal l) {
        atts.clear();
        try {
            String s = l.getLexicalForm();
            String lang = l.getLanguage();
            String dt = l.getDatatypeURI();
            // Literal with lang?
            if (lang != null && lang.length() != 0) {
                atts.addAttribute(ARQConstants.XML_NS, "lang", "xml:lang", "CDATA", lang);
            }
            // Literal with datatype?
            if (dt != null && dt.length() != 0) {
                atts.addAttribute(dfNamespace, dfAttrDatatype, dfAttrDatatype, "CDATA", dt);
            }
            handler.startElement(dfNamespace, dfLiteral, dfLiteral, atts);
            handler.characters(s.toCharArray(), 0, s.length());
            handler.endElement(dfNamespace, dfLiteral, dfLiteral);
        } catch (SAXException ex) {
        }
    }

    /* Resource binding to handler*/
    private void resource(Resource r) {
        atts.clear();
        try {
            String uri = r.getURI();
            if (uri != null) {
                // named node
                handler.startElement(dfNamespace, dfURI, dfURI, atts);
                handler.characters(uri.toCharArray(), 0, uri.length());
                handler.endElement(dfNamespace, dfURI, dfURI);
            } else {
                // blank node
                uri = r.asNode().getBlankNodeLabel();
                handler.startElement(dfNamespace, dfBNode, dfBNode, atts);
                handler.characters(uri.toCharArray(), 0, uri.length());
                handler.endElement(dfNamespace, dfBNode, dfBNode);
            }
        } catch (SAXException ex) {
        }

    }

}
