package com.edge.pulse.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single user record from the SAP SuccessFactors OData v2 User entity.
 *
 * <p>Org hierarchy is stored denormalized on the User entity:
 * <ul>
 *   <li>custom02 → GROUP level (e.g. "EDGE Missiles and Weapons (13000)")</li>
 *   <li>custom01 → ENTITY level (e.g. "Al Tariq - Barij Dynamics LLC (4600)")</li>
 *   <li>division  → ORG_UNIT level (e.g. "Testing and Software (10001368)")</li>
 *   <li>department → TEAM level (e.g. "Testing and Software (10010992)")</li>
 * </ul>
 *
 * <p>Code extraction: split on last '(' and strip the trailing ')'.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SfUserRecord(
        @JsonProperty("userId")       String userId,
        @JsonProperty("username")     String username,
        @JsonProperty("firstName")    String firstName,
        @JsonProperty("lastName")     String lastName,
        @JsonProperty("title")        String title,
        @JsonProperty("hireDate")     String hireDate,      // ISO date string or SF /Date(...)/
        @JsonProperty("isAlumni")     Boolean isAlumni,
        @JsonProperty("companyExitDate") String companyExitDate,
        @JsonProperty("status")       String status,        // "active" or "inactive"
        @JsonProperty("department")   String department,    // TEAM level
        @JsonProperty("division")     String division,      // ORG_UNIT level
        @JsonProperty("custom01")     String custom01,      // ENTITY level
        @JsonProperty("custom02")     String custom02,      // GROUP level
        @JsonProperty("custom03")     String custom03,      // spare
        @JsonProperty("custom05")     String custom05,      // employee type
        @JsonProperty("custom08")     String custom08,      // function
        @JsonProperty("custom09")     String custom09,      // spare
        @JsonProperty("email")        String email,
        @JsonProperty("manager")      SfManagerRef manager
) {
    /** Derives the email address from SF data.
     *  SF username in preview often carries an "X-" prefix — strip it.
     *  Falls back to the email field if username is not a valid UPN. */
    public String effectiveEmail() {
        if (username != null && username.contains("@")) {
            String stripped = username.startsWith("X-") ? username.substring(2) : username;
            return stripped.toLowerCase();
        }
        return email != null ? email.toLowerCase() : null;
    }

    /** Returns a displayable full name. */
    public String displayName() {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        if (firstName != null) return firstName;
        if (lastName  != null) return lastName;
        return username;
    }

    /** Extracts the code portion from a "(code)" suffix pattern.
     *  E.g. "EDGE Missiles and Weapons (13000)" → "13000".
     *  Returns the full value if no parentheses are found. */
    public static String extractCode(String value) {
        if (value == null || value.isBlank()) return null;
        int open  = value.lastIndexOf('(');
        int close = value.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return value.substring(open + 1, close).trim();
        }
        return value.trim();
    }

    /** Extracts the name portion before the "(code)" suffix.
     *  E.g. "EDGE Missiles and Weapons (13000)" → "EDGE Missiles and Weapons". */
    public static String extractName(String value) {
        if (value == null || value.isBlank()) return null;
        int open = value.lastIndexOf('(');
        if (open > 0) {
            return value.substring(0, open).trim();
        }
        return value.trim();
    }

    public boolean isActive() {
        // SF status field: "active" means active. Alumni or inactive = deactivated.
        if (Boolean.TRUE.equals(isAlumni)) return false;
        if (status != null && !status.equalsIgnoreCase("active")) return false;
        return true;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SfManagerRef(
            @JsonProperty("userId") String userId
    ) {}
}
