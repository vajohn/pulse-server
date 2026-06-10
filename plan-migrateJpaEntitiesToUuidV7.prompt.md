## Plan: Migrate JPA Entities to UUID (v7) Primary Keys

Update all relevant JPA entity models, repositories, and service classes to use UUID (v7) as the primary key for user, role, permission, team, group, and title entities. Ensure all entity relationships and mapping tables are updated accordingly, matching the latest Flyway migration. Validate changes with a clean build and test run.

### Steps
1. Identify all affected entity classes ([models](src/main/java/com/edge/pulse/data/models/)).
2. Update primary key fields and annotations to use UUID (v7) in each entity.
3. Refactor all entity relationships and mapping tables to use UUID.
4. Update corresponding repository interfaces ([repositories](src/main/java/com/edge/pulse/data/repositories/)) to use UUID as the ID type.
5. Refactor service classes ([services](src/main/java/com/edge/pulse/services/)) and any business logic to handle UUIDs.
6. Review and update any DTOs, controllers, or utility code that reference entity IDs.
7. Run a clean build and execute all tests to verify application context loads and passes.

### Further Considerations
1. Should legacy data migration or UUID conversion be handled, or is this a greenfield change?
2. Confirm if UUID v7 generation should be handled by the application or the database.
3. Review for any hardcoded ID references or test data that may need updating.

*Draft for your review—please confirm or clarify any requirements or constraints before proceeding.*

