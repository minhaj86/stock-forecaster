import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMemo, useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import type { AdminStock } from '@/models/admin';
import { useRetainedValue } from '../components/useRetainedValue';
import { duplicateJobMessage, jobReference, useJobNotifier } from '../useJobNotifier';
import { createBackfillSchema, defaultHistoryRange, todayUtc, type BackfillFormValues } from './dateRange';
import { useBackfill } from './hooks';
import { applyJobFormError } from './jobFormErrors';
import { DateRangeFields, ExistingDataField, IntervalField } from './MarketDataFormFields';

const FIELDS: FieldPath<BackfillFormValues>[] = ['fromDate', 'toDate', 'interval', 'existingDataStrategy'];

function BackfillForm({ stock, onClose }: { stock: AdminStock; onClose: () => void }) {
  const [today] = useState(todayUtc);
  const schema = useMemo(() => createBackfillSchema(today), [today]);
  const backfill = useBackfill();
  const notifyJob = useJobNotifier();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const populate = stock.marketData.barCount === 0;
  const { control, handleSubmit, setError, formState: { isSubmitting } } = useForm<BackfillFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { ...defaultHistoryRange(today), interval: 'ONE_DAY', existingDataStrategy: 'SKIP' },
  });

  const submit = async (values: BackfillFormValues) => {
    setSubmitError(null);
    try {
      const submission = await backfill.mutateAsync({ symbol: stock.symbol, request: values });
      if (submission.created) {
        notifyJob(`Historical data backfill started. ${stock.symbol} ${values.fromDate} → ${values.toDate} · ${jobReference(submission.job.id)}`,
          submission.job.id);
      } else {
        notifyJob(duplicateJobMessage(`A backfill for ${stock.symbol}`, submission), submission.job.id, 'info');
      }
      onClose();
    } catch (error) {
      setSubmitError(applyJobFormError(error, FIELDS, setError));
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} aria-label="Backfill form">
      <DialogTitle id="backfill-title">{populate ? 'Populate historical data' : 'Backfill historical data'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
          <TextField label="Stock" value={`${stock.symbol} – ${stock.companyName}`} fullWidth
            slotProps={{ input: { readOnly: true } }} />
          <DateRangeFields control={control} today={today} fromName="fromDate" toName="toDate" />
          <IntervalField control={control} name="interval" />
          <ExistingDataField control={control} name="existingDataStrategy" />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" variant="contained" loading={isSubmitting}>{populate ? 'Populate data' : 'Start backfill'}</Button>
      </DialogActions>
    </form>
  );
}

/** Loads (missing) history for a range; runs as a background job. */
export function BackfillDialog({ stock, onClose }: { stock: AdminStock | null; onClose: () => void }) {
  const shown = useRetainedValue(stock);
  return (
    <Dialog open={stock !== null} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="backfill-title">
      {shown && <BackfillForm key={shown.symbol} stock={shown} onClose={onClose} />}
    </Dialog>
  );
}
