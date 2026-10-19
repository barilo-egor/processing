package net.rcetech.web.documentation;

import net.rcetech.meta.documentation.dto.DocumentationItem;
import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.exception.BaseException;

import java.util.*;

class DocumentationPages {

    private final Map<String, DocumentationPage> pages;

    private final List<DocumentationItem> items;

    DocumentationPages(List<DocumentationPage> pages) {
        Map<String, DocumentationPage> byId = new LinkedHashMap<>();
        for (DocumentationPage page : pages) {
            if (Objects.nonNull(byId.putIfAbsent(page.id(), page))) {
                throw new BaseException("Duplicate documentation page id " + page.id());
            }
        }
        this.pages = Collections.unmodifiableMap(byId);
        this.items = byId.values().stream()
                .map(page -> new DocumentationItem(page.id(), page.title()))
                .toList();
    }

    List<DocumentationItem> items() {
        return items;
    }

    Optional<DocumentationPage> find(String id) {
        return Optional.ofNullable(pages.get(id));
    }
}
