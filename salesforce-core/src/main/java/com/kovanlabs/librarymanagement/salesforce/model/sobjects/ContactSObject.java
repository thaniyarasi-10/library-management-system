package com.kovanlabs.librarymanagement.salesforce.model.sobjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactSObject extends SObjectBase {

    public static final String EXTERNAL_ID_FIELD = ContactFields.EXTERNAL_USER_UUID;

    @JsonProperty(ContactFields.EXTERNAL_USER_UUID)
    private String externalUserUuid;

    @JsonProperty(ContactFields.LAST_NAME)
    private String lastName;

    @JsonProperty(ContactFields.EMAIL)
    private String email;
}
