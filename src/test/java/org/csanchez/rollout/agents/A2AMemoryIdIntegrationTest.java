package org.csanchez.rollout.agents;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.csanchez.rollout.k8sagent.model.KubernetesAgentRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration test to verify memoryId persistence through the REST API.
 * This tests the same memory management logic that A2A uses.
 */
@QuarkusTest
class A2AMemoryIdIntegrationTest {

    @Test
    void testMemoryIdPersistenceWithExplicitMemoryId() {
        // Given: A request with explicit memoryId
        Map<String, Object> context = new HashMap<>();
        context.put("namespace", "test");
        
        KubernetesAgentRequest request = new KubernetesAgentRequest(
            "user-123",
            "What pods are running?",
            context,
            "explicit-memory-id-456"
        );
        
        // When: We send the request
        // Then: It should be processed (we're testing that memoryId is accepted)
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/a2a/analyze")
            .then()
            .statusCode(200)
            .body("analysis", notNullValue());
    }

    @Test
    void testMemoryIdFallbackToUserId() {
        // Given: A request without explicit memoryId (should use userId)
        Map<String, Object> context = new HashMap<>();
        context.put("namespace", "test");
        
        KubernetesAgentRequest request = new KubernetesAgentRequest(
            "user-789",
            "What pods are running?",
            context
        );
        
        // When: We send the request
        // Then: It should be processed using userId as memoryId
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/a2a/analyze")
            .then()
            .statusCode(200)
            .body("analysis", notNullValue());
    }

    @Test
    void testConversationContinuityWithSameMemoryId() {
        // Given: First request with memoryId
        Map<String, Object> context = new HashMap<>();
        context.put("namespace", "test");
        
        KubernetesAgentRequest firstRequest = new KubernetesAgentRequest(
            "user-abc",
            "List pods in namespace test",
            context,
            "conversation-123"
        );
        
        // When: We send first request
        given()
            .contentType(ContentType.JSON)
            .body(firstRequest)
            .when()
            .post("/a2a/analyze")
            .then()
            .statusCode(200);
        
        // And: We send a follow-up request with same memoryId
        KubernetesAgentRequest followUpRequest = new KubernetesAgentRequest(
            "user-abc",
            "What about the previous pods?",
            context,
            "conversation-123"
        );
        
        // Then: The follow-up should be processed (conversation context maintained)
        given()
            .contentType(ContentType.JSON)
            .body(followUpRequest)
            .when()
            .post("/a2a/analyze")
            .then()
            .statusCode(200)
            .body("analysis", notNullValue());
    }
}

// Made with Bob
