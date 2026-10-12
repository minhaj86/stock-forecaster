import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import HourglassTopIcon from '@mui/icons-material/HourglassTop';
import ReplayIcon from '@mui/icons-material/Replay';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Grid from '@mui/material/Grid';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { useNotify } from '@/app/providers/notificationContext';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import type { MarketDataJob } from '@/models/admin';
import { formatDate, formatDateTime, formatDuration, formatInteger, formatTime } from '@/utils/format';
import { DetailList } from '../components/DetailList';
import { RefreshWarning } from '../components/RefreshWarning';
import { JobStatusChip } from '../components/StatusChips';
import { errorMessage, isNotFound } from '../errors';
import {
  actorLabel, EXISTING_DATA_LABELS, INTERVAL_LABELS, JOB_TRIGGER_LABELS, JOB_TYPE_LABELS, jobErrorLabel, shortId,
} from '../labels';
import { adminPaths } from '../paths';
import { duplicateJobMessage, jobReference, useJobNotifier } from '../useJobNotifier';
import { useCancelJob, useMarketDataJob, useRetryJob } from './hooks';
import { JobProgressBar } from './JobProgressBar';

/** The code exactly as the backend sent it, so codes added after this release stay recognisable. */
function ErrorCodeLine({ job }: { job: MarketDataJob }) {
  if (!job.errorCode) return null;
  return (
    <Typography variant="caption" component="p" sx={{ mt: 0.5 }}>
      Error code: <Box component="code">{job.errorCode}</Box>
      {job.status === 'FAILED' && job.retryable ? ' · This error is temporary, so the job can be retried.' : ''}
    </Typography>
  );
}

/** Explains waiting, failed, partial and cancelled jobs. Unknown error codes fall back to generic wording. */
function JobNotices({ job }: { job: MarketDataJob }) {
  const errorLabel = job.errorCode ? jobErrorLabel(job.errorCode) : undefined;

  if (job.status === 'QUEUED' && job.nextAttemptAt && job.errorCode === 'PROVIDER_RATE_LIMITED') {
    return (
      <Alert severity="info" icon={<HourglassTopIcon />} role="status">
        Provider rate limit reached. The job will automatically resume at {formatTime(job.nextAttemptAt)}.
      </Alert>
    );
  }
  if (job.status === 'QUEUED' && job.nextAttemptAt && job.errorCode) {
    return (
      <Alert severity="info" icon={<HourglassTopIcon />} role="status">
        {errorLabel ?? 'The last attempt failed'} (attempt {job.attempts} of {job.maxAttempts}). The job will retry
        automatically at {formatTime(job.nextAttemptAt)}.
        {job.errorMessage && <Typography variant="body2" component="p" sx={{ mt: 0.5 }}>{job.errorMessage}</Typography>}
        <ErrorCodeLine job={job} />
      </Alert>
    );
  }
  if (job.status === 'FAILED') {
    return (
      <Alert severity="error">
        <AlertTitle>{errorLabel ?? 'The job failed'}</AlertTitle>
        {job.errorMessage ?? 'No further details were reported.'}
        <ErrorCodeLine job={job} />
      </Alert>
    );
  }
  if (job.status === 'PARTIALLY_COMPLETED') {
    return (
      <Alert severity="warning">
        <AlertTitle>Partially completed</AlertTitle>
        {formatInteger(job.recordsFailed)} {job.recordsFailed === 1 ? 'record was' : 'records were'} rejected by
        validation; all valid records were stored.{job.errorMessage ? ` ${job.errorMessage}` : ''}
      </Alert>
    );
  }
  if (job.status === 'CANCELLED') {
    return <Alert severity="info">This job was cancelled before it started.</Alert>;
  }
  return null;
}

