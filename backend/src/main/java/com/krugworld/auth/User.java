package com.krugworld.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Optional; only used to send a password-reset link. Unique ignoring case. */
    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 20)
    private String skin = "modern";

    @Column(nullable = false, length = 20)
    private String theme = "steel";

    @Column(name = "calorie_goal", nullable = false)
    private int calorieGoal = 2000;

    @Column(name = "weather_place", length = 120)
    private String weatherPlace;

    @Column(name = "weather_lat", precision = 8, scale = 5)
    private BigDecimal weatherLat;

    @Column(name = "weather_lon", precision = 8, scale = 5)
    private BigDecimal weatherLon;

    @Column(name = "temp_unit", nullable = false, length = 1)
    private String tempUnit = "C";

    /** Read-only here on purpose: no code path can make someone an admin, only SQL on the server. */
    @Column(name = "is_admin", nullable = false, insertable = false, updatable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected User() {
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSkin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getCalorieGoal() {
        return calorieGoal;
    }

    public void setCalorieGoal(int calorieGoal) {
        this.calorieGoal = calorieGoal;
    }

    public String getWeatherPlace() {
        return weatherPlace;
    }

    public BigDecimal getWeatherLat() {
        return weatherLat;
    }

    public BigDecimal getWeatherLon() {
        return weatherLon;
    }

    /** Place name and coordinates move together: all set, or all cleared. */
    public void setWeatherLocation(String place, BigDecimal lat, BigDecimal lon) {
        this.weatherPlace = place;
        this.weatherLat = lat;
        this.weatherLon = lon;
    }

    public String getTempUnit() {
        return tempUnit;
    }

    public void setTempUnit(String tempUnit) {
        this.tempUnit = tempUnit;
    }

    public boolean isAdmin() {
        return admin;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
