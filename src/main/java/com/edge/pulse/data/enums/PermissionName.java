package com.edge.pulse.data.enums;

import lombok.Getter;

@Getter
public enum PermissionName {

    // ── USR — User Management ────────────────────────────────────────────────
    USR_READ("View user profiles, search the directory"),
    USR_CREATE("Create user accounts manually (outside sync)"),
    USR_UPDATE("Edit user profile fields"),
    USR_DELETE("Deactivate users (SF handles removal via sync)"),
    USR_ROLE_ASSIGN("Assign and remove roles from users"),
    USR_IMPORT("Trigger and monitor bulk SF / Entra directory sync"),
    USR_ALL("All user management permissions"),

    // ── ORG — Org Unit Management ────────────────────────────────────────────
    ORG_READ("View org unit tree, unit details, and membership"),
    ORG_CREATE("Create org units in Pulse (SF is authoritative — Pulse stores metadata)"),
    ORG_UPDATE("Edit org unit metadata (SF handles structural changes)"),
    ORG_DELETE("Soft-delete (temporarily disable) an org unit"),
    ORG_MOVE_USER("Transfer a user to a different org unit in Pulse"),
    ORG_ALL("All org unit management permissions"),

    // ── SYNC — Directory Sync ────────────────────────────────────────────────
    SYNC_TRIGGER("Trigger a full or delta SF / Entra directory sync job"),
    SYNC_STATUS("View sync job results, error reports, skipped users"),
    SYNC_ALL("All directory sync permissions"),

    // ── ROLE — Role & Permission Management ─────────────────────────────────
    ROLE_READ("List roles and view their full permission sets"),
    ROLE_CREATE("Create new empty roles"),
    ROLE_UPDATE("Replace a role's permission set atomically"),
    ROLE_DELETE("Soft-delete a role (rejected if users are assigned)"),
    ROLE_ASSIGN_APPROVE("Countersign role-assignment changes (dual-approval)"),
    ROLE_ALL("All role management permissions"),

    // ── SCOPE — Data Visibility Qualifiers (no _ALL) ─────────────────────────
    SCOPE_TEAM("Team and subtree data visibility (own org unit and descendants)"),
    SCOPE_ENTITY("Company / legal entity data visibility (SF custom01 field)"),
    SCOPE_ORG_WIDE("Full org-wide visibility across all entities"),

    // ── FORM — Form & Survey Management ─────────────────────────────────────
    FORM_READ("View forms and their questions"),
    FORM_CREATE("Create new forms"),
    FORM_UPDATE("Edit form content, questions, and settings"),
    FORM_DELETE("Delete or permanently archive forms"),
    FORM_ASSIGN("Assign forms to users or org units"),
    FORM_PUBLISH("Publish, unpublish, and configure form lifecycle"),
    FORM_SESSION_READ("View and manage form response sessions (admin monitoring)"),
    FORM_ALL("All form management permissions"),

    // ── ASSESS — Assessment & Psychometric Management ────────────────────────
    ASSESS_READ("View psychometric test definitions, scales, and configurations"),
    ASSESS_CREATE("Create psychometric tests and add scales"),
    ASSESS_UPDATE("Edit psychometric test and scale configurations"),
    ASSESS_DELETE("Archive psychometric tests"),
    ASSESS_ASSIGN("Assign psychometric tests to candidate cohorts or org units"),
    ASSESS_KEY_MANAGE("Upload, approve, or reject scoring keys and norm tables"),
    ASSESS_RESULT_READ("View candidate test result details and score profiles"),
    ASSESS_COMPETENCY_MANAGE("Manage competency frameworks and scale weights"),
    ASSESS_ALL("All assessment management permissions"),

    // ── REPORT — Analytics & Reporting ───────────────────────────────────────
    REPORT_VIEW("View analytics dashboards and summary reports"),
    REPORT_EXPORT("Export report data to CSV or Excel"),
    REPORT_TEXT_VIEW("View free-text survey responses (privacy-sensitive)"),
    REPORT_ASSESS_VIEW("View psychometric result dashboards and score profiles"),
    REPORT_ALL("All reporting and analytics permissions"),

    // ── SPARK — Recognition & Rewards ────────────────────────────────────────
    SPARK_NOMINATE("Submit Spark award nominations"),
    SPARK_VOTE("Vote on nominations as an entity leader"),
    SPARK_REVIEW("Read-only view of Spark nominations within scope (HR compliance)"),
    SPARK_MANAGE("Full Spark administration: periods, winners, awards"),
    SPARK_ALL("All Spark recognition permissions"),

    // ── AI — AI Features ─────────────────────────────────────────────────────
    AI_USE("Use AI assistant for analytics queries and insights"),

    // ── SYS — System & Audit ─────────────────────────────────────────────────
    SYS_AUDIT_VIEW("View audit logs and admin activity history"),
    SYS_APPROVE("Approve pending admin operations in approval workflows");

    private final String description;

    PermissionName(String description) { this.description = description; }
}
