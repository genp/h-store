#!/usr/bin/env python

import os
import sys
import string

LOWER_BOUNDS = float(sys.argv[1])
BENCHMARK = "tpce"
TOTAL_TIME = 7200

for factor in range(300, 3000, 300):
    f = "%s-1.%dx%d.costs" % (BENCHMARK, factor, TOTAL_TIME)
    if not os.path.exists(f):
        print "Missing '%s'" % f
        continue

    best_cost = None
    first_time = None
    first_cost = None
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
        ## FOR
    ## WITH
    assert first_cost
    assert best_cost
    
    #print "first_cost:", first_cost
    #print "best_costs:", best_cost
    
    normalized = 1.0 - ((best_cost - LOWER_BOUNDS) / (first_cost - LOWER_BOUNDS))
    print "%d\t%f\t%f" % (factor/60, normalized, first_cost)
## FOR
