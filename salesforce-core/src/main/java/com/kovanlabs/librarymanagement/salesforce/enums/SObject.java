package com.kovanlabs.librarymanagement.salesforce.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SObject {
    CONTACT("Contact"),
    BOOK("Book__c"),
    BORROW("Borrow__c");

    private final String objectName;

    @Override
    public String toString() {
        return this.objectName;
    }
}
