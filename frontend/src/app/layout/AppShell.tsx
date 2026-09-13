import MenuIcon from '@mui/icons-material/Menu';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Container from '@mui/material/Container';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Toolbar from '@mui/material/Toolbar';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { Suspense, useState } from 'react';
import { Outlet } from 'react-router';
import { StockSearch } from '@/features/stocks/StockSearch';
import { ColorModeToggle } from './ColorModeToggle';
import { DemoDataBanner } from './DemoDataBanner';
import { Sidebar } from './Sidebar';
import { UserMenu } from './UserMenu';

export const DRAWER_WIDTH = 248;

/** Authenticated layout: permanent sidebar on desktop, drawer on small screens, sticky top bar with search. */
export function AppShell() {
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <Box sx={{ display: 'flex', minHeight: '100dvh' }}>
      <Link
        href="#main-content"
        sx={{
          position: 'absolute', left: -9999, top: 8, zIndex: 2000, bgcolor: 'background.paper', p: 1, borderRadius: 1,
          '&:focus': { left: 8 },
        }}
      >
        Skip to content
      </Link>
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
        }}
      >
        <Toolbar sx={{ gap: 1.5 }}>
          {!desktop && (
            <IconButton edge="start" onClick={() => setMobileOpen(true)} aria-label="Open navigation">
              <MenuIcon />
            </IconButton>
          )}
          <Box sx={{ flexGrow: 1, minWidth: 0 }}>
            <StockSearch />
          </Box>
          <ColorModeToggle />
          <UserMenu />
        </Toolbar>
      </AppBar>
      <Box component="aside" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        {desktop ? (
          <Drawer variant="permanent" open slotProps={{ paper: { sx: { width: DRAWER_WIDTH, borderRight: 1, borderColor: 'divider' } } }}>
            <Sidebar />
          </Drawer>
        ) : (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={() => setMobileOpen(false)}
            ModalProps={{ keepMounted: true }}
            slotProps={{ paper: { sx: { width: DRAWER_WIDTH } } }}
          >
            <Sidebar onNavigate={() => setMobileOpen(false)} />
          </Drawer>
        )}
      </Box>
      <Box component="main" id="main-content" tabIndex={-1} sx={{ flexGrow: 1, minWidth: 0, outline: 'none' }}>
        <Toolbar />
        <Container maxWidth="xl" sx={{ py: { xs: 2, md: 4 }, px: { xs: 2, md: 4 } }}>
          <DemoDataBanner />
          <Suspense fallback={<Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}><CircularProgress aria-label="Loading page" /></Box>}>
            <Outlet />
          </Suspense>
        </Container>
      </Box>
    </Box>
  );
}
