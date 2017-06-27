package org.exist.indexing.rdf;

import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.indexing.AbstractIndex;
import org.exist.storage.DBBroker;

/**
 *
 * @author <a href="mailto:abc2386@gmail.com">Anton Olsson</a> for friprogramvarusyndikatet.se
 */
public abstract class RDFIndex extends AbstractIndex {

    public final static String ID = RDFIndex.class.getName();
    private final static Logger LOG = LogManager.getLogger(RDFIndex.class);
    protected HashMap workers = new HashMap();

    @Override
    public boolean checkIndex(DBBroker broker) {
        return getWorker(broker).checkIndex(broker);
    }
    
    @Override
    public String getIndexId(){
        return ID;
    }

}
