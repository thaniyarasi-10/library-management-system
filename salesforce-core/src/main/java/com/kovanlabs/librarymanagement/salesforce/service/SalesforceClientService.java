package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesforceClientService {

    private final SalesforceConfig salesforceConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

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
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "client_credentials");
            params.add("client_id", salesforceConfig.getClientId());
            params.add("client_secret", salesforceConfig.getClientSecret());

            String responseBody = restClient.post()
                    .uri(salesforceConfig.getAuthUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("[SALESFORCE AUTH] Authentication returned error status: {}", res.getStatusCode());
                    })
                    .body(String.class);

            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode root = objectMapper.readTree(responseBody);
                this.accessToken = root.path("access_token").asText(null);
                this.instanceUrl = root.path("instance_url").asText(null);
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

            String uri = host + "/services/data/" + salesforceConfig.getApiVersion() + "/query?q={soql}";

            log.info("[SALESFORCE QUERY] Executing SOQL: {}", soql);
            String responseBody = restClient.get()
                    .uri(uri, soql)
                    .headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    })
                    .retrieve()
                    .body(String.class);

            if (responseBody != null) {
                return objectMapper.readTree(responseBody);
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

            String uri = host + "/services/data/" + salesforceConfig.getApiVersion()
                    + "/sobjects/" + sObjectName + "/" + externalIdFieldName + "/" + externalIdValue;

            String requestJson = objectMapper.writeValueAsString(fields);

            restClient.patch()
                    .uri(uri)
                    .headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(requestJson)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully synced {} (ExternalId: {}) to Salesforce", sObjectName, externalIdValue);
        } catch (Exception e) {
            log.error("Failed to sync {} (ExternalId: {}) to Salesforce: {}", sObjectName, externalIdValue,
                    e.getMessage());
            this.accessToken = null;
        }
    }
}
