import { alpha, createTheme, type PaletteMode, type Theme } from '@mui/material/styles';

declare module '@mui/material/styles' {
  interface Palette {
    positive: Palette['primary'];
    negative: Palette['primary'];
    forecast: Palette['primary'];
    chart: string[];
  }
  interface PaletteOptions {
    positive?: PaletteOptions['primary'];
    negative?: PaletteOptions['primary'];
    forecast?: PaletteOptions['primary'];
    chart?: string[];
  }
}

/** Distinguishable series colours (colour-blind aware ordering), tuned per mode for contrast. */
const CHART_COLORS: Record<PaletteMode, string[]> = {
  light: ['#2457d6', '#d97706', '#0f8a6b', '#c0392b', '#7c3aed', '#0e7490', '#be185d', '#4d7c0f', '#9a3412', '#475569'],
  dark: ['#7aa2ff', '#fbbf24', '#34d399', '#f87171', '#c4b5fd', '#22d3ee', '#f472b6', '#a3e635', '#fdba74', '#cbd5e1'],
};

export function createAppTheme(mode: PaletteMode): Theme {
  const light = mode === 'light';
  return createTheme({
    palette: {
      mode,
      primary: { main: light ? '#2457d6' : '#7aa2ff' },
      secondary: { main: light ? '#0f766e' : '#5eead4' },
      positive: { main: light ? '#137a3a' : '#4ade80', contrastText: light ? '#ffffff' : '#052e16' },
      negative: { main: light ? '#c0262d' : '#f87171', contrastText: light ? '#ffffff' : '#450a0a' },
      forecast: { main: light ? '#7c3aed' : '#c4b5fd' },
      background: { default: light ? '#f5f7fb' : '#0b0f17', paper: light ? '#ffffff' : '#121826' },
      divider: light ? '#e3e8ef' : '#232b3a',
      text: light
        ? { primary: '#111827', secondary: '#4b5563' }
        : { primary: '#e5e7eb', secondary: '#9ca3af' },
      chart: CHART_COLORS[mode],
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: '"Inter", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      h4: { fontWeight: 700, letterSpacing: '-0.02em' },
      h5: { fontWeight: 700, letterSpacing: '-0.01em' },
      h6: { fontWeight: 600 },
      subtitle2: { fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: { fontFeatureSettings: '"tnum" 1, "cv11" 1' },
        },
      },
      MuiCard: {
        defaultProps: { variant: 'outlined' },
        styleOverrides: {
          root: ({ theme }) => ({
            borderRadius: 14,
            boxShadow: light ? '0 1px 2px rgba(16, 24, 40, 0.04)' : 'none',
            borderColor: theme.palette.divider,
          }),
        },
      },
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiAppBar: { defaultProps: { elevation: 0, color: 'inherit' } },
      MuiTableCell: {
        styleOverrides: {
          head: ({ theme }) => ({
            fontWeight: 600,
            color: theme.palette.text.secondary,
            whiteSpace: 'nowrap',
          }),
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: ({ theme }) => ({
            '&.MuiTableRow-hover:hover': { backgroundColor: alpha(theme.palette.primary.main, light ? 0.04 : 0.08) },
          }),
        },
      },
      MuiTooltip: { defaultProps: { arrow: true } },
    },
  });
}
