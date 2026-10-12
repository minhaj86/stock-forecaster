import SearchIcon from '@mui/icons-material/Search';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import type { SxProps, Theme } from '@mui/material/styles';
import { useEffect, useRef, useState } from 'react';
import { SEARCH_DEBOUNCE_MS } from '@/features/stocks/StockSearch';

interface DebouncedSearchFieldProps {
  label: string;
  /** The committed (trimmed) value, e.g. from the URL. */
  value: string;
  /** Called once typing pauses, with the trimmed text. */
  onChange: (value: string) => void;
  placeholder?: string;
  type?: 'search' | 'email';
  sx?: SxProps<Theme>;
}

/** Search box that commits its text after a pause in typing (one request per pause, not per keystroke). */
export function DebouncedSearchField({ label, value, onChange, placeholder, type = 'search', sx }: DebouncedSearchFieldProps) {
  const [input, setInput] = useState(value);
  const [committed, setCommitted] = useState(value);
  const timer = useRef<number | undefined>(undefined);

  // The value can also change from outside (clearing filters, browser navigation): show it.
  if (value !== committed) {
    setCommitted(value);
    if (value !== input.trim()) setInput(value);
  }

  useEffect(() => {
    const pending = timer;
    return () => window.clearTimeout(pending.current);
  }, []);

  const change = (next: string) => {
    setInput(next);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => onChange(next.trim()), SEARCH_DEBOUNCE_MS);
  };

  return (
    <TextField
      label={label}
      value={input}
      type={type}
      placeholder={placeholder}
      onChange={(event) => change(event.target.value)}
      size="small"
      sx={sx}
      slotProps={{
        input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" aria-hidden /></InputAdornment> },
      }}
    />
  );
}
