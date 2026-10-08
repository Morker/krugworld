package com.coffold.game;

import com.coffold.auth.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Everything the Hall (dashboard) shows, in one request. */
@RestController
public class CharacterController {

    private final CharacterService characters;

    public CharacterController(CharacterService characters) {
        this.characters = characters;
    }

    @GetMapping("/api/character")
    public CharacterService.CharacterSheet character(@AuthenticationPrincipal CurrentUser me) {
        return characters.sheet(me.id());
    }
}
