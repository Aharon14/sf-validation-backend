package com.sfvalidator.backend;

import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SalesforceProxyController {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @PostMapping("/auth/token")
    public ResponseEntity<?> exchangeToken(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String redirectUri = body.get("redirectUri");
        String clientId = body.get("clientId");
        String loginUrl = body.getOrDefault("loginUrl", "https://login.salesforce.com");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    loginUrl + "/services/oauth2/token", request, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/validation-rules")
    public ResponseEntity<?> getValidationRules(
            @RequestHeader("X-SF-Token") String accessToken,
            @RequestHeader("X-SF-Instance") String instanceUrl) {

        String soql = "SELECT Id, ValidationName, Active, Description, ErrorMessage, ErrorDisplayField, NamespacePrefix FROM ValidationRule WHERE EntityDefinition.QualifiedApiName = 'Account' ORDER BY ValidationName ASC";

        String url = UriComponentsBuilder
                .fromHttpUrl(instanceUrl + "/services/data/v59.0/tooling/query")
                .queryParam("q", soql)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/validation-rules/{ruleId}")
    public ResponseEntity<?> updateValidationRule(
            @PathVariable String ruleId,
            @RequestHeader("X-SF-Token") String accessToken,
            @RequestHeader("X-SF-Instance") String instanceUrl,
            @RequestBody Map<String, Object> body) {

        // ✅ FIXED: added ?fields=Metadata so Salesforce returns the full Metadata object
        String getUrl = instanceUrl + "/services/data/v59.0/tooling/sobjects/ValidationRule/" + ruleId + "?fields=Metadata";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);

        try {
                    onseEntity<Map> getResponse = restTemplate.exchange(
                getUrl, HttpMethod.GET, getRequest, Map.class);

            Map<String, Object> ruleData = getResponse.getBody();
            Map<String, Object> metadata = (Map<String, Object>) ruleData.get("Metadata");

            if (metadata == null) {
                    return ResponseEntity.status(400).body(Map.of("error", "Metadata not found for rule: " + ruleId));
            }

            metadata.put("active", body.get("active"));

            // Use URL without ?fields=Metadata for the PATCH request
            String patchUrl = instanceUrl + "/services/data/v59.0/tooling/sobjects/ValidationRule/" + ruleId;
            HttpEntity<Map<String, Object>> patchRequest = new HttpEntity<>(
                Map.of("Metadata", metadata), headers);
            restTemplate.exchange(patchUrl, HttpMethod.PATCH, patchRequest, Void.class);
            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/userinfo")
    public ResponseEntity<?> getUserInfo(
            @RequestHeader("X-SF-Token") String accessToken,
                    uestHeader("X-SF-Instance") String instanc
                    
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                instanceUrl + "/services/oauth2/userinfo",
                HttpMethod.GET, request, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
}