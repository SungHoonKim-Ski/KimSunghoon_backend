package barley.wire.wirebarley;

import barley.wire.wirebarley.fixture.TestFixture;
import barley.wire.wirebarley.infrastructure.client.ExchangeRateClient;
import barley.wire.wirebarley.infrastructure.client.ExchangeRateFallbackClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void registerMySQLProperties(DynamicPropertyRegistry registry) {
        mysql.start();
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void cleanDatabase() {
        flyway.clean();
        flyway.migrate();
    }

    @MockitoBean
    protected ExchangeRateClient exchangeRateClient;

    @MockitoBean
    protected ExchangeRateFallbackClient exchangeRateFallbackClient;

    protected TestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(mockMvc, objectMapper);
    }

    protected <T> ApiResponse<T> postAction(String url, Object request, Class<T> responseType) throws Exception {
        String requestBody = request != null ? objectMapper.writeValueAsString(request) : "";
        MvcResult result = mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andReturn();

        return parseResponse(result, responseType);
    }

    protected <T> ApiResponse<T> deleteAction(String url) throws Exception {
        MvcResult result = mockMvc.perform(delete(url)).andReturn();
        return new ApiResponse<>(result.getResponse().getStatus(), null);
    }

    protected <T> ApiResponse<T> getAction(String url, Class<T> responseType) throws Exception {
        MvcResult result = mockMvc.perform(get(url)).andReturn();
        return parseResponse(result, responseType);
    }

    protected <T> ApiResponse<T> performAction(MockHttpServletRequestBuilder requestBuilder, Class<T> responseType)
            throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder).andReturn();
        return parseResponse(result, responseType);
    }

    private <T> ApiResponse<T> parseResponse(MvcResult result, Class<T> responseType) throws Exception {
        int status = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString();

        if (content.isEmpty() || responseType == null) {
            return new ApiResponse<>(status, null);
        }

        T body = objectMapper.readValue(content, responseType);
        return new ApiResponse<>(status, body);
    }

    protected record ApiResponse<T>(int status, T body) {
    }
}
