import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import StorageOutlinedIcon from '@mui/icons-material/StorageOutlined';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import { SEARCH_DEBOUNCE_MS } from '@/features/stocks/StockSearch';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { AdminStock } from '@/models/admin';
import { formatDateTime, formatInteger } from '@/utils/format';
import { DetailList } from '../components/DetailList';
import { ProviderStatusChip } from '../components/StatusChips';
import { useAdminDashboard } from '../dashboard/hooks';
import { adminPaths } from '../paths';
import { useMarketDataProviders } from '../providers/hooks';
import { useAdminStock, useStockOptions } from '../stocks/hooks';
import { BulkLoadDialog } from './BulkLoadDialog';
import { StockMarketDataPanel } from './StockMarketDataPanel';

function ProviderSummary() {
  const providers = useMarketDataProviders();
  const dashboard = useAdminDashboard();
  const active = providers.data?.active;

  return (
    <Card component="section" aria-labelledby="provider-summary-heading">
      <CardHeader
        title="Market-data provider"
        slotProps={{ title: { id: 'provider-summary-heading', variant: 'h6', component: 'h2' } }}
        action={<Button component={RouterLink} to={adminPaths.providers} endIcon={<ArrowForwardIcon />}>Provider details</Button>}
      />
      <CardContent sx={{ pt: 0 }}>
        {providers.isPending && <CardGridSkeleton count={1} label="Loading provider" />}
        {providers.isError && (
          <AdminErrorState title="Unable to load the provider" error={providers.error} onRetry={() => void providers.refetch()} />
        )}
        {active && (
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
              <Typography sx={{ fontWeight: 700 }}>{active.name}</Typography>
              <ProviderStatusChip status={active.health.status} />
              {active.synthetic && <Chip size="small" label="Demo data" variant="outlined" />}
            </Box>
            <DetailList
              columns={2}
              items={[
                { label: 'Last successful provider request', value: formatDateTime(active.usage.lastSuccessAt) },
                {
                  label: 'Last synchronization',
                  value: dashboard.data ? formatDateTime(dashboard.data.marketData.lastSuccessfulSyncAt)
                    : dashboard.isError ? 'Unavailable' : 'Loading…',
                },
                { label: 'Requests today', value: formatInteger(active.usage.requestsToday) },
                { label: 'Failures today', value: formatInteger(active.usage.failuresToday) },
              ]}
            />
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

/** Catalogue search for one stock; the selection lives in the URL (`?symbol=AAPL`). */
function StockPicker({ symbol, onChange }: { symbol: string; onChange: (symbol: string) => void }) {
  const [searchText, setSearchText] = useState('');
  const [picked, setPicked] = useState<AdminStock | null>(null);
  const query = useDebouncedValue(searchText, SEARCH_DEBOUNCE_MS);
  const options = useStockOptions(query);
  const selected = useAdminStock(symbol);
  // The picked option bridges the time until the chosen stock has loaded.
  const value = selected.data ?? (picked?.symbol === symbol ? picked : null);

  return (
    <Autocomplete<AdminStock>
      options={options.data?.content ?? []}
      value={value}
      onChange={(_, next) => {
        setPicked(next);
        onChange(next?.symbol ?? '');
      }}
      onInputChange={(_, next, reason) => {
        if (reason === 'input' || reason === 'clear') setSearchText(next);
      }}
      filterOptions={(candidates) => candidates}
      getOptionLabel={(stock) => `${stock.symbol} – ${stock.companyName}`}
      isOptionEqualToValue={(option, value) => option.symbol === value.symbol}
      loading={options.isFetching}
      noOptionsText={options.isError ? 'Unable to load stocks' : 'No stocks match'}
      sx={{ maxWidth: 520 }}
      renderInput={(params) => <TextField {...params} label="Stock" placeholder="Search by symbol or company" />}
    />
  );
}

export default function AdminMarketDataPage() {
  const [params, setParams] = useSearchParams();
  const symbol = (params.get('symbol') ?? '').toUpperCase();
  const [bulkOpen, setBulkOpen] = useState(false);

  return (
    <>
      <PageHeader
        title="Market data"
        subtitle="Provider status and the stored prices of each stock."
        actions={(
          <Button variant="contained" startIcon={<PlaylistAddIcon />} onClick={() => setBulkOpen(true)}>
            Bulk historical load
          </Button>
        )}
      />
      <Stack spacing={3}>
        <ProviderSummary />
        <Card component="section" aria-labelledby="stock-picker-heading">
          <CardHeader title="Stock" slotProps={{ title: { id: 'stock-picker-heading', variant: 'h6', component: 'h2' } }}
            subheader="Choose a stock to inspect and manage its market data." />
          <CardContent sx={{ pt: 0 }}>
            <StockPicker symbol={symbol} onChange={(next) => setParams(next ? { symbol: next } : {})} />
          </CardContent>
        </Card>
        {symbol ? <StockMarketDataPanel key={symbol} symbol={symbol} showStock /> : (
          <Card>
            <EmptyState icon={<StorageOutlinedIcon />} title="No stock selected"
              description="Search the catalogue above to see a stock's data range, records and synchronization status."
              action={<Button component={RouterLink} to={adminPaths.stocks}>Browse stocks</Button>} />
          </Card>
        )}
      </Stack>
      <BulkLoadDialog open={bulkOpen} onClose={() => setBulkOpen(false)} />
    </>
  );
}
