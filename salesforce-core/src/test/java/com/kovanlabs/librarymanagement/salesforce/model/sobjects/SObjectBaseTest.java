package com.kovanlabs.librarymanagement.salesforce.model.sobjects;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SObjectBaseTest {

    @Test
    void testSObjectBaseGettersAndBuilder() {
        SObjectAttributes attributes = SObjectAttributes.builder()
                .type("Book__c")
                .url("/services/data/v58.0/sobjects/Book__c/a00xx0000001")
                .build();

        SObjectBase sObject = SObjectBase.builder()
                .id("001xx000003DGSWAA4")
                .success(true)
                .errors(List.of("Error message"))
                .attributes(attributes)
                .build();

        assertEquals("001xx000003DGSWAA4", sObject.getId());
        assertTrue(sObject.getSuccess());
        assertEquals(1, sObject.getErrors().size());
        assertNotNull(sObject.getAttributes());
        assertEquals("Book__c", sObject.getAttributes().getType());
        assertEquals("/services/data/v58.0/sobjects/Book__c/a00xx0000001", sObject.getAttributes().getUrl());
    }

    @Test
    void testNoArgsConstructor() {
        SObjectBase sObject = new SObjectBase();
        assertNull(sObject.getId());
        assertNull(sObject.getSuccess());
        assertNull(sObject.getErrors());
        assertNull(sObject.getAttributes());
    }

    @Test
    void testSObjectAttributes() {
        SObjectAttributes attributes = new SObjectAttributes("Contact",
                "/services/data/v58.0/sobjects/Contact/003xx000001");
        assertEquals("Contact", attributes.getType());
        assertEquals("/services/data/v58.0/sobjects/Contact/003xx000001", attributes.getUrl());
    }
}
