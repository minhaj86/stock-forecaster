import FilterAltOffOutlinedIcon from '@mui/icons-material/FilterAltOffOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import type { ReactNode } from 'react';
import { parseOption } from '../labels';

const ALL = 'ALL';

/** Search landmark holding a list's filters, with "Clear filters" once any filter is set. */
export function FilterBar({ label, children, active, onClear }: {
  label: string; children: ReactNode; active: boolean; onClear: () => void;
}) {
  return (
    <Box role="search" aria-label={label}
      sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center', p: 2, borderBottom: 1, borderColor: 'divider' }}>
      {children}
      {active && (
        <Button size="small" startIcon={<FilterAltOffOutlinedIcon />} onClick={onClear}>Clear filters</Button>
      )}
    </Box>
  );
}

interface SelectFilterProps<T extends string> {
  label: string;
  value: T | undefined;
  options: readonly T[];
  labels: Record<T, string>;
  allLabel: string;
  onChange: (value: T | null) => void;
  minWidth?: number;
}

/** Single-choice filter whose first option clears it ("All statuses"). */
export function SelectFilter<T extends string>(props: SelectFilterProps<T>) {
  const { label, value, options, labels, allLabel, onChange, minWidth = 170 } = props;
  return (
    <TextField
      select
      size="small"
      label={label}
      value={value ?? ALL}
      onChange={(event) => onChange(parseOption(event.target.value, options) ?? null)}
      sx={{ minWidth }}
    >
      <MenuItem value={ALL}>{allLabel}</MenuItem>
      {options.map((option) => <MenuItem key={option} value={option}>{labels[option]}</MenuItem>)}
    </TextField>
  );
}

interface DateFilterProps {
  label: string;
  /** `yyyy-MM-dd` or empty. */
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
}

export function DateFilter({ label, value, onChange, min, max }: DateFilterProps) {
  return (
    <TextField
      type="date"
      size="small"
      label={label}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      sx={{ width: 175 }}
      slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: min || undefined, max: max || undefined } }}
    />
  );
}
