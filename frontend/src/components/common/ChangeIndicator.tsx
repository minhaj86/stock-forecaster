import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import ArrowDropUpIcon from '@mui/icons-material/ArrowDropUp';
import Box from '@mui/material/Box';
import type { SxProps, Theme } from '@mui/material/styles';
import { formatPercent, formatSignedCurrency } from '@/utils/format';

interface ChangeIndicatorProps {
  /** Ratio, e.g. 0.0423 for +4.23 %. */
  value: number | null | undefined;
  /** Optional absolute change shown before the percentage. */
  change?: number | null;
  currency?: string;
  size?: 'small' | 'medium';
  sx?: SxProps<Theme>;
}

/**
 * Positive/negative change with an arrow, an explicit sign and colour: meaning never depends on colour alone.
 */
export function ChangeIndicator({ value, change, currency = 'USD', size = 'medium', sx }: ChangeIndicatorProps) {
  if (value === null || value === undefined) {
    return <Box component="span" sx={{ color: 'text.secondary', ...sx }} aria-label="no data">—</Box>;
  }
  const direction = value > 0 ? 'up' : value < 0 ? 'down' : 'flat';
  const color = direction === 'up' ? 'positive.main' : direction === 'down' ? 'negative.main' : 'text.secondary';
  const percent = formatPercent(value);
  const text = change === null || change === undefined ? percent : `${formatSignedCurrency(change, currency)} (${percent})`;
  const Icon = direction === 'up' ? ArrowDropUpIcon : direction === 'down' ? ArrowDropDownIcon : null;
  const label = direction === 'flat' ? `unchanged, ${percent}` : `${direction} ${text}`;

  return (
    <Box
      component="span"
      aria-label={label}
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        color,
        fontWeight: 600,
        fontVariantNumeric: 'tabular-nums',
        fontSize: size === 'small' ? '0.8125rem' : 'inherit',
        whiteSpace: 'nowrap',
        ...sx,
      }}
    >
      {Icon && <Icon aria-hidden fontSize={size === 'small' ? 'small' : 'medium'} sx={{ ml: -0.75 }} />}
      <span aria-hidden>{text}</span>
    </Box>
  );
}
