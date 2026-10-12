import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { Controller, useForm, type FieldPath } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { useNotify } from '@/app/providers/notificationContext';
import { SEARCH_DEBOUNCE_MS } from '@/features/stocks/StockSearch';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { adminPaths } from '../paths';
import { useStockOptions } from '../stocks/hooks';
import {
  createBulkLoadSchema, defaultHistoryRange, MAX_BULK_STOCKS, todayUtc, type BulkLoadFormValues,
} from './dateRange';
import { useBulkLoad } from './hooks';
import { applyJobFormError } from './jobFormErrors';
import { DateRangeFields, ExistingDataField, IntervalField } from './MarketDataFormFields';

type StockOption = BulkLoadFormValues['stocks'][number];

const FIELDS: FieldPath<BulkLoadFormValues>[] = ['stocks', 'fromDate', 'toDate', 'interval', 'existingDataStrategy'];

function BulkLoadForm({ onClose }: { onClose: () => void }) {
  const [today] = useState(todayUtc);
  const schema = useMemo(() => createBulkLoadSchema(today), [today]);
  const bulkLoad = useBulkLoad();
  const notify = useNotify();
  const navigate = useNavigate();
  const [searchText, setSearchText] = useState('');
  const query = useDebouncedValue(searchText, SEARCH_DEBOUNCE_MS);
  const options = useStockOptions(query);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const { control, handleSubmit, setError, formState: { isSubmitting } } = useForm<BulkLoadFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { stocks: [], ...defaultHistoryRange(today), interval: 'ONE_DAY', existingDataStrategy: 'SKIP' },
  });
  const stockOptions: StockOption[] = (options.data?.content ?? [])
    .filter((stock) => stock.active)
    .map((stock) => ({ symbol: stock.symbol, companyName: stock.companyName }));

  const submit = async (values: BulkLoadFormValues) => {
    setSubmitError(null);
    const { stocks, ...range } = values;
    try {
      const result = await bulkLoad.mutateAsync({ symbols: stocks.map((stock) => stock.symbol), ...range });
      notify({
        message: `Queued ${result.jobs.length} backfill ${result.jobs.length === 1 ? 'job' : 'jobs'} for ${range.fromDate} → ${range.toDate}`,
        action: { label: 'View jobs', onClick: () => navigate(adminPaths.jobs) },
      });
      onClose();
    } catch (error) {
      setSubmitError(applyJobFormError(error, FIELDS, setError));
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} aria-label="Bulk historical load form">
      <DialogTitle id="bulk-load-title">Bulk historical load</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5}>
          <DialogContentText>
            Queues one backfill job per stock. Jobs respect the provider&apos;s rate limit, so large loads take a while.
          </DialogContentText>
          {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
          <Controller
            name="stocks"
            control={control}
            render={({ field, fieldState }) => (
              <Autocomplete<StockOption, true>
                multiple
                options={stockOptions}
                value={field.value}
                onChange={(_, next) => field.onChange(next)}
                onInputChange={(_, next, reason) => {
                  if (reason === 'input' || reason === 'clear') setSearchText(next);
                }}
                filterOptions={(candidates) => candidates}
                filterSelectedOptions
                getOptionLabel={(option) => option.symbol}
                isOptionEqualToValue={(option, value) => option.symbol === value.symbol}
                getOptionDisabled={(option) => field.value.length >= MAX_BULK_STOCKS
                  && !field.value.some((selected) => selected.symbol === option.symbol)}
                loading={options.isFetching}
                noOptionsText={options.isError ? 'Unable to load stocks' : 'No active stocks match'}
                renderOption={(props, option) => {
                  const { key, ...optionProps } = props;
                  return (
                    <Box component="li" key={key} {...optionProps} sx={{ display: 'flex', gap: 1.5 }}>
                      <Typography sx={{ fontWeight: 700, minWidth: 64 }}>{option.symbol}</Typography>
                      <Typography variant="body2" noWrap>{option.companyName}</Typography>
                    </Box>
                  );
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    inputRef={field.ref}
                    label="Stocks"
                    placeholder={field.value.length === 0 ? 'Search by symbol or company' : undefined}
                    error={Boolean(fieldState.error)}
                    helperText={fieldState.error?.message ?? `${field.value.length} of at most ${MAX_BULK_STOCKS} selected`}
                  />
                )}
              />
            )}
          />
          <DateRangeFields control={control} today={today} fromName="fromDate" toName="toDate" />
          <IntervalField control={control} name="interval" />
          <ExistingDataField control={control} name="existingDataStrategy" />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" variant="contained" loading={isSubmitting}>Queue backfill jobs</Button>
      </DialogActions>
    </form>
  );
}

export function BulkLoadDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="bulk-load-title">
      <BulkLoadForm onClose={onClose} />
    </Dialog>
  );
}
