package com.edge.pulse.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphUserProfile(
    String id,
    String displayName,
    String mail,
    String userPrincipalName,   // always set; fallback when mail is null
    String jobTitle,
    String department,
    String employeeId,
    String officeLocation,
    String companyName,
    Boolean accountEnabled   // null-safe; treat null as true
) {
    /** Returns mail if set, otherwise falls back to userPrincipalName. */
    public String effectiveEmail() {
        return (mail != null && !mail.isBlank()) ? mail : userPrincipalName;
    }
}
