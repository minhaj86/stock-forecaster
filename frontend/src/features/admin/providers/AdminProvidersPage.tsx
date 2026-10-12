import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import KeyOffOutlinedIcon from '@mui/icons-material/KeyOffOutlined';
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { useNotify } from '@/app/providers/notificationContext';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import type { ActiveProvider, ProviderHealth, ProviderOverview } from '@/models/admin';
import { formatDateTime, formatInteger } from '@/utils/format';
import { DetailList } from '../components/DetailList';
import { MetadataList } from '../components/MetadataList';
import { ProviderStatusChip } from '../components/StatusChips';
import { errorMessage } from '../errors';
import { INTERVAL_LABELS, PROVIDER_STATUS_LABELS } from '../labels';
import { useMarketDataProviders, useProviderHealth, useRefreshProviderHealth } from './hooks';

function Section({ id, title, action, children }: { id: string; title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <Card component="section" aria-labelledby={id} sx={{ height: '100%' }}>
      <CardHeader title={title} action={action}
        slotProps={{ title: { id, variant: 'h6', component: 'h2' } }}
        sx={{ '& .MuiCardHeader-action': { alignSelf: 'center', m: 0 } }} />
      <CardContent sx={{ pt: 0 }}>{children}</CardContent>
    </Card>
  );
}

const yesNo = (value: boolean) => (
  <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
    {value ? <CheckIcon fontSize="small" color="success" aria-hidden /> : <CloseIcon fontSize="small" color="disabled" aria-hidden />}
    {value ? 'Yes' : 'No'}
  </Box>
);

function ActiveProviderSection({ provider, health }: { provider: ActiveProvider; health: ProviderHealth }) {
  return (
    <Section id="active-provider-heading" title="Active provider" action={<ProviderStatusChip status={health.status} />}>
      <Stack spacing={2}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="h5" component="p">{provider.name}</Typography>
          <Chip size="small" variant="outlined" label={provider.synthetic ? 'Synthetic demo data' : 'Live market data'} />
        </Box>
        <Typography variant="body2" color="text.secondary">{provider.description}</Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {!provider.apiKeyRequired || provider.apiKeyConfigured
            ? <KeyOutlinedIcon fontSize="small" color={provider.apiKeyRequired ? 'success' : 'disabled'} aria-hidden />
            : <KeyOffOutlinedIcon fontSize="small" color="warning" aria-hidden />}
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {!provider.apiKeyRequired ? 'API key: not required'
              : provider.apiKeyConfigured ? 'API key: configured' : 'API key: missing'}
          </Typography>
        </Box>
        <DetailList columns={2} items={[{ label: 'Provider ID', value: provider.id }]} />
      </Stack>
    </Section>
  );
}

function HealthSection({ health }: { health: ProviderHealth }) {
  return (
    <Section id="provider-health-heading" title="Health">
      <Stack spacing={2}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
          <ProviderStatusChip status={health.status} />
          <Typography variant="body2">{health.message}</Typography>
        </Box>
        <DetailList
          columns={2}
          items={[
            { label: 'Checked', value: health.checkedAt ? formatDateTime(health.checkedAt) : 'Not checked yet' },
            { label: 'Latency', value: health.latencyMs === null ? '—' : `${formatInteger(health.latencyMs)} ms` },
          ]}
        />
        <Typography variant="subtitle2" component="h3">Details</Typography>
        <MetadataList values={health.details} emptyText="The provider reported no further details." />
      </Stack>
    </Section>
  );
}

function UsageSection({ provider }: { provider: ActiveProvider }) {
  const { usage } = provider;
  return (
    <Section id="provider-usage-heading" title="Usage today">
      <Stack spacing={2}>
        <DetailList
          columns={2}
          items={[
            { label: 'Requests today', value: formatInteger(usage.requestsToday) },
            { label: 'Failures today', value: formatInteger(usage.failuresToday) },
            { label: 'Rate-limited today', value: formatInteger(usage.rateLimitedToday) },
            { label: 'Last successful request', value: formatDateTime(usage.lastSuccessAt) },
            { label: 'Last failed request', value: formatDateTime(usage.lastFailureAt) },
          ]}
        />
        {usage.lastFailureMessage && (
          <Alert severity="warning" variant="outlined"><strong>Last failure:</strong> {usage.lastFailureMessage}</Alert>
        )}
      </Stack>
    </Section>
  );
}

