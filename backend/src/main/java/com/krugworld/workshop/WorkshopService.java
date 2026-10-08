package com.krugworld.workshop;

import com.krugworld.common.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/** The idea board: pin an idea, start it, finish it. */
@Service
@Transactional
public class WorkshopService {

    public record IdeaRequest(@NotBlank @Size(max = 120) String title, @Size(max = 1000) String notes,
                              Idea.Status status) {
    }

    public record IdeaView(Long id, String title, String notes, Idea.Status status, OffsetDateTime updatedAt) {
        static IdeaView of(Idea i) {
            return new IdeaView(i.getId(), i.getTitle(), i.getNotes(), i.getStatus(), i.getUpdatedAt());
        }
    }

    private final IdeaRepository ideas;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WorkshopService(IdeaRepository ideas, ApplicationEventPublisher events, Clock clock) {
        this.ideas = ideas;
        this.events = events;
        this.clock = clock;
    }

    /** In progress first, then to-do, then done; most recently touched first within each. */
    @Transactional(readOnly = true)
    public List<IdeaView> list(Long userId) {
        Comparator<Idea> byStatus = Comparator.comparingInt(i -> switch (i.getStatus()) {
            case DOING -> 0;
            case TODO -> 1;
            case DONE -> 2;
        });
        return ideas.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .sorted(byStatus)
                .map(IdeaView::of)
                .toList();
    }

    public IdeaView create(Long userId, IdeaRequest req) {
        Idea idea = new Idea(userId);
        apply(idea, req);
        Idea saved = ideas.save(idea);
        announceIfDone(saved, Idea.Status.TODO);
        return IdeaView.of(saved);
    }

    public IdeaView update(Long userId, Long id, IdeaRequest req) {
        Idea idea = idea(userId, id);
        Idea.Status before = idea.getStatus();
        apply(idea, req);
        announceIfDone(idea, before);
        return IdeaView.of(idea);
    }

    public void delete(Long userId, Long id) {
        ideas.delete(idea(userId, id));
    }

    private void apply(Idea idea, IdeaRequest req) {
        idea.setTitle(req.title().trim());
        idea.setNotes(req.notes() == null ? "" : req.notes().trim());
        if (req.status() != null) {
            idea.setStatus(req.status());
        }
        idea.setUpdatedAt(OffsetDateTime.now(clock));
    }

    private void announceIfDone(Idea idea, Idea.Status before) {
        if (idea.getStatus() == Idea.Status.DONE && before != Idea.Status.DONE) {
            events.publishEvent(new IdeaFinished(idea.getUserId(), idea.getId()));
        }
    }

    private Idea idea(Long userId, Long id) {
        return ideas.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Idea"));
    }
}
