package com.krugworld.quest;

import com.krugworld.auth.CurrentUser;
import com.krugworld.quest.QuestService.ContributionRequest;
import com.krugworld.quest.QuestService.QuestRequest;
import com.krugworld.quest.QuestService.QuestView;
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
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestService quests;

    public QuestController(QuestService quests) {
        this.quests = quests;
    }

    @GetMapping
    public List<QuestView> list(@AuthenticationPrincipal CurrentUser me) {
        return quests.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestView create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody QuestRequest body) {
        return quests.create(me.id(), body);
    }

    @PutMapping("/{id}")
    public QuestView update(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                            @Valid @RequestBody QuestRequest body) {
        return quests.update(me.id(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        quests.delete(me.id(), id);
    }

    @PostMapping("/{id}/contributions")
    public QuestView contribute(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                                @Valid @RequestBody ContributionRequest body) {
        return quests.contribute(me.id(), id, body);
    }
}
