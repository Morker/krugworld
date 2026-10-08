package com.krugworld.workshop;

import com.krugworld.auth.CurrentUser;
import com.krugworld.workshop.WorkshopService.IdeaRequest;
import com.krugworld.workshop.WorkshopService.IdeaView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ideas")
public class WorkshopController {

    private final WorkshopService workshop;

    public WorkshopController(WorkshopService workshop) {
        this.workshop = workshop;
    }

    @GetMapping
    public List<IdeaView> list(@AuthenticationPrincipal CurrentUser me) {
        return workshop.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdeaView create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody IdeaRequest body) {
        return workshop.create(me.id(), body);
    }

    @PutMapping("/{id}")
    public IdeaView update(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                           @Valid @RequestBody IdeaRequest body) {
        return workshop.update(me.id(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        workshop.delete(me.id(), id);
    }
}
