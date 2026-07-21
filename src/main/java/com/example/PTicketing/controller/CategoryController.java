package com.example.PTicketing.controller;

import com.example.PTicketing.dto.response.CategoryResponse;
import com.example.PTicketing.entity.Category;
import com.example.PTicketing.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> categories = categoryRepository.findAll()
                .stream()
                .map(cat -> CategoryResponse.builder()
                        .id(cat.getId())
                        .name(cat.getName())
                        .slug(cat.getSlug())
                        .build())
                .toList();
        return ResponseEntity.ok(categories);
    }
}
