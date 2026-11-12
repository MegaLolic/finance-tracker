# Local credentials

The root `.env` contains `JWT_SECRET`, PostgreSQL credentials, and Grafana admin
credentials. It is excluded from Git. For a fresh checkout, copy `.env.example`
to `.env` and fill in the empty values.

Run `docker compose up -d` from the repository root. Compose reads `.env`
automatically. The Java services import the same file when their working
directory is the repository root or their module directory; no IDEA dotenv
plugin is required. From another working directory, supply the variables through
the process environment instead.

Keep values on a single line without quotes or `export`. The file is also parsed
as Java properties: avoid backslash escapes and Docker interpolation expressions
in values. A randomly generated hexadecimal JWT secret is compatible with both
parsers; HS512 requires at least 64 UTF-8 bytes in the current implementation.

Existing credential values were moved without rotation. Changing PostgreSQL or
Grafana variables does not reset passwords already stored in existing Docker
volumes. Secrets previously committed remain in Git history; moving them into
`.env` does not remove that history.
