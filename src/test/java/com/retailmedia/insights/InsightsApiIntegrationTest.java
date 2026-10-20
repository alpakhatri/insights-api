package com.retailmedia.insights;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End to end against a real ClickHouse in Docker: HTTP -> security -> service -> SQL.
 *
 * <p>Seeds three customers on a reconciled day and two on today, one of them shared. The range
 * total must be 4, not 5: this proves unique counts are merged across the reconciliation boundary
 * rather than added up. Skipped automatically when Docker is not available.
 */
@SpringBootTest(properties = {
    "insights.security.jwt.hmac-secret=test-secret-test-secret-test-secret-123",
    "insights.rate-limit.capacity=100",
    "insights.rate-limit.refill-per-second=100"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InsightsApiIntegrationTest {

    @Container
    static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer("clickhouse/clickhouse-server:24.3");

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void clickhouse(DynamicPropertyRegistry registry) {
        // All SQL uses fully qualified insights.* table names, so the URL's default database is fine.
        registry.add("spring.datasource.url", CLICKHOUSE::getJdbcUrl);
        registry.add("spring.datasource.username", CLICKHOUSE::getUsername);
        registry.add("spring.datasource.password", CLICKHOUSE::getPassword);
    }

    @BeforeAll
    static void createSchemaAndSeed() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                CLICKHOUSE.getJdbcUrl(), CLICKHOUSE.getUsername(), CLICKHOUSE.getPassword());
             Statement statement = connection.createStatement()) {
            runScript(statement, Path.of("docker/clickhouse/init/01-schema.sql"));
            statement.execute("""
                    INSERT INTO insights.campaigns (campaign_id, tenant_id, brand_id, name)
                    VALUES ('c-1001', 'tesco', 'nestle', 'KitKat'), ('c-2001', 'amzn', 'nestle', 'Nescafe')""");
            statement.execute("INSERT INTO insights.reconciliation_watermark (tenant_id, reconciled_through) "
                    + "VALUES ('tesco', today() - 1)");
            statement.execute("""
                    INSERT INTO insights.metrics_reconciled
                    SELECT 'tesco', 'c-1001', 'ad-1', 'homepage', 'clicks',
                           toDateTime(today() - 1) + INTERVAL 10 HOUR, uniqExactState(toUInt64(x)), toUInt64(7)
                    FROM (SELECT arrayJoin([1, 2, 3]) AS x)""");
            statement.execute("""
                    INSERT INTO insights.metrics_realtime
                    SELECT 'tesco', 'c-1001', 'ad-1', 'homepage', 'clicks',
                           toDateTime(today()), uniqExactState(toUInt64(x))
                    FROM (SELECT arrayJoin([3, 4]) AS x)""");
        }
    }

    @Test
    void mergesUniqueCustomersAcrossReconciledAndRealtimeDays() throws Exception {
        mvc.perform(get("/ad/c-1001/clicks")
                        .param("from", YESTERDAY.toString())
                        .param("to", TODAY.toString())
                        .with(jwt().jwt(j -> j.claim("tenant_id", "tesco").claim("role", "RETAILER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uniqueCustomers").value(4))
                .andExpect(jsonPath("$.reconciledThrough").value(YESTERDAY.toString()))
                .andExpect(jsonPath("$.breakdown[0].uniqueCustomers").value(3))
                .andExpect(jsonPath("$.breakdown[0].status").value("final"))
                .andExpect(jsonPath("$.breakdown[0].invalidRemoved").value(7))
                .andExpect(jsonPath("$.breakdown[1].uniqueCustomers").value(2))
                .andExpect(jsonPath("$.breakdown[1].status").value("preliminary"));
    }

    @Test
    void anotherTenantCannotSeeTheCampaign() throws Exception {
        mvc.perform(get("/ad/c-1001/clicks")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "amzn").claim("role", "RETAILER"))))
                .andExpect(status().isNotFound());
    }

    private static void runScript(Statement statement, Path script) throws IOException, SQLException {
        String withoutComments = Files.readString(script).replaceAll("(?m)--.*$", "");
        for (String sql : withoutComments.split(";")) {
            if (!sql.isBlank()) {
                statement.execute(sql);
            }
        }
    }
}
