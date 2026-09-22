# Keycloak realm import (M0-04)

Import `nexus-realm.json` into a **native** Keycloak 26.5.2 process. See `nexus-p2-iam/README.md` for the no-Docker run steps.

The client secret in the JSON is a placeholder. Set the real secret via environment (`NEXUS_KEYCLOAK_CLIENT_SECRET`). Do not commit secrets. Direct-grant smoke clients from `poc/p0-03` are not part of this realm.
