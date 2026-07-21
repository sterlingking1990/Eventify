package com.example.PTicketing.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public class SlugUtils {

    public static String toSlug(String input) {
        String slug = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isEmpty() ? "event" : slug;
    }

    public static String uniqueSlug(String input) {
        String base = toSlug(input);
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
