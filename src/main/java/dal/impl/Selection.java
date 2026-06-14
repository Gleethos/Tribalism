package dal.impl;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 *  The outcome of resolving a property selector (a method reference like {@code Model::field})
 *  in the fluent query API. A selection knows how to render itself both as a {@code WHERE}
 *  predicate and as an {@code ORDER BY} expression.
 *  <p>
 *  There are two flavours:
 *  <ul>
 *      <li>{@link Flat} — a plain column on the queried model table (the classic case).</li>
 *      <li>{@link Zoom} — a "zoom lens" into a {@link dal.api.Value} held by the model, possibly
 *          several {@code Value} hops deep. It is rendered as nested {@code IN (SELECT id ...)}
 *          sub-queries so the comparison happens against the leaf column in the value table.</li>
 *  </ul>
 */
@NullMarked
sealed interface Selection {

    /**
     *  Renders the full {@code WHERE} predicate for this selection given the operator fragment
     *  (e.g. {@code "= ?"}, {@code "IN (?, ?)"}, {@code "IS NULL"}).
     */
    String wherePredicate(String operatorFragment);

    /** Renders the expression used after {@code ORDER BY} (a column, or a scalar sub-query). */
    String orderByExpression();

    /** A flat column on the model table itself. */
    record Flat(EntityTableField field) implements Selection {
        @Override public String wherePredicate(String operatorFragment) {
            return field.name() + " " + operatorFragment;
        }
        @Override public String orderByExpression() {
            return field.name();
        }
    }

    /**
     *  A zoom into one or more nested {@link dal.api.Value}s.
     *
     * @param rootColumn  The foreign-key column on the model table that points at the first value
     *                    table (e.g. {@code fk_state_id}).
     * @param tables      The value tables traversed, outermost first: {@code [vt0, vt1, ... vtN]}.
     * @param links       The foreign-key columns linking each table to the next; {@code links[i]}
     *                    lives in {@code tables[i]} and points at {@code tables[i+1]}.
     *                    Its size is always {@code tables.size() - 1}.
     * @param leafColumn  The primitive column in the final table that the comparison targets.
     */
    record Zoom(String rootColumn, List<String> tables, List<String> links, String leafColumn) implements Selection {

        @Override public String wherePredicate(String operatorFragment) {
            // fk IN (SELECT id FROM vt0 WHERE link0 IN (SELECT id FROM vt1 WHERE ... leaf <op> ) ... )
            StringBuilder sb = new StringBuilder();
            sb.append(rootColumn).append(" IN (SELECT ").append(EntityTable.ID)
              .append(" FROM ").append(tables.get(0)).append(" WHERE ");
            for ( int i = 0; i < links.size(); i++ ) {
                sb.append(links.get(i)).append(" IN (SELECT ").append(EntityTable.ID)
                  .append(" FROM ").append(tables.get(i + 1)).append(" WHERE ");
            }
            sb.append(leafColumn).append(' ').append(operatorFragment);
            sb.append(")".repeat(tables.size())); // one closing paren per opened sub-query
            return sb.toString();
        }

        @Override public String orderByExpression() {
            // A correlated scalar sub-query that drills down to the leaf column:
            //   (SELECT leaf FROM vtN WHERE id = (SELECT linkN-1 FROM vtN-1 WHERE id = ... rootColumn))
            String idExpr = rootColumn; // resolves to the id within tables[0]
            for ( int k = 0; k < links.size(); k++ )
                idExpr = "(SELECT " + links.get(k) + " FROM " + tables.get(k)
                       + " WHERE " + EntityTable.ID + " = " + idExpr + ")";
            return "(SELECT " + leafColumn + " FROM " + tables.get(tables.size() - 1)
                 + " WHERE " + EntityTable.ID + " = " + idExpr + ")";
        }
    }
}
