import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import { NavLink, useLocation } from 'react-router';
import { useAuth } from '@/features/auth/authContext';
import { BrandMark } from './BrandMark';
import { ADMIN_NAV, PRIMARY_NAV, SECONDARY_NAV, type NavItem } from './navigation';

function isSelected(item: NavItem, pathname: string): boolean {
  if (pathname === item.to) return true;
  return !item.exact && pathname.startsWith(`${item.to}/`);
}

function NavEntry({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const location = useLocation();
  const selected = isSelected(item, location.pathname);
  const Icon = item.icon;
  return (
    <ListItemButton
      component={NavLink}
      to={item.to}
      end={item.exact}
      selected={selected}
      onClick={onNavigate}
      aria-current={selected ? 'page' : undefined}
      sx={{ borderRadius: 2, mx: 1.5, mb: 0.5, '&.Mui-selected': { fontWeight: 700 } }}
    >
      <ListItemIcon sx={{ minWidth: 38 }}><Icon fontSize="small" /></ListItemIcon>
      <ListItemText primary={item.label} slotProps={{ primary: { sx: { fontWeight: selected ? 700 : 500 } } }} />
      {item.soon && <Chip label="Soon" size="small" variant="outlined" sx={{ height: 20, fontSize: '0.7rem' }} />}
    </ListItemButton>
  );
}

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  return (
    <Box component="nav" aria-label="Main navigation" sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ px: 3, py: 2.25 }}>
        <BrandMark />
      </Box>
      <List sx={{ pt: 1 }}>
        {PRIMARY_NAV.map((item) => <NavEntry key={item.to} item={item} onNavigate={onNavigate} />)}
      </List>
      {user?.role === 'ADMIN' && (
        <>
          <Divider sx={{ mx: 1.5 }} />
          <ListSubheader component="div" disableSticky
            sx={{
              bgcolor: 'transparent', lineHeight: 2.5, mt: 1, px: 3, fontSize: '0.72rem', fontWeight: 700,
              letterSpacing: '0.08em', textTransform: 'uppercase',
            }}>
            Admin
          </ListSubheader>
          <List aria-label="Administration" sx={{ pt: 0 }}>
            {ADMIN_NAV.map((item) => <NavEntry key={item.to} item={item} onNavigate={onNavigate} />)}
          </List>
        </>
      )}
      <Box sx={{ flexGrow: 1 }} />
      <Divider />
      <List>
        {SECONDARY_NAV.map((item) => <NavEntry key={item.to} item={item} onNavigate={onNavigate} />)}
      </List>
    </Box>
  );
}
