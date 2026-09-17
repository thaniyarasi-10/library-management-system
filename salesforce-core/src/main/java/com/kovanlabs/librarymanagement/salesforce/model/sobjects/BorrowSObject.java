package com.kovanlabs.librarymanagement.salesforce.model.sobjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BorrowFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BorrowSObject extends SObjectBase {

    public static final String EXTERNAL_ID_FIELD = BorrowFields.EXTERNAL_BORROW_UUID;

    @JsonProperty(BorrowFields.EXTERNAL_BORROW_UUID)
    private String externalBorrowUuid;

    @JsonProperty(BorrowFields.BORROW_DATE)
    private String borrowDate;

    @JsonProperty(BorrowFields.DUE_DATE)
    private String dueDate;

    @JsonProperty(BorrowFields.RETURN_DATE)
    private String returnDate;

    @JsonProperty(BorrowFields.BORROW_STATUS)
    private String borrowStatus;

    @JsonProperty(BorrowFields.CONTACT_RELATION)
    private ContactSObject contact;

    @JsonProperty(BorrowFields.BOOK_RELATION)
    private BookSObject book;
}
