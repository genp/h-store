package edu.brown.costmodel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.voltdb.benchmark.tpcc.procedures.neworder;
import org.voltdb.catalog.CatalogType;
import org.voltdb.catalog.Procedure;
import org.voltdb.catalog.Table;

import edu.brown.BaseTestCase;
import edu.brown.utils.ProjectType;
import edu.brown.workload.Workload;
import edu.brown.workload.filters.ProcedureLimitFilter;

public class TestSingleSitedCostModelInvalidateCache extends BaseTestCase {

    private static final int WORKLOAD_LIMIT = 500;
    private static final int NUM_PARTITIONS = 32;
    
    // Reading the workload takes a long time, so we only want to do it once
    private static Workload workload;
    private static final Random rand = new Random(100);
    
    @Override
    protected void setUp() throws Exception {
        super.setUp(ProjectType.TPCC);
        this.addPartitions(NUM_PARTITIONS);
        
        // Super hack! Walk back the directories and find out workload directory
        if (workload == null) {
            File workload_file = this.getWorkloadFile(ProjectType.TPCC); 
            workload = new Workload(catalog);
            
            workload.load(workload_file.getAbsolutePath(), catalog_db, new ProcedureLimitFilter(WORKLOAD_LIMIT));
            assertEquals(WORKLOAD_LIMIT, workload.getTransactionCount());
            System.err.println(workload.getProcedureHistogram());
        }
    }
 
    private void validateCosts(SingleSitedCostModel cost_model, double expected, Collection<? extends CatalogType> items) throws Exception {
        // Now go through and invalidate each table once and then recompute the cost
        // We should always get the same cost back
        for (CatalogType catalog_item : items) {
            cost_model.invalidateCache(catalog_item);
            double cost = cost_model.estimateWorkloadCost(catalog_db, workload);
            assertEquals(catalog_item.getName(), expected, cost, 0.00001);
        } // FOR
        
        // We now want to do the reverse
        // We'll invalidate the cache for every element *but* one
        List<CatalogType> single = new ArrayList<CatalogType>(1);
        for (CatalogType catalog_item : items) {
            single.clear();
            single.add(catalog_item);
            for (CatalogType catalog_item2 : items) {
                if (catalog_item.equals(catalog_item2)) continue;
                cost_model.invalidateCache(catalog_item2);
            } // FOR
            double cost = cost_model.estimateWorkloadCost(catalog_db, workload);
            assertEquals(catalog_item.getName(), expected, cost, 0.00001);
        } // FOR
    }
    
    /**
     * testInvalidateCacheProcedures
     */
    public void testInvalidateCacheProcedures() throws Exception {
        // Calculate the total cost of the workload once
        final SingleSitedCostModel cost_model = new SingleSitedCostModel(catalog_db);
        final double expected = cost_model.estimateWorkloadCost(catalog_db, workload);
        assert(expected > 0);
        this.validateCosts(cost_model, expected, catalog_db.getProcedures());
    }

    /**
     * testInvalidateCacheTables
     */
    public void testInvalidateCacheTables() throws Exception {
        // Calculate the total cost of the workload once
        final SingleSitedCostModel cost_model = new SingleSitedCostModel(catalog_db);
        final double expected = cost_model.estimateWorkloadCost(catalog_db, workload);
        assert(expected > 0);
        
        List<Table> all_tables = new ArrayList<Table>(catalog_db.getTables());
        for (int i = 0; i < 4; i++) {
            Collections.shuffle(all_tables, rand);
            this.validateCosts(cost_model, expected, all_tables);    
        } // FOR
    }
    
    /**
     * testInvalidateCacheMixed
     */
    public void testInvalidateCacheMixed() throws Exception {
        // Calculate the total cost of the workload once
        final SingleSitedCostModel cost_model = new SingleSitedCostModel(catalog_db);
        final double expected = cost_model.estimateWorkloadCost(catalog_db, workload);
        assert(expected > 0);
        
        List<CatalogType> all_items = new ArrayList<CatalogType>();
        all_items.addAll(catalog_db.getTables());
        all_items.addAll(catalog_db.getProcedures());
        this.validateCosts(cost_model, expected, all_items);
    }
    
    /**
     * testInvalidateCacheQueries
     */
    public void testInvalidateCacheQueries() throws Exception {
        // Calculate the total cost of the workload once
        final SingleSitedCostModel cost_model = new SingleSitedCostModel(catalog_db);
        final double expected = cost_model.estimateWorkloadCost(catalog_db, workload);
        assert(expected > 0);
        
        Procedure catalog_proc = this.getProcedure(neworder.class);
        List<CatalogType> all_items = new ArrayList<CatalogType>(catalog_proc.getStatements());
        this.validateCosts(cost_model, expected, all_items);
    }
}
