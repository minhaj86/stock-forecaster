import { Navigate, type RouteObject } from 'react-router';
import { AppShell } from '@/app/layout/AppShell';
import { AdminRoute } from '@/features/admin/AdminRoute';
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { PasswordChangeRoute, ProtectedRoute, PublicOnlyRoute } from './guards';
import { NotFoundPage, RouteErrorPage } from './ErrorPages';
import {
  AdminAuditLogPage, AdminDashboardPage, AdminJobDetailsPage, AdminJobsPage, AdminMarketDataPage, AdminProvidersPage,
  AdminStockDetailsPage, AdminStocksPage, AdminUserDetailsPage, AdminUsersPage, AiAssistantPage, ComparePage,
  DashboardPage, ExplorePage, ForecastsPage, ProfilePage, SettingsPage, StockDetailsPage, WatchlistDetailPage,
  WatchlistsPage,
} from './lazyPages';

export const routes: RouteObject[] = [
  { path: '/', element: <Navigate to="/app/dashboard" replace /> },
  {
    element: <PublicOnlyRoute />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <PasswordChangeRoute />,
    errorElement: <RouteErrorPage />,
    children: [{ path: '/change-password', element: <ChangePasswordPage /> }],
  },
  {
    path: '/app',
    element: <ProtectedRoute><AppShell /></ProtectedRoute>,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'watchlists', element: <WatchlistsPage /> },
      { path: 'watchlists/:watchlistId', element: <WatchlistDetailPage /> },
      { path: 'stocks', element: <ExplorePage /> },
      { path: 'stocks/:symbol', element: <StockDetailsPage /> },
      { path: 'compare', element: <ComparePage /> },
      { path: 'forecasts', element: <ForecastsPage /> },
      { path: 'forecasts/:symbol', element: <ForecastsPage /> },
      { path: 'ai', element: <AiAssistantPage /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'settings', element: <SettingsPage /> },
      {
        path: 'admin',
        element: <AdminRoute />,
        children: [
          { index: true, element: <AdminDashboardPage /> },
          { path: 'users', element: <AdminUsersPage /> },
          { path: 'users/:userId', element: <AdminUserDetailsPage /> },
          { path: 'stocks', element: <AdminStocksPage /> },
          { path: 'stocks/:symbol', element: <AdminStockDetailsPage /> },
          { path: 'market-data', element: <AdminMarketDataPage /> },
          { path: 'sync-jobs', element: <AdminJobsPage /> },
          { path: 'sync-jobs/:jobId', element: <AdminJobDetailsPage /> },
          { path: 'providers', element: <AdminProvidersPage /> },
          { path: 'audit-log', element: <AdminAuditLogPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];
