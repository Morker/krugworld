package com.coffold.importer;

import com.coffold.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TavernImportController {

    private final TavernImportService importer;

    public TavernImportController(TavernImportService importer) {
        this.importer = importer;
    }

    @PostMapping("/api/import/tavern")
    public TavernImportService.ImportResult importTavern(@AuthenticationPrincipal CurrentUser me,
                                                         @RequestBody JsonNode payload) {
        return importer.importTavern(me.id(), payload);
    }
}
