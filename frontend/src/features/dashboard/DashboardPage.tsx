import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import RefreshIcon from '@mui/icons-material/Refresh';
import TravelExploreOutlinedIcon from '@mui/icons-material/TravelExploreOutlined';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { Disclaimer } from '@/components/common/Disclaimer';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { PeriodSelector } from '@/components/common/PeriodSelector';
import { CardGridSkeleton, ChartSkeleton, TableSkeleton } from '@/components/common/Skeletons';
import { StockMetricsTable, type StockRow } from '@/components/tables/StockMetricsTable';
import { useCurrentUserProfile } from '@/features/profile/hooks';
import { useWatchlists } from '@/features/watchlist/hooks';
import type { Dashboard } from '@/models/analytics';
import type { MarketPeriod } from '@/models/marketData';
import { formatDate, formatTime, greeting } from '@/utils/format';
import { MoversSection, SummaryCards } from './DashboardCards';
import { PerformanceChart } from './PerformanceChart';
import { useDashboard } from './useDashboard';

const PERIODS: MarketPeriod[] = ['1W', '1M', '3M', '6M', 'YTD', '1Y', '5Y'];
const TABLE_ROWS = 8;

function DataNotices({ dashboard }: { dashboard: Dashboard }) {
  const pending = dashboard.stocks.filter((stock) => stock.dataStatus === 'PENDING').map((stock) => stock.symbol);
  const issues = dashboard.stocks.filter((stock) => stock.syncIssue);
  return (
    <>
      {pending.length > 0 && (
        <Alert severity="info" sx={{ mb: 2 }} role="status">
          Collecting market data for {pending.join(', ')}. This usually takes a few seconds; the dashboard updates automatically.
        </Alert>
      )}
      {issues.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          The market-data provider is temporarily unavailable for {issues.map((stock) => stock.symbol).join(', ')}.
          Previously stored data is shown{dashboard.lastSyncedAt ? ` (last successful update ${formatTime(dashboard.lastSyncedAt)})` : ''}.
        </Alert>
      )}
    </>
  );
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const profile = useCurrentUserProfile();
  const watchlists = useWatchlists();
  const [period, setPeriod] = useState<MarketPeriod>('1M');
  const [watchlistId, setWatchlistId] = useState<string | null>(null);
  const dashboard = useDashboard(period, watchlistId);
  const data = dashboard.data;

  const name = profile.data?.displayName?.split(' ')[0];
  const subtitle = data?.lastSyncedAt
    ? `Your market overview · Last updated ${formatTime(data.lastSyncedAt)} · Market data as of ${formatDate(data.dataAsOf)}`
    : 'Your market overview';

  const rows: StockRow[] = (data?.stocks ?? []).map((snapshot) => ({
    symbol: snapshot.symbol, companyName: snapshot.companyName, exchange: snapshot.exchange,
    currency: snapshot.currency, snapshot,
  }));

  return (
    <>
      <PageHeader
        title={`${greeting()}${name ? `, ${name}` : ''}`}
        subtitle={subtitle}
        actions={(
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <TextField
              select
              size="small"
              label="Show"
              value={watchlistId ?? 'all'}
              onChange={(event) => setWatchlistId(event.target.value === 'all' ? null : event.target.value)}
              sx={{ minWidth: 200 }}
            >
              <MenuItem value="all">All watchlists</MenuItem>
              {watchlists.data?.map((watchlist) => (
                <MenuItem key={watchlist.id} value={watchlist.id}>{watchlist.name}</MenuItem>
              ))}
            </TextField>
            <Tooltip title="Refresh">
              <span>
                <IconButton onClick={() => void dashboard.refetch()} disabled={dashboard.isFetching} aria-label="Refresh dashboard">
                  <RefreshIcon />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
        )}
      />

      {dashboard.isPending && (
        <Stack spacing={3}>
          <CardGridSkeleton count={3} label="Loading summary" />
          <ChartSkeleton height={360} label="Loading performance chart" />
          <Card><TableSkeleton rows={6} label="Loading watchlist" /></Card>
        </Stack>
      )}

      {dashboard.isError && (
        <ErrorState title="Unable to load your dashboard" error={dashboard.error} onRetry={() => void dashboard.refetch()} />
      )}

      {data && data.stockCount === 0 && (
        <Card>
          <EmptyState
            icon={<TravelExploreOutlinedIcon />}
            title="Your watchlist is empty"
            description="Start following stocks to build your personalized dashboard."
            action={<Button variant="contained" onClick={() => navigate('/app/stocks')}>Search stocks</Button>}
          />
        </Card>
      )}

      {data && data.stockCount > 0 && (
        <Stack spacing={3}>
          <DataNotices dashboard={data} />
          <SummaryCards dashboard={data} />
          <Card component="section" aria-labelledby="performance-heading">
            <CardHeader
              title="Watchlist Performance"
              slotProps={{ title: { id: 'performance-heading', variant: 'h6', component: 'h2' } }}
              subheader="Indexed to 100 at the start of the period"
              action={<PeriodSelector value={period} options={PERIODS} onChange={setPeriod} label="Performance period" />}
              sx={{ flexWrap: 'wrap', gap: 1, '& .MuiCardHeader-action': { alignSelf: 'center', m: 0 } }}
            />
            <CardContent sx={{ pt: 0 }}>
              <Box sx={{ opacity: dashboard.isPlaceholderData ? 0.6 : 1, transition: 'opacity 150ms' }}>
                <PerformanceChart series={data.performance.series} periodLabel={period} />
              </Box>
            </CardContent>
          </Card>
          <MoversSection dashboard={data} />
          <Card component="section" aria-labelledby="watchlist-heading">
            <CardHeader
              title="Watchlist"
              slotProps={{ title: { id: 'watchlist-heading', variant: 'h6', component: 'h2' } }}
              action={(
                <Button component={RouterLink} to="/app/watchlists" endIcon={<ArrowForwardIcon />}>
                  View all
                </Button>
              )}
            />
            <StockMetricsTable
              rows={rows.slice(0, TABLE_ROWS)}
              columns={['price', 'today', 'return1M']}
              label="Your watched stocks"
            />
            {data.truncated && (
              <Alert severity="info" sx={{ m: 2 }}>The dashboard shows your first {data.stocks.length} stocks.</Alert>
            )}
          </Card>
        </Stack>
      )}
      <Disclaimer />
    </>
  );
}
