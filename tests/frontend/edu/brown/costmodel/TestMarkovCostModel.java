package edu.brown.costmodel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.voltdb.VoltProcedure;
import org.voltdb.benchmark.tpcc.procedures.neworder;
import org.voltdb.catalog.CatalogType;
import org.voltdb.catalog.Procedure;
import org.voltdb.types.ExpressionType;

import edu.brown.BaseTestCase;
import edu.brown.catalog.CatalogUtil;
import edu.brown.correlations.ParameterCorrelations;
import edu.brown.costmodel.MarkovCostModel.Penalty;
import edu.brown.markov.EstimationThresholds;
import edu.brown.markov.MarkovGraph;
import edu.brown.markov.MarkovGraphsContainer;
import edu.brown.markov.MarkovUtil;
import edu.brown.markov.TransactionEstimator;
import edu.brown.markov.Vertex;
import edu.brown.markov.TransactionEstimator.State;
import edu.brown.utils.CollectionUtil;
import edu.brown.utils.ProjectType;
import edu.brown.workload.AbstractTraceElement;
import edu.brown.workload.TransactionTrace;
import edu.brown.workload.Workload;
import edu.brown.workload.filters.BasePartitionTxnFilter;
import edu.brown.workload.filters.MultiPartitionTxnFilter;
import edu.brown.workload.filters.ProcParameterArraySizeFilter;
import edu.brown.workload.filters.ProcParameterValueFilter;
import edu.brown.workload.filters.ProcedureLimitFilter;
import edu.brown.workload.filters.ProcedureNameFilter;

public class TestMarkovCostModel extends BaseTestCase {

    private static final Class<? extends VoltProcedure> TARGET_PROCEDURE = neworder.class;
    private static final int WORKLOAD_XACT_LIMIT = 500;
    private static final int BASE_PARTITION = 1;
    private static final int NUM_PARTITIONS = 5;
    private static final EstimationThresholds thresholds = new EstimationThresholds();
    private static final Random rand = new Random();

    private static Workload workload;
    private static MarkovGraphsContainer markovs;
    private static ParameterCorrelations correlations;
    private static MarkovCostModel costmodel;
    private static Procedure catalog_proc;
    private static TransactionEstimator t_estimator;

    private MarkovGraph markov;
    private TransactionTrace txn_trace;
    private State txn_state;
    private List<Vertex> estimated_path;
    private List<Vertex> actual_path;
    
    @Before
    public void setUp() throws Exception {
        super.setUp(ProjectType.TPCC);
        this.addPartitions(NUM_PARTITIONS);

        if (markovs == null) {
            catalog_proc = this.getProcedure(TARGET_PROCEDURE);
            
            File file = this.getCorrelationsFile(ProjectType.TPCC);
            correlations = new ParameterCorrelations();
            correlations.load(file.getAbsolutePath(), catalog_db);

            file = this.getWorkloadFile(ProjectType.TPCC, "100w.large");
            workload = new Workload(catalog);

            // Check out this beauty:
            // (1) Filter by procedure name
            // (2) Filter on partitions that start on our BASE_PARTITION
            // (3) Filter to only include multi-partition txns
            // (4) Another limit to stop after allowing ### txns
            // Where is your god now???
            Workload.Filter filter = new ProcedureNameFilter()
                    .include(TARGET_PROCEDURE.getSimpleName())
                    .attach(new ProcParameterValueFilter().include(1, new Long(5))) // D_ID
                    .attach(new ProcParameterArraySizeFilter(CatalogUtil.getArrayProcParameters(catalog_proc).get(0), 10, ExpressionType.COMPARE_EQUAL))
                    .attach(new BasePartitionTxnFilter(p_estimator, BASE_PARTITION))
                    .attach(new MultiPartitionTxnFilter(p_estimator))
                    .attach(new ProcedureLimitFilter(WORKLOAD_XACT_LIMIT));
            workload.load(file.getAbsolutePath(), catalog_db, filter);
            
            // Make a copy that doesn't have the first TransactionTrace
            Workload clone = new Workload(workload, new Workload.Filter() {
                private boolean first = true;
                @Override
                protected FilterResult filter(AbstractTraceElement<? extends CatalogType> element) {
                    if (element instanceof TransactionTrace && first) {
                        this.first = false;
                        return (FilterResult.SKIP);
                    }
                    return FilterResult.ALLOW;
                }
                @Override
                protected String debug() { return null; }
                @Override
                protected void resetImpl() { }
            });
            assert(workload.getTransactions().get(0).getTransactionId() != clone.getTransactions().get(0).getTransactionId());
            
            // assertEquals(WORKLOAD_XACT_LIMIT, workload.getTransactionCount());

            // for (TransactionTrace xact : workload.getTransactions()) {
            // System.err.println(xact + ": " + p_estimator.getAllPartitions(xact));
            // }

            // Generate MarkovGraphs per base partition
//            file = this.getMarkovFile(ProjectType.TPCC);
//            markovs = MarkovUtil.load(catalog_db, file.getAbsolutePath());
            markovs = MarkovUtil.createBasePartitionGraphs(catalog_db, clone, p_estimator);
            assertNotNull(markovs);
            
            // And then populate the MarkovCostModel
            t_estimator = new TransactionEstimator(p_estimator, correlations, markovs);
            costmodel = new MarkovCostModel(catalog_db, p_estimator, t_estimator, thresholds);
        }
        
        // Take a TransactionTrace and throw it at the estimator to get our path info
        this.txn_trace = workload.getTransactions().get(0);
        assertNotNull(this.txn_trace);
        
        this.txn_state = t_estimator.processTransactionTrace(txn_trace);
        assertNotNull(this.txn_state);
        this.markov = markovs.get(BASE_PARTITION, catalog_proc);
        assertNotNull(this.markov);
        
        this.estimated_path = this.txn_state.getEstimatedPath();
        assertNotNull(this.estimated_path);
        assert(this.estimated_path.isEmpty() == false);
        this.actual_path = this.txn_state.getActualPath();
        assertNotNull(this.actual_path);
        assert(this.actual_path.isEmpty() == false);
    }

