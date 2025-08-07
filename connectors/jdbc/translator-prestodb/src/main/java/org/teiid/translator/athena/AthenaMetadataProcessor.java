package org.teiid.translator.athena;

import org.teiid.metadata.BaseColumn;
import org.teiid.metadata.Column;
import org.teiid.metadata.MetadataFactory;
import org.teiid.metadata.Table;
import org.teiid.translator.prestodb.PrestoDBMetadataProcessor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

public class AthenaMetadataProcessor extends PrestoDBMetadataProcessor {
    @Override
    protected List<String> getCatalogs(Connection conn) {
        return Collections.singletonList("AWSDataCatalog");
    }

    protected void addTable(String tableName, Connection conn, String catalog, String schema, MetadataFactory metadataFactory) throws SQLException {
        Table table = addTable(metadataFactory, null, null, tableName, null, tableName);
        if (table == null) {
            return;
        }
        String nis = catalog+"."+schema+"."+tableName; //$NON-NLS-1$ //$NON-NLS-2$
        table.setNameInSource(nis);

        Statement stmt = conn.createStatement();
        ResultSet rs =  stmt.executeQuery("SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE\n" +
                "FROM   information_schema.columns\n" +
                "WHERE  table_schema = '" + schema + "'\n" +
                "       AND table_name = '" + tableName + "'"); //$NON-NLS-1$
        while (rs.next()){
            String name = rs.getString(1);
            if (this.trimColumnNames) {
                name = name.trim();
            }
            String type = rs.getString(2);
            if (type != null) {
                type = type.trim();
            }
            String runtimeType = getRuntimeType(type);

            BaseColumn.NullType nt = Boolean.valueOf(rs.getString(3))? BaseColumn.NullType.Nullable: BaseColumn.NullType.No_Nulls;

            Column column = metadataFactory.addColumn(name, runtimeType, table);
            column.setNameInSource(name);
            column.setUpdatable(true);
            column.setNullType(nt);
        }
        rs.close();
    }
}
