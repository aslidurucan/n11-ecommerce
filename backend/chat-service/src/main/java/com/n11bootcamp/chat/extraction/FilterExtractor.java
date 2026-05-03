package com.n11bootcamp.chat.extraction;

public interface FilterExtractor {
    ExtractedFilter extract(String userQuery);
}
