package com.krugworld.calendar;

import com.krugworld.common.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class CalendarService {

    public record EventRequest(@NotNull LocalDate occursOn, LocalTime atTime,
                               @NotBlank @Size(max = 200) String title) {
    }

    public record EventView(Long id, LocalDate occursOn, LocalTime atTime, String title) {
        static EventView of(CalendarEvent e) {
            return new EventView(e.getId(), e.getOccursOn(), e.getAtTime(), e.getTitle());
        }
    }

    private final CalendarEventRepository events;

    public CalendarService(CalendarEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<EventView> between(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 400) {
            throw ApiException.badRequest("bad_range", "Ask for at most about a year of events at a time.");
        }
        return events.between(userId, from, to)
                .stream().map(EventView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<EventView> upcoming(Long userId, LocalDate from) {
        return events.upcoming(userId, from, Limit.of(5))
                .stream().map(EventView::of).toList();
    }

    public EventView create(Long userId, EventRequest req) {
        CalendarEvent event = new CalendarEvent(userId);
        apply(event, req);
        return EventView.of(events.save(event));
    }

    public EventView update(Long userId, Long id, EventRequest req) {
        CalendarEvent event = event(userId, id);
        apply(event, req);
        return EventView.of(event);
    }

    public void delete(Long userId, Long id) {
        events.delete(event(userId, id));
    }

    private static void apply(CalendarEvent event, EventRequest req) {
        event.setOccursOn(req.occursOn());
        event.setAtTime(req.atTime());
        event.setTitle(req.title().trim());
    }

    private CalendarEvent event(Long userId, Long id) {
        return events.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Event"));
    }
}