function RateLimitSection({ provider }: { provider: ActiveProvider }) {
  const { rateLimit } = provider.usage;
  return (
    <Section id="provider-rate-limit-heading" title="Rate limit">
      <Stack spacing={2}>
        <Typography variant="body2">
          <strong>{formatInteger(rateLimit.availablePermits)}</strong> of {formatInteger(rateLimit.requestsPerMinute)} requests
          available this minute
        </Typography>
        <DetailList
          columns={2}
          items={[
            { label: 'Requests per minute', value: formatInteger(rateLimit.requestsPerMinute) },
            { label: 'Throttled until', value: rateLimit.throttledUntil ? formatDateTime(rateLimit.throttledUntil) : 'Not throttled' },
          ]}
        />
      </Stack>
    </Section>
  );
}

function CapabilitiesSection({ provider }: { provider: ActiveProvider }) {
  const { capabilities } = provider;
  return (
    <Section id="provider-capabilities-heading" title="Capabilities">
      <DetailList
        columns={2}
        items={[
          { label: 'Intervals', value: capabilities.supportedIntervals.map((interval) => INTERVAL_LABELS[interval] ?? interval).join(', ') },
          { label: 'Bars per request', value: formatInteger(capabilities.maxBarsPerRequest) },
          { label: 'Symbol search', value: yesNo(capabilities.symbolSearch) },
          { label: 'Metadata lookup', value: yesNo(capabilities.metadataLookup) },
          { label: 'Latest quote', value: yesNo(capabilities.latestQuote) },
          { label: 'Price adjustment', value: capabilities.adjustment },
        ]}
      />
    </Section>
  );
}

function AvailableProvidersSection({ overview }: { overview: ProviderOverview }) {
  return (
    <Section id="available-providers-heading" title="Available providers">
      <List aria-label="Available providers" disablePadding>
        {overview.available.map((provider) => (
          <ListItem key={provider.id} divider disableGutters
            secondaryAction={provider.active ? <Chip size="small" color="primary" label="Active" icon={<CheckIcon />} /> : undefined}>
            <ListItemText primary={provider.name} secondary={provider.id} />
          </ListItem>
        ))}
      </List>
      <Alert severity="info" variant="outlined" sx={{ mt: 2 }}>{overview.configurationHint}</Alert>
    </Section>
  );
}

export default function AdminProvidersPage() {
  const providers = useMarketDataProviders();
  const health = useProviderHealth();
  const refresh = useRefreshProviderHealth();
  const notify = useNotify();

  const checkNow = async () => {
    try {
      const result = await refresh.mutateAsync();
      notify({
        message: `Health check finished: ${PROVIDER_STATUS_LABELS[result.status]}. ${result.message}`,
        severity: result.status === 'HEALTHY' ? 'success' : 'warning',
      });
    } catch (error) {
      notify({ message: errorMessage(error), severity: 'error' });
    }
  };

  const overview = providers.data;
  // The dedicated health query is refreshed by "Check now"; the overview carries the last known result.
  const currentHealth = health.data ?? overview?.active.health;
  return (
    <>
      <PageHeader
        title="Providers"
        subtitle="The market-data provider in use, its health, usage and capabilities."
        actions={(
          <Button variant="contained" startIcon={<RefreshIcon />} loading={refresh.isPending} onClick={() => void checkNow()}
            disabled={!overview}>
            Check now
          </Button>
        )}
      />
      {providers.isPending && <CardGridSkeleton count={4} label="Loading providers" />}
      {providers.isError && (
        <AdminErrorState title="Unable to load the market-data providers" error={providers.error} onRetry={() => void providers.refetch()} />
      )}
      {overview && currentHealth && (
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}><ActiveProviderSection provider={overview.active} health={currentHealth} /></Grid>
          <Grid size={{ xs: 12, md: 6 }}><HealthSection health={currentHealth} /></Grid>
          <Grid size={{ xs: 12, md: 6 }}><UsageSection provider={overview.active} /></Grid>
          <Grid size={{ xs: 12, md: 6 }}><RateLimitSection provider={overview.active} /></Grid>
          <Grid size={{ xs: 12, md: 6 }}><CapabilitiesSection provider={overview.active} /></Grid>
          <Grid size={{ xs: 12, md: 6 }}><AvailableProvidersSection overview={overview} /></Grid>
        </Grid>
      )}
    </>
  );
}
