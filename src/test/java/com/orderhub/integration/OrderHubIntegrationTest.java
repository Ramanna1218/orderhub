package com.orderhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against a real PostgreSQL (Testcontainers) with Flyway migrations,
 * real JWT security and the full MVC stack. Skipped automatically when Docker is unavailable.
 */
@SpringBootTest(properties = {
        "app.admin.email=admin@test.local",
        "app.admin.password=AdminPass123"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderHubIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void fullOrderLifecycle() throws Exception {
        String admin = login("admin@test.local", "AdminPass123");

        long productId = id(send(post("/api/products"), admin, Map.of(
                "sku", unique("WIDGET"), "name", "Widget", "price", 19.99, "stockQuantity", 10))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION)));

        long customerId = id(send(post("/api/customers"), admin, Map.of(
                "firstName", "Ada", "lastName", "Lovelace", "email", unique("ada") + "@example.com"))
                .andExpect(status().isCreated()));

        String user = register(unique("user") + "@example.com", "Password123");

        // Regular users cannot manage the catalog
        send(post("/api/products"), user, Map.of(
                "sku", unique("NOPE"), "name", "Nope", "price", 1, "stockQuantity", 1))
                .andExpect(status().isForbidden());

        // Place an order: stock is reserved and the total is computed server-side
        long orderId = id(send(post("/api/orders"), user, Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", productId, "quantity", 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(59.97))
                .andExpect(jsonPath("$.items[0].quantity").value(3)));

        mvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(7));

        // Overselling is rejected with 409 and nothing changes
        send(post("/api/orders"), user, Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", productId, "quantity", 100))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));

        // Only admins can change status
        send(patch("/api/orders/" + orderId + "/status"), user, Map.of("status", "CONFIRMED"))
                .andExpect(status().isForbidden());

        // Cancelling restores stock
        send(patch("/api/orders/" + orderId + "/status"), admin, Map.of("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.stockQuantity").value(10));

        // Terminal states cannot move again
        send(patch("/api/orders/" + orderId + "/status"), admin, Map.of("status", "SHIPPED"))
                .andExpect(status().isConflict());
    }

    @Test
    void protectedEndpoints_requireToken() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/customers").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPayload_returnsFieldErrors() throws Exception {
        String admin = login("admin@test.local", "AdminPass123");

        send(post("/api/products"), admin, Map.of("sku", "", "name", "X", "price", -5, "stockQuantity", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists())
                .andExpect(jsonPath("$.fieldErrors.stockQuantity").exists());
    }

    @Test
    void wrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "admin@test.local", "password", "wrong-pass"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productList_isPaginated() throws Exception {
        mvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }

    // ---- helpers ----

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String register(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private long id(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
