package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesforceClientService {

    private final SalesforceConfig salesforceConfig;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate createRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        return new RestTemplate(factory);
    }

    private String accessToken;
    private String instanceUrl;

    public boolean isConfigured() {
        return salesforceConfig.isEnabled()
                && salesforceConfig.getClientId() != null && !salesforceConfig.getClientId().isBlank()
                && salesforceConfig.getClientSecret() != null && !salesforceConfig.getClientSecret().isBlank();
    }

    public synchronized String getAccessToken() {
        if (accessToken != null) {
            return accessToken;
        }
        authenticate();
        return accessToken;
    }

    public synchronized String getInstanceUrl() {
        if (instanceUrl != null) {
            return instanceUrl;
        }
        authenticate();
        return instanceUrl;
    }

    private void authenticate() {
        if (!isConfigured()) {
            log.info(
                    "[SALESFORCE CONFIG] Salesforce integration is not enabled or credentials (SALESFORCE_CLIENT_ID / SALESFORCE_CLIENT_SECRET) are missing.");
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "client_credentials");
            params.add("client_id", salesforceConfig.getClientId());
            params.add("client_secret", salesforceConfig.getClientSecret());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(salesforceConfig.getAuthUrl(), request,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                this.accessToken = root.path("access_token").asText();
                this.instanceUrl = root.path("instance_url").asText();
                log.info("[SALESFORCE AUTH] Successfully authenticated with Salesforce at {}", instanceUrl);
            }
        } catch (Exception e) {
            log.error("[SALESFORCE AUTH] OAuth authentication failed: {}", e.getMessage());
            this.accessToken = null;
            this.instanceUrl = null;
        }
    }

    public JsonNode query(String soql) {
        if (!isConfigured()) {
            log.info("[SALESFORCE QUERY] Salesforce is not enabled or credentials missing. Cannot execute SOQL: {}",
                    soql);
            return null;
        }
        try {
            String token = getAccessToken();
            String host = getInstanceUrl();
            if (token == null || host == null) {
                log.warn("[SALESFORCE QUERY] Failed to obtain access token or instance URL for SOQL: {}", soql);
                return null;
            }

            URI uri = UriComponentsBuilder
                    .fromUriString(host + "/services/data/" + salesforceConfig.getApiVersion() + "/query")
                    .queryParam("q", soql)
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            log.info("[SALESFORCE QUERY] Executing SOQL: {}", soql);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("[SALESFORCE QUERY] SOQL query failed [{}]: {}", soql, e.getMessage());
            this.accessToken = null; // reset token on failure
        }
        return null;
    }

    public void upsertByExternalId(String sObjectName, String externalIdFieldName, String externalIdValue,
            Map<String, Object> fields) {
        if (!isConfigured()) {
            return;
        }
        try {
            String token = getAccessToken();
            String host = getInstanceUrl();
            if (token == null || host == null) {
                return;
            }

            String url = host + "/services/data/" + salesforceConfig.getApiVersion()
                    + "/sobjects/" + sObjectName + "/" + externalIdFieldName + "/" + externalIdValue;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestJson = objectMapper.writeValueAsString(fields);
            HttpEntity<String> request = new HttpEntity<>(requestJson, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode() == HttpStatus.NO_CONTENT
                    || response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Successfully synced {} (ExternalId: {}) to Salesforce", sObjectName, externalIdValue);
            }
        } catch (Exception e) {
            log.error("Failed to sync {} (ExternalId: {}) to Salesforce: {}", sObjectName, externalIdValue,
                    e.getMessage());
            this.accessToken = null;
        }
    }
}
