import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined';
import PersonOffOutlinedIcon from '@mui/icons-material/PersonOffOutlined';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Grid from '@mui/material/Grid';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import { useAuth } from '@/features/auth/authContext';
import { formatDateTime, formatInteger } from '@/utils/format';
import { RecentActivity } from '../audit/RecentActivity';
import { DetailList } from '../components/DetailList';
import { RefreshWarning } from '../components/RefreshWarning';
import { AccountStatusChip, RoleChip } from '../components/StatusChips';
import { isNotFound } from '../errors';
import { adminPaths } from '../paths';
import { useAdminUser } from './hooks';
import { UserActionDialogs, type UserAction } from './UserActionDialogs';
import { useEnableUserAction } from './useEnableUserAction';

export default function AdminUserDetailsPage() {
  const { userId = '' } = useParams();
  const auth = useAuth();
  const navigate = useNavigate();
  const user = useAdminUser(userId);
  const [action, setAction] = useState<UserAction | null>(null);
  const { enable, pendingUserId } = useEnableUserAction();

  const back = (
    <Button component={RouterLink} to={adminPaths.users} startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>All users</Button>
  );

  if (user.isPending) {
    return <>{back}<CardGridSkeleton count={2} label="Loading user" /></>;
  }
  if (!user.data) {
    return (
      <>
        {back}
        {isNotFound(user.error) ? (
          <Card>
            <EmptyState
              icon={<PersonOffOutlinedIcon />}
              title="User not found"
              description="The account may have been deleted, or the link is incorrect."
              action={<Button variant="contained" component={RouterLink} to={adminPaths.users}>Back to users</Button>}
            />
          </Card>
        ) : (
          <AdminErrorState title="Unable to load this user" error={user.error} onRetry={() => void user.refetch()} />
        )}
      </>
    );
  }

  const details = user.data;
  const self = auth.user?.id === details.id;

  return (
    <>
      {back}
      {user.isError && <RefreshWarning error={user.error} />}
      <PageHeader
        title={details.displayName ?? details.email}
        subtitle={self ? `${details.email} · your account` : details.email}
        actions={(
          <>
            <Button startIcon={<EditOutlinedIcon />} onClick={() => setAction({ kind: 'edit', user: details })}>Edit</Button>
            <Button startIcon={<LockResetOutlinedIcon />} onClick={() => setAction({ kind: 'reset', user: details })}>
              Reset password
            </Button>
            {details.status !== 'ACTIVE' && (
              <Button startIcon={<CheckCircleOutlineIcon />} loading={pendingUserId === details.id} onClick={() => void enable(details)}>
                Enable
              </Button>
            )}
            {details.status !== 'DISABLED' && !self && (
              <Button startIcon={<BlockIcon />} onClick={() => setAction({ kind: 'disable', user: details })}>Disable</Button>
            )}
            {!self && (
              <Button color="error" startIcon={<DeleteOutlineIcon />} onClick={() => setAction({ kind: 'delete', user: details })}>
                Delete
              </Button>
            )}
          </>
        )}
      />
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Card component="section" aria-labelledby="user-profile-heading">
            <CardHeader title="Profile" slotProps={{ title: { id: 'user-profile-heading', variant: 'h6', component: 'h2' } }} />
            <CardContent sx={{ pt: 0 }}>
              <DetailList
                columns={2}
                items={[
                  { label: 'Email', value: details.email },
                  { label: 'Display name', value: details.displayName ?? '—' },
                  { label: 'Role', value: <RoleChip role={details.role} /> },
                  { label: 'Status', value: <AccountStatusChip status={details.status} /> },
                  { label: 'Time zone', value: details.timeZone },
                  { label: 'Watchlists', value: formatInteger(details.watchlistCount) },
                  { label: 'Created', value: formatDateTime(details.createdAt) },
                  { label: 'Last updated', value: formatDateTime(details.updatedAt) },
                  { label: 'Last sign-in', value: details.lastLoginAt ? formatDateTime(details.lastLoginAt) : 'Never' },
                  {
                    label: 'Password change pending',
                    value: details.passwordChangeRequired ? 'Yes, at the next sign-in' : 'No',
                  },
                ]}
              />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <RecentActivity entityType="USER" entityId={details.id} />
        </Grid>
      </Grid>
      <UserActionDialogs
        action={action}
        onChange={setAction}
        onDeleted={() => navigate(adminPaths.users, { replace: true })}
      />
    </>
  );
}
