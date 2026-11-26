package com.xsframework.it;

import com.jayway.jsonpath.JsonPath;
import com.xsframework.app.monolith.MonolithApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MonolithApplication.class)
@AutoConfigureMockMvc
class TenantApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createListGetAndChangeStatus() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantName\":\"Acme\",\"description\":\"Acme Ltd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tenantCode").value(org.hamcrest.Matchers.matchesPattern("^TN[0-9A-F]{14}$")))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        String tenantId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.tenantId");

        mockMvc.perform(get("/api/v1/tenants/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(tenantId));

        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(patch("/api/v1/tenants/{tenantId}/status", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    void missingTenantReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/{tenantId}", "missing-tenant"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
