package com.kovanlabs.librarymanagement.salesforce.model.sobjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactSObject implements Serializable {

    public static final String SOBJECT_NAME = SObject.CONTACT.getObjectName();
    public static final String EXTERNAL_ID_FIELD = ContactFields.EXTERNAL_USER_UUID;

    @JsonProperty(ContactFields.EXTERNAL_USER_UUID)
    private String externalUserUuid;

    @JsonProperty(ContactFields.LAST_NAME)
    private String lastName;

    @JsonProperty(ContactFields.EMAIL)
    private String email;
}
