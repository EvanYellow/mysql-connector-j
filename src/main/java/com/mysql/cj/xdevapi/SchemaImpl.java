/*
  Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package com.mysql.cj.xdevapi;

import java.util.List;
import java.util.stream.Collectors;

import com.mysql.cj.api.xdevapi.Collection;
import com.mysql.cj.api.xdevapi.Schema;
import com.mysql.cj.api.xdevapi.Session;
import com.mysql.cj.api.xdevapi.Table;
import com.mysql.cj.core.exceptions.MysqlErrorNumbers;
import com.mysql.cj.core.exceptions.WrongArgumentException;
import com.mysql.cj.x.core.DatabaseObjectDescription;
import com.mysql.cj.x.core.MysqlxSession;
import com.mysql.cj.x.core.XDevAPIError;

public class SchemaImpl implements Schema {
    private MysqlxSession mysqlxSession;
    private Session session;
    private String name;

    /* package private */ SchemaImpl(MysqlxSession mysqlxSession, Session session, String name) {
        this.mysqlxSession = mysqlxSession;
        this.session = session;
        this.name = name;
    }

    public Session getSession() {
        return this.session;
    }

    public Schema getSchema() {
        return this;
    }

    public String getName() {
        return this.name;
    }

    public DbObjectStatus existsInDatabase() {
        return this.mysqlxSession.schemaExists(this.name) ? DbObjectStatus.EXISTS : DbObjectStatus.NOT_EXISTS;
    }

    public List<Collection> getCollections() {
        return this.mysqlxSession.getObjectNamesOfType(this.name, DbObjectType.COLLECTION).stream().map(this::getCollection).collect(Collectors.toList());
    }

    public List<Collection> getCollections(String pattern) {
        return this.mysqlxSession.getObjectNamesOfType(this.name, pattern, DbObjectType.COLLECTION).stream().map(this::getCollection)
                .collect(Collectors.toList());
    }

    public List<Table> getTables() {
        // TODO we need to consider lower_case_table_names server variable for some cases
        return this.mysqlxSession.getObjectNamesOfType(this.name, DbObjectType.TABLE, DbObjectType.VIEW, DbObjectType.COLLECTION_VIEW).stream()
                .map(this::getTable).collect(Collectors.toList());
    }

    public List<Table> getTables(String pattern) {
        return this.mysqlxSession.getObjectNamesOfType(this.name, pattern, DbObjectType.TABLE, DbObjectType.VIEW, DbObjectType.COLLECTION_VIEW).stream()
                .map(this::getTable).collect(Collectors.toList());
    }

    public Collection getCollection(String collectionName) {
        return new CollectionImpl(this.mysqlxSession, this, collectionName);
    }

    public Collection getCollection(String collectionName, boolean requireExists) {
        CollectionImpl coll = new CollectionImpl(this.mysqlxSession, this, collectionName);
        if (requireExists && coll.existsInDatabase() != DbObjectStatus.EXISTS) {
            throw new WrongArgumentException(coll.toString() + " doesn't exist");
        }
        return coll;
    }

    public Table getCollectionAsTable(String collectionName) {
        return getTable(collectionName);
    }

    public Table getTable(String tableName) {
        return new TableImpl(this.mysqlxSession, this, tableName);
    }

    /* package private */ Table getTable(DatabaseObjectDescription descr) {
        return new TableImpl(this.mysqlxSession, this, descr);
    }

    public Table getTable(String tableName, boolean requireExists) {
        TableImpl table = new TableImpl(this.mysqlxSession, this, tableName);
        if (requireExists && table.existsInDatabase() != DbObjectStatus.EXISTS) {
            throw new WrongArgumentException(table.toString() + " doesn't exist");
        }
        return table;
    }

    public Collection createCollection(String collectionName) {
        this.mysqlxSession.createCollection(this.name, collectionName);
        return new CollectionImpl(this.mysqlxSession, this, collectionName);
    }

    public Collection createCollection(String collectionName, boolean reuseExistingObject) {
        try {
            return createCollection(collectionName);
        } catch (XDevAPIError ex) {
            if (ex.getErrorCode() == MysqlErrorNumbers.ER_TABLE_EXISTS_ERROR) {
                return getCollection(collectionName);
            }
            throw ex;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == SchemaImpl.class && ((SchemaImpl) other).session == this.session
                && ((SchemaImpl) other).mysqlxSession == this.mysqlxSession && this.name.equals(((SchemaImpl) other).name);
    }

    @Override
    public int hashCode() {
        assert false : "hashCode not designed";
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Schema(");
        sb.append(ExprUnparser.quoteIdentifier(this.name));
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void dropCollection(String collectionName) {
        try {
            this.mysqlxSession.dropCollection(this.name, collectionName);
        } catch (XDevAPIError e) {
            // If specified object does not exist, dropX() methods succeed (no error is reported)
            // TODO check MySQL > 8.0.1 for built in solution, like passing ifExists to dropView
            if (e.getErrorCode() != MysqlErrorNumbers.ER_BAD_TABLE_ERROR) {
                throw e;
            }
        }
    }
}