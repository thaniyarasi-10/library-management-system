package com.kovanlabs.librarymanagement.salesforce.builder;

import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.enums.SalesforceOperator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder for constructing Salesforce Object Query Language (SOQL) queries.
 *
 * @param <T> Target model type
 */
public class SOQLBuilder<T> {

    private final List<String> fields = new ArrayList<>();
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

    public static SOQLBuilder<Object> selectCount() {
        SOQLBuilder<Object> builder = new SOQLBuilder<>();
        builder.isCount = true;
        return builder;
    }

    public SOQLBuilder<T> count() {
        this.isCount = true;
        return this;
    }

    public SOQLBuilder<T> select(String... selectedFields) {
        if (selectedFields != null) {
            fields.addAll(List.of(selectedFields));
        }
        return this;
    }

    public SOQLBuilder<T> select(Collection<String> selectedFields) {
        if (selectedFields != null) {
            fields.addAll(selectedFields);
        }
        return this;
    }

    public SOQLBuilder<T> from(SObject sObject) {
        if (sObject != null) {
            this.fromObject = sObject.getObjectName();
        }
        return this;
    }

    public SOQLBuilder<T> from(String objectName) {
        this.fromObject = objectName;
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
        if (!isCount && fields.isEmpty()) {
            throw new IllegalStateException("SOQL query must specify at least one field to select");
        }
        if (fromObject == null || fromObject.isBlank()) {
            throw new IllegalStateException("SOQL query must specify a FROM sObject");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(isCount ? "SELECT COUNT()" : "SELECT " + String.join(", ", fields));
        sb.append(" FROM ").append(fromObject);

        if (!whereClauses.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" " + SalesforceOperator.AND.getOperator() + " ", whereClauses));
        }

        if (!orderByClauses.isEmpty()) {
            sb.append(" ORDER BY ").append(String.join(", ", orderByClauses));
        }

        Optional.ofNullable(limit).ifPresent(l -> sb.append(" LIMIT ").append(l));
        Optional.ofNullable(offset).ifPresent(o -> sb.append(" OFFSET ").append(o));

        return sb.toString();
    }

    @Override
    public String toString() {
        return build();
    }

    private static String formatValue(Object val) {
        return switch (val) {
            case null -> "null";
            case Number num -> String.valueOf(num);
            case Boolean bool -> String.valueOf(bool);
            default -> "'" + escape(val.toString()) + "'";
        };
    }

    private static String escape(String value) {
        return (value == null) ? "" : value.replace("'", "\\'");
    }
}
