package com.devilsvault.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class CorrelationIdFilterTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    CorrelationIdFilter correlationIdFilter;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac)
                .addFilter(correlationIdFilter)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    @Test
    void echoesInboundCorrelationId() throws Exception {
        String clientId = "test-corr-id-12345";
        mvc().perform(get("/actuator/health")
                        .header("X-Correlation-Id", clientId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", clientId));
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        MvcResult result = mvc().perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();
        String responseCorrelationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(responseCorrelationId)
                .isNotNull()
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void rejectsOverlongCorrelationId() throws Exception {
        String tooLong = "x".repeat(65);
        MvcResult result = mvc().perform(get("/actuator/health")
                        .header("X-Correlation-Id", tooLong))
                .andExpect(status().isOk())
                .andReturn();
        String responseCorrelationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(responseCorrelationId).isNotEqualTo(tooLong);
        assertThat(responseCorrelationId)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
