/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * This file contains original code and/or modifications of original code.
 * Any modifications made by VoltDB L.L.C. are licensed under the following
 * terms and conditions:
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */
/* Copyright (C) 2008 by H-Store Project
 * Brown University
 * Massachusetts Institute of Technology
 * Yale University
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

#ifndef HSTORETABLEFACTORY_H
#define HSTORETABLEFACTORY_H

#include <string>
#include <vector>
#include "boost/shared_ptr.hpp"
#include "common/ids.h"
#include "common/types.h"
#include "common/TupleSchema.h"
#include "common/Pool.hpp"
#include "indexes/tableindex.h"
#include "indexes/tableindexfactory.h"

namespace voltdb {

class Table;
class PersistentTable;
class SerializeInput;
class TempTable;
class TableColumn;
class TableIndex;
class ExecutorContext;

class TableFactory {
public:
    /**
    * Creates an empty persistent table with given name and columns.
    * Every PersistentTable must be instantiated via this method.
    * Also, columns can't be added/changed/removed after a PersistentTable
    * instance is made. TableColumn is immutable.
    * In the same way, Indexes, Primary Keys, Constraints are immutable
    * to make the classes easy to maintain.
    */
    static Table* getPersistentTable(
        voltdb::CatalogId databaseId,
        CatalogId tableId,
        ExecutorContext *ctx,
        const std::string &name,
        TupleSchema* schema,
        const std::string* columnNames,
        int partitionColumn,
        bool exportEnabled,
        bool exportOnly);

    /**
    * Creates an empty persistent table with given ID, name, columns and PK index.
    */
    static Table* getPersistentTable(
        voltdb::CatalogId databaseId,
        CatalogId tableId,
        ExecutorContext *ctx,
        const std::string &name,
        TupleSchema* schema,
        const std::string* columnNames,
        const TableIndexScheme &pkey_index,
        int partitionColumn,
        bool exportEnabled,
        bool exportOnly);


    /**
    * Creates an empty persistent table with given name, columns and indexes.
    */
    static Table* getPersistentTable(
        voltdb::CatalogId databaseId,
        CatalogId tableId,
        ExecutorContext *ctx,
        const std::string &name,
        TupleSchema* schema,
        const std::string* columnNames,
        const std::vector<TableIndexScheme> &indexes,
        int partitionColumn,
        bool exportEnabled,
        bool exportOnly);


    /**
    * Creates an empty persistent table with given name, columns, PK index and indexes.
    */
    static Table* getPersistentTable(
        voltdb::CatalogId databaseId,
        CatalogId tableId,
        ExecutorContext *ctx,
        const std::string &name,
        TupleSchema* schema,
        const std::string* columnNames,
        const TableIndexScheme &pkeyIndex,
        const std::vector<TableIndexScheme> &indexes,
        int partitionColumn,
        bool exportEnabled,
        bool exportOnly);


    /**
    * Creates an empty temp table with given name and columns.
    * Every TempTable must be instantiated via these factory methods.
    * TempTable doesn't have constraints or indexes. Also, insert/delete/update
    * of tuples doesn't involve Undolog.
    */
    static TempTable* getTempTable(
        voltdb::CatalogId databaseId,
        const std::string &name,
        TupleSchema* schema,
        const std::string* columnNames,
        int* tempTableMemoryInBytes);

    /**
    * Creates an empty temp table with given template table.
    */
    static TempTable* getCopiedTempTable(
        const voltdb::CatalogId databaseId,
        const std::string &name,
        const Table* templateTablezz,
        int* tempTableMemoryInBytes);

private:
    static void initConstraints(PersistentTable* table);
    static void initCommon(
        voltdb::CatalogId databaseId,
        Table *table,
        const std::string &name,
        TupleSchema *schema,
        const std::string *columnNames,
        const bool ownsTupleSchema);
};

}

#endif
