import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import ToggleOnOutlinedIcon from '@mui/icons-material/ToggleOnOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Grid from '@mui/material/Grid';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import { useNotify } from '@/app/providers/notificationContext';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import type { AdminStock } from '@/models/admin';
import { formatDateTime, formatInteger } from '@/utils/format';
import { DetailList } from '../components/DetailList';
import { RefreshWarning } from '../components/RefreshWarning';
import { StockActiveChip } from '../components/StatusChips';
import { errorMessage, isNotFound } from '../errors';
import { ASSET_TYPE_LABELS } from '../labels';
import { StockMarketDataPanel } from '../marketData/StockMarketDataPanel';
import { adminPaths } from '../paths';
import { useActivateStock, useAdminStock, useDeactivateStock } from './hooks';

const SOURCE_LABELS: Record<string, string> = { CATALOGUE: 'Seeded catalogue', MANUAL: 'Entered manually' };

function StockActions({ stock }: { stock: AdminStock }) {
  const activate = useActivateStock();
  const deactivate = useDeactivateStock();
  const notify = useNotify();
  const [confirming, setConfirming] = useState(false);

  const run = async (active: boolean) => {
    try {
      if (active) {
        await activate.mutateAsync(stock.symbol);
        notify({ message: `Activated ${stock.symbol}. It can be found again and is synchronized automatically.` });
      } else {
        await deactivate.mutateAsync(stock.symbol);
        notify({ message: `Deactivated ${stock.symbol}. It is hidden from search and no longer synchronized.` });
      }
    } catch (error) {
      notify({ message: errorMessage(error), severity: 'error' });
    } finally {
      setConfirming(false);
    }
  };

  return (
    <>
      {stock.active && (
        <Button component={RouterLink} to={`/app/stocks/${encodeURIComponent(stock.symbol)}`} startIcon={<OpenInNewIcon />}>
          Open stock page
        </Button>
      )}
      {stock.active ? (
        <Button color="error" startIcon={<VisibilityOffOutlinedIcon />} onClick={() => setConfirming(true)}>Deactivate</Button>
      ) : (
        <Button variant="contained" startIcon={<ToggleOnOutlinedIcon />} loading={activate.isPending} onClick={() => void run(true)}>
          Activate
        </Button>
      )}
      <ConfirmDialog
        open={confirming}
        title={`Deactivate ${stock.symbol}?`}
        description="This hides the stock from search and stops automatic synchronisation. Stored market data is kept and you can activate the stock again at any time."
        confirmLabel="Deactivate"
        destructive
        pending={deactivate.isPending}
        onConfirm={() => void run(false)}
        onClose={() => setConfirming(false)}
      />
    </>
  );
}

export default function AdminStockDetailsPage() {
  const { symbol = '' } = useParams();
  const upper = symbol.toUpperCase();
  const stock = useAdminStock(upper);

  const back = (
    <Button component={RouterLink} to={adminPaths.stocks} startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>All stocks</Button>
  );

  if (stock.isPending) return <>{back}<CardGridSkeleton count={2} label="Loading stock" /></>;
  if (!stock.data) {
    return (
      <>
        {back}
        {isNotFound(stock.error) ? (
          <Card>
            <EmptyState icon={<SearchOffIcon />} title={`No stock with symbol “${upper}”`}
              description="Check the symbol, or add the stock to the catalogue."
              action={<Button variant="contained" component={RouterLink} to={adminPaths.stocks}>Back to stocks</Button>} />
          </Card>
        ) : (
          <AdminErrorState title="Unable to load this stock" error={stock.error} onRetry={() => void stock.refetch()} />
        )}
      </>
    );
  }

  const data = stock.data;
  return (
    <>
      {back}
      {stock.isError && <RefreshWarning error={stock.error} />}
      <PageHeader
        title={`${data.symbol} · ${data.companyName}`}
        subtitle={`${data.exchange} · ${data.mic} · ${data.currency}${data.active ? '' : ' · inactive'}`}
        actions={<StockActions stock={data} />}
      />
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card component="section" aria-labelledby="stock-details-heading">
            <CardHeader title="Details" slotProps={{ title: { id: 'stock-details-heading', variant: 'h6', component: 'h2' } }} />
            <CardContent sx={{ pt: 0 }}>
              <DetailList
                columns={2}
                items={[
                  { label: 'Status', value: <StockActiveChip active={data.active} /> },
                  { label: 'Company', value: data.companyName },
                  { label: 'Asset type', value: ASSET_TYPE_LABELS[data.assetType] ?? data.assetType },
                  { label: 'Exchange', value: data.exchange },
                  { label: 'MIC', value: data.mic },
                  { label: 'Currency', value: data.currency },
                  { label: 'Country', value: data.country ?? '—' },
                  { label: 'Sector', value: data.sector ?? '—' },
                  { label: 'Industry', value: data.industry ?? '—' },
                  { label: 'Source', value: SOURCE_LABELS[data.source] ?? `Provider (${data.source})` },
                  { label: 'Watchers', value: formatInteger(data.watchers) },
                  { label: 'Added', value: formatDateTime(data.createdAt) },
                  { label: 'Last updated', value: formatDateTime(data.updatedAt) },
                ]}
              />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 7 }}>
          <StockMarketDataPanel symbol={data.symbol} />
        </Grid>
      </Grid>
    </>
  );
}
