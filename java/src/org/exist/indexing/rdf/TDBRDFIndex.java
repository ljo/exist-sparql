package org.exist.indexing.rdf;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.hp.hpl.jena.query.ARQ;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.tdb.StoreConnection;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.tdb.base.block.FileMode;
import com.hp.hpl.jena.tdb.sys.SystemTDB;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
//import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/**
 *
 * @author Anton Olsson <abc2386@gmail.com> for friprogramvarusyndikatet.se
 * @author ljo
 */
public class TDBRDFIndex extends RDFIndex implements RawBackupSupport {

    private final static Logger LOG = LogManager.getLogger(TDBRDFIndex.class);
    private Dataset dataset;
    private StoreConnection connection;
    public static String ID = RDFIndex.ID; //"tdb-rdf-index";
    private static final String DIR_NAME = "tdb";
    protected Path directory;

    public String getDirName() {
        return DIR_NAME;
    }

    @Override
    public void open() throws DatabaseConfigurationException {
//        this.dataset = TDBFactory.createDataset(getDataDir());
	directory = getDataDir();

        TDB.getContext().set(TDB.symUnionDefaultGraph, true); // todo: make configurable?
        connection = StoreConnection.make(getMyDataDir());
    }

    @Override
    public void close() throws DBException {
        if (dataset != null) {
            dataset.close();
            dataset = null;
        }
        connection = null;
        TDB.closedown();
    }

    @Override
    public void sync() throws DBException {
        if (connection != null) {
            connection.flush();
        }
        TDB.sync(dataset);
    }

    @Override
    public void remove() throws DBException {
	close();
	try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
	    for (Path file : stream) {
                Files.delete(file);
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    @Override
    public Path getDataDir() {
        return super.getDataDir().resolve(getDirName());
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        TDBIndexWorker worker = (TDBIndexWorker) workers.get(broker);
        if (worker == null) {
            worker = new TDBIndexWorker(this, broker);
            workers.put(broker, worker);
        }
        return worker;
    }

    public Dataset getDataset() {
        if (dataset == null) {
            if (connection == null) {
                throw new IllegalStateException("TDB was never opened or was already closed");
	    }
            dataset = TDBFactory.createDataset(connection.getLocation());
        }
        return dataset;
    }

    @Override
    public String getIndexId() {
        return ID;
    }

    private String getMyDataDir() {
	if (directory == null) {
	    directory = getDataDir();
	}
        return directory.toAbsolutePath().toString();
    }

    @Override
    public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);

	if (LOG.isDebugEnabled()) {
            LOG.debug("Configuring SPARQL index");
	}

        /*
         * Some configurables.
         */
        NamedNodeMap attributes = config.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            if (attr.getName().equals(CFG_FILE_MODE)) {
                if (attr.getValue().equals(CFG_FILE_MODE_MAPPED)) {
                    SystemTDB.setFileMode(FileMode.mapped);
                } else if (attr.getValue().equals(CFG_FILE_MODE_DIRECT)) {
                    SystemTDB.setFileMode(FileMode.direct);
                }
            } else if (attr.getName().equals(CFG_LOG_EXEC)) {
                if (attr.getValue().equals(CFG_LOG_EXEC_TRUE)) {
                    ARQ.isTrue(ARQ.symLogExec);
                }
            }
        }

//        TDB.transactionJournalWriteBlockMode
    }

    @Override
    public void backupToArchive(RawDataBackup backup) throws IOException {
	try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
	    for (Path file : stream) {
		String path = file.getFileName().toString();
		try (final OutputStream os = backup.newEntry(path)) {
		    Files.copy(getDataDir().resolve(path), os);
		} finally {
		    backup.closeEntry();
		}
	    }
	} catch (IOException | DirectoryIteratorException die) {
	    LOG.error("Could not read directory: " + directory.toAbsolutePath().toString());
	}
    }

    private final static String CFG_FILE_MODE = "fileMode";
    private final static String CFG_FILE_MODE_MAPPED = "mapped";
    private final static String CFG_FILE_MODE_DIRECT = "direct";

    private final static String CFG_LOG_EXEC = "logExec";
    private final static String CFG_LOG_EXEC_TRUE = "true";

}
