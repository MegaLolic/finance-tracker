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
@RequestMapping("/categories")
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
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable Long id) {
        CategoryDto categoryDto = categoryService.getCategoryById(id);
        if (categoryDto == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(categoryDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateCategory(@PathVariable Long id,@Valid @RequestBody CategoryDto categoryDto) {
            categoryService.updateCategory(id, categoryDto);
            return ResponseEntity.ok("Category updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
            categoryService.deleteCategory(id);
            return ResponseEntity.noContent().build();
    }
}