package org.portfolio.financeservice.controller;

import jakarta.validation.Valid;
import org.portfolio.financeservice.dto.CategoryDto;
import org.portfolio.financeservice.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/finance/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryDto categoryDto, @AuthenticationPrincipal Jwt jwt){
        Long userId=jwt.getClaim("userId");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(categoryService.createCategory(userId, categoryDto));
    }

    @GetMapping
    public ResponseEntity<List<CategoryDto>> getCategoriesByUserId(@AuthenticationPrincipal Jwt jwt) {
        Long userId=jwt.getClaim("userId");
        return ResponseEntity.ok(categoryService.getCategoriesByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategoryById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        return ResponseEntity.ok(
                categoryService.getCategoryById(id, userId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryDto categoryDto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        categoryService.updateCategory(id, userId, categoryDto);

        return ResponseEntity.ok("Category updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        categoryService.deleteCategory(id, userId);

        return ResponseEntity.noContent().build();
    }
}