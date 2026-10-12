import CloudDownloadOutlinedIcon from '@mui/icons-material/CloudDownloadOutlined';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SyncIcon from '@mui/icons-material/Sync';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import LinearProgress from '@mui/material/LinearProgress';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import type { ActiveMarketDataJob, AdminStock } from '@/models/admin';
import { formatDate, formatDateTime, formatInteger } from '@/utils/format';
import { DetailList } from '../components/DetailList';
import { RefreshWarning } from '../components/RefreshWarning';
import { MarketDataStatusChip, StockActiveChip } from '../components/StatusChips';
import { errorMessage, isNotFound } from '../errors';
import { JOB_STATUS_LABELS, JOB_TYPE_LABELS } from '../labels';
import { adminPaths } from '../paths';
import { useAdminStock } from '../stocks/hooks';
import { duplicateJobMessage, jobReference, useJobNotifier } from '../useJobNotifier';
import { useRefreshWhenJobFinishes } from '../useRefreshWhenJobFinishes';
import { BackfillDialog } from './BackfillDialog';
import { useUpdateLatestData } from './hooks';
import { RepopulateDialog } from './RepopulateDialog';

function ActiveJobProgress({ job }: { job: ActiveMarketDataJob }) {
  const label = `${JOB_TYPE_LABELS[job.type]} ${job.status === 'RUNNING' ? 'running' : 'queued'}`;
  // Announced politely when the job starts or changes state; the changing percentage is left to the progress bar.
  return (
    <Alert severity="info" icon={<SyncIcon />} role="status" sx={{ '& .MuiAlert-message': { flexGrow: 1 } }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {label}
          {job.status === 'RUNNING' && <Box component="span" aria-hidden> · {job.progress}%</Box>}
        </Typography>
        <Link component={RouterLink} to={adminPaths.job(job.id)} variant="body2">View job</Link>
      </Box>
      {job.status === 'RUNNING' ? (
        <LinearProgress variant="determinate" value={job.progress} aria-label={`${JOB_TYPE_LABELS[job.type]} progress`} sx={{ mt: 1 }} />
      ) : (
        <Typography variant="caption" component="p">{JOB_STATUS_LABELS[job.status]}: waiting for the job runner.</Typography>
      )}
    </Alert>
  );
}

function MarketDataDetails({ stock }: { stock: AdminStock }) {
  const updateLatest = useUpdateLatestData();
  const notifyJob = useJobNotifier();
  const [dialog, setDialog] = useState<'backfill' | 'repopulate' | null>(null);
  const { marketData } = stock;
  const hasData = marketData.barCount > 0;

  const update = async () => {
    try {
      const submission = await updateLatest.mutateAsync(stock.symbol);
      if (submission.created) {
        notifyJob(`Latest-data update queued for ${stock.symbol}. ${jobReference(submission.job.id)}`, submission.job.id);
      } else {
        notifyJob(duplicateJobMessage(`A latest-data update for ${stock.symbol}`, submission), submission.job.id, 'info');
      }
    } catch (error) {
      notifyJob(errorMessage(error), null, 'error');
    }
  };

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <MarketDataStatusChip status={marketData.dataStatus} />
        {!stock.active && <StockActiveChip active={false} />}
      </Box>
      {marketData.activeJob && <ActiveJobProgress job={marketData.activeJob} />}
      <DetailList
        columns={2}
        items={[
          { label: 'Provider', value: marketData.provider ?? '—' },
          {
            label: 'Data range',
            value: marketData.firstBarDate && marketData.lastBarDate
              ? `${formatDate(marketData.firstBarDate)} → ${formatDate(marketData.lastBarDate)}` : 'No data stored',
          },
          { label: 'Records', value: formatInteger(marketData.barCount) },
          { label: 'Last synchronization', value: marketData.lastSuccessAt ? formatDateTime(marketData.lastSuccessAt) : 'Never' },
          { label: 'Last attempt', value: formatDateTime(marketData.lastAttemptAt) },
          { label: 'Watchers', value: formatInteger(stock.watchers) },
        ]}
      />
      {marketData.lastError && (
        <Alert severity="warning"><strong>Last synchronization failed:</strong> {marketData.lastError}</Alert>
      )}
      {!stock.active && (
        <Alert severity="info" variant="outlined">
          This stock is inactive. Activate it to update or load its market data.
        </Alert>
      )}
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Button variant="contained" startIcon={<SyncIcon />} disabled={!stock.active} loading={updateLatest.isPending}
          onClick={() => void update()}>
          Update latest data
        </Button>
        <Button variant="outlined" startIcon={<CloudDownloadOutlinedIcon />} disabled={!stock.active} onClick={() => setDialog('backfill')}>
          {hasData ? 'Backfill historical data' : 'Populate data'}
        </Button>
        <Button variant="outlined" color="error" startIcon={<RestartAltIcon />} disabled={!stock.active}
          onClick={() => setDialog('repopulate')}>
          Re-populate all data
        </Button>
      </Box>
      <BackfillDialog stock={dialog === 'backfill' ? stock : null} onClose={() => setDialog(null)} />
      <RepopulateDialog stock={dialog === 'repopulate' ? stock : null} onClose={() => setDialog(null)} />
    </Stack>
  );
}

interface StockMarketDataPanelProps {
  symbol: string;
  /** On pages about several stocks, names the stock and links to its admin page. */
  showStock?: boolean;
}

/**
 * Stored market data of one stock with its synchronisation status and data actions. While a job is queued or running
 * the panel polls the stock; once the job finishes, everything derived from prices is refreshed.
 */
export function StockMarketDataPanel({ symbol, showStock = false }: StockMarketDataPanelProps) {
  const stock = useAdminStock(symbol);
  useRefreshWhenJobFinishes(Boolean(stock.data?.marketData.activeJob));

  return (
    <Card component="section" aria-labelledby="market-data-heading">
      <CardHeader
        title={showStock && stock.data ? `${stock.data.symbol} · ${stock.data.companyName}` : 'Market data'}
        subheader={showStock && stock.data ? (
          <Link component={RouterLink} to={adminPaths.stock(stock.data.symbol)} variant="body2">Stock details</Link>
        ) : undefined}
        slotProps={{ title: { id: 'market-data-heading', variant: 'h6', component: 'h2' } }}
      />
      <CardContent sx={{ pt: 0 }}>
        {stock.isPending && <CardGridSkeleton count={2} label="Loading market data" />}
        {stock.isError && !stock.data && (isNotFound(stock.error) ? (
          <EmptyState title={`No stock with symbol “${symbol}”`} description="Choose another stock from the catalogue." />
        ) : (
          <AdminErrorState title="Unable to load market data" error={stock.error} onRetry={() => void stock.refetch()} />
        ))}
        {stock.isError && stock.data && <RefreshWarning error={stock.error} />}
        {stock.data && <MarketDataDetails stock={stock.data} />}
      </CardContent>
    </Card>
  );
}
