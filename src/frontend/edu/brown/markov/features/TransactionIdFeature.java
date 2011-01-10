package edu.brown.markov.features;

import org.voltdb.catalog.Procedure;

import edu.brown.markov.FeatureSet;
import edu.brown.utils.PartitionEstimator;
import edu.brown.workload.TransactionTrace;

/**
 * 
 * @author pavlo
 */
public class TransactionIdFeature extends AbstractFeature {

    public TransactionIdFeature(PartitionEstimator p_estimator, Procedure catalog_proc) {
        super(p_estimator, catalog_proc, TransactionIdFeature.class);
    }

    
    @Override
    public void extract(FeatureSet fset, TransactionTrace txnTrace) throws Exception {
        fset.addFeature(txnTrace, this.getFeatureKey(), txnTrace.getTransactionId());
    }
    
    @Override
    public Object calculate(String key, TransactionTrace txnTrace) throws Exception {
        return (txnTrace.getTransactionId());
    }

}