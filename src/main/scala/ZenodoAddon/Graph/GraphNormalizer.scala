package ZenodoAddon.Graph

import oracle.pgx.api.PgxGraph


trait GraphNormalizer[GraphTypeVar]
{
  def normalize(pgxGraph: GraphTypeVar): PgxGraph
}