function JobView({ job }: { job: MarketDataJob }) {
  const navigate = useNavigate();
  const notify = useNotify();
  const notifyJob = useJobNotifier();
  const retry = useRetryJob();
  const cancel = useCancelJob();

  const retryJob = async () => {
    try {
      const submission = await retry.mutateAsync(job.id);
      notifyJob(submission.created
        ? `Retry queued for ${job.symbol}. ${jobReference(submission.job.id)}`
        : duplicateJobMessage(`A job for ${job.symbol}`, submission), submission.job.id);
      navigate(adminPaths.job(submission.job.id));
    } catch (error) {
      notify({ message: errorMessage(error), severity: 'error' });
    }
  };

  const cancelJob = async () => {
    try {
      const cancelled = await cancel.mutateAsync(job.id);
      notify({ message: `Cancelled ${jobReference(cancelled.id)} (${JOB_TYPE_LABELS[cancelled.type]}, ${cancelled.symbol})` });
    } catch (error) {
      notify({ message: errorMessage(error), severity: 'error' });
    }
  };

  const range = job.fromDate || job.toDate ? `${formatDate(job.fromDate)} → ${formatDate(job.toDate)}` : '—';

  return (
    <>
      <PageHeader
        title={`${JOB_TYPE_LABELS[job.type]} · ${job.symbol}`}
        subtitle={`${jobReference(job.id)} · requested ${formatDateTime(job.requestedAt)}`}
        actions={(
          <>
            {job.retryable && (
              <Button variant="contained" startIcon={<ReplayIcon />} loading={retry.isPending} onClick={() => void retryJob()}>Retry</Button>
            )}
            {job.status === 'QUEUED' && (
              <Button startIcon={<CancelOutlinedIcon />} loading={cancel.isPending} onClick={() => void cancelJob()}>
                Cancel job
              </Button>
            )}
          </>
        )}
      />
      <Stack spacing={3}>
        <Card component="section" aria-labelledby="job-status-heading">
          <CardHeader
            title="Status"
            slotProps={{ title: { id: 'job-status-heading', variant: 'h6', component: 'h2' } }}
            action={<JobStatusChip status={job.status} />}
            sx={{ '& .MuiCardHeader-action': { alignSelf: 'center', m: 0 } }}
          />
          <CardContent sx={{ pt: 0 }}>
            <Stack spacing={2}>
              <JobProgressBar progress={job.progress} label="Job progress" />
              {job.statusMessage && <Typography variant="body2" color="text.secondary">{job.statusMessage}</Typography>}
              <JobNotices job={job} />
            </Stack>
          </CardContent>
        </Card>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Card component="section" aria-labelledby="job-details-heading" sx={{ height: '100%' }}>
              <CardHeader title="Details" slotProps={{ title: { id: 'job-details-heading', variant: 'h6', component: 'h2' } }} />
              <CardContent sx={{ pt: 0 }}>
                <DetailList
                  columns={2}
                  items={[
                    { label: 'Job ID', value: <Box component="code" sx={{ fontSize: '0.8rem' }}>{job.id}</Box> },
                    {
                      label: 'Stock',
                      value: <><Link component={RouterLink} to={adminPaths.stock(job.symbol)}>{job.symbol}</Link> · {job.companyName}</>,
                    },
                    { label: 'Type', value: JOB_TYPE_LABELS[job.type] },
                    { label: 'Trigger', value: JOB_TRIGGER_LABELS[job.trigger] },
                    { label: 'Provider', value: job.provider },
                    { label: 'Interval', value: INTERVAL_LABELS[job.interval] ?? job.interval },
                    {
                      label: 'Existing-data handling',
                      value: job.existingDataStrategy ? EXISTING_DATA_LABELS[job.existingDataStrategy] : 'Not applicable',
                    },
                    { label: 'Date range', value: range },
                    { label: 'Requested by', value: actorLabel(job.requestedBy) },
                    { label: 'Requested', value: formatDateTime(job.requestedAt) },
                    { label: 'Started', value: formatDateTime(job.startedAt) },
                    { label: 'Completed', value: formatDateTime(job.completedAt) },
                    { label: 'Duration', value: formatDuration(job.durationMs) },
                    { label: 'Attempts', value: `${job.attempts} of ${job.maxAttempts}` },
                    { label: 'Next attempt', value: formatDateTime(job.nextAttemptAt) },
                    {
                      label: 'Retry of',
                      value: job.retryOfJobId
                        ? <Link component={RouterLink} to={adminPaths.job(job.retryOfJobId)}>{jobReference(job.retryOfJobId)}</Link> : '—',
                    },
                    { label: 'Batch', value: job.batchId ? `#${shortId(job.batchId)}` : '—' },
                  ]}
                />
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <Card component="section" aria-labelledby="job-records-heading" sx={{ height: '100%' }}>
              <CardHeader title="Records" slotProps={{ title: { id: 'job-records-heading', variant: 'h6', component: 'h2' } }} />
              <CardContent sx={{ pt: 0 }}>
                <DetailList
                  columns={2}
                  items={[
                    { label: 'Fetched', value: formatInteger(job.recordsFetched) },
                    { label: 'Inserted', value: formatInteger(job.recordsInserted) },
                    { label: 'Updated', value: formatInteger(job.recordsUpdated) },
                    { label: 'Skipped', value: formatInteger(job.recordsSkipped) },
                    { label: 'Failed', value: formatInteger(job.recordsFailed) },
                    { label: 'Deleted', value: formatInteger(job.recordsDeleted) },
                  ]}
                />
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Stack>
    </>
  );
}

export default function AdminJobDetailsPage() {
  const { jobId = '' } = useParams();
  const job = useMarketDataJob(jobId);

  const back = (
    <Button component={RouterLink} to={adminPaths.jobs} startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>All sync jobs</Button>
  );

  if (job.isPending) return <>{back}<CardGridSkeleton count={2} label="Loading job" /></>;
  if (!job.data) {
    return (
      <>
        {back}
        {isNotFound(job.error) ? (
          <Card>
            <EmptyState icon={<SearchOffIcon />} title="Job not found"
              description="The job does not exist or the link is incorrect."
              action={<Button variant="contained" component={RouterLink} to={adminPaths.jobs}>Back to sync jobs</Button>} />
          </Card>
        ) : (
          <AdminErrorState title="Unable to load this job" error={job.error} onRetry={() => void job.refetch()} />
        )}
      </>
    );
  }
  return (
    <>
      {back}
      {job.isError && <RefreshWarning error={job.error} />}
      <JobView key={job.data.id} job={job.data} />
    </>
  );
}
