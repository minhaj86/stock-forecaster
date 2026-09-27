import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import LinearProgress from '@mui/material/LinearProgress';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useMemo, useState, type ReactNode } from 'react';
import { useParams, useSearchParams } from 'react-router';
import { toApiError } from '@/api/apiError';
import { LazyEChart } from '@/components/charts/LazyEChart';
import { useChartTheme } from '@/components/charts/useChartTheme';
import { ChangeIndicator } from '@/components/common/ChangeIndicator';
import { Disclaimer } from '@/components/common/Disclaimer';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PeriodSelector } from '@/components/common/PeriodSelector';
import { ChartSkeleton } from '@/components/common/Skeletons';
import { ForecastPanel } from '@/features/forecast/ForecastPanel';
import { AddToWatchlistButton } from '@/features/watchlist/AddToWatchlistButton';
import type { StockSnapshot } from '@/models/analytics';
import type { MarketPeriod } from '@/models/marketData';
import { formatCurrency, formatDate, formatNumber, formatPercent, formatTime } from '@/utils/format';
import { useStock, useStockAnalytics, useStockPrices } from './hooks';
import { buildPriceOption } from './priceChartOption';

const CHART_PERIODS: MarketPeriod[] = ['5D', '1M', '3M', '6M', 'YTD', '1Y', '5Y', 'MAX'];

function Metric({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
      <Card sx={{ height: '100%' }}>
        <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
          <Tooltip title={hint ?? ''} disableHoverListener={!hint}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>{label}</Typography>
          </Tooltip>
          <Typography component="div" sx={{ fontWeight: 600, mt: 0.5 }}>{children}</Typography>
        </CardContent>
      </Card>
    </Grid>
  );
}

function MetricGrid({ snapshot }: { snapshot: StockSnapshot }) {
  return (
    <Stack spacing={2}>
      <Typography variant="h6" component="h2">Performance</Typography>
      <Grid container spacing={2}>
        <Metric label="1 day"><ChangeIndicator value={snapshot.changePercent} /></Metric>
        <Metric label="1 month"><ChangeIndicator value={snapshot.return1M} /></Metric>
        <Metric label="6 months"><ChangeIndicator value={snapshot.return6M} /></Metric>
        <Metric label="1 year"><ChangeIndicator value={snapshot.return1Y} /></Metric>
        <Metric label="Year to date"><ChangeIndicator value={snapshot.returnYtd} /></Metric>
      </Grid>
      <Typography variant="h6" component="h2">Risk &amp; momentum</Typography>
      <Grid container spacing={2}>
        <Metric label="Volatility (30D)" hint="Annualised standard deviation of the last 30 daily returns">
          {formatPercent(snapshot.volatility30d, { signed: false, digits: 1 })}
        </Metric>
        <Metric label="Volatility (90D)" hint="Annualised standard deviation of the last 90 daily returns">
          {formatPercent(snapshot.volatility90d, { signed: false, digits: 1 })}
        </Metric>
        <Metric label="Max drawdown (1Y)" hint="Largest fall from a previous peak within the last year">
          <ChangeIndicator value={snapshot.maxDrawdown1Y} />
        </Metric>
        <Metric label="RSI (14)" hint="Relative Strength Index; above 70 is often read as overbought, below 30 as oversold">
          {formatNumber(snapshot.rsi14, 1)}
        </Metric>
        <Metric label="Momentum (3M)" hint="Price change over the last 63 trading days">
          <ChangeIndicator value={snapshot.momentum3M} />
        </Metric>
      </Grid>
    </Stack>
  );
}

