/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.teiid.jboss;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.dmr.ModelType;
import org.teiid.adminapi.Admin;

class OperationsConstants {
    public static final SimpleAttributeDefinition SESSION = SimpleAttributeDefinitionBuilder.create("session", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition VDB_NAME = SimpleAttributeDefinitionBuilder.create("vdb-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition VDB_VERSION = SimpleAttributeDefinitionBuilder.create("vdb-version", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition EXECUTION_ID = SimpleAttributeDefinitionBuilder.create("execution-id", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition CACHE_TYPE = new SimpleAttributeDefinitionBuilder("cache-type", ModelType.STRING) //$NON-NLS-1$
        .setNullSignificant(true)
        .setAllowExpression(false)
        .setAllowedValues(Admin.Cache.PREPARED_PLAN_CACHE.name(), Admin.Cache.QUERY_SERVICE_RESULT_SET_CACHE.name())
        .build();
    public static final SimpleAttributeDefinition XID = SimpleAttributeDefinitionBuilder.create("xid", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition DATA_ROLE = SimpleAttributeDefinitionBuilder.create("data-role", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition MAPPED_ROLE = SimpleAttributeDefinitionBuilder.create("mapped-role", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition CONNECTION_TYPE = SimpleAttributeDefinitionBuilder.create("connection-type", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition MODEL_NAME = SimpleAttributeDefinitionBuilder.create("model-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition SOURCE_NAME = SimpleAttributeDefinitionBuilder.create("source-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition DS_NAME = SimpleAttributeDefinitionBuilder.create("ds-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition RAR_NAME = SimpleAttributeDefinitionBuilder.create("rar-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition MODEL_NAMES = SimpleAttributeDefinitionBuilder.create("model-names", ModelType.STRING, true).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition SOURCE_VDBNAME = SimpleAttributeDefinitionBuilder.create("source-vdb-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition SOURCE_VDBVERSION = SimpleAttributeDefinitionBuilder.create("source-vdb-version", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition TARGET_VDBNAME = SimpleAttributeDefinitionBuilder.create("target-vdb-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition TARGET_VDBVERSION = SimpleAttributeDefinitionBuilder.create("target-vdb-version", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition SQL_QUERY = SimpleAttributeDefinitionBuilder.create("sql-query", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition TIMEOUT_IN_MILLI = SimpleAttributeDefinitionBuilder.create("timeout-in-milli", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition TRANSLATOR_NAME = SimpleAttributeDefinitionBuilder.create("translator-name", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition PROPERTY_TYPE = SimpleAttributeDefinitionBuilder.create("type", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition ENTITY_TYPE = SimpleAttributeDefinitionBuilder.create("entity-type", ModelType.STRING, true).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition ENTITY_PATTERN = SimpleAttributeDefinitionBuilder.create("entity-pattern", ModelType.STRING, true).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition FORMAT = new SimpleAttributeDefinitionBuilder("format", ModelType.STRING) //$NON-NLS-1$
            .setNullSignificant(false)
            .setAllowExpression(false)
            .build();
    public static final SimpleAttributeDefinition INCLUDE_SOURCE = SimpleAttributeDefinitionBuilder.create("include-source", ModelType.BOOLEAN, true).build(); //$NON-NLS-1$

    public static final SimpleAttributeDefinition OPTIONAL_VDB_NAME = SimpleAttributeDefinitionBuilder.create("vdb-name", ModelType.STRING, true).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition OPTIONAL_VDB_VERSION = SimpleAttributeDefinitionBuilder.create("vdb-version", ModelType.STRING, true).build(); //$NON-NLS-1$


    public static final SimpleAttributeDefinition DBNAME = SimpleAttributeDefinitionBuilder.create("vdb-name", ModelType.STRING, true).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition VERSION = SimpleAttributeDefinitionBuilder.create("vdb-version", ModelType.STRING, true).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition SCHEMA = SimpleAttributeDefinitionBuilder.create("schema", ModelType.STRING, true).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition DDL = SimpleAttributeDefinitionBuilder.create("ddl", ModelType.STRING, false).build(); //$NON-NLS-1$
    public static final SimpleAttributeDefinition PERSIST = SimpleAttributeDefinitionBuilder.create("persist", ModelType.BOOLEAN, false).build(); //$NON-NLS-1$

    public static final SimpleAttributeDefinition INCLUDE_SCHEMA = SimpleAttributeDefinitionBuilder.create("include-schema", ModelType.BOOLEAN, true).build(); //$NON-NLS-1$
}
