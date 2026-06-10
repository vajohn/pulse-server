package com.edge.pulse.services;

import com.edge.pulse.data.dto.SfUserRecord;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Exports user data as CSV — either live from SF or from the local database.
 *
 * <p>SF export fields: firstName, lastName, email, status, title, department, division, employeeType, function
 * <p>DB export fields: displayName, email, department, division, status
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SfUserExportService {

    private static final List<String> DEFAULT_SF_FIELDS = List.of("firstName", "lastName", "email");
    private static final List<String> DEFAULT_DB_FIELDS = List.of("firstName", "lastName", "email");

    private final SfODataClient sfClient;
    private final UserRepository userRepository;

    /**
     * Fetches all SF users (live OData call) and returns a CSV with the requested columns.
     * Falls back to {@link #DEFAULT_SF_FIELDS} when {@code fields} is null or empty.
     */
    public String buildCsv(List<String> fields) {
        List<String> columns = (fields == null || fields.isEmpty()) ? DEFAULT_SF_FIELDS : fields;

        List<SfUserRecord> users = sfClient.fetchAllUsers();
        log.info("SfExport: building CSV for {} users, columns={}", users.size(), columns);

        StringBuilder sb = new StringBuilder();

        // Header row
        appendRow(sb, columns.toArray(String[]::new));

        // Data rows
        for (SfUserRecord u : users) {
            String[] values = columns.stream()
                    .map(col -> resolveField(u, col))
                    .toArray(String[]::new);
            appendRow(sb, values);
        }

        return sb.toString();
    }

    /**
     * Reads all users from the local database and returns a CSV with the requested columns.
     * Falls back to {@link #DEFAULT_DB_FIELDS} when {@code fields} is null or empty.
     *
     * <p>Note: firstName/lastName are not stored separately in the DB — use {@code displayName}.
     */
    @Transactional(readOnly = true)
    public String buildCsvFromDb(List<String> fields) {
        List<String> columns = (fields == null || fields.isEmpty()) ? DEFAULT_DB_FIELDS : fields;

        List<User> users = userRepository.findAll();
        log.info("SfExport (DB): building CSV for {} users, columns={}", users.size(), columns);

        StringBuilder sb = new StringBuilder();
        appendRow(sb, columns.toArray(String[]::new));

        for (User u : users) {
            String[] values = columns.stream()
                    .map(col -> resolveDbField(u, col))
                    .toArray(String[]::new);
            appendRow(sb, values);
        }

        return sb.toString();
    }

    private String resolveDbField(User u, String field) {
        return switch (field.trim()) {
            case "firstName"    -> extractFirstName(u.getDisplayName());
            case "lastName"     -> extractLastName(u.getDisplayName());
            case "middleName"   -> extractMiddleName(u.getDisplayName());
            case "email"        -> u.getEmail();
            case "department"   -> u.getDepartment();
            case "division"     -> u.getDivision();
            case "status"       -> u.isActive() ? "active" : "inactive";
            default             -> "";
        };
    }

    /** First word of the display name. */
    private String extractFirstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "";
        String[] parts = displayName.trim().split("\\s+");
        return parts[0];
    }

    /** Last word of the display name. Empty string if only one word. */
    private String extractLastName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "";
        String[] parts = displayName.trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : "";
    }

    /** Everything between the first and last word. Empty string if fewer than 3 words. */
    private String extractMiddleName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length < 3) return "";
        return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 1));
    }

    private String resolveField(SfUserRecord u, String field) {
        return switch (field.trim()) {
            case "firstName"   -> u.firstName();
            case "lastName"    -> u.lastName();
            case "email"       -> u.effectiveEmail();
            case "status"      -> u.status();
            case "title"       -> u.title();
            case "department"  -> u.department();
            case "division"    -> u.division();
            case "employeeType" -> u.custom05();
            case "function"    -> u.custom08();
            default            -> "";
        };
    }

    /** Appends a single CSV row, quoting any value that contains a comma, quote, or newline. */
    private void appendRow(StringBuilder sb, String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(values[i]));
        }
        sb.append("\r\n");
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
