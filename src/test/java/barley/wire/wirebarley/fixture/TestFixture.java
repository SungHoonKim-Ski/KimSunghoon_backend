package barley.wire.wirebarley.fixture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import barley.wire.wirebarley.presentation.dto.request.AmountRequest;
import barley.wire.wirebarley.presentation.dto.request.CreateAccountRequest;
import barley.wire.wirebarley.presentation.dto.request.CreateGlobalAccountRequest;
import barley.wire.wirebarley.presentation.dto.response.AccountResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

@Component
public class TestFixture {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TestFixture(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public AccountResponse createAccount(String accountNumber, String ownerName) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(accountNumber, ownerName);
        String response = mockMvc
                .perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AccountResponse.class);
    }

    public AccountResponse createGlobalAccount(String accountNumber, String ownerName,
            barley.wire.wirebarley.domain.account.Currency currency) throws Exception {
        CreateGlobalAccountRequest request = new CreateGlobalAccountRequest(accountNumber, ownerName, currency);

        String response = mockMvc
                .perform(post("/api/v2/global-accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AccountResponse.class);
    }

    public void deposit(Long accountId, BigDecimal amount) throws Exception {
        AmountRequest request = new AmountRequest(amount);
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposit").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isOk());
    }

    public void withdraw(Long accountId, BigDecimal amount) throws Exception {
        AmountRequest request = new AmountRequest(amount);
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdraw").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isOk());
    }
}
