import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { ChangeIndicator } from '@/components/common/ChangeIndicator';
import type { Dashboard, Mover } from '@/models/analytics';
import { formatPercent } from '@/utils/format';

function MetricCard({ title, mover, value, caption }: {
  title: string; mover?: Mover | null; value?: ReactNode; caption?: ReactNode;
}) {
  const navigate = useNavigate();
  const content = (
    <CardContent>
      <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>{title}</Typography>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mt: 1, flexWrap: 'wrap' }}>
        <Typography variant="h5" component="p">{mover ? mover.symbol : value ?? '—'}</Typography>
        {mover && value}
      </Box>
      {caption && <Typography variant="caption" color="text.secondary" component="p" noWrap>{caption}</Typography>}
    </CardContent>
  );
  return (
    <Card sx={{ height: '100%' }}>
      {mover ? (
        <CardActionArea sx={{ height: '100%' }} onClick={() => navigate(`/app/stocks/${encodeURIComponent(mover.symbol)}`)}
          aria-label={`${title}: ${mover.symbol}`}>
          {content}
        </CardActionArea>
      ) : content}
    </Card>
  );
}

const volatility = (mover: Mover | null | undefined) =>
  mover ? <Typography component="span" sx={{ fontWeight: 600 }}>{formatPercent(mover.value, { signed: false, digits: 1 })}</Typography> : undefined;

export function SummaryCards({ dashboard }: { dashboard: Dashboard }) {
  const { movers } = dashboard;
  return (
    <Grid container spacing={2} component="section" aria-label="Watchlist summary">
      <Grid size={{ xs: 12, sm: 4 }}>
        <MetricCard
          title="Watchlist"
          value={`${dashboard.stockCount} ${dashboard.stockCount === 1 ? 'stock' : 'stocks'}`}
          caption={`across ${dashboard.watchlistCount} ${dashboard.watchlistCount === 1 ? 'watchlist' : 'watchlists'}`}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 4 }}>
        <MetricCard
          title={`Best performer (${dashboard.period})`}
          mover={movers.topPerformer}
          value={movers.topPerformer && <ChangeIndicator value={movers.topPerformer.value} />}
          caption={movers.topPerformer?.companyName}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 4 }}>
        <MetricCard
          title="Highest volatility (30D)"
          mover={movers.highestVolatility}
          value={volatility(movers.highestVolatility)}
          caption={movers.highestVolatility?.companyName}
        />
      </Grid>
    </Grid>
  );
}

/** Movers of the selected period plus risk and momentum leaders, all computed by the backend. */
export function MoversSection({ dashboard }: { dashboard: Dashboard }) {
  const { movers } = dashboard;
  const weakest = movers.weakestPerformer;
  const declineTitle = weakest && weakest.value < 0 ? 'Largest decline' : 'Weakest performer';
  const cards = [
    { title: `Top performer (${dashboard.period})`, mover: movers.topPerformer, value: movers.topPerformer && <ChangeIndicator value={movers.topPerformer.value} size="small" /> },
    { title: `${declineTitle} (${dashboard.period})`, mover: weakest, value: weakest && <ChangeIndicator value={weakest.value} size="small" /> },
    { title: 'Lowest volatility (30D)', mover: movers.lowestVolatility, value: volatility(movers.lowestVolatility) },
    { title: 'Highest volatility (30D)', mover: movers.highestVolatility, value: volatility(movers.highestVolatility) },
    { title: 'Strongest momentum (3M)', mover: movers.strongestMomentum, value: movers.strongestMomentum && <ChangeIndicator value={movers.strongestMomentum.value} size="small" /> },
  ];
  return (
    <Grid container spacing={2} component="section" aria-label="Market movers">
      {cards.map((card) => (
        <Grid key={card.title} size={{ xs: 12, sm: 6, md: 4, lg: 2.4 }}>
          <MetricCard title={card.title} mover={card.mover} value={card.value} caption={card.mover?.companyName} />
        </Grid>
      ))}
    </Grid>
  );
}
