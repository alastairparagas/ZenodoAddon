package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.Runner
import oracle.pgx.api.{PgxGraph, PgxVertex}

class PgxRunner extends
  Runner[PgxGraph, PgxVertex[String]](new PgxSessionControl)
