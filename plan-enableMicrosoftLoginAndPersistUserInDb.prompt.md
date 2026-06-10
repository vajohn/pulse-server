## Plan: Enable Microsoft Login & Persist User in DB

This plan will update the project to support user login via Microsoft credentials, persist user info in a PostgreSQL database, and ensure all required dependencies and configurations are present. It covers database connectivity, Flyway migration, required Spring Boot components, and Azure AD integration.

### Steps
1. Update [`application.yaml`](src/main/resources/application.yaml) for PostgreSQL, Flyway, and JPA configs.
2. Add PostgreSQL and Flyway dependencies in [`build.gradle`](build.gradle).
3. Create a `User` entity and JPA repository in [`data/models/`] and [`repositories/`].
4. Implement a service to save user info after successful Microsoft login in [`services/`].
5. Update or create a controller to handle OAuth2 login success and trigger user persistence in [`controllers/`].
6. Ensure Azure AD Spring Security config is correct in [`configs/AadOAuth2LoginSecurityConfig.java`].
7. Add Flyway migration scripts in [`resources/db/migration/`] for the user table.

### Further Considerations
1. Should user info include only basic profile or also roles/permissions?
2. Should login be restricted to specific Azure AD groups/tenants?
3. Is email uniqueness required for user records?
4. Should additional error handling/logging be added for failed logins or DB errors?

Please review and specify any preferences or constraints before proceeding to a more detailed breakdown.

