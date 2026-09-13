import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined';
import BookmarksOutlinedIcon from '@mui/icons-material/BookmarksOutlined';
import CompareArrowsOutlinedIcon from '@mui/icons-material/CompareArrowsOutlined';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import TravelExploreOutlinedIcon from '@mui/icons-material/TravelExploreOutlined';
import type { SvgIconComponent } from '@mui/icons-material';

export interface NavItem {
  label: string;
  to: string;
  icon: SvgIconComponent;
  /** Scheduled for a later release. */
  soon?: boolean;
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
