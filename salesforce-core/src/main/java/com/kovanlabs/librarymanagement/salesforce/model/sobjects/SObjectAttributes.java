package com.kovanlabs.librarymanagement.salesforce.model.sobjects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents the Salesforce "attributes" metadata object containing SObject type and REST URL.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SObjectAttributes {
    private String type;
    private String url;
}
