package com.krugworld.vitals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "workout")
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String name;

    /** One bit per weekday, Monday = bit 0 ... Sunday = bit 6; 0 = any day. */
    @Column(nullable = false)
    private short weekdays;

    protected Workout() {
    }

    public Workout(Long userId) {
        this.userId = userId;
    }

    public Set<DayOfWeek> getDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            if ((weekdays & (1 << (d.getValue() - 1))) != 0) {
                days.add(d);
            }
        }
        return days;
    }

    public void setDays(Set<DayOfWeek> days) {
        int bits = 0;
        for (DayOfWeek d : days) {
            bits |= 1 << (d.getValue() - 1);
        }
        this.weekdays = (short) bits;
    }

    /** Planned for that date? A workout with no days set counts as every day. */
    public boolean isScheduledOn(LocalDate date) {
        return weekdays == 0 || getDays().contains(date.getDayOfWeek());
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
