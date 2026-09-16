import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import type { MarketPeriod } from '@/models/marketData';

interface PeriodSelectorProps {
  value: MarketPeriod;
  options: MarketPeriod[];
  onChange: (period: MarketPeriod) => void;
  label?: string;
  disabled?: boolean;
}

export function PeriodSelector({ value, options, onChange, label = 'Period', disabled }: PeriodSelectorProps) {
  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={value}
      disabled={disabled}
      aria-label={label}
      onChange={(_, next: MarketPeriod | null) => next && onChange(next)}
      sx={{ flexWrap: 'wrap', '& .MuiToggleButton-root': { px: 1.25, py: 0.25, fontWeight: 600 } }}
    >
      {options.map((option) => (
        <ToggleButton key={option} value={option} aria-label={`${option} period`}>{option}</ToggleButton>
      ))}
    </ToggleButtonGroup>
  );
}
