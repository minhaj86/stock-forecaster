import SyncOutlinedIcon from '@mui/icons-material/SyncOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useNavigate } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { TableSkeleton } from '@/components/common/Skeletons';
import type { MarketDataJob, MarketDataJobFilters } from '@/models/admin';
import { formatDateTime } from '@/utils/format';
import { DataTable, type Column } from '../components/DataTable';
import { DebouncedSearchField } from '../components/DebouncedSearchField';
import { FilterBar, SelectFilter } from '../components/Filters';
import { ListPagination } from '../components/ListPagination';
import { RefreshWarning } from '../components/RefreshWarning';
import { JobStatusChip } from '../components/StatusChips';
import { useListParams } from '../components/useListParams';
import {
  actorLabel, JOB_STATUSES, JOB_STATUS_LABELS, JOB_TRIGGERS, JOB_TRIGGER_LABELS, JOB_TYPES, JOB_TYPE_LABELS, parseOption,
  shortId,
} from '../labels';
import { adminPaths } from '../paths';
import { useMarketDataJobs } from './hooks';
import { recordsSummary } from './jobFormat';
import { JobProgressBar } from './JobProgressBar';

const FILTER_KEYS = ['status', 'type', 'trigger', 'symbol'] as const;

function JobProgress({ job }: { job: MarketDataJob }) {
  return job.status === 'RUNNING'
    ? <JobProgressBar progress={job.progress} label={`Progress of job ${shortId(job.id)}`} />
    : <>—</>;
}

export default function AdminJobsPage() {
  const navigate = useNavigate();
  const { get, page, size, setFilters, setPage, setSize } = useListParams();
  const status = parseOption(get('status'), JOB_STATUSES);
  const type = parseOption(get('type'), JOB_TYPES);
  const trigger = parseOption(get('trigger'), JOB_TRIGGERS);
  const symbol = get('symbol').toUpperCase();
  const filters: MarketDataJobFilters = { status, type, trigger, symbol: symbol || undefined, page, size };
  const jobs = useMarketDataJobs(filters);
  const filtered = FILTER_KEYS.some((key) => get(key) !== '');
  const clearFilters = () => setFilters(Object.fromEntries(FILTER_KEYS.map((key) => [key, null])));

  const columns: Column<MarketDataJob>[] = [
    { id: 'status', header: 'Status', cell: (job) => <JobStatusChip status={job.status} /> },
    {
      id: 'type',
      header: 'Type',
      cell: (job) => (
        <Box>
          <Link component={RouterLink} to={adminPaths.job(job.id)} underline="hover" sx={{ fontWeight: 600 }}>
            {JOB_TYPE_LABELS[job.type]}
          </Link>
          <Typography variant="caption" color="text.secondary" component="div">#{shortId(job.id)}</Typography>
        </Box>
      ),
    },
    {
      id: 'stock',
      header: 'Stock',
      cell: (job) => (
        <Box>
          <Link component={RouterLink} to={adminPaths.stock(job.symbol)} underline="hover">{job.symbol}</Link>
          <Typography variant="caption" color="text.secondary" component="div" noWrap sx={{ maxWidth: 200 }}>{job.companyName}</Typography>
        </Box>
      ),
    },
    {
      id: 'trigger',
      header: 'Trigger',
      cell: (job) => (
        <Box>
          {JOB_TRIGGER_LABELS[job.trigger]}
          {job.requestedBy && (
            <Typography variant="caption" color="text.secondary" component="div">{actorLabel(job.requestedBy)}</Typography>
          )}
        </Box>
      ),
      hideBelowMd: true,
    },
    {
      id: 'times',
      header: 'Requested / Started',
      cell: (job) => (
        <Box>
          {formatDateTime(job.requestedAt)}
          <Typography variant="caption" color="text.secondary" component="div">
            {job.startedAt ? `Started ${formatDateTime(job.startedAt)}` : 'Not started'}
          </Typography>
        </Box>
      ),
    },
    { id: 'progress', header: 'Progress', cell: (job) => <JobProgress job={job} /> },
    {
      id: 'records',
      header: 'Records',
      cell: (job) => <Typography variant="body2" sx={{ maxWidth: 260 }}>{recordsSummary(job)}</Typography>,
      hideBelowMd: true,
    },
  ];

  return (
    <>
      <PageHeader title="Sync jobs" subtitle="Market-data jobs, newest first. Running jobs update automatically." />
      <Card>
        <FilterBar label="Filter sync jobs" active={filtered} onClear={clearFilters}>
          <SelectFilter label="Status" value={status} options={JOB_STATUSES} labels={JOB_STATUS_LABELS} allLabel="All statuses"
            onChange={(next) => setFilters({ status: next })} />
          <SelectFilter label="Type" value={type} options={JOB_TYPES} labels={JOB_TYPE_LABELS} allLabel="All types"
            onChange={(next) => setFilters({ type: next })} />
          <SelectFilter label="Trigger" value={trigger} options={JOB_TRIGGERS} labels={JOB_TRIGGER_LABELS} allLabel="All triggers"
            onChange={(next) => setFilters({ trigger: next })} />
          <DebouncedSearchField label="Symbol" value={get('symbol')} onChange={(next) => setFilters({ symbol: next.toUpperCase() })}
            sx={{ width: 160 }} />
        </FilterBar>

        {jobs.isPending && <TableSkeleton rows={8} label="Loading sync jobs" />}
        {jobs.isError && !jobs.data && (
          <Box sx={{ p: 2 }}>
            <AdminErrorState title="Unable to load sync jobs" error={jobs.error} onRetry={() => void jobs.refetch()} />
          </Box>
        )}
        {jobs.isError && jobs.data && <Box sx={{ px: 2, pt: 2 }}><RefreshWarning error={jobs.error} /></Box>}
        {jobs.data && jobs.data.content.length === 0 && (
          <EmptyState
            icon={<SyncOutlinedIcon />}
            title={filtered ? 'No jobs match these filters' : 'No sync jobs yet'}
            description={filtered ? 'Try other filters, or clear them to see every job.'
              : 'Jobs appear when stocks are watched, scheduled updates run, or an administrator loads data.'}
            action={filtered
              ? <Button variant="outlined" onClick={clearFilters}>Clear filters</Button>
              : <Button variant="contained" component={RouterLink} to={adminPaths.marketData}>Manage market data</Button>}
          />
        )}
        {jobs.data && jobs.data.content.length > 0 && (
          <>
            <DataTable
              label="Sync jobs"
              rows={jobs.data.content}
              columns={columns}
              rowKey={(job) => job.id}
              onRowClick={(job) => navigate(adminPaths.job(job.id))}
              dimmed={jobs.isPlaceholderData}
              renderCard={(job) => (
                <CardActionArea onClick={() => navigate(adminPaths.job(job.id))} sx={{ p: 2 }}
                  aria-label={`Open ${JOB_TYPE_LABELS[job.type]} job ${shortId(job.id)} for ${job.symbol}`}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, mb: 1 }}>
                    <Typography sx={{ fontWeight: 700 }}>{JOB_TYPE_LABELS[job.type]} · {job.symbol}</Typography>
                    <JobStatusChip status={job.status} />
                  </Box>
                  <JobProgress job={job} />
                  <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1 }}>
                    {formatDateTime(job.requestedAt)} · {JOB_TRIGGER_LABELS[job.trigger]} · {recordsSummary(job)}
                  </Typography>
                </CardActionArea>
              )}
            />
            <ListPagination page={jobs.data.page} label="Sync jobs pages" onPageChange={setPage} onSizeChange={setSize} />
          </>
        )}
      </Card>
    </>
  );
}
