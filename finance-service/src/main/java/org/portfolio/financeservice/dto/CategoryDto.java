package org.portfolio.financeservice.dto;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.portfolio.financeservice.entity.CategoryEnum;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;
    @NotNull(message = "User ID is required")
    @NotBlank(message = "Category name is required")
    @Size(max = 50, message = "Category name cannot exceed 100 characters")
    private String name;
    @NotNull(message = "Category type is required")
    private CategoryEnum type;
}
