# Flyway migrations — intentionally empty

This directory is wired to Flyway (`spring.flyway.locations=classpath:db/migration`)
**but ships no business migrations**. The reason:

- kuship-console **shares** the MySQL `console` schema with rainbond-console.
- The authoritative DDL lifecycle belongs to **rainbond-console (Django migrations)**.
  Any DDL change must land there first; kuship-console only validates.
- JPA is locked to `hibernate.ddl-auto=validate`. We never emit DDL from this side.

## When may we add a `V*__*.sql` here?

Only for tables that are **kuship-exclusive** (not read by Rainbond Go services
or rainbond-console), e.g. a Java-side audit/lock table that has no peer in
the Django world. Even then:

1. Confirm the table name is not used by Django models in `reference/rainbond-console/console/models/`.
2. Document the table's purpose in this README and link to the OpenSpec change that introduced it.

## Why keep Flyway enabled at all?

`baseline-on-migrate=true` + `baseline-version=0` lets the app boot against the
existing populated `console` database without complaint. It also reserves the
mechanism in case kuship-exclusive tables become necessary later.
