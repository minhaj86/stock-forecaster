import LogoutIcon from '@mui/icons-material/Logout';
import PersonOutlineIcon from '@mui/icons-material/PersonOutlined';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '@/features/auth/authContext';
import { useCurrentUserProfile } from '@/features/profile/hooks';

function initials(name: string | undefined, email: string | undefined): string {
  const source = name?.trim() || email || '?';
  const parts = source.split(/\s+/).filter(Boolean);
  return (parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : source.slice(0, 2)).toUpperCase();
}

export function UserMenu() {
  const auth = useAuth();
  const profile = useCurrentUserProfile();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const displayName = profile.data?.displayName;
  const go = (path: string) => {
    setAnchor(null);
    navigate(path);
  };

  return (
    <>
      <Tooltip title="Account">
        <IconButton
          onClick={(event) => setAnchor(event.currentTarget)}
          aria-label="Open account menu"
          aria-haspopup="menu"
          aria-expanded={anchor ? 'true' : undefined}
        >
          <Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.main', fontSize: '0.875rem', fontWeight: 700 }}>
            {initials(displayName, auth.user?.email)}
          </Avatar>
        </IconButton>
      </Tooltip>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
        <Box sx={{ px: 2, py: 1, maxWidth: 260 }}>
          <Typography sx={{ fontWeight: 700 }} noWrap>{displayName ?? 'Signed in'}</Typography>
          <Typography variant="body2" color="text.secondary" noWrap>{auth.user?.email}</Typography>
        </Box>
        <Divider />
        <MenuItem onClick={() => go('/app/profile')}>
          <ListItemIcon><PersonOutlineIcon fontSize="small" /></ListItemIcon>Profile
        </MenuItem>
        <MenuItem onClick={() => go('/app/settings')}>
          <ListItemIcon><SettingsOutlinedIcon fontSize="small" /></ListItemIcon>Settings
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => { setAnchor(null); void auth.logout(); }}>
          <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>Sign out
        </MenuItem>
      </Menu>
    </>
  );
}
