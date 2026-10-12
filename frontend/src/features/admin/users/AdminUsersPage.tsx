import MoreVertIcon from '@mui/icons-material/MoreVert';
import PeopleAltOutlinedIcon from '@mui/icons-material/PeopleAltOutlined';
import PersonAddAltOutlinedIcon from '@mui/icons-material/PersonAddAltOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Skeleton from '@mui/material/Skeleton';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { TableSkeleton } from '@/components/common/Skeletons';
import { useAuth } from '@/features/auth/authContext';
import type { AdminUser, AdminUserFilters } from '@/models/admin';
import { formatDate, formatDateTime, formatInteger } from '@/utils/format';
import { DataTable, type Column } from '../components/DataTable';
import { DebouncedSearchField } from '../components/DebouncedSearchField';
import { DateFilter, FilterBar, SelectFilter } from '../components/Filters';
import { ListPagination } from '../components/ListPagination';
import { RefreshWarning } from '../components/RefreshWarning';
import { AccountStatusChip, RoleChip } from '../components/StatusChips';
import { useListParams } from '../components/useListParams';
import { visuallyHidden } from '../components/visuallyHidden';
import { ACCOUNT_STATUSES, ACCOUNT_STATUS_LABELS, parseOption, ROLES, ROLE_LABELS } from '../labels';
import { adminPaths } from '../paths';
import { CreateUserDialog } from './CreateUserDialog';
import { useAdminUsers, useUserStatistics } from './hooks';
import { UserActionDialogs, type UserAction } from './UserActionDialogs';
import { useEnableUserAction } from './useEnableUserAction';
import { UserActionsMenu } from './UserActionsMenu';

const FILTER_KEYS = ['query', 'role', 'status', 'createdFrom', 'createdTo'] as const;

function StatisticsLine() {
  const statistics = useUserStatistics();
  if (statistics.isPending) return <Skeleton width={360} aria-label="Loading user statistics" />;
  if (statistics.isError) return <>Manage accounts, roles and access.</>;
  const { totalUsers, activeUsers, disabledUsers, lockedUsers, administrators, registeredLast7Days } = statistics.data;
  return (
    <>
      {formatInteger(totalUsers)} users · {formatInteger(activeUsers)} active · {formatInteger(disabledUsers)} disabled
      · {formatInteger(lockedUsers)} locked · {formatInteger(administrators)} administrators
      · {formatInteger(registeredLast7Days)} new in the last 7 days
    </>
  );
}

function NameCell({ user, self }: { user: AdminUser; self: boolean }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>{user.displayName ?? '—'}</Typography>
      {self && <Chip label="You" size="small" />}
    </Box>
  );
}

export default function AdminUsersPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const { get, page, size, setFilters, setPage, setSize } = useListParams();
  const role = parseOption(get('role'), ROLES);
  const status = parseOption(get('status'), ACCOUNT_STATUSES);
  const filters: AdminUserFilters = {
    query: get('query') || undefined,
    role,
    status,
    createdFrom: get('createdFrom') || undefined,
    createdTo: get('createdTo') || undefined,
    page,
    size,
  };
  const users = useAdminUsers(filters);
  const filtered = FILTER_KEYS.some((key) => get(key) !== '');
  const [creating, setCreating] = useState(false);
  const [action, setAction] = useState<UserAction | null>(null);
  const [menu, setMenu] = useState<{ anchor: HTMLElement; user: AdminUser } | null>(null);
  const { enable } = useEnableUserAction();
  const isSelf = (user: AdminUser) => auth.user?.id === user.id;
  const clearFilters = () => setFilters(Object.fromEntries(FILTER_KEYS.map((key) => [key, null])));

  const actionsButton = (user: AdminUser) => (
    <IconButton aria-label={`Actions for ${user.email}`} onClick={(event) => setMenu({ anchor: event.currentTarget, user })}>
      <MoreVertIcon />
    </IconButton>
  );

  const emailLink = (user: AdminUser) => (
    <Link component={RouterLink} to={adminPaths.user(user.id)} underline="hover">{user.email}</Link>
  );

  const columns: Column<AdminUser>[] = [
    { id: 'name', header: 'Name', cell: (user) => <NameCell user={user} self={isSelf(user)} /> },
    { id: 'email', header: 'Email', cell: emailLink },
    { id: 'role', header: 'Role', cell: (user) => <RoleChip role={user.role} /> },
    {
      id: 'status',
      header: 'Status',
      cell: (user) => (
        <Box>
          <AccountStatusChip status={user.status} />
          {user.passwordChangeRequired && (
            <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 0.5 }}>Password change pending</Typography>
          )}
        </Box>
      ),
    },
    { id: 'created', header: 'Created', cell: (user) => formatDate(user.createdAt) },
    { id: 'lastLogin', header: 'Last sign-in', cell: (user) => formatDateTime(user.lastLoginAt), hideBelowMd: true },
    { id: 'actions', header: <Box component="span" sx={visuallyHidden}>Actions</Box>, align: 'right', cell: actionsButton },
  ];

  return (
    <>
      <PageHeader
        title="Users"
        subtitle={<StatisticsLine />}
        actions={<Button variant="contained" startIcon={<PersonAddAltOutlinedIcon />} onClick={() => setCreating(true)}>Add user</Button>}
      />
      <Card>
        <FilterBar label="Filter users" active={filtered} onClear={clearFilters}>
          <DebouncedSearchField label="Search by name or email" value={get('query')} onChange={(query) => setFilters({ query })}
            sx={{ minWidth: { xs: '100%', sm: 280 } }} />
          <SelectFilter label="Role" value={role} options={ROLES} labels={ROLE_LABELS} allLabel="All roles"
            onChange={(next) => setFilters({ role: next })} />
          <SelectFilter label="Status" value={status} options={ACCOUNT_STATUSES} labels={ACCOUNT_STATUS_LABELS}
            allLabel="All statuses" onChange={(next) => setFilters({ status: next })} />
          <DateFilter label="Created from" value={get('createdFrom')} max={get('createdTo')}
            onChange={(createdFrom) => setFilters({ createdFrom })} />
          <DateFilter label="Created to" value={get('createdTo')} min={get('createdFrom')}
            onChange={(createdTo) => setFilters({ createdTo })} />
        </FilterBar>

        {users.isPending && <TableSkeleton rows={8} label="Loading users" />}
        {users.isError && !users.data && (
          <Box sx={{ p: 2 }}>
            <AdminErrorState title="Unable to load users" error={users.error} onRetry={() => void users.refetch()} />
          </Box>
        )}
        {users.isError && users.data && <Box sx={{ px: 2, pt: 2 }}><RefreshWarning error={users.error} /></Box>}
        {users.data && users.data.content.length === 0 && (
          filtered ? (
            <EmptyState icon={<PeopleAltOutlinedIcon />} title="No users match these filters"
              description="Try a different name or email, or clear the filters."
              action={<Button variant="outlined" onClick={clearFilters}>Clear filters</Button>} />
          ) : (
            <EmptyState icon={<PeopleAltOutlinedIcon />} title="No users yet"
              description="Add the first account; new users can also register themselves."
              action={<Button variant="contained" onClick={() => setCreating(true)}>Add user</Button>} />
          )
        )}
        {users.data && users.data.content.length > 0 && (
          <>
            <DataTable
              label="Users"
              rows={users.data.content}
              columns={columns}
              rowKey={(user) => user.id}
              onRowClick={(user) => navigate(adminPaths.user(user.id))}
              dimmed={users.isPlaceholderData}
              renderCard={(user) => (
                <Box sx={{ p: 2, display: 'flex', gap: 1, alignItems: 'flex-start' }}>
                  <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <NameCell user={user} self={isSelf(user)} />
                    <Typography variant="body2" noWrap>{emailLink(user)}</Typography>
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', my: 1 }}>
                      <RoleChip role={user.role} />
                      <AccountStatusChip status={user.status} />
                    </Box>
                    <Typography variant="caption" color="text.secondary">Created {formatDate(user.createdAt)}</Typography>
                  </Box>
                  {actionsButton(user)}
                </Box>
              )}
            />
            <ListPagination page={users.data.page} label="Users pages" onPageChange={setPage} onSizeChange={setSize} />
          </>
        )}
      </Card>

      <UserActionsMenu
        anchor={menu?.anchor ?? null}
        user={menu?.user ?? null}
        self={menu ? isSelf(menu.user) : false}
        onClose={() => setMenu(null)}
        onView={(user) => navigate(adminPaths.user(user.id))}
        onAction={setAction}
        onEnable={(user) => void enable(user)}
      />
      <UserActionDialogs action={action} onChange={setAction} />
      <CreateUserDialog open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
