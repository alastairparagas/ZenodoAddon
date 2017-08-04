package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.Runner
import oracle.pgx.api.PgxGraph

class PgxRunner extends Runner[PgxGraph](new PgxSessionControl)
