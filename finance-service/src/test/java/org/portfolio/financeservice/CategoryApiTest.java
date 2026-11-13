package org.portfolio.financeservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.controller.CategoryController;
import org.portfolio.financeservice.entity.Category;
import org.portfolio.financeservice.entity.CategoryEnum;
import org.portfolio.financeservice.exception.GlobalExceptionHandler;
import org.portfolio.financeservice.repository.CategoryRepository;
import org.portfolio.financeservice.service.CategoryService;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CategoryApiTest {
    private CategoryRepository categories;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        CategoryService service = new CategoryService(categories, mock(UserClient.class));
        mvc = standaloneSetup(new CategoryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binder) {
                        return Jwt.withTokenValue("test").header("alg", "none").claim("userId", 7L).build();
                    }
                }).build();
    }

    @Test
    void referencedCategoryReturnsConflictWithoutExposingDatabaseDetails() throws Exception {
        Category category = Category.builder().id(1L).userId(7L).build();
        when(categories.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(category));
        doThrow(new DataIntegrityViolationException("fk_transactions_category: SQL details"))
                .when(categories).delete(category);

        mvc.perform(delete("/finance/categories/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Operation conflicts with existing or related data"));
    }

    @Test
    void duplicateCategoryNameReturnsConflictOnCreateAndUpdate() throws Exception {
        when(categories.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("idx_categories_user_name"));
        when(categories.findByIdAndUserId(1L, 7L))
                .thenReturn(Optional.of(Category.builder().id(1L).userId(7L).type(CategoryEnum.EXPENSE).build()));
        String body = "{\"name\":\"Food\",\"type\":\"EXPENSE\"}";

        mvc.perform(post("/finance/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(put("/finance/categories/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void unusedCategoryCanBeDeleted() throws Exception {
        Category category = Category.builder().id(1L).userId(7L).build();
        when(categories.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(category));

        mvc.perform(delete("/finance/categories/1")).andExpect(status().isNoContent());
        verify(categories).delete(category);
    }

    @Test
    void missingOrForeignCategoryIsNotDeleted() throws Exception {
        when(categories.findByIdAndUserId(1L, 7L)).thenReturn(Optional.empty());

        mvc.perform(delete("/finance/categories/1")).andExpect(status().isNotFound());
        verify(categories, never()).delete(any(Category.class));
        verify(categories).findByIdAndUserId(1L, 7L);
    }

    @Test
    void changingTypeReturnsConflictWithoutChangingNameOrSaving() throws Exception {
        Category category = Category.builder().id(1L).userId(7L).name("Food")
                .type(CategoryEnum.EXPENSE).build();
        when(categories.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(category));

        mvc.perform(put("/finance/categories/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Salary\",\"type\":\"INCOME\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category type cannot be changed"));

        assertEquals("Food", category.getName());
        assertEquals(CategoryEnum.EXPENSE, category.getType());
        verify(categories, never()).save(any(Category.class));
    }

    @Test
    void renamingWithSameTypeSucceeds() throws Exception {
        Category category = Category.builder().id(1L).userId(7L).name("Food")
                .type(CategoryEnum.EXPENSE).build();
        when(categories.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(category));

        mvc.perform(put("/finance/categories/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Groceries\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isOk());

        assertEquals("Groceries", category.getName());
        assertEquals(CategoryEnum.EXPENSE, category.getType());
        verify(categories).save(category);
    }
}
