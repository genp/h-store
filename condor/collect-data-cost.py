#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import sys
import math
import string
import glob

TYPE = sys.argv[1].lower()
LOWER_BOUNDS = float(sys.argv[2])

BENCHMARKS = [ 'tm1', 'tpcc', 'airline', 'auctionmark', 'tpce' ]
RESULTS = { }

for benchmark in BENCHMARKS:
    for f in glob("output/CostEstimate/%s/%s*.out" % (benchmark, benchmark)):
        
    
    costs = [ ]
    upper_bound = float(sys.argv[3]) if len(sys.argv) > 3 else -1
    
    for factor in FACTORS:
        factor_str = "" if TYPE == "lowerbounds" else str(factor)
        f = "output/%s/%s/%s-1.%s%s.costs" % (DATA_DIR, benchmark, benchmark, factor_str, DATA_EXT)
        if not os.path.exists(f):
            print "Missing '%s'" % f
            continue

        best_cost = None
        first_time = None
        first_cost = None
        inner_costs = [ ]
        with open(f, 'r') as f:
            ## Go backwards until the cost goes up
            for line in map(string.strip, reversed(f.readlines())):
                if line.startswith("--"): continue
                #print line
                data = line.split("\t")
                assert len(data) == 2
                time = int(data[0])
                cost = float(data[1])
                if first_time == None:
                    best_cost = first_cost = cost
                elif first_time < time:
                    break
                else:
                    first_cost = cost
                first_time = time
                inner_costs.append((time, cost))
            ## FOR
        ## WITH
        assert first_cost
        assert best_cost
        upper_bound = max(upper_bound, first_cost)
        
        #print "first_cost:", first_cost
        #print "best_costs:", best_cost
        
        if TYPE == "lowerbounds":
            inner_costs.reverse()
            last_time, last_cost = inner_costs[-1]
            last_time /= FACTOR_DIV
            inner_costs.append((math.ceil(last_time / 60.0) * FACTOR_DIV * 60, last_cost))
            for time, cost in inner_costs:
                normalized = 1.0 - ((cost - LOWER_BOUNDS) / (upper_bound - LOWER_BOUNDS))
                costs.append( (time/FACTOR_DIV, normalized, cost, best_cost) )
            ## FOR
        else:
            normalized = 1.0 - ((best_cost - LOWER_BOUNDS) / (first_cost - LOWER_BOUNDS))
            costs.append( (factor/FACTOR_DIV, normalized, upper_bound, best_cost) )
    ## FOR
    
    if len(BENCHMARKS) > 1: print benchmark
    for factor, normalized, first_cost, best_cost in costs:
        print "%d\t%f\t%f\t%f" % (factor, normalized, first_cost, best_cost)
    print
## FOR


    
