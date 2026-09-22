package com.kovanlabs.librarymanagement.salesforce.constant.fields;

public final class BorrowFields {

    private BorrowFields() {
        // Prevent instantiation
    }

    public static final String EXTERNAL_BORROW_UUID = "External_Borrow_UUID__c";
    public static final String BORROW_DATE = "Borrow_Date__c";
    public static final String DUE_DATE = "Due_Date__c";
    public static final String RETURN_DATE = "Return_Date__c";
    public static final String BORROW_STATUS = "Borrow_Status__c";
    public static final String CONTACT_RELATION = "Contact__r";
    public static final String BOOK_RELATION = "Book__r";
}
