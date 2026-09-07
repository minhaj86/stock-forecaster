import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { ColorModeContext, type ColorModePreference } from './colorModeContext';
import { createAppTheme } from './theme';

const STORAGE_KEY = 'stocklens.colorMode';

function readPreference(): ColorModePreference {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
  } catch {
    return 'system';
  }
}

/** Light/dark/system theme; the preference is stored locally in the browser. */
export function ColorModeProvider({ children }: { children: ReactNode }) {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)', { noSsr: true });
  const [preference, setPreferenceState] = useState<ColorModePreference>(readPreference);
  const mode = preference === 'system' ? (prefersDark ? 'dark' : 'light') : preference;

  const setPreference = useCallback((next: ColorModePreference) => {
    setPreferenceState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Storage may be unavailable (private mode); the choice then lasts for this visit only.
    }
  }, []);

  const theme = useMemo(() => createAppTheme(mode), [mode]);
  const value = useMemo(() => ({ preference, mode, setPreference }), [preference, mode, setPreference]);

  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline enableColorScheme />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}
