package org.portfolio.financeservice.service;

import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.CategoryDto;
import org.portfolio.financeservice.entity.Category;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.portfolio.financeservice.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserClient userClient;

    public CategoryService(CategoryRepository categoryRepository, UserClient userClient) {
        this.categoryRepository = categoryRepository;
        this.userClient = userClient;
    }

    public CategoryDto createCategory(Long userId,CategoryDto input) {
        userClient.getUserById(userId);

        final Category createdCategory = Category.builder()
                .name(input.getName())
                .type(input.getType())
                .userId(userId)
                .build();
        final Category saved = categoryRepository.save(createdCategory);
        return toDto(saved);
    }

    private CategoryDto toDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .build();
    }

    public CategoryDto getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    public void updateCategory(Long id, CategoryDto categoryDto) {
        Category updatedCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        updatedCategory.setName(categoryDto.getName());
        categoryRepository.save(updatedCategory);
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        categoryRepository.delete(category);
    }

    public List<CategoryDto> getCategoriesByUserId(Long userId) {
        return categoryRepository.findCategoriesByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }
}