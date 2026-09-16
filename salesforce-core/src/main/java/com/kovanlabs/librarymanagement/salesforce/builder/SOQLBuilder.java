package com.kovanlabs.librarymanagement.salesforce.builder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.enums.SalesforceOperator;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public static <E> SOQLBuilder<E> fromSObjectClass(Class<E> sObjectClass) {
        SOQLBuilder<E> builder = new SOQLBuilder<>();
        builder.selectFields.addAll(extractSelectFields(sObjectClass, null));

        if (sObjectClass.equals(ContactSObject.class)) {
            builder.from(ContactSObject.SOBJECT_NAME);
        } else if (sObjectClass.equals(BookSObject.class)) {
            builder.from(BookSObject.SOBJECT_NAME);
        } else if (sObjectClass.equals(BorrowSObject.class)) {
            builder.from(BorrowSObject.SOBJECT_NAME);
        }

        return builder;
    }

    private static List<String> extractSelectFields(Class<?> clazz, String prefix) {
        if (clazz == null || clazz.equals(Object.class)) {
            return List.of();
        }

        return Arrays.stream(clazz.getDeclaredFields())
                .map(field -> mapFieldToSelectTokens(field, prefix))
                .flatMap(List::stream)
                .toList();
    }

    private static List<String> mapFieldToSelectTokens(Field field, String prefix) {
        return Optional.ofNullable(field.getAnnotation(JsonProperty.class))
                .map(JsonProperty::value)
                .filter(name -> !name.isBlank())
                .map(fieldName -> {
                    String fullPath = (prefix == null || prefix.isBlank()) ? fieldName : prefix + "." + fieldName;
                    Class<?> type = field.getType();
                    if (type.equals(ContactSObject.class) || type.equals(BookSObject.class) || type.equals(BorrowSObject.class)) {
                        return extractSelectFields(type, fullPath);
                    }
                    return List.of(fullPath);
                })
                .orElseGet(List::of);
    }

    public static SOQLBuilder<Object> selectFields(String... fields) {
        SOQLBuilder<Object> builder = new SOQLBuilder<>();
        if (fields != null) {
            Arrays.stream(fields)
                    .filter(Objects::nonNull)
                    .filter(f -> !f.isBlank())
                    .forEach(builder.selectFields::add);
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
            Arrays.stream(fields)
                    .filter(Objects::nonNull)
                    .filter(f -> !f.isBlank())
                    .forEach(this.selectFields::add);
        }
        return this;
    }

    public SOQLBuilder<T> select(Collection<String> fields) {
        if (fields != null) {
            fields.stream()
                    .filter(Objects::nonNull)
                    .filter(f -> !f.isBlank())
                    .forEach(this.selectFields::add);
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
        return select(fields);
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
        if (!isCount && selectFields.isEmpty()) {
            throw new IllegalStateException("SOQL query must specify at least one field to select");
        }
        if (fromObject == null || fromObject.isBlank()) {
            throw new IllegalStateException("SOQL query must specify a FROM sObject");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(isCount ? "SELECT COUNT()" : "SELECT " + String.join(", ", selectFields));
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