export default function StockDetailsPage() {
  const { symbol = '' } = useParams();
  const upper = symbol.toUpperCase();
  const [period, setPeriod] = useState<MarketPeriod>('1Y');
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'forecast' ? 'forecast' : 'overview';
  const stock = useStock(upper);
  const analytics = useStockAnalytics(upper);
  const prices = useStockPrices(upper, period);
  const chartTheme = useChartTheme();
  const option = useMemo(
    () => (prices.data ? buildPriceOption(prices.data.bars, prices.data.currency, chartTheme) : null),
    [prices.data, chartTheme],
  );

  if (stock.isError) {
    const notFound = toApiError(stock.error).status === 404;
    return notFound
      ? <EmptyState title={`No stock with symbol “${upper}”`} description="Check the ticker or use the search above." />
      : <ErrorState title="Unable to load this stock" error={stock.error} onRetry={() => void stock.refetch()} />;
  }

  const snapshot = analytics.data;
  const status = snapshot?.dataStatus;

  return (
    <>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: 2, mb: 3 }}>
        <Box>
          {stock.data ? (
            <Typography variant="h4" component="h1">{stock.data.companyName}</Typography>
          ) : <Skeleton width={320} height={44} />}
          <Stack direction="row" spacing={1} sx={{ mt: 1, alignItems: 'center' }}>
            <Chip label={upper} size="small" color="primary" sx={{ fontWeight: 700 }} />
            {stock.data && <Chip label={stock.data.exchange} size="small" variant="outlined" />}
            {stock.data?.sector && <Typography variant="body2" color="text.secondary">{stock.data.sector}</Typography>}
          </Stack>
          {snapshot?.dataStatus === 'READY' && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="h3" component="p" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                {formatCurrency(snapshot.price, snapshot.currency)}
              </Typography>
              <ChangeIndicator value={snapshot.changePercent} change={snapshot.change} currency={snapshot.currency} />
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Close of {formatDate(snapshot.asOf)} · Last updated {formatTime(snapshot.lastSyncedAt)}
              </Typography>
            </Box>
          )}
        </Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <AddToWatchlistButton symbol={upper} />
          <Tooltip title="Stock comparison is coming in a later release">
            <span><Button startIcon={<CompareArrowsIcon />} disabled>Compare</Button></span>
          </Tooltip>
          <Tooltip title="The AI assistant is coming in a later release">
            <span><Button startIcon={<AutoAwesomeOutlinedIcon />} disabled>Ask AI</Button></span>
          </Tooltip>
        </Stack>
      </Box>

      <Tabs value={tab} aria-label="Stock sections" sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}
        onChange={(_, next: string) => setSearchParams(next === 'forecast' ? { tab: 'forecast' } : {}, { replace: true })}>
        <Tab label="Overview" value="overview" id="stock-tab-overview" aria-controls="stock-panel-overview" />
        <Tab label="Forecast" value="forecast" id="stock-tab-forecast" aria-controls="stock-panel-forecast" />
      </Tabs>
      {tab === 'forecast' ? (
        <Box role="tabpanel" id="stock-panel-forecast" aria-labelledby="stock-tab-forecast">
          <ForecastPanel symbol={upper} />
        </Box>
      ) : (
        <Box role="tabpanel" id="stock-panel-overview" aria-labelledby="stock-tab-overview">
          {analytics.isPending && <LinearProgress aria-label="Loading market data" />}
          {analytics.isError && (
            <ErrorState title="Unable to load market data" error={analytics.error} onRetry={() => void analytics.refetch()} />
          )}
          {status === 'NOT_TRACKED' && (
            <Alert severity="info" sx={{ mb: 3 }} action={<AddToWatchlistButton symbol={upper} variant="icon" />}>
              Market data for {upper} is collected once it is on a watchlist. Add it to one to start tracking.
            </Alert>
          )}
          {status === 'PENDING' && (
            <Alert severity="info" sx={{ mb: 3 }} role="status">Collecting market data for {upper}… this page updates automatically.</Alert>
          )}
          {status === 'UNAVAILABLE' && (
            <Alert severity="warning" sx={{ mb: 3 }}>
              Market data for {upper} could not be retrieved yet. {snapshot?.syncIssue}
            </Alert>
          )}
          {snapshot?.dataStatus === 'READY' && snapshot.syncIssue && (
            <Alert severity="warning" sx={{ mb: 3 }}>
              The market-data provider is temporarily unavailable. Previously stored data is shown
              (last successful update {formatTime(snapshot.lastSyncedAt)}).
            </Alert>
          )}

          {status === 'READY' && (
            <Stack spacing={3}>
              <Card component="section" aria-labelledby="price-heading">
                <CardHeader
                  title="Price"
                  slotProps={{ title: { id: 'price-heading', variant: 'h6', component: 'h2' } }}
                  action={<PeriodSelector value={period} options={CHART_PERIODS} onChange={setPeriod} label="Chart period" />}
                  sx={{ flexWrap: 'wrap', gap: 1, '& .MuiCardHeader-action': { alignSelf: 'center', m: 0 } }}
                />
                <CardContent sx={{ pt: 0 }}>
                  {prices.isPending && <ChartSkeleton height={340} />}
                  {prices.isError && <ErrorState title="Unable to load price history" error={prices.error} onRetry={() => void prices.refetch()} />}
                  {prices.data && prices.data.bars.length === 0 && (
                    <Alert severity="info" role="status">
                      {prices.data.dataStatus === 'PENDING'
                        ? 'Collecting price history… the chart appears automatically.'
                        : 'No prices are available for this period.'}
                    </Alert>
                  )}
                  {option && prices.data && prices.data.bars.length > 0 && (
                    <Box sx={{ opacity: prices.isPlaceholderData ? 0.6 : 1 }}>
                      <LazyEChart option={option} height={340}
                        ariaLabel={`${upper} daily closing prices for the ${period} period`} />
                    </Box>
                  )}
                </CardContent>
              </Card>
              {snapshot && <MetricGrid snapshot={snapshot} />}
            </Stack>
          )}
          <Disclaimer />
        </Box>
      )}
    </>
  );
}
