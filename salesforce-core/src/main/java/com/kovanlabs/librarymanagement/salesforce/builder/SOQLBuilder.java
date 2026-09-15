package com.kovanlabs.librarymanagement.salesforce.builder;

import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.enums.SalesforceOperator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
public class SOQLBuilder<T> {

    private final List<String> selectFields = new ArrayList<>();
    private boolean isCount = false;
    private String fromObject;
    private final List<String> whereClauses = new ArrayList<>();
    private final List<String> orderByClauses = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    public SOQLBuilder() {
    }

    public static SOQLBuilder<Object> of() {
        return new SOQLBuilder<>();
    }

    public static SOQLBuilder<Object> selectFields(String... fields) {
        SOQLBuilder<Object> builder = new SOQLBuilder<>();
        if (fields != null) {
            builder.selectFields.addAll(Arrays.asList(fields));
        }
        return builder;
    }

    public static SOQLBuilder<Object> selectCount() {
        SOQLBuilder<Object> builder = new SOQLBuilder<>();
        builder.isCount = true;
        return builder;
    }

    public SOQLBuilder<T> count() {
        this.isCount = true;
        return this;
    }

    public SOQLBuilder<T> select(String... fields) {
        if (fields != null) {
            for (String field : fields) {
                if (field != null && !field.isBlank()) {
                    this.selectFields.add(field);
                }
            }
        }
        return this;
    }

    public SOQLBuilder<T> select(Collection<String> fields) {
        if (fields != null) {
            this.selectFields.addAll(fields);
        }
        return this;
    }

    public SOQLBuilder<T> field(String field) {
        if (field != null && !field.isBlank()) {
            this.selectFields.add(field);
        }
        return this;
    }

    public SOQLBuilder<T> fields(String... fields) {
        if (fields != null) {
            for (String field : fields) {
                if (field != null && !field.isBlank()) {
                    this.selectFields.add(field);
                }
            }
        }
        return this;
    }

    public SOQLBuilder<T> from(String objectName) {
        this.fromObject = objectName;
        return this;
    }

    public SOQLBuilder<T> from(SObject object) {
        if (object != null) {
            this.fromObject = object.getObjectName();
        }
        return this;
    }

    public SOQLBuilder<T> where(String condition) {
        if (condition != null && !condition.isBlank()) {
            this.whereClauses.add(condition);
        }
        return this;
    }

    public SOQLBuilder<T> where(String field, SalesforceOperator operator, Object value) {
        if (field != null && !field.isBlank() && operator != null) {
            String formattedVal = formatValue(value);
            this.whereClauses.add(field + " " + operator.getOperator() + " " + formattedVal);
        }
        return this;
    }

    public SOQLBuilder<T> whereNotNull(String field) {
        if (field != null && !field.isBlank()) {
            this.whereClauses.add(field + " != null");
        }
        return this;
    }

    public SOQLBuilder<T> whereEquals(String field, String value) {
        if (field != null && !field.isBlank()) {
            this.whereClauses.add(field + " = '" + escape(value) + "'");
        }
        return this;
    }

    public SOQLBuilder<T> orderBy(String field, boolean ascending) {
        if (field != null && !field.isBlank()) {
            this.orderByClauses.add(field + (ascending ? " ASC" : " DESC"));
        }
        return this;
    }

    public SOQLBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SOQLBuilder<T> offset(int offset) {
        this.offset = offset;
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        if (isCount) {
            sb.append("COUNT()");
        } else {
            if (selectFields.isEmpty()) {
                throw new IllegalStateException("SOQL query must specify at least one field to select");
            }
            sb.append(String.join(", ", selectFields));
        }

        if (fromObject == null || fromObject.isBlank()) {
            throw new IllegalStateException("SOQL query must specify a FROM sObject");
        }
        sb.append(" FROM ").append(fromObject);

        if (!whereClauses.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" " + SalesforceOperator.AND.getOperator() + " ", whereClauses));
        }

        if (!orderByClauses.isEmpty()) {
            sb.append(" ORDER BY ").append(String.join(", ", orderByClauses));
        }

        if (limit != null) {
            sb.append(" LIMIT ").append(limit);
        }

        if (offset != null) {
            sb.append(" OFFSET ").append(offset);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return build();
    }

    private static String formatValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof Number || val instanceof Boolean) {
            return String.valueOf(val);
        }
        return "'" + escape(val.toString()) + "'";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "\\'");
    }
}
