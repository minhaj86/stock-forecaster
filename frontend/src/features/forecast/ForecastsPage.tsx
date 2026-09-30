import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { toApiError } from '@/api/apiError';
import { Disclaimer } from '@/components/common/Disclaimer';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { useStock, useStockSearch } from '@/features/stocks/hooks';
import { useWatchlists } from '@/features/watchlist/hooks';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { FORECAST_DISCLAIMER } from '@/models/forecast';
import type { Stock } from '@/models/stock';
import { ForecastPanel } from './ForecastPanel';

const SEARCH_DEBOUNCE_MS = 350;

function StockPicker() {
  const navigate = useNavigate();
  const [input, setInput] = useState('');
  const query = useDebouncedValue(input, SEARCH_DEBOUNCE_MS);
  const search = useStockSearch(query);
  return (
    <Autocomplete<Stock>
      options={input.trim() ? search.data ?? [] : []}
      loading={search.isFetching}
      filterOptions={(options) => options}
      getOptionLabel={(stock) => `${stock.symbol} · ${stock.companyName}`}
      onInputChange={(_, value) => setInput(value)}
      onChange={(_, stock) => stock && void navigate(`/app/forecasts/${stock.symbol}`)}
      noOptionsText={input.trim() ? 'No matching stocks' : 'Type a ticker or company name'}
      sx={{ maxWidth: 480 }}
      renderInput={(params) => <TextField {...params} label="Forecast a stock" placeholder="e.g. AAPL or Microsoft" />}
    />
  );
}

function ForecastHome() {
  const watchlists = useWatchlists();
  const symbols = [...new Set((watchlists.data ?? []).flatMap((watchlist) => watchlist.symbols))];
  return (
    <>
      <PageHeader title="Forecasts"
        subtitle="Statistical forecasts with uncertainty ranges, each checked against its own historical backtest." />
      <Stack spacing={3}>
        <Card>
          <CardContent>
            <StockPicker />
            <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>From your watchlists</Typography>
            {watchlists.isPending && <Skeleton width={320} height={32} />}
            {watchlists.isError && (
              <ErrorState title="Unable to load your watchlists" error={watchlists.error}
                onRetry={() => void watchlists.refetch()} />
            )}
            {watchlists.data && symbols.length === 0 && (
              <EmptyState icon={<InsightsOutlinedIcon />} title="No watched stocks yet"
                description="Price history is collected for stocks on your watchlists, so add a stock to one to forecast it." />
            )}
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {symbols.map((symbol) => (
                <Chip key={symbol} label={symbol} icon={<ShowChartIcon />} clickable component={RouterLink}
                  to={`/app/forecasts/${symbol}`} aria-label={`Forecast ${symbol}`} />
              ))}
            </Box>
          </CardContent>
        </Card>
        <Card component="section" aria-labelledby="method-heading">
          <CardHeader title="How to read a forecast"
            slotProps={{ title: { id: 'method-heading', variant: 'h6', component: 'h2' } }} />
          <CardContent sx={{ pt: 0 }}>
            <Typography variant="body2" component="div">
              <Box component="ul" sx={{ pl: 2.5, m: 0, '& li': { mb: 0.75 } }}>
                <li>The dashed line is the median estimate; the shaded band is the range that contained a stated share
                  (80%) of the model&apos;s past errors. Outcomes outside it are expected regularly.</li>
                <li>Every model is backtested walk-forward: refitted at each past date with only the data available
                  then, and compared with the naive &quot;last close&quot; baseline.</li>
                <li>Good historical accuracy does not mean the model will be accurate in the future.</li>
              </Box>
            </Typography>
          </CardContent>
        </Card>
      </Stack>
      <Disclaimer text={FORECAST_DISCLAIMER} />
    </>
  );
}

function SymbolForecast({ symbol }: { symbol: string }) {
  const stock = useStock(symbol);
  if (stock.isError) {
    return toApiError(stock.error).status === 404
      ? <EmptyState title={`No stock with symbol “${symbol}”`} description="Check the ticker or pick another stock."
        action={<Button component={RouterLink} to="/app/forecasts">All forecasts</Button>} />
      : <ErrorState title="Unable to load this stock" error={stock.error} onRetry={() => void stock.refetch()} />;
  }
  return (
    <>
      <PageHeader
        title={stock.data ? `${stock.data.companyName} forecast` : <Skeleton width={320} />}
        subtitle={<Chip label={symbol} size="small" color="primary" sx={{ fontWeight: 700 }} component="span" />}
        actions={(
          <>
            <Button component={RouterLink} to="/app/forecasts">All forecasts</Button>
            <Button variant="outlined" component={RouterLink} to={`/app/stocks/${symbol}`}>Stock details</Button>
          </>
        )}
      />
      <ForecastPanel symbol={symbol} />
    </>
  );
}

export default function ForecastsPage() {
  const { symbol } = useParams();
  return symbol ? <SymbolForecast key={symbol} symbol={symbol.toUpperCase()} /> : <ForecastHome />;
}
