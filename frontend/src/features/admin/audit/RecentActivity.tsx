import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import { Link as RouterLink } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { TableSkeleton } from '@/components/common/Skeletons';
import type { AuditEntityType } from '@/models/admin';
import { formatDateTime } from '@/utils/format';
import { actorLabel, AUDIT_ACTION_LABELS } from '../labels';
import { adminPaths } from '../paths';
import { useAuditLog } from './hooks';

const RECENT_EVENTS = 10;

/** The latest audit events of one entity, e.g. a user's account changes. */
export function RecentActivity({ entityType, entityId }: { entityType: AuditEntityType; entityId: string }) {
  const activity = useAuditLog({ entityType, entityId, page: 0, size: RECENT_EVENTS });
  const fullLog = `${adminPaths.auditLog}?${new URLSearchParams({ entityType, entityId }).toString()}`;

  return (
    <Card component="section" aria-labelledby="recent-activity-heading" sx={{ height: '100%' }}>
      <CardHeader
        title="Recent activity"
        slotProps={{ title: { id: 'recent-activity-heading', variant: 'h6', component: 'h2' } }}
        action={<Button component={RouterLink} to={fullLog} size="small">Open audit log</Button>}
      />
      <CardContent sx={{ pt: 0 }}>
        {activity.isPending && <TableSkeleton rows={4} label="Loading recent activity" />}
        {activity.isError && (
          <AdminErrorState title="Unable to load recent activity" error={activity.error} onRetry={() => void activity.refetch()} />
        )}
        {activity.data?.content.length === 0 && (
          <EmptyState icon={<HistoryOutlinedIcon />} title="No recorded activity"
            description="Administrative changes appear here as soon as they are made." />
        )}
        {activity.data && activity.data.content.length > 0 && (
          <List aria-label="Recent activity" disablePadding>
            {activity.data.content.map((event) => (
              <ListItem key={event.id} divider disableGutters>
                <ListItemText
                  primary={`${AUDIT_ACTION_LABELS[event.action] ?? event.action}: ${event.summary}`}
                  secondary={`${formatDateTime(event.occurredAt)} · ${actorLabel(event.actor)}`}
                />
              </ListItem>
            ))}
          </List>
        )}
      </CardContent>
    </Card>
  );
}
