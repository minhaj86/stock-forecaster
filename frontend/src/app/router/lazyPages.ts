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
