package org.exist.indexing.rdf;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdfxml.xmlinput.SAX2Model;
import com.hp.hpl.jena.sparql.graph.GraphFactory;
import com.hp.hpl.jena.sparql.resultset.ResultSetApply;
import com.hp.hpl.jena.sparql.resultset.XMLResults;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.persistent.*;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author Anton Olsson <abc2386@gmail.com> for friprogramvarusyndikatet.se
 */
public class TDBIndexWorker implements IndexWorker {

    private static final Logger LOG = LogManager.getLogger(TDBIndexWorker.class);

    private final DBBroker broker;
    private final TDBRDFIndex index;
    private DocumentImpl currentDoc;
    /** Model for storing pending RDF triples, for store or remove **/
    private final Model cacheModel = GraphFactory.makeDefaultModel();

    private RDFIndexConfig config;
    private ReindexMode mode;
    private final TDBStreamListener listener = new TDBStreamListener();
    private IndexController controller;
    private static final String CONFIG_ELEMENT_NAME = "rdf";

    TDBIndexWorker(TDBRDFIndex index, DBBroker broker) {
        this.index = index;
        this.broker = broker;
    }

    @Override
    public String getIndexId() {
        return TDBRDFIndex.ID;
    }

    @Override
    public String getIndexName() {
        return index.getIndexName();
    }

    @Override
    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
        LOG.debug("Configuring TDB index...");

