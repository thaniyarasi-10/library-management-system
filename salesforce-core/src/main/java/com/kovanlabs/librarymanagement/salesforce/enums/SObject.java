package com.kovanlabs.librarymanagement.salesforce.enums;

public enum SObject {
    CONTACT("Contact"),
    BOOK("Book__c"),
    BORROW("Borrow__c");

    private final String objectName;

    SObject(String objectName) {
        this.objectName = objectName;
    }

    public String getObjectName() {
        return this.objectName;
    }

    @Override
    public String toString() {
        return this.objectName;
    }
}
