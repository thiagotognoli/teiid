package org.teiid.translator.jdbc.redshift;

import org.teiid.language.DerivedColumn;
import org.teiid.language.Literal;
import org.teiid.language.SQLConstants;
import org.teiid.translator.TypeFacility;
import org.teiid.translator.jdbc.postgresql.PostgreSQLConversionVisitor;
import org.teiid.translator.jdbc.postgresql.PostgreSQLExecutionFactory;

import static org.teiid.language.SQLConstants.Reserved.AS;

public class RedshiftSQLConversionVisitor extends PostgreSQLConversionVisitor {
    public RedshiftSQLConversionVisitor(PostgreSQLExecutionFactory ef) {
        super(ef);
    }

    /**
     * Some literals in the select need a cast to prevent being seen as the unknown/string type
     *
     * @param obj Derived Column to visit.
     */
    @Override
    public void visit(DerivedColumn obj) {
        // Unlike the PostgreSQL implementation that casts strings to byte padded character, the Redshift translator should simply use the String.
        if (obj.getExpression() instanceof Literal && obj.getExpression().getType() == TypeFacility.RUNTIME_TYPES.STRING) {
            append(obj.getExpression());
            if (obj.getAlias() != null) {
                buffer.append(SQLConstants.Tokens.SPACE)
                        .append(AS)
                        .append(SQLConstants.Tokens.SPACE)
                        .append(obj.getAlias());
            }
        } else {
            super.visit(obj);
        }
    }
}