        for (int i = 0; i < configNodes.getLength(); i++) {
            Node node = configNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && node.getLocalName().equals(CONFIG_ELEMENT_NAME)) {
                RDFIndexConfig tdbIndexConfig = new RDFIndexConfig((Element) node, namespaces);
                return tdbIndexConfig;
            }
        }

        return null;
    }

    private RDFIndexConfig getIndexConfig(Collection coll) {
        IndexSpec indexConf = coll.getIndexConfiguration(broker);
        if (indexConf != null) {
            return (RDFIndexConfig) indexConf.getCustomIndexSpec(getIndexId());
        }
        return null;
    }

    @Override
    public void setDocument(DocumentImpl doc) {
        RDFIndexConfig origConfig = getIndexConfig(doc.getCollection());
        if (origConfig != null) {
            currentDoc = doc;
            // Create a copy of the original RDFIndexConfig (there's only one per db instance), so we can safely work with it.
            this.config = new RDFIndexConfig(origConfig);
        } else {
            currentDoc = null;
            this.config = null;
        }
    }

    @Override
    public void setDocument(DocumentImpl document, ReindexMode mode) {
        setDocument(document);
        setMode(mode);
    }

    @Override
    public void setMode(ReindexMode mode) {
        this.mode = mode;
    }

    @Override
    public DocumentImpl getDocument() {
        return currentDoc;
    }

    @Override
    public ReindexMode getMode() {
        return mode;
    }

    @Override
    public <T extends IStoredNode> IStoredNode getReindexRoot(IStoredNode<T> node, NodePath path, boolean insert, boolean includeSelf) {
        // until TDBStreamReader$reset can handle base-uri properly when reindexing at non-root level, we reindex the whole document.
        return (IStoredNode) node.getOwnerDocument().getFirstChild();

        // return topmost parent element under root (tree level 2)
//        NodeId nodeId = node.getNodeId();
//        while (nodeId.getTreeLevel() > 2) {
//            nodeId = nodeId.getParentId();
//        }
//        return broker.objectWith(node.getOwnerDocument(), nodeId);
    }

    @Override
    public StreamListener getListener() {
        if (currentDoc == null || mode == ReindexMode.REMOVE_ALL_NODES)
            return null;

        listener.reset(currentDoc);
        return listener;
    }

    @Override
    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        return null;
    }

    @Override
    public void flush() {
        if (currentDoc == null)
            return;

        switch (mode) {
            case STORE:
                storeNodes();
                break;
            case REMOVE_ALL_NODES:
                removeDocument();
                // reset mode because we dont want to try to remove doc again
                // on any subsequent flush (like during database shutdown)
                mode = ReindexMode.UNKNOWN;
                break;
            case REMOVE_SOME_NODES:
                removeNodes();
                break;
            case REMOVE_BINARY:
                break;
            case UNKNOWN:
                return;
        }
        // reset mode (as per method description: prepare for being reused for a different job.)
        mode = ReindexMode.UNKNOWN;
        // clear model cache
        cacheModel.removeAll();
    }

    @Override
    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) throws PermissionDeniedException {
        RDFIndexConfig cfg = getIndexConfig(collection);
        if (cfg != null) {
            Iterator<DocumentImpl> it = collection.iterator(broker);
            while (it.hasNext()) {
                DocumentImpl doc = it.next();
                removeDocument(doc);
            }
        }
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return true;
    }

    private Model getOrCreateModel(DocumentImpl doc) {
        return index.getDataset().getNamedModel(doc.getDocumentURI());
    }

    private Model getModelOrNull(DocumentImpl doc) {
        final String documentURI = doc.getDocumentURI();

        if (index.getDataset().containsNamedModel(documentURI)) {
            return index.getDataset().getNamedModel(documentURI);
        } else
            return null;
    }

    @Override
    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map<?, ?> hints) {
        return new Occurrences[0];
    }

    @Override
    public QueryRewriter getQueryRewriter(XQueryContext context) {
        return null;
    }

    /* Does the index have an entry for this document? */
    public boolean isDocumentIndexed(Document doc) {
        String documentURI = doc.getDocumentURI();
        return index.getDataset().containsNamedModel(documentURI);
    }

    private void removeDocument() {
        removeDocument(currentDoc);
    }

    private void removeNodes() {
        if (cacheModel.isEmpty())
            return;
        Model model = getModelOrNull(currentDoc);
        if (model == null) {
            LOG.warn("removeNodes with null model");
            return;
        }
        model.remove(cacheModel);
    }

    private void storeNodes() {
        if (cacheModel.isEmpty())
            return;
        Model model = getOrCreateModel(currentDoc);
        model.add(cacheModel);
    }

    private void removeDocument(Document doc) {
        if (doc != null) {
            String documentURI = doc.getDocumentURI();
            Dataset dataset = index.getDataset();
            if (dataset.containsNamedModel(documentURI))
                dataset.removeNamedModel(documentURI);
        }
    }

    /**
     * Query TDB with SPARQL
     * @param context 
     * @param queryString SPARQL query string
     * @return Result document
     * @throws org.exist.xquery.XPathException Query error
     */
    public Sequence query(XQueryContext context, String queryString) throws XPathException {
        Query q;
        Sequence resultDoc = null;
        try {
//            String baseURI = context.getBaseURI().getStringValue();
            String baseURI = "";
            q = QueryFactory.create(queryString, baseURI);
        } catch (QueryParseException ex) {
            LOG.warn("QueryException: " + ex.getLocalizedMessage());
            throw new XPathException(ex.getLine(), ex.getColumn(), "in SPARQL query: " + ex.getMessage());
        }

        // all query types not implemented yet
        if (!(q.isSelectType() || q.isAskType() || q.isConstructType())) {
            throw new XPathException("SPARQL query type not supported: " + queryString);
        }

        try (QueryExecution qe = QueryExecutionFactory.create(q, index.getDataset())) {

            if (q.isSelectType()) {
                ResultSet result = qe.execSelect();
                /*
                 * Build SELECT result 
                 */
                context.pushDocumentContext();
                try {
                    DocumentBuilderReceiver builder = new DocumentBuilderReceiver(context.getDocumentBuilder(), true);
                    JenaResultSet2Sax jenaResultSet2Sax = new JenaResultSet2Sax(builder);
                    ResultSetApply.apply(result, jenaResultSet2Sax);
                    resultDoc = (Sequence) builder.getDocument();
                } finally {
                    context.popDocumentContext();
                }
            } else if (q.isAskType()) {
                boolean result = qe.execAsk();
                /*
                 * Build ASK (boolean) result
                 */
                context.pushDocumentContext();
                try {
                    DocumentBuilderReceiver builder = new DocumentBuilderReceiver(context.getDocumentBuilder(), true);
                    final Attributes attrs = new AttributesImpl();
                    builder.startDocument();
//                    builder.startPrefixMapping("", XMLResults.baseNamespace);
                    builder.startElement(XMLResults.baseNamespace, XMLResults.dfRootTag, XMLResults.dfRootTag, attrs);
                    builder.startElement(XMLResults.baseNamespace, XMLResults.dfHead, XMLResults.dfHead, attrs);
                    builder.endElement(XMLResults.baseNamespace, XMLResults.dfHead, XMLResults.dfHead);
                    builder.startElement(XMLResults.baseNamespace, XMLResults.dfBoolean, XMLResults.dfBoolean, attrs);
                    builder.characters(Boolean.toString(result));
                    builder.endElement(XMLResults.baseNamespace, XMLResults.dfBoolean, XMLResults.dfBoolean);
                    builder.endElement(XMLResults.baseNamespace, XMLResults.dfRootTag, XMLResults.dfRootTag);
                    builder.endDocument();

                    resultDoc = (Sequence) builder.getDocument();

                } catch (SAXException ex) {
                    LOG.error(ex);
                } finally {
                    context.popDocumentContext();
                }
            } else if (q.isConstructType()) {
                // this may not be a very nice solution?
                Writer out = new StringWriter(256);
                qe.execConstruct().write(out);
                context.pushDocumentContext();
                try {
                    resultDoc = ModuleUtils.stringToXML(context, out.toString());
                } catch (SAXException | IOException ex) {
                    LOG.error(ex);
                } finally {
                    context.popDocumentContext();
                }
            }

        } catch (QueryException ex) {
            LOG.warn("QueryException: " + ex.getLocalizedMessage());
            throw new XPathException("Sparql query execution: " + ex);
        }

        return resultDoc;
    }

    private class TDBStreamListener extends AbstractStreamListener {

        private ElementImpl deferredElement;
        private final AttributesImpl deferredAttribs = new AttributesImpl();
        private SAX2Model saxHandler;

        private TDBStreamListener() {
            try {
                saxHandler = SAX2Model.create("", cacheModel);
            } catch (SAXParseException ex) {
                LOG.error(ex);
            }
        }

        /*
         * Reset RDF sax handler with the new document
         */
        public void reset(Document doc) {
//            String base = "resource:" + doc.getBaseURI() + "#";
            String base = "";
            String lang = "";

            // todo: base URL and lang for the case where only a subtree is added/removed
            // if at reindex we start from first level under RDF root, we'd want base uri and lang from root element.
            // how can we know if doc has root node yet? getFirstChild prints error messages and stacktrace when root node not built yet 
//            ElementImpl root = (ElementImpl) doc.getFirstChild();
//            if (root != null) {
//                base = root.getBaseURI();
//                String langAttr = root.getAttributeNS(NamespaceSupport.XMLNS, "lang");
//                if (langAttr != null)
//                    lang = langAttr;
//            }
            try {
                saxHandler.initParse(base, lang);
            } catch (SAXParseException ex) {
                LOG.error(ex);
            }

            if (!cacheModel.isEmpty()) {
                LOG.warn("TDBStreamListener: Model is not empty at reset");
                cacheModel.removeAll();
            }
        }

        @Override
        public IndexWorker getWorker() {
            return TDBIndexWorker.this;
        }

        @Override
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (deferredElement != null) {
                processDeferredElement();
            }
            deferredElement = element;
            super.startElement(transaction, element, path);
        }

        @Override
        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (deferredElement != null) {
                processDeferredElement();
            }
            try {
                saxHandler.endElement(
                        element.getNamespaceURI(),
                        element.getLocalName(),
                        element.getQName().getStringValue());
            } catch (Exception ex) {
                LOG.error(ex);
            }
            super.endElement(transaction, element, path);
        }

        @Override
        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            deferredAttribs.addAttribute(
                    attrib.getNamespaceURI(),
                    attrib.getLocalName(),
                    attrib.getQName().getStringValue(),
                    AttrImpl.getAttributeType(attrib.getType()),
                    attrib.getValue()
            );
            super.attribute(transaction, attrib, path);
        }

        @Override
        public void characters(Txn transaction, AbstractCharacterData text, NodePath path) {
            if (deferredElement != null) {
                processDeferredElement();
            }
            try {
                saxHandler.characters(text.getData().toCharArray(), 0, text.getLength());
            } catch (Exception ex) {
                LOG.error(ex);
            }
            super.characters(transaction, text, path);
        }

        private void processDeferredElement() {
            try {

                saxHandler.startElement(
                        deferredElement.getNamespaceURI(),
                        deferredElement.getLocalName(),
                        deferredElement.getQName().getStringValue(),
                        deferredAttribs
                );
            } catch (Exception ex) {
                LOG.error(ex);
//                deferredAttribs.clear();
            } finally {
                deferredAttribs.clear();
                deferredElement = null;
            }
        }

    }

}
