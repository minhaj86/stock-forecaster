import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import PeopleAltOutlinedIcon from '@mui/icons-material/PeopleAltOutlined';
import StorageOutlinedIcon from '@mui/icons-material/StorageOutlined';
import SyncIcon from '@mui/icons-material/Sync';
import SyncOutlinedIcon from '@mui/icons-material/SyncOutlined';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActions from '@mui/material/CardActions';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { useNotify } from '@/app/providers/notificationContext';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import type { AdminDashboard } from '@/models/admin';
import { formatDateTime, formatInteger, formatTime } from '@/utils/format';
import { DetailList } from '../components/DetailList';
import { ProviderStatusChip } from '../components/StatusChips';
import { errorMessage } from '../errors';
import { useTriggerSynchronization } from '../marketData/hooks';
import { adminPaths } from '../paths';
import { useAdminDashboard } from './hooks';
import { synchronizationMessage } from './synchronizationMessage';

interface Metric {
  label: string;
  value: number;
  /** Marks figures that need attention with an icon, not colour alone. */
  attention?: boolean;
}

function Metrics({ items }: { items: Metric[] }) {
  return (
    <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', columnGap: 2, rowGap: 1.75 }}>
      {items.map((item) => (
        <Box key={item.label}>
          <Typography component="dt" variant="caption" color="text.secondary" sx={{ fontWeight: 600, display: 'block' }}>
            {item.label}
          </Typography>
          <Typography component="dd" variant="h5" sx={{ m: 0, display: 'flex', alignItems: 'center', gap: 0.5, fontVariantNumeric: 'tabular-nums' }}>
            {item.attention && item.value > 0 && <ErrorOutlineIcon fontSize="small" color="error" aria-hidden />}
            {formatInteger(item.value)}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

function SummaryCard({ id, title, icon, children, actions }: {
  id: string; title: string; icon: ReactNode; children: ReactNode; actions: ReactNode;
}) {
  return (
    <Card component="section" aria-labelledby={id} sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardHeader
        avatar={<Box sx={{ color: 'primary.main', display: 'flex' }} aria-hidden>{icon}</Box>}
        title={title}
        slotProps={{ title: { id, variant: 'h6', component: 'h2' } }}
      />
      <CardContent sx={{ pt: 0, flexGrow: 1 }}>{children}</CardContent>
      <CardActions sx={{ px: 2, pb: 2, flexWrap: 'wrap', gap: 1 }}>{actions}</CardActions>
    </Card>
  );
}

function MarketDataSummary({ marketData }: { marketData: AdminDashboard['marketData'] }) {
  const { provider } = marketData;
  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography sx={{ fontWeight: 700 }}>{provider.name}</Typography>
        <ProviderStatusChip status={provider.status} />
        {provider.synthetic && <Chip size="small" variant="outlined" label="Demo data" />}
      </Box>
      <DetailList
        items={[
          { label: 'Last successful sync', value: marketData.lastSuccessfulSyncAt ? formatDateTime(marketData.lastSuccessfulSyncAt) : 'Never' },
          { label: 'Last failed sync', value: marketData.lastFailedSyncAt ? formatDateTime(marketData.lastFailedSyncAt) : 'None' },
          { label: 'Health checked', value: provider.checkedAt ? formatDateTime(provider.checkedAt) : 'Not checked yet' },
        ]}
      />
      {marketData.lastFailedSyncAt && marketData.lastFailureMessage && (
        <Alert severity="warning" variant="outlined">{marketData.lastFailureMessage}</Alert>
      )}
    </Stack>
  );
}

export default function AdminDashboardPage() {
  const dashboard = useAdminDashboard();
  const synchronize = useTriggerSynchronization();
  const notify = useNotify();
  const navigate = useNavigate();
  const data = dashboard.data;

  const trigger = async () => {
    try {
      const result = await synchronize.mutateAsync();
      notify({
        message: synchronizationMessage(result),
        severity: result.jobsQueued > 0 ? 'success' : 'info',
        action: { label: 'View jobs', onClick: () => navigate(adminPaths.jobs) },
      });
    } catch (error) {
      notify({ message: errorMessage(error), severity: 'error' });
    }
  };

  const link = (to: string, label: string) => (
    <Button component={RouterLink} to={to} size="small" endIcon={<ArrowForwardIcon />}>{label}</Button>
  );

  return (
    <>
      <PageHeader
        title="Administration"
        subtitle={data ? `Users, stocks and market data at a glance · updated ${formatTime(data.generatedAt)}`
          : 'Users, stocks and market data at a glance'}
        actions={(
          <Button variant="contained" startIcon={<SyncIcon />} loading={synchronize.isPending} onClick={() => void trigger()}>
            Trigger synchronization
          </Button>
        )}
      />
      {dashboard.isPending && <CardGridSkeleton count={4} label="Loading the admin overview" />}
      {dashboard.isError && (
        <AdminErrorState title="Unable to load the admin overview" error={dashboard.error} onRetry={() => void dashboard.refetch()} />
      )}
      {data && (
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <SummaryCard id="admin-users-heading" title="Users" icon={<PeopleAltOutlinedIcon />} actions={link(adminPaths.users, 'Manage users')}>
              <Metrics items={[
                { label: 'Total', value: data.users.total },
                { label: 'Active', value: data.users.active },
                { label: 'Disabled', value: data.users.disabled },
                { label: 'Locked', value: data.users.locked, attention: true },
                { label: 'Administrators', value: data.users.administrators },
                { label: 'New (7 days)', value: data.users.newLast7Days },
              ]} />
            </SummaryCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <SummaryCard id="admin-stocks-heading" title="Stocks" icon={<Inventory2OutlinedIcon />} actions={link(adminPaths.stocks, 'Manage stocks')}>
              <Metrics items={[
                { label: 'Catalogue', value: data.stocks.total },
                { label: 'Active', value: data.stocks.active },
                { label: 'Tracked', value: data.stocks.tracked },
                { label: 'With market data', value: data.stocks.withMarketData },
                { label: 'Missing data', value: data.stocks.missingData, attention: true },
              ]} />
            </SummaryCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <SummaryCard id="admin-market-data-heading" title="Market data" icon={<StorageOutlinedIcon />}
              actions={<>{link(adminPaths.marketData, 'Manage market data')}{link(adminPaths.providers, 'Provider')}</>}>
              <MarketDataSummary marketData={data.marketData} />
            </SummaryCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <SummaryCard id="admin-jobs-heading" title="Jobs" icon={<SyncOutlinedIcon />}
              actions={<>{link(`${adminPaths.jobs}?status=FAILED`, 'View failed jobs')}{link(adminPaths.jobs, 'All jobs')}</>}>
              <Metrics items={[
                { label: 'Queued', value: data.jobs.queued },
                { label: 'Running', value: data.jobs.running },
                { label: 'Completed today', value: data.jobs.completedToday },
                { label: 'Failed today', value: data.jobs.failedToday, attention: true },
              ]} />
            </SummaryCard>
          </Grid>
        </Grid>
      )}
    </>
  );
}
