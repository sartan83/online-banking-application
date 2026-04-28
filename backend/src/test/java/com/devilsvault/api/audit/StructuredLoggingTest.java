package com.devilsvault.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "logging.level.com.devilsvault.api.audit.CorrelationIdFilter=DEBUG"
})
@ActiveProfiles("test")
class StructuredLoggingTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    CorrelationIdFilter correlationIdFilter;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger filterLogger;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        filterLogger.addAppender(listAppender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac)
                .addFilter(correlationIdFilter)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    @Test
    void logEventsCarryRequiredMdcFields() throws Exception {
        String correlationId = "json-log-test-id-001";
        mvc().perform(get("/actuator/health")
                        .header("X-Correlation-Id", correlationId))
                .andExpect(status().isOk());

        assertThat(listAppender.list).isNotEmpty();
        ILoggingEvent event = listAppender.list.get(0);

        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc.get("requestId")).isEqualTo(correlationId);
        assertThat(mdc.get("correlationId")).isEqualTo(correlationId);
        assertThat(event.getLoggerName()).isNotBlank();
        assertThat(event.getThreadName()).isNotBlank();
        assertThat(event.getMessage()).isNotBlank();
        assertThat(event.getLevel()).isNotNull();
        assertThat(event.getInstant()).isNotNull();
    }
}
