import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined';
import BookmarksOutlinedIcon from '@mui/icons-material/BookmarksOutlined';
import CloudOutlinedIcon from '@mui/icons-material/CloudOutlined';
import CompareArrowsOutlinedIcon from '@mui/icons-material/CompareArrowsOutlined';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import PeopleAltOutlinedIcon from '@mui/icons-material/PeopleAltOutlined';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import StorageOutlinedIcon from '@mui/icons-material/StorageOutlined';
import SyncOutlinedIcon from '@mui/icons-material/SyncOutlined';
import TravelExploreOutlinedIcon from '@mui/icons-material/TravelExploreOutlined';
import type { SvgIconComponent } from '@mui/icons-material';

export interface NavItem {
  label: string;
  to: string;
  icon: SvgIconComponent;
  /** Scheduled for a later release. */
  soon?: boolean;
  /** Selected only on exactly this path (not on its sub-pages), e.g. an overview above sibling sections. */
  exact?: boolean;
}

export const PRIMARY_NAV: NavItem[] = [
  { label: 'Dashboard', to: '/app/dashboard', icon: DashboardOutlinedIcon },
  { label: 'Watchlists', to: '/app/watchlists', icon: BookmarksOutlinedIcon },
  { label: 'Explore', to: '/app/stocks', icon: TravelExploreOutlinedIcon },
  { label: 'Compare', to: '/app/compare', icon: CompareArrowsOutlinedIcon, soon: true },
  { label: 'Forecasts', to: '/app/forecasts', icon: InsightsOutlinedIcon },
  { label: 'AI Assistant', to: '/app/ai', icon: AutoAwesomeOutlinedIcon, soon: true },
];

export const SECONDARY_NAV: NavItem[] = [
  { label: 'Settings', to: '/app/settings', icon: SettingsOutlinedIcon },
];

/** Shown to administrators only. */
export const ADMIN_NAV: NavItem[] = [
  { label: 'Overview', to: '/app/admin', icon: AdminPanelSettingsOutlinedIcon, exact: true },
  { label: 'Users', to: '/app/admin/users', icon: PeopleAltOutlinedIcon },
  { label: 'Stocks', to: '/app/admin/stocks', icon: Inventory2OutlinedIcon },
  { label: 'Market Data', to: '/app/admin/market-data', icon: StorageOutlinedIcon },
  { label: 'Sync Jobs', to: '/app/admin/sync-jobs', icon: SyncOutlinedIcon },
  { label: 'Providers', to: '/app/admin/providers', icon: CloudOutlinedIcon },
  { label: 'Audit Log', to: '/app/admin/audit-log', icon: HistoryOutlinedIcon },
];
