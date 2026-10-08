package com.krugworld.vitals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "meal")
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "eaten_on", nullable = false)
    private LocalDate eatenOn;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int calories;

    protected Meal() {
    }

    public Meal(Long userId, LocalDate eatenOn, String name, int calories) {
        this.userId = userId;
        this.eatenOn = eatenOn;
        this.name = name;
        this.calories = calories;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getEatenOn() {
        return eatenOn;
    }

    public String getName() {
        return name;
    }

    public int getCalories() {
        return calories;
    }
}
