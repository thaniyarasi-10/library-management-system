package com.kovanlabs.librarymanagement.salesforce.model.sobjects;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all Salesforce SObjects containing common Salesforce API response fields,
 * dynamic additional attributes mapping, and helper methods.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SObjectBase {

    @JsonProperty(value = "Id", access = JsonProperty.Access.WRITE_ONLY)
    private String id;

    @JsonProperty("success")
    private Boolean success;

    private List<Object> errors;

    @JsonProperty("attributes")
    private SObjectAttributes attributes;

    @JsonIgnore
    @Builder.Default
    private Map<String, Object> additionalAttributes = new HashMap<>();

    /**
     * Captures any unknown JSON properties into additionalAttributes map.
     *
     * @param key   JSON property key
     * @param value JSON property value
     */
    @JsonAnySetter
    public void setAdditionalAttribute(String key, Object value) {
        if (additionalAttributes == null) {
            additionalAttributes = new HashMap<>();
        }
        additionalAttributes.put(key, value);
    }

}
