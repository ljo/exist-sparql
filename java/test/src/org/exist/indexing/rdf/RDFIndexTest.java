package org.exist.indexing.rdf;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.indexing.Index;
import org.exist.indexing.IndexWorker;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 *
 * @author Anton Olsson <abc2386@gmail.com> for friprogramvarusyndikatet.se
 */
public class RDFIndexTest {

    private static BrokerPool pool;
    private static Collection root;

    private static String XUPDATE_START
            = "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\" "
            + "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" >";

    private static String XUPDATE_END
            = "</xu:modifications>";

    private static String rdfNS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private static String COLLECTION_CONFIG
            = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
            + "	<index>"
            + "       <rdf>"
            + "       </rdf>"
            + "	</index>"
            + "</collection>";

    private static String XML
            = "<?xml version=\"1.0\"?>\n"
            + "\n"
            + "<rdf:RDF xml:lang=\"en\"\n"
            + "        xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n"
            + "        xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\">\n"
            + "\n"
            + "<rdf:Description ID=\"biologicalParent\">\n"
            + "  <rdf:type resource=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#Property\"/>\n"
            + "</rdf:Description>\n"
            + "\n"
            + "<rdf:Description ID=\"biologicalFather\">\n"
            + "  <rdf:type resource=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#Property\"/>\n"
            + "  <rdfs:subPropertyOf rdf:resource=\"#biologicalParent\"/>\n"
            + "</rdf:Description>\n"
            + "\n"
            + "</rdf:RDF>\n"
            + "";

    @Test
    public void query() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG, XML, "test1.xml");
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> SELECT $x WHERE {$x a rdf:Property }";
            Sequence seq = xquery.execute("import module namespace sparql=\"http://exist-db.org/xquery/sparql\"; sparql:query(\"" + query + "\")",
                    null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void borkenQuery() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG, XML, "test1.xml");
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> SELECT $x WHERE {$x a unknownPrefix:asd }";

            XPathException expectedException = null;
            Sequence seq = null;

            try {
                seq = xquery.execute("import module namespace sparql=\"http://exist-db.org/xquery/sparql\"; sparql:query(\"" + query + "\")", null, AccessContext.TEST);
            } catch (XPathException ex) {
                expectedException = ex;
            }

            assertNotNull(expectedException);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void dropSingleDocument() {
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            TDBIndexWorker worker = (TDBIndexWorker) broker.getIndexController().getWorkerByIndexId(TDBRDFIndex.ID);

            DocumentSet docs = configureAndStore(COLLECTION_CONFIG, XML, "dropDocument.xml");
            DocumentImpl doc = docs.getDocumentIterator().next();

            assertTrue(worker.isDocumentIndexed(doc));

            root.removeXMLResource(transaction, broker, XmldbURI.create("dropDocument.xml"));

            transact.commit(transaction);

            assertFalse(worker.isDocumentIndexed(doc));

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void removeCollection() {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            DocumentSet docs = configureAndStore(COLLECTION_CONFIG, XML, "dropDocument.xml");

            broker.removeCollection(transaction, root);
            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            transact.commit(transaction);
            
            TDBIndexWorker worker = (TDBIndexWorker) broker.getIndexController().getWorkerByIndexId(TDBRDFIndex.ID);

            Iterator<DocumentImpl> it = docs.getDocumentIterator();
            while (it.hasNext()) {
                Document doc = it.next();
                assertFalse(worker.isDocumentIndexed(doc));
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private DocumentSet configureAndStore(String configuration, String data, String docName) {
        MutableDocumentSet docs = new DefaultDocumentSet();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            if (configuration != null) {
                CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
            assertNotNull(info);
            root.store(transaction, broker, info, data, false);

            docs.add(info.getDocument());
            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return docs;
    }

    private DocumentSet configureAndStore(String configuration, String directory) {

        MutableDocumentSet docs = new DefaultDocumentSet();

        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            if (configuration != null) {
                CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }

            File file = new File(directory);
            File[] files = file.listFiles();
            MimeTable mimeTab = MimeTable.getInstance();
            for (int j = 0; j < files.length; j++) {
                MimeType mime = mimeTab.getContentTypeFor(files[j].getName());
                if (mime != null && mime.isXMLType()) {
                    System.out.println("Storing document " + files[j].getName());
                    InputSource is = new InputSource(files[j].getAbsolutePath());
                    IndexInfo info
                            = root.validateXMLResource(transaction, broker, XmldbURI.create(files[j].getName()), is);
                    assertNotNull(info);
                    is = new InputSource(files[j].getAbsolutePath());
                    root.store(transaction, broker, info, is, false);
                    docs.add(info.getDocument());
                }
            }
            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return docs;
    }

    @Before
    public void setup() {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);

//            Configuration config = BrokerPool.getInstance().getConfiguration();
//            savedConfig = (Boolean) config.getProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT);
//            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, Boolean.TRUE);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @After
    public void cleanup() throws EXistException {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            Collection collConfig = broker.getOrCreateCollection(transaction,
                    XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"));
            assertNotNull(collConfig);
            broker.removeCollection(transaction, collConfig);

            if (root != null) {
                assertNotNull(root);
                broker.removeCollection(transaction, root);
            }
            transact.commit(transaction);

//            Configuration config = BrokerPool.getInstance().getConfiguration();
//            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, savedConfig);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    static String backupDir;

    @BeforeClass
    public static void startDB() {
        try {
            // backup and create new clean test DB
            backupDir = TestUtils.moveDataDirToTempAndCreateClean();

            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());

//            config.setProperty(Indexer.PROPERTY_SUPPRESS_WHITESPACE, "none");
//            config.setProperty(Indexer.PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE, Boolean.TRUE);
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopDB() throws IOException, DatabaseConfigurationException {
//        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        TestUtils.moveDataDirBack(backupDir);
        pool = null;
        root = null;
    }
}
