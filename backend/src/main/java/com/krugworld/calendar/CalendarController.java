package com.krugworld.calendar;

import com.krugworld.auth.CurrentUser;
import com.krugworld.calendar.CalendarService.EventRequest;
import com.krugworld.calendar.CalendarService.EventView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class CalendarController {

    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    /** Events from {@code from} to {@code to}, both inclusive (YYYY-MM-DD). */
    @GetMapping
    public List<EventView> between(@AuthenticationPrincipal CurrentUser me,
                                   @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return calendar.between(me.id(), from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventView create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody EventRequest body) {
        return calendar.create(me.id(), body);
    }

    @PutMapping("/{id}")
    public EventView update(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                            @Valid @RequestBody EventRequest body) {
        return calendar.update(me.id(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        calendar.delete(me.id(), id);
    }
}
