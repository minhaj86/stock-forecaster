import { Navigate, type RouteObject } from 'react-router';
import { AppShell } from '@/app/layout/AppShell';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { ProtectedRoute, PublicOnlyRoute } from './guards';
import { NotFoundPage, RouteErrorPage } from './ErrorPages';
import {
  AiAssistantPage, ComparePage, DashboardPage, ExplorePage, ForecastsPage, ProfilePage, SettingsPage, StockDetailsPage,
  WatchlistDetailPage, WatchlistsPage,
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
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];