    /**
     * testComparePathsFast
     */
    @Test
    public void testComparePathsFast() throws Exception {
        List<Vertex> clone = new ArrayList<Vertex>(this.actual_path);
        
        // At the very least the exact clone should be equal
        assert(costmodel.comparePathsFast(clone, this.actual_path));
        
        // Test to make sure that it catches when one path aborts while the other commits
        this.actual_path.add(markov.getCommitVertex());
        clone.add(markov.getCommitVertex());
        assert(costmodel.comparePathsFast(clone, this.actual_path));
        clone.set(clone.size()-1, markov.getAbortVertex());
        assertFalse(costmodel.comparePathsFast(clone, this.actual_path));
        this.actual_path.remove(this.actual_path.size()-1);
        
        // Now check to make sure that it catches when the read/write differ
        clone.get(1).partitions.add(Integer.MAX_VALUE);
        assertEquals(false, costmodel.comparePathsFast(clone, this.actual_path));
    }
    
    /**
     * testComparePathsFull_Penalty1
     */
    @Test
    public void testComparePathsFull_Penalty1() throws Exception {
        // We have to call comparePathsFast first to setup some sets
        // We don't care what the outcome is here...
        costmodel.comparePathsFast(this.txn_state.getEstimatedPath(), this.txn_state.getActualPath());
        
        Vertex abort_v = markov.getAbortVertex();
        List<Vertex> actual = this.txn_state.getActualPath();
        actual.set(actual.size()-1, abort_v);
        double cost = costmodel.comparePathsFull(this.txn_state);
        
        List<Penalty> penalties = costmodel.getLastPenalties();
        assertNotNull(penalties);
        System.err.println(String.format("COST=%.03f PENALTIES=%s", cost, penalties));
        assert(penalties.contains(Penalty.MISSED_ABORT_MULTI) || penalties.contains(Penalty.MISSED_ABORT_SINGLE)); 
    }
    
    /**
     * testComparePathsFull_Penalty2
     */
    @Test
    public void testComparePathsFull_Penalty2() throws Exception {
        // We have to call comparePathsFast first to setup some sets
        // We don't care what the outcome is here...
        costmodel.comparePathsFast(this.txn_state.getEstimatedPath(), this.txn_state.getActualPath());
        
        // Remove all of the estimated read partitions except for one
        Set<Integer> e_read_partitions = costmodel.getLastEstimatedReadPartitions();
        assertNotNull(e_read_partitions);
        Set<Integer> retain = (Set<Integer>)CollectionUtil.addAll(new HashSet<Integer>(), CollectionUtil.getFirst(e_read_partitions)); 
        e_read_partitions.retainAll(retain);
        
        // Then add all of our partitions to the actual read partitions
        Set<Integer> a_read_partitions = costmodel.getLastActualReadPartitions();
        a_read_partitions.addAll(CatalogUtil.getAllPartitionIds(catalog_db));
        
        double cost = costmodel.comparePathsFull(this.txn_state);
        
        List<Penalty> penalties = costmodel.getLastPenalties();
        assertNotNull(penalties);
        System.err.println(String.format("COST=%.03f PENALTIES=%s", cost, penalties));
        assert(penalties.contains(Penalty.MISSING_READ_PARTITION)); 
    }
    
    /**
     * testCompareIncompletePath
     */
    @Test
    public void testCompareIncompletePath() throws Exception {
        // Then make sure that our cost model can handle paths where the estimated path isn't complete
        List<Vertex> tester = new ArrayList<Vertex>(this.actual_path);
        tester.removeAll(this.actual_path.subList(this.actual_path.size() - 5, this.actual_path.size()));
        /* FIXME
        double cost = costmodel.comparePathsFast(tester, this.actual_path);
        */
        // assert(cost > 0);
     }
}