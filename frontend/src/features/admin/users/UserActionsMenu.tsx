import BlockIcon from '@mui/icons-material/Block';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import type { AdminUser } from '@/models/admin';
import type { UserAction } from './UserActionDialogs';

interface UserActionsMenuProps {
  anchor: HTMLElement | null;
  user: AdminUser | null;
  /** The signed-in administrator's own account: it cannot be disabled or deleted here. */
  self: boolean;
  onClose: () => void;
  onView: (user: AdminUser) => void;
  onAction: (action: UserAction) => void;
  onEnable: (user: AdminUser) => void;
}

export function UserActionsMenu({ anchor, user, self, onClose, onView, onAction, onEnable }: UserActionsMenuProps) {
  const run = (callback: (target: AdminUser) => void) => () => {
    if (user) callback(user);
    onClose();
  };
  return (
    <Menu anchorEl={anchor} open={anchor !== null && user !== null} onClose={onClose}>
      <MenuItem onClick={run(onView)}>
        <ListItemIcon><OpenInNewIcon fontSize="small" /></ListItemIcon>View details
      </MenuItem>
      <MenuItem onClick={run((target) => onAction({ kind: 'edit', user: target }))}>
        <ListItemIcon><EditOutlinedIcon fontSize="small" /></ListItemIcon>Edit
      </MenuItem>
      <MenuItem onClick={run((target) => onAction({ kind: 'reset', user: target }))}>
        <ListItemIcon><LockResetOutlinedIcon fontSize="small" /></ListItemIcon>Reset password
      </MenuItem>
      {user && user.status !== 'ACTIVE' && (
        <MenuItem onClick={run(onEnable)}>
          <ListItemIcon><CheckCircleOutlineIcon fontSize="small" /></ListItemIcon>Enable
        </MenuItem>
      )}
      {user && user.status !== 'DISABLED' && !self && (
        <MenuItem onClick={run((target) => onAction({ kind: 'disable', user: target }))}>
          <ListItemIcon><BlockIcon fontSize="small" /></ListItemIcon>Disable
        </MenuItem>
      )}
      {!self && <Divider />}
      {!self && (
        <MenuItem onClick={run((target) => onAction({ kind: 'delete', user: target }))} sx={{ color: 'error.main' }}>
          <ListItemIcon><DeleteOutlineIcon fontSize="small" color="error" /></ListItemIcon>Delete
        </MenuItem>
      )}
    </Menu>
  );
}
