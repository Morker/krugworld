package com.krugworld.admin;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** The append-only record of admin changes. Nothing in the app updates or deletes these rows. */
@Repository
public class AdminAudit {

    public enum Action { ENTRY_CREATED, ENTRY_UPDATED, ENTRY_DELETED, BALANCE_SET }

    /** {@code before} and {@code after} are JSON snapshots, sent as they are. */
    public record Row(Long id, String admin, Action action, Long refId,
                      @JsonRawValue String before, @JsonRawValue String after, OffsetDateTime at) {
    }

    private static final int SHOWN = 50;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AdminAudit(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(Long adminId, Long targetUserId, Action action, Long refId, Object before, Object after) {
        jdbc.update("""
                INSERT INTO admin_audit (admin_id, target_user_id, action, ref_id, before_state, after_state)
                VALUES (?, ?, ?, ?, ?, ?)
                """, adminId, targetUserId, action.name(), refId, snapshot(before), snapshot(after));
    }

    public List<Row> recent(Long targetUserId) {
        return jdbc.query("""
                SELECT a.id, u.username, a.action, a.ref_id, a.before_state, a.after_state, a.created_at
                FROM admin_audit a
                JOIN app_user u ON u.id = a.admin_id
                WHERE a.target_user_id = ?
                ORDER BY a.id DESC
                LIMIT ?
                """,
                (rs, n) -> new Row(rs.getLong("id"), rs.getString("username"), Action.valueOf(rs.getString("action")),
                        rs.getLong("ref_id"), rs.getString("before_state"), rs.getString("after_state"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                targetUserId, SHOWN);
    }

    private String snapshot(Object state) {
        return state == null ? null : json.valueToTree(state).toString();
    }
}
