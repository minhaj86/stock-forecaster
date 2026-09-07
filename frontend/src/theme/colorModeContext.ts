import type { PaletteMode } from '@mui/material/styles';
import { createContext, useContext } from 'react';

export type ColorModePreference = PaletteMode | 'system';

export interface ColorModeContextValue {
  preference: ColorModePreference;
  /** The mode actually applied after resolving "system". */
  mode: PaletteMode;
  setPreference: (preference: ColorModePreference) => void;
}

export const ColorModeContext = createContext<ColorModeContextValue | null>(null);

export function useColorMode(): ColorModeContextValue {
  const context = useContext(ColorModeContext);
  if (!context) {
    throw new Error('useColorMode must be used inside ColorModeProvider');
  }
  return context;
}
