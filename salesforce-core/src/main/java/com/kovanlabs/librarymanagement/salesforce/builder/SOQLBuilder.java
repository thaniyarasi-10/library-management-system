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

/**
 * Fluent builder for constructing Salesforce Object Query Language (SOQL) queries.
 *
 * @param <T> The target model type (optional, for type-safe query building)
 */
public class SOQLBuilder<T> {

    private final List<String> selectFields = new ArrayList<>();
    private boolean isCount = false;
    private String fromObject;
    private final List<String> whereClauses = new ArrayList<>();
    private final List<String> orderByClauses = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    /**
     * Constructs a new empty {@link SOQLBuilder} instance.
     */
    public SOQLBuilder() {
    }

    /**
     * Creates a new generic {@link SOQLBuilder} instance.
     *
     * @return A new {@link SOQLBuilder} instance
     */
    public static SOQLBuilder<Object> of() {
        return new SOQLBuilder<>();
    }

    /**
     * Creates a {@link SOQLBuilder} pre-configured with fields and SObject table name extracted
     * from the given SObject class annotations (e.g. {@code @JsonProperty}).
     *
     * @param <E> The SObject model type
     * @param sObjectClass The class of the SObject (e.g., {@code ContactSObject.class})
     * @return A configured {@link SOQLBuilder} instance
     */
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

    /**
     * Recursively extracts field names mapped with {@code @JsonProperty} from the given class.
     *
     * @param clazz The SObject class to inspect
     * @param prefix Prefix for nested relationship fields (e.g., "Contact__r")
     * @return List of SOQL field paths
     */
    private static List<String> extractSelectFields(Class<?> clazz, String prefix) {
        if (clazz == null || clazz.equals(Object.class)) {
            return List.of();
        }

        return Arrays.stream(clazz.getDeclaredFields())
                .map(field -> mapFieldToSelectTokens(field, prefix))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Maps an individual field to its corresponding SOQL select token or recurses for nested objects.
     *
     * @param field The class field
     * @param prefix Field path prefix for nested objects
     * @return List of select tokens for this field
     */
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

    /**
     * Creates a {@link SOQLBuilder} with the specified field names to select.
     *
     * @param fields The field names to include in the SELECT clause
     * @return A new {@link SOQLBuilder} instance
     */
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

    /**
     * Creates a {@link SOQLBuilder} configured to perform a {@code SELECT COUNT()} query.
     *
     * @return A new {@link SOQLBuilder} instance configured for count queries
     */
    public static SOQLBuilder<Object> selectCount() {
        SOQLBuilder<Object> builder = new SOQLBuilder<>();
        builder.isCount = true;
        return builder;
    }

    /**
     * Sets this builder to generate a {@code SELECT COUNT()} query.
     *
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> count() {
        this.isCount = true;
        return this;
    }

    /**
     * Adds fields to the SELECT clause.
     *
     * @param fields The field names to add
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> select(String... fields) {
        if (fields != null) {
            Arrays.stream(fields)
                    .filter(Objects::nonNull)
                    .filter(f -> !f.isBlank())
                    .forEach(this.selectFields::add);
        }
        return this;
    }

    /**
     * Adds a collection of fields to the SELECT clause.
     *
     * @param fields Collection of field names to add
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> select(Collection<String> fields) {
        if (fields != null) {
            fields.stream()
                    .filter(Objects::nonNull)
                    .filter(f -> !f.isBlank())
                    .forEach(this.selectFields::add);
        }
        return this;
    }

    /**
     * Adds a single field to the SELECT clause.
     *
     * @param field The field name to add
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> field(String field) {
        if (field != null && !field.isBlank()) {
            this.selectFields.add(field);
        }
        return this;
    }

    /**
     * Adds multiple fields to the SELECT clause.
     *
     * @param fields The field names to add
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> fields(String... fields) {
        return select(fields);
    }

    /**
     * Specifies the target Salesforce object (table name) in the FROM clause.
     *
     * @param objectName The Salesforce object API name (e.g., "Contact", "Book__c")
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> from(String objectName) {
        this.fromObject = objectName;
        return this;
    }

    /**
     * Specifies the target Salesforce object using the {@link SObject} enum.
     *
     * @param object The SObject enum constant
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> from(SObject object) {
        if (object != null) {
            this.fromObject = object.getObjectName();
        }
        return this;
    }

    /**
     * Adds a raw condition string to the WHERE clause.
     *
     * @param condition The raw WHERE condition (e.g., "IsActive = true")
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> where(String condition) {
        if (condition != null && !condition.isBlank()) {
            this.whereClauses.add(condition);
        }
        return this;
    }

    /**
     * Adds a formatted WHERE condition comparing a field with an operator and value.
     *
     * @param field The API field name
     * @param operator The {@link SalesforceOperator} (e.g., EQUALS, NOT_EQUALS)
     * @param value The value to compare against (automatically formatted/escaped)
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> where(String field, SalesforceOperator operator, Object value) {
        if (field != null && !field.isBlank() && operator != null) {
            String formattedVal = formatValue(value);
            this.whereClauses.add(field + " " + operator.getOperator() + " " + formattedVal);
        }
        return this;
    }

    /**
     * Adds a condition to the WHERE clause verifying that the field is not null.
     *
     * @param field The API field name
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> whereNotNull(String field) {
        if (field != null && !field.isBlank()) {
            this.whereClauses.add(field + " != null");
        }
        return this;
    }

    /**
     * Adds an equality condition to the WHERE clause with single quotes and proper escaping.
     *
     * @param field The API field name
     * @param value The string value to match
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> whereEquals(String field, String value) {
        if (field != null && !field.isBlank()) {
            this.whereClauses.add(field + " = '" + escape(value) + "'");
        }
        return this;
    }

    /**
     * Adds an ORDER BY clause for the specified field.
     *
     * @param field The API field name to order by
     * @param ascending True for ascending order (ASC), false for descending (DESC)
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> orderBy(String field, boolean ascending) {
        if (field != null && !field.isBlank()) {
            this.orderByClauses.add(field + (ascending ? " ASC" : " DESC"));
        }
        return this;
    }

    /**
     * Sets the maximum number of records to return.
     *
     * @param limit The LIMIT count
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Sets the number of records to skip before returning results.
     *
     * @param offset The OFFSET count
     * @return This builder instance for method chaining
     */
    public SOQLBuilder<T> offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Builds and returns the final SOQL query string.
     *
     * @return The constructed SOQL query string
     * @throws IllegalStateException If required clauses (SELECT or FROM) are missing
     */
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

    /**
     * Returns the built SOQL query string.
     *
     * @return The SOQL query string
     */
    @Override
    public String toString() {
        return build();
    }

    /**
     * Formats an object value for safe inclusion into a SOQL statement.
     *
     * @param val The value to format
     * @return Formatted SOQL literal representation
     */
    private static String formatValue(Object val) {
        return switch (val) {
            case null -> "null";
            case Number num -> String.valueOf(num);
            case Boolean bool -> String.valueOf(bool);
            default -> "'" + escape(val.toString()) + "'";
        };
    }

    /**
     * Escapes single quotes in string literals to prevent SOQL injection.
     *
     * @param value The raw string value
     * @return Escaped string
     */
    private static String escape(String value) {
        return (value == null) ? "" : value.replace("'", "\\'");
    }
}
