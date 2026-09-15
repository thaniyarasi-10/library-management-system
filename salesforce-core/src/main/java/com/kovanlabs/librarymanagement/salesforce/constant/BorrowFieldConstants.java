package com.kovanlabs.librarymanagement.salesforce.constant;

public final class BorrowFieldConstants {

    private BorrowFieldConstants() {
        // Prevent instantiation
    }

    public static final String EXTERNAL_BORROW_UUID = "External_Borrow_UUID__c";
    public static final String BORROW_DATE = "Borrow_Date__c";
    public static final String DUE_DATE = "Due_Date__c";
    public static final String RETURN_DATE = "Return_Date__c";
    public static final String BORROW_STATUS = "Borrow_Status__c";
    public static final String CONTACT_RELATION = "Contact__r";
    public static final String BOOK_RELATION = "Book__r";

    // Compound relationship fields for SOQL queries
    public static final String CONTACT_LAST_NAME = "Contact__r.LastName";
    public static final String CONTACT_EMAIL = "Contact__r.Email";
    public static final String CONTACT_EXTERNAL_USER_UUID = "Contact__r.External_User_UUID__c";

    public static final String BOOK_NAME = "Book__r.Name";
    public static final String BOOK_TITLE = "Book__r.Title__c";
    public static final String BOOK_AUTHOR = "Book__r.Author__c";
    public static final String BOOK_COVER_IMAGE_URL = "Book__r.Cover_Image_Url__c";
    public static final String BOOK_EXTERNAL_BOOK_UUID = "Book__r.External_Book_UUID__c";
}
