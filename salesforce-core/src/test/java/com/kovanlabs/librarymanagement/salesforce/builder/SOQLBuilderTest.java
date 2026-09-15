package com.kovanlabs.librarymanagement.salesforce.builder;

import com.kovanlabs.librarymanagement.salesforce.constant.BookFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.BorrowFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.ContactFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.enums.SalesforceOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SOQLBuilderTest {

    @Test
    void build_withInstanceAndFieldConstants_buildsCorrectQuery() {
        String soql = new SOQLBuilder<>()
                .select(ContactFieldConstants.LAST_NAME,
                        ContactFieldConstants.EMAIL,
                        ContactFieldConstants.EXTERNAL_USER_UUID)
                .from(SObject.CONTACT)
                .whereNotNull(ContactFieldConstants.EXTERNAL_USER_UUID)
                .build();

        assertEquals("SELECT LastName, Email, External_User_UUID__c FROM Contact WHERE External_User_UUID__c != null", soql);
    }

    @Test
    void build_withStaticSelect_buildsCorrectQuery() {
        String soql = SOQLBuilder.selectFields(
                        ContactFieldConstants.LAST_NAME,
                        ContactFieldConstants.EMAIL,
                        ContactFieldConstants.EXTERNAL_USER_UUID
                )
                .from(SObject.CONTACT)
                .whereNotNull(ContactFieldConstants.EXTERNAL_USER_UUID)
                .build();

        assertEquals("SELECT LastName, Email, External_User_UUID__c FROM Contact WHERE External_User_UUID__c != null", soql);
    }

    @Test
    void build_withCountAndObject_buildsCorrectQuery() {
        String soql = new SOQLBuilder<>()
                .count()
                .from(SObject.BOOK)
                .whereNotNull(BookFieldConstants.EXTERNAL_BOOK_UUID)
                .build();

        assertEquals("SELECT COUNT() FROM Book__c WHERE External_Book_UUID__c != null", soql);
    }

    @Test
    void build_withLimitAndOffsetAndOrderBy_buildsCorrectQuery() {
        String soql = new SOQLBuilder<>()
                .select(BookFieldConstants.NAME, BookFieldConstants.TITLE)
                .from(SObject.BOOK)
                .whereNotNull(BookFieldConstants.EXTERNAL_BOOK_UUID)
                .orderBy(BookFieldConstants.TITLE, true)
                .limit(10)
                .offset(20)
                .build();

        assertEquals("SELECT Name, Title__c FROM Book__c WHERE External_Book_UUID__c != null ORDER BY Title__c ASC LIMIT 10 OFFSET 20", soql);
    }

    @Test
    void build_withSalesforceOperator_buildsCorrectQuery() {
        String soql = new SOQLBuilder<>()
                .select(BorrowFieldConstants.EXTERNAL_BORROW_UUID, BorrowFieldConstants.BORROW_STATUS)
                .from(SObject.BORROW)
                .where(BorrowFieldConstants.BORROW_STATUS, SalesforceOperator.EQUALS, "BORROWED")
                .whereNotNull(BorrowFieldConstants.EXTERNAL_BORROW_UUID)
                .build();

        assertEquals("SELECT External_Borrow_UUID__c, Borrow_Status__c FROM Borrow__c WHERE Borrow_Status__c = 'BORROWED' AND External_Borrow_UUID__c != null", soql);
    }

    @Test
    void build_withoutFields_throwsException() {
        SOQLBuilder<Object> builder = new SOQLBuilder<>().from(SObject.CONTACT);
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void build_withoutFrom_throwsException() {
        SOQLBuilder<Object> builder = new SOQLBuilder<>().select(ContactFieldConstants.EMAIL);
        assertThrows(IllegalStateException.class, builder::build);
    }
}
