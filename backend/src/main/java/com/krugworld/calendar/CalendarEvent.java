package com.krugworld.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "calendar_event")
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "occurs_on", nullable = false)
    private LocalDate occursOn;

    @Column(name = "at_time")
    private LocalTime atTime;

    @Column(nullable = false, length = 200)
    private String title;

    protected CalendarEvent() {
    }

    public CalendarEvent(Long userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getOccursOn() {
        return occursOn;
    }

    public void setOccursOn(LocalDate occursOn) {
        this.occursOn = occursOn;
    }

    public LocalTime getAtTime() {
        return atTime;
    }

    public void setAtTime(LocalTime atTime) {
        this.atTime = atTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
