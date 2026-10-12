import { lazy } from 'react';

/** Route-level code splitting: each page (and heavy dependencies such as charts) loads on first visit. */
export const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'));
export const WatchlistsPage = lazy(() => import('@/features/watchlist/WatchlistsPage'));
export const WatchlistDetailPage = lazy(() => import('@/features/watchlist/WatchlistDetailPage'));
export const ExplorePage = lazy(() => import('@/features/stocks/ExplorePage'));
export const StockDetailsPage = lazy(() => import('@/features/stocks/StockDetailsPage'));
export const ComparePage = lazy(() => import('@/features/compare/ComparePage'));
export const ForecastsPage = lazy(() => import('@/features/forecast/ForecastsPage'));
export const AiAssistantPage = lazy(() => import('@/features/ai/AiAssistantPage'));
export const ProfilePage = lazy(() => import('@/features/profile/ProfilePage'));
export const SettingsPage = lazy(() => import('@/features/profile/SettingsPage'));

// Admin console (ADMIN role only; the backend authorises every request again).
export const AdminDashboardPage = lazy(() => import('@/features/admin/dashboard/AdminDashboardPage'));
export const AdminUsersPage = lazy(() => import('@/features/admin/users/AdminUsersPage'));
export const AdminUserDetailsPage = lazy(() => import('@/features/admin/users/AdminUserDetailsPage'));
export const AdminStocksPage = lazy(() => import('@/features/admin/stocks/AdminStocksPage'));
export const AdminStockDetailsPage = lazy(() => import('@/features/admin/stocks/AdminStockDetailsPage'));
export const AdminMarketDataPage = lazy(() => import('@/features/admin/marketData/AdminMarketDataPage'));
export const AdminJobsPage = lazy(() => import('@/features/admin/jobs/AdminJobsPage'));
export const AdminJobDetailsPage = lazy(() => import('@/features/admin/jobs/AdminJobDetailsPage'));
export const AdminProvidersPage = lazy(() => import('@/features/admin/providers/AdminProvidersPage'));
export const AdminAuditLogPage = lazy(() => import('@/features/admin/audit/AdminAuditLogPage'));
