import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import FormLabel from '@mui/material/FormLabel';
import MenuItem from '@mui/material/MenuItem';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import { useId } from 'react';
import { Controller, type Control, type FieldPath, type FieldValues } from 'react-hook-form';
import { FormTextField } from '@/components/forms/FormTextField';
import type { ExistingDataStrategy, MarketDataInterval } from '@/models/admin';
import { EXISTING_DATA_LABELS, INTERVAL_LABELS } from '../labels';
import { useMarketDataProviders } from '../providers/hooks';
import { EARLIEST_DATE } from './dateRange';

interface FieldProps<T extends FieldValues> {
  control: Control<T>;
}

/** "From"/"To" calendar dates, limited to 1970-01-01 … today (UTC). */
export function DateRangeFields<T extends FieldValues>({ control, today, fromName, toName }: FieldProps<T> & {
  today: string; fromName: FieldPath<T>; toName: FieldPath<T>;
}) {
  const dateProps = { type: 'date', slotProps: { inputLabel: { shrink: true }, htmlInput: { min: EARLIEST_DATE, max: today } } };
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <FormTextField control={control} name={fromName} label="From" {...dateProps} />
      <FormTextField control={control} name={toName} label="To" {...dateProps} />
    </Stack>
  );
}

const FALLBACK_INTERVALS: MarketDataInterval[] = ['ONE_DAY'];

/** Bar intervals offered by the active provider's capabilities. */
export function IntervalField<T extends FieldValues>({ control, name }: FieldProps<T> & { name: FieldPath<T> }) {
  const providers = useMarketDataProviders();
  const intervals = providers.data?.active.capabilities.supportedIntervals ?? FALLBACK_INTERVALS;
  return (
    <FormTextField control={control} name={name} label="Interval" select
      helperText={providers.data ? `Supported by ${providers.data.active.name}` : undefined}>
      {intervals.map((interval) => (
        <MenuItem key={interval} value={interval}>{INTERVAL_LABELS[interval] ?? interval}</MenuItem>
      ))}
    </FormTextField>
  );
}

const STRATEGIES: ExistingDataStrategy[] = ['SKIP', 'UPDATE'];

export function ExistingDataField<T extends FieldValues>({ control, name }: FieldProps<T> & { name: FieldPath<T> }) {
  const labelId = useId();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControl>
          <FormLabel id={labelId}>Existing-data handling</FormLabel>
          <RadioGroup aria-labelledby={labelId} name={field.name} value={field.value}
            onChange={(event) => field.onChange(event.target.value)}>
            {STRATEGIES.map((strategy) => (
              <FormControlLabel key={strategy} value={strategy} control={<Radio />} label={EXISTING_DATA_LABELS[strategy]} />
            ))}
          </RadioGroup>
          <FormHelperText sx={{ mx: 0 }}>
            Skipping only fills in missing days; updating also overwrites stored days with the provider&apos;s values.
          </FormHelperText>
        </FormControl>
      )}
    />
  );
}
