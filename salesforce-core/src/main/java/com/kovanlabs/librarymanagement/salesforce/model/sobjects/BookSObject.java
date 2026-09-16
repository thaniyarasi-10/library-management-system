package com.kovanlabs.librarymanagement.salesforce.model.sobjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BookFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookSObject {

    public static final String SOBJECT_NAME = SObject.BOOK.getObjectName();
    public static final String EXTERNAL_ID_FIELD = BookFields.EXTERNAL_BOOK_UUID;

    @JsonProperty(BookFields.EXTERNAL_BOOK_UUID)
    private String externalBookUuid;

    @JsonProperty(BookFields.NAME)
    private String name;

    @JsonProperty(BookFields.TITLE)
    private String title;

    @JsonProperty(BookFields.AUTHOR)
    private String author;

    @JsonProperty(BookFields.ISBN)
    private String isbn;

    @JsonProperty(BookFields.COVER_IMAGE_URL)
    private String coverImageUrl;
}
