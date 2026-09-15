package com.kovanlabs.librarymanagement.salesforce.enums;

public enum SalesforceOperator {
    AND("AND"),
    OR("OR"),
    NOT("NOT"),
    EQUALS("="),
    NOT_EQUALS("!="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LIKE("LIKE"),
    IN("IN"),
    NOT_IN("NOT IN");

    private final String operator;

    SalesforceOperator(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return this.operator;
    }

    @Override
    public String toString() {
        return this.operator;
    }
}
