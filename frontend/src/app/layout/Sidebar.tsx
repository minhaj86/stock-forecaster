import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import { NavLink, useLocation } from 'react-router';
import { BrandMark } from './BrandMark';
import { PRIMARY_NAV, SECONDARY_NAV, type NavItem } from './navigation';

function NavEntry({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const location = useLocation();
  const selected = location.pathname === item.to || location.pathname.startsWith(`${item.to}/`);
  const Icon = item.icon;
  return (
    <ListItemButton
      component={NavLink}
      to={item.to}
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
  return (
    <Box component="nav" aria-label="Main navigation" sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ px: 3, py: 2.25 }}>
        <BrandMark />
      </Box>
      <List sx={{ flexGrow: 1, pt: 1 }}>
        {PRIMARY_NAV.map((item) => <NavEntry key={item.to} item={item} onNavigate={onNavigate} />)}
      </List>
      <Divider />
      <List>
        {SECONDARY_NAV.map((item) => <NavEntry key={item.to} item={item} onNavigate={onNavigate} />)}
      </List>
    </Box>
  );
}
