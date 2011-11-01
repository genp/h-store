#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import sys
import math
import string
import re
import glob
from pprint import pprint

BENCHMARKS = [ 'tm1', 'tpcc', 'airline', 'auctionmark', 'tpce' ]
RESULTS = { }

COSTS = [ 'EXEC', 'SKEW' ]
REGEXES = { }
for c in COSTS:
    REGEXES[c] = re.compile("%s COST:[\s]+([\d\.]+)" % c, re.IGNORECASE)
## FOR
P_REGEX = re.compile("PARTITIONS:[\s]+([\d]+)", re.IGNORECASE)

for benchmark in BENCHMARKS:
    PPLAN_REGEX = re.compile("%s\.([\w]+)\.pplan" % benchmark, re.IGNORECASE)
        
    RESULTS[benchmark] = { }
    for f in glob.glob("output/CostEstimate/%s/%s*.out" % (benchmark, benchmark)):
        print f
        with open(f, 'r') as f:
            contents = f.read()
            
            match = PPLAN_REGEX.search(contents)
            if not match:
                print "WARN: Missing PARTITION PLAN DESIGNER in %s" % f
                continue
            design = match.group(1)
            if not design in RESULTS[benchmark]:
                RESULTS[benchmark][design] = { }
            
            match = P_REGEX.search(contents)
            if not match:
                print "WARN: Missing PARTITIONS in %s" % f
                continue
            partitions = int(match.group(1))
            RESULTS[benchmark][design][partitions] = { }
            if not partitions in RESULTS[benchmark][design]:
                RESULTS[benchmark][design][partitions] = { }
            
            for c in COSTS:
                match = REGEXES[c].search(contents)
                assert match, "Missing %s in %s" % (c, f)
                val = float(match.group(1))
                if c == "EXEC": val *= 0.1
                RESULTS[benchmark][design][partitions][c] = val
            ## FOR
        ## WITH
    ## FOR
## FOR

expected = [ 'lns', 'schism', 'greedy', 'pkey', 'popular' ]
for b in RESULTS.keys():
    print b
    for d in expected:
        vals = [ ]
        for p in sorted(RESULTS[b][d].keys()):
            vals.append(RESULTS[b][d][p]["EXEC"])
            vals.append(RESULTS[b][d][p]["SKEW"])
        ## FOR
        print " ".join(map(str, vals))
    print
## FOR

    
