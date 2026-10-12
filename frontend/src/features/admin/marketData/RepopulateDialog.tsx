import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import type { AdminStock } from '@/models/admin';
import { formatDate, formatInteger } from '@/utils/format';
import { ConfirmPhraseField } from '../components/ConfirmPhraseField';
import { DetailList } from '../components/DetailList';
import { useRetainedValue } from '../components/useRetainedValue';
import { duplicateJobMessage, jobReference, useJobNotifier } from '../useJobNotifier';
import { createRepopulateSchema, defaultHistoryRange, todayUtc, type RepopulateFormValues } from './dateRange';
import { useRepopulate } from './hooks';
import { applyJobFormError } from './jobFormErrors';
import { DateRangeFields, IntervalField } from './MarketDataFormFields';

const PHRASE = 'REPOPULATE';
const FIELDS: FieldPath<RepopulateFormValues>[] = ['fromDate', 'toDate', 'interval'];

function RepopulateForm({ stock, onClose }: { stock: AdminStock; onClose: () => void }) {
  const [today] = useState(todayUtc);
  const schema = useMemo(() => createRepopulateSchema(today), [today]);
  const repopulate = useRepopulate();
  const notifyJob = useJobNotifier();
  const [phrase, setPhrase] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const { firstBarDate, lastBarDate, barCount } = stock.marketData;
  // Replace the stored range by default; without data, the usual five-year history.
  const defaultRange = firstBarDate && lastBarDate ? { fromDate: firstBarDate, toDate: lastBarDate } : defaultHistoryRange(today);
  const { control, handleSubmit, setError, formState: { isSubmitting } } = useForm<RepopulateFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { ...defaultRange, interval: 'ONE_DAY' },
  });

  const submit = async (values: RepopulateFormValues) => {
    setSubmitError(null);
    try {
      const submission = await repopulate.mutateAsync({ symbol: stock.symbol, request: values });
      if (submission.created) {
        notifyJob(`Re-population started. ${stock.symbol} ${values.fromDate} → ${values.toDate} · ${jobReference(submission.job.id)}`,
          submission.job.id);
      } else {
        notifyJob(duplicateJobMessage(`A re-population of ${stock.symbol}`, submission), submission.job.id, 'info');
      }
      onClose();
    } catch (error) {
      setSubmitError(applyJobFormError(error, FIELDS, setError));
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} aria-label="Re-populate form">
      <DialogTitle id="repopulate-title">Re-populate historical data?</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5}>
          <DetailList
            columns={2}
            items={[
              { label: 'Stock', value: `${stock.symbol} – ${stock.companyName}` },
              { label: 'Existing records', value: formatInteger(barCount) },
              {
                label: 'Current data range',
                value: firstBarDate && lastBarDate ? `${formatDate(firstBarDate)} → ${formatDate(lastBarDate)}` : 'No data stored',
              },
            ]}
          />
          <Typography variant="subtitle2" component="h3">Replacement range</Typography>
          <DateRangeFields control={control} today={today} fromName="fromDate" toName="toDate" />
          <IntervalField control={control} name="interval" />
          <Alert severity="warning">
            This operation will replace existing market data and recalculate analytics and forecasts. If the provider
            fails, existing data is kept.
          </Alert>
          {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
          <ConfirmPhraseField phrase={PHRASE} value={phrase} onChange={setPhrase} disabled={isSubmitting} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" variant="contained" color="error" disabled={phrase !== PHRASE} loading={isSubmitting}>
          Re-populate data
        </Button>
      </DialogActions>
    </form>
  );
}

/** Replaces stored bars in a range with fresh provider data; needs REPOPULATE typed to confirm. */
export function RepopulateDialog({ stock, onClose }: { stock: AdminStock | null; onClose: () => void }) {
  const shown = useRetainedValue(stock);
  return (
    <Dialog open={stock !== null} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="repopulate-title">
      {shown && <RepopulateForm key={shown.symbol} stock={shown} onClose={onClose} />}
    </Dialog>
  );
}
