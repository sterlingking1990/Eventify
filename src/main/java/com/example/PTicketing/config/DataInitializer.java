package com.example.PTicketing.config;

import com.example.PTicketing.entity.Category;
import com.example.PTicketing.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) return;

        String[][] categories = {
                {"Afrobeats", "afrobeats"},
                {"Amapiano", "amapiano"},
                {"Arts & Crafts", "arts-crafts"},
                {"Awards", "awards"},
                {"Business", "business"},
                {"Charity", "charity"},
                {"Comedy", "comedy"},
                {"Conferences", "conferences"},
                {"Conventions", "conventions"},
                {"Cultural", "cultural"},
                {"Dance", "dance"},
                {"Education", "education"},
                {"Exhibitions", "exhibitions"},
                {"Family-friendly", "family-friendly"},
                {"Festivals", "festivals"},
                {"Food & Drink", "food-drink"},
                {"Gaming", "gaming"},
                {"Health & Wellness", "health-wellness"},
                {"House Parties", "house-parties"},
                {"Lifestyle", "lifestyle"},
                {"Masterclass", "masterclass"},
                {"Music", "music"},
                {"Networking", "networking"},
                {"Nightlife", "nightlife"},
                {"Pool Party", "pool-party"},
                {"Pop-up", "pop-up"},
                {"Religious", "religious"},
                {"Roadshow", "roadshow"},
                {"Seminar", "seminar"},
                {"Student Events", "student-events"},
                {"Sports", "sports"},
                {"Technology", "technology"},
                {"Theater", "theater"},
                {"Trade Fair", "trade-fair"},
                {"Wedding & Bridal", "wedding-bridal"},
                {"Workshop & Training", "workshop-training"},
                {"Workshops", "workshops"}
        };

        for (String[] cat : categories) {
            Category category = Category.builder()
                    .name(cat[0])
                    .slug(cat[1])
                    .build();
            categoryRepository.save(category);
        }
    }
}
