package org.portfolio.financeservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.portfolio.financeservice.controller.AccountController;
import org.portfolio.financeservice.controller.TransactionController;
import org.portfolio.financeservice.dto.AccountDto;
import org.portfolio.financeservice.dto.TransactionDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.exception.GlobalExceptionHandler;
import org.portfolio.financeservice.service.AccountService;
import org.portfolio.financeservice.service.TransactionService;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class VersionApiTest {
    private AccountService accounts;
    private TransactionService transactions;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        accounts = mock(AccountService.class);
        transactions = mock(TransactionService.class);
        mvc = standaloneSetup(new AccountController(accounts), new TransactionController(transactions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }
                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binder) {
                        return Jwt.withTokenValue("test").header("alg", "none").claim("userId", 1L).build();
                    }
                }).build();
    }

    private String body(String resource, String version) {
        String fields = resource.equals("accounts")
                ? "\"name\":\"Account\",\"balance\":100,\"currency\":\"BYN\""
                : "\"accountId\":1,\"categoryId\":2,\"amount\":10";
        return "{" + fields + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "transactions"})
    void updateRequiresVersion(String resource) throws Exception {
        mvc.perform(put("/finance/" + resource + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content(body(resource, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
        verifyNoInteractions(accounts, transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "transactions"})
    void negativeVersionIsRejected(String resource) throws Exception {
        mvc.perform(put("/finance/" + resource + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content(body(resource, "-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "transactions"})
    void concurrentConflictReturns409(String resource) throws Exception {
        var conflict = new ObjectOptimisticLockingFailureException(Account.class, 1L);
        when(accounts.updateAccount(eq(1L), eq(1L), any())).thenThrow(conflict);
        when(transactions.updateTransaction(eq(1L), eq(1L), any())).thenThrow(conflict);
        mvc.perform(put("/finance/" + resource + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content(body(resource, "0")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "transactions"})
    void updateReturnsTheNewVersion(String resource) throws Exception {
        when(accounts.updateAccount(eq(1L), eq(1L), any()))
                .thenReturn(AccountDto.builder().id(1L).version(1L).build());
        when(transactions.updateTransaction(eq(1L), eq(1L), any()))
                .thenReturn(TransactionDto.builder().id(1L).version(1L).build());
        mvc.perform(put("/finance/" + resource + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content(body(resource, "0")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "transactions"})
    void creationDoesNotRequireVersion(String resource) throws Exception {
        mvc.perform(post("/finance/" + resource).contentType(MediaType.APPLICATION_JSON)
                        .content(body(resource, null)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "transactions"})
    void deletionRequiresVersion(String resource) throws Exception {
        mvc.perform(delete("/finance/" + resource + "/1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accounts, transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "transactions"})
    void deletionPassesClientVersionToService(String resource) throws Exception {
        mvc.perform(delete("/finance/" + resource + "/1").param("version", "2"))
                .andExpect(status().isNoContent());
        if (resource.equals("accounts")) {
            verify(accounts).deleteAccount(1L, 1L, 2L);
        } else {
            verify(transactions).deleteTransaction(1L, 1L, 2L);
        }
    }
}
