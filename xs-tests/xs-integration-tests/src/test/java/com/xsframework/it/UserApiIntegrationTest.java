package com.xsframework.it;

import com.jayway.jsonpath.JsonPath;
import com.xsframework.app.monolith.MonolithApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MonolithApplication.class)
@AutoConfigureMockMvc
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAndGetUser() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/users")
                        .header("X-Tenant-Id", "tenant-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhangsan\",\"displayName\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.data.userCode").exists())
                .andReturn();

        String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.userId");

        mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId));
    }
}
