import CloseIcon from '@mui/icons-material/Close';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import { useTheme } from '@mui/material/styles';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { Fragment, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { TableSkeleton } from '@/components/common/Skeletons';
import type { AuditEvent, AuditLogFilters } from '@/models/admin';
import { formatDateTime } from '@/utils/format';
import { DebouncedSearchField } from '../components/DebouncedSearchField';
import { DateFilter, FilterBar, SelectFilter } from '../components/Filters';
import { ListPagination } from '../components/ListPagination';
import { RefreshWarning } from '../components/RefreshWarning';
import { MetadataList } from '../components/MetadataList';
import { useListParams } from '../components/useListParams';
import { visuallyHidden } from '../components/visuallyHidden';
import {
  actorLabel, AUDIT_ACTIONS, AUDIT_ACTION_LABELS, AUDIT_ENTITY_LABELS, AUDIT_ENTITY_TYPES, parseOption, shortId,
} from '../labels';
import { adminPaths } from '../paths';
import { useAuditLog } from './hooks';

const FILTER_KEYS = ['action', 'entityType', 'entityId', 'actor', 'from', 'to'] as const;

/** Users and jobs are addressed by id, so their events link to the details page. */
function EntityCell({ event }: { event: AuditEvent }) {
  const label = AUDIT_ENTITY_LABELS[event.entityType] ?? event.entityType;
  const id = event.entityId;
  const target = id && event.entityType === 'USER' ? adminPaths.user(id)
    : id && event.entityType === 'MARKET_DATA_JOB' ? adminPaths.job(id) : null;
  return (
    <Box>
      {label}
      {id && (
        <Typography variant="caption" color="text.secondary" component="div">
          {target ? <Link component={RouterLink} to={target}>#{shortId(id)}</Link> : `#${shortId(id)}`}
        </Typography>
      )}
    </Box>
  );
}

function EventDetails({ event }: { event: AuditEvent }) {
  return (
    <Stack spacing={1.5} sx={{ py: 1 }}>
      <MetadataList values={event.metadata} emptyText="No additional details were recorded." />
      <Typography variant="caption" color="text.secondary">
        Event #{shortId(event.id)}{event.entityId ? ` · entity ${event.entityId}` : ''}
        {event.correlationId ? ` · correlation ${event.correlationId}` : ''}
      </Typography>
    </Stack>
  );
}

function ExpandButton({ event, open, onToggle }: { event: AuditEvent; open: boolean; onToggle: () => void }) {
  return (
    <IconButton size="small" onClick={onToggle} aria-expanded={open} aria-controls={`audit-details-${event.id}`}
      aria-label={`Details: ${event.summary}`}>
      {open ? <ExpandLessIcon /> : <ExpandMoreIcon />}
    </IconButton>
  );
}

function AuditTable({ events, expanded, onToggle, dimmed }: {
  events: AuditEvent[]; expanded: string | null; onToggle: (id: string) => void; dimmed: boolean;
}) {
  const theme = useTheme();
  const compact = useMediaQuery(theme.breakpoints.down('sm'));

  if (compact) {
    return (
      <Stack component="ul" spacing={1.5} aria-label="Audit events" sx={{ listStyle: 'none', p: 1.5, m: 0, opacity: dimmed ? 0.6 : 1 }}>
        {events.map((event) => (
          <Box component="li" key={event.id}>
            <Card variant="outlined" sx={{ p: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 700 }}>{AUDIT_ACTION_LABELS[event.action] ?? event.action}</Typography>
                  <Typography variant="body2">{event.summary}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {formatDateTime(event.occurredAt)} · {actorLabel(event.actor)}
                  </Typography>
                </Box>
                <ExpandButton event={event} open={expanded === event.id} onToggle={() => onToggle(event.id)} />
              </Box>
              <Collapse in={expanded === event.id} id={`audit-details-${event.id}`} unmountOnExit>
                <EventDetails event={event} />
              </Collapse>
            </Card>
          </Box>
        ))}
      </Stack>
    );
  }

  return (
    <TableContainer sx={{ opacity: dimmed ? 0.6 : 1 }}>
      <Table aria-label="Audit events">
        <TableHead>
          <TableRow>
            <TableCell>Time</TableCell>
            <TableCell>Actor</TableCell>
            <TableCell>Action</TableCell>
            <TableCell>Entity</TableCell>
            <TableCell>Summary</TableCell>
            <TableCell align="right"><Box component="span" sx={visuallyHidden}>Details</Box></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {events.map((event) => {
            const open = expanded === event.id;
            return (
              <Fragment key={event.id}>
                <TableRow sx={open ? { '& > td': { borderBottom: 0 } } : undefined}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(event.occurredAt)}</TableCell>
                  <TableCell>{actorLabel(event.actor)}</TableCell>
                  <TableCell>{AUDIT_ACTION_LABELS[event.action] ?? event.action}</TableCell>
                  <TableCell><EntityCell event={event} /></TableCell>
                  <TableCell>{event.summary}</TableCell>
                  <TableCell align="right">
                    <ExpandButton event={event} open={open} onToggle={() => onToggle(event.id)} />
                  </TableCell>
                </TableRow>
                {open && (
                  <TableRow>
                    <TableCell colSpan={6} id={`audit-details-${event.id}`} sx={{ bgcolor: 'action.hover' }}>
                      <EventDetails event={event} />
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default function AdminAuditLogPage() {
  const { get, page, size, setFilters, setPage, setSize } = useListParams();
  const action = parseOption(get('action'), AUDIT_ACTIONS);
  const entityType = parseOption(get('entityType'), AUDIT_ENTITY_TYPES);
  const entityId = get('entityId');
  const filters: AuditLogFilters = {
    action,
    entityType,
    entityId: entityId || undefined,
    actor: get('actor') || undefined,
    from: get('from') || undefined,
    to: get('to') || undefined,
    page,
    size,
  };
  const events = useAuditLog(filters);
  const [expanded, setExpanded] = useState<string | null>(null);
  const filtered = FILTER_KEYS.some((key) => get(key) !== '');
  const clearFilters = () => setFilters(Object.fromEntries(FILTER_KEYS.map((key) => [key, null])));

  return (
    <>
      <PageHeader title="Audit log" subtitle="Every administrative change, newest first." />
      <Card>
        <FilterBar label="Filter audit events" active={filtered} onClear={clearFilters}>
          <SelectFilter label="Action" value={action} options={AUDIT_ACTIONS} labels={AUDIT_ACTION_LABELS} allLabel="All actions"
            onChange={(next) => setFilters({ action: next })} minWidth={210} />
          <SelectFilter label="Entity type" value={entityType} options={AUDIT_ENTITY_TYPES} labels={AUDIT_ENTITY_LABELS}
            allLabel="All entities" onChange={(next) => setFilters({ entityType: next })} />
          <DebouncedSearchField label="Actor email" value={get('actor')} onChange={(actor) => setFilters({ actor })}
            sx={{ minWidth: { xs: '100%', sm: 220 } }} />
          <DateFilter label="From" value={get('from')} max={get('to')} onChange={(from) => setFilters({ from })} />
          <DateFilter label="To" value={get('to')} min={get('from')} onChange={(to) => setFilters({ to })} />
          {entityId && (
            <Box sx={{ display: 'inline-flex', alignItems: 'center' }}>
              <Chip label={`Entity #${shortId(entityId)}`} variant="outlined" />
              <IconButton size="small" aria-label="Remove entity filter" onClick={() => setFilters({ entityId: null })}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Box>
          )}
        </FilterBar>

        {events.isPending && <TableSkeleton rows={8} label="Loading audit events" />}
        {events.isError && !events.data && (
          <Box sx={{ p: 2 }}>
            <AdminErrorState title="Unable to load the audit log" error={events.error} onRetry={() => void events.refetch()} />
          </Box>
        )}
        {events.isError && events.data && <Box sx={{ px: 2, pt: 2 }}><RefreshWarning error={events.error} /></Box>}
        {events.data && events.data.content.length === 0 && (
          <EmptyState
            icon={<HistoryOutlinedIcon />}
            title={filtered ? 'No events match these filters' : 'No administrative activity yet'}
            description={filtered ? 'Try a wider date range or other filters.'
              : 'Changes to users, stocks and market data are recorded here as they happen.'}
            action={filtered ? <Button variant="outlined" onClick={clearFilters}>Clear filters</Button> : undefined}
          />
        )}
        {events.data && events.data.content.length > 0 && (
          <>
            <AuditTable events={events.data.content} expanded={expanded} dimmed={events.isPlaceholderData}
              onToggle={(id) => setExpanded((current) => (current === id ? null : id))} />
            <ListPagination page={events.data.page} label="Audit log pages" onPageChange={setPage} onSizeChange={setSize} />
          </>
        )}
      </Card>
    </>
  );
}
