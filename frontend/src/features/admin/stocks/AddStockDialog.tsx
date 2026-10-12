import { zodResolver } from '@hookform/resolvers/zod';
import EditNoteOutlinedIcon from '@mui/icons-material/EditNoteOutlined';
import SearchIcon from '@mui/icons-material/Search';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import InputAdornment from '@mui/material/InputAdornment';
import LinearProgress from '@mui/material/LinearProgress';
import MenuItem from '@mui/material/MenuItem';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type ReactNode } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { FormTextField } from '@/components/forms/FormTextField';
import { SEARCH_DEBOUNCE_MS } from '@/features/stocks/StockSearch';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { CreateStockRequest, CreateStockResponse, ProviderSearchResult } from '@/models/admin';
import { friendlyError } from '../errors';
import { ASSET_TYPES, ASSET_TYPE_LABELS } from '../labels';
import { adminPaths } from '../paths';
import { jobReference, useJobNotifier } from '../useJobNotifier';
import { useCreateStock, useProviderSearch } from './hooks';
import { EMPTY_MANUAL_STOCK, manualStockSchema, toManualRequest, type ManualStockFormValues } from './schemas';

const MAX_QUERY_LENGTH = 50;
const MANUAL_FIELDS: FieldPath<ManualStockFormValues>[] = [
  'symbol', 'companyName', 'exchange', 'mic', 'currency', 'country', 'sector', 'industry', 'assetType',
];

const resultKey = (result: ProviderSearchResult) => `${result.symbol}:${result.mic}`;

function prefillFrom(result: ProviderSearchResult): Partial<ManualStockFormValues> {
  return {
    symbol: result.symbol, companyName: result.name, exchange: result.exchange, mic: result.mic,
    currency: result.currency, country: result.country ?? '', assetType: result.assetType ?? 'COMMON_STOCK',
  };
}

/** A typed ticker becomes the symbol, anything else the company name. */
function prefillFromQuery(query: string): Partial<ManualStockFormValues> {
  const text = query.trim();
  return /^[A-Za-z0-9][A-Za-z0-9.-]{0,19}$/.test(text) ? { symbol: text.toUpperCase() } : { companyName: text };
}

interface StepProps {
  loadHistory: boolean;
  loadHistoryControl: ReactNode;
  create: (request: CreateStockRequest) => Promise<CreateStockResponse>;
  onCreated: (response: CreateStockResponse) => void;
  onClose: () => void;
}

/** Phrasing content only, because it is rendered inside the radio's label. */
function ResultLabel({ result }: { result: ProviderSearchResult }) {
  const details = [result.exchange, result.mic, result.currency, result.country, result.instrumentType].filter(Boolean);
  return (
    <Box component="span" sx={{ display: 'block', py: 0.5 }}>
      <Typography component="span" sx={{ display: 'block', fontWeight: 700 }}>{result.symbol} · {result.name}</Typography>
      <Typography component="span" variant="body2" color="text.secondary" sx={{ display: 'block' }}>{details.join(' · ')}</Typography>
      {(result.alreadyListed || result.assetType === null) && (
        <Box component="span" sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
          {result.alreadyListed && <Chip component="span" size="small" label="Already listed" />}
          {result.assetType === null && <Chip component="span" size="small" variant="outlined" label="Unsupported instrument type" />}
        </Box>
      )}
    </Box>
  );
}

function ProviderSearchStep({ loadHistory, loadHistoryControl, create, onCreated, onClose, onManual }: StepProps & {
  onManual: (prefill: Partial<ManualStockFormValues>) => void;
}) {
  const navigate = useNavigate();
  const [input, setInput] = useState('');
  const query = useDebouncedValue(input, SEARCH_DEBOUNCE_MS).trim().slice(0, MAX_QUERY_LENGTH);
  const search = useProviderSearch(query);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const response = input.trim() && query ? search.data : undefined;
  const results = response?.supported ? response.results : [];
  const selected = results.find((result) => resultKey(result) === selectedKey && result.assetType && !result.alreadyListed) ?? null;
  const failure = submitError ? friendlyError(submitError) : null;
  const searchFailure = search.isError && input.trim() ? friendlyError(search.error) : null;

  const add = async () => {
    if (!selected) return;
    setSubmitError(null);
    setSubmitting(true);
    try {
      onCreated(await create({ symbol: selected.symbol, mic: selected.mic, loadHistory }));
    } catch (error) {
      setSubmitError(error);
    } finally {
      setSubmitting(false);
    }
  };

  const manualButton = (prefill: Partial<ManualStockFormValues>) => (
    <Button color="inherit" size="small" onClick={() => onManual(prefill)}>Enter details manually</Button>
  );

  return (
    <>
      <DialogContent>
        <Stack spacing={2}>
          <TextField
            autoFocus
            fullWidth
            label="Search the provider by symbol or company"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            slotProps={{
              htmlInput: { maxLength: MAX_QUERY_LENGTH },
              input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" aria-hidden /></InputAdornment> },
            }}
          />
          {search.isFetching && <LinearProgress aria-label="Searching the provider" />}
          {searchFailure && (
            <Alert severity={searchFailure.status === 429 ? 'warning' : 'error'} action={manualButton(prefillFromQuery(input))}>
              {searchFailure.message}
            </Alert>
          )}
          {response && !response.supported && (
            <Alert severity="info" action={manualButton(prefillFromQuery(input))}>
              {response.provider.name} cannot search for instruments. Enter the stock details manually.
            </Alert>
          )}
          {response?.supported && results.length === 0 && !search.isFetching && (
            <Alert severity="info" action={manualButton(prefillFromQuery(input))}>
              No instruments at {response.provider.name} match “{query}”.
            </Alert>
          )}
          {results.length > 0 && (
            <RadioGroup aria-label="Provider results" value={selected ? resultKey(selected) : ''}
              onChange={(event) => {
                setSelectedKey(event.target.value);
                setSubmitError(null);
              }}>
              {results.map((result) => (
                <FormControlLabel
                  key={resultKey(result)}
                  value={resultKey(result)}
                  disableTypography
                  control={<Radio />}
                  disabled={result.alreadyListed || result.assetType === null}
                  label={<ResultLabel result={result} />}
                  sx={{ alignItems: 'flex-start', mx: 0, py: 0.5, borderBottom: 1, borderColor: 'divider', '& .MuiRadio-root': { mt: 0.25 } }}
                />
              ))}
            </RadioGroup>
          )}
          {failure && (
            <Alert
              role="alert"
              severity={failure.code === 'PROVIDER_METADATA_UNAVAILABLE' ? 'warning' : 'error'}
              action={failure.code === 'STOCK_ALREADY_EXISTS' && selected ? (
                <Button color="inherit" size="small" onClick={() => { onClose(); navigate(adminPaths.stock(selected.symbol)); }}>
                  Open stock
                </Button>
              ) : failure.code === 'PROVIDER_METADATA_UNAVAILABLE' && selected ? manualButton(prefillFrom(selected)) : undefined}
            >
              {failure.message}
              {failure.code === 'PROVIDER_METADATA_UNAVAILABLE' && ' You can enter the details manually instead.'}
            </Alert>
          )}
          {loadHistoryControl}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button startIcon={<EditNoteOutlinedIcon />} onClick={() => onManual(selected ? prefillFrom(selected) : prefillFromQuery(input))}>
          Enter details manually
        </Button>
        <Box sx={{ flexGrow: 1 }} />
        <Button onClick={onClose} disabled={submitting}>Cancel</Button>
        <Button variant="contained" disabled={!selected} loading={submitting} onClick={() => void add()}>Add stock</Button>
      </DialogActions>
    </>
  );
}

function ManualEntryStep({ prefill, loadHistory, loadHistoryControl, create, onCreated, onClose, onBack }: StepProps & {
  prefill: ManualStockFormValues;
  onBack: () => void;
}) {
  const [submitError, setSubmitError] = useState<string | null>(null);
  const { control, handleSubmit, setError, formState: { isSubmitting } } = useForm<ManualStockFormValues>({
    resolver: zodResolver(manualStockSchema),
    defaultValues: prefill,
    mode: 'onTouched',
  });

  const submit = async (values: ManualStockFormValues) => {
    setSubmitError(null);
    try {
      onCreated(await create({ ...toManualRequest(values), loadHistory }));
    } catch (error) {
      const apiError = friendlyError(error);
      if (apiError.code === 'STOCK_ALREADY_EXISTS') {
        setError('symbol', { message: apiError.message }, { shouldFocus: true });
        return;
      }
      // Violations may be reported as `manual.companyName` or `companyName`.
      const violations = apiError.fieldErrors.flatMap(({ field, message }) => {
        const target = MANUAL_FIELDS.find((name) => name === field.replace(/^manual\./, ''));
        return target ? [{ target, message }] : [];
      });
      violations.forEach(({ target, message }) => setError(target, { message }));
      if (violations.length === 0) setSubmitError(apiError.message);
    }
  };

  const half = { xs: 12, sm: 6 } as const;
  return (
    <form noValidate onSubmit={handleSubmit(submit)} aria-label="Manual stock form">
      <DialogContent>
        <Stack spacing={2.5}>
          <Alert severity="info" variant="outlined">
            Enter the instrument as listed by its exchange. Market data is loaded from the configured provider.
          </Alert>
          {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
          <Grid container spacing={2}>
            <Grid size={half}><FormTextField control={control} name="symbol" label="Symbol" autoFocus autoComplete="off" /></Grid>
            <Grid size={half}><FormTextField control={control} name="companyName" label="Company name" autoComplete="off" /></Grid>
            <Grid size={half}><FormTextField control={control} name="exchange" label="Exchange" placeholder="e.g. NASDAQ" autoComplete="off" /></Grid>
            <Grid size={half}><FormTextField control={control} name="mic" label="MIC" placeholder="e.g. XNAS" autoComplete="off" /></Grid>
            <Grid size={half}><FormTextField control={control} name="currency" label="Currency" placeholder="e.g. USD" autoComplete="off" /></Grid>
            <Grid size={half}><FormTextField control={control} name="country" label="Country" placeholder="e.g. US" autoComplete="off" /></Grid>
            <Grid size={half}><FormTextField control={control} name="sector" label="Sector (optional)" autoComplete="off" /></Grid>
            <Grid size={half}><FormTextField control={control} name="industry" label="Industry (optional)" autoComplete="off" /></Grid>
            <Grid size={half}>
              <FormTextField control={control} name="assetType" label="Asset type" select>
                {ASSET_TYPES.map((type) => <MenuItem key={type} value={type}>{ASSET_TYPE_LABELS[type]}</MenuItem>)}
              </FormTextField>
            </Grid>
          </Grid>
          {loadHistoryControl}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button startIcon={<SearchIcon />} onClick={onBack} disabled={isSubmitting}>Back to provider search</Button>
        <Box sx={{ flexGrow: 1 }} />
        <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" variant="contained" loading={isSubmitting}>Add stock</Button>
      </DialogActions>
    </form>
  );
}

function AddStockFlow({ onClose }: { onClose: () => void }) {
  const createStock = useCreateStock();
  const notifyJob = useJobNotifier();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'search' | 'manual'>('search');
  const [prefill, setPrefill] = useState<ManualStockFormValues>(EMPTY_MANUAL_STOCK);
  const [loadHistory, setLoadHistory] = useState(true);

  const onCreated = ({ stock, job }: CreateStockResponse) => {
    const added = `Added ${stock.symbol} (${stock.companyName})`;
    notifyJob(job ? `${added}. Historical data load queued. ${jobReference(job.id)}` : added, job?.id);
    onClose();
    navigate(adminPaths.stock(stock.symbol));
  };

  const shared: StepProps = {
    loadHistory,
    loadHistoryControl: (
      <FormControlLabel
        control={<Checkbox checked={loadHistory} onChange={(event) => setLoadHistory(event.target.checked)} />}
        label="Load historical data now"
      />
    ),
    create: (request) => createStock.mutateAsync(request),
    onCreated,
    onClose,
  };

  return (
    <>
      <DialogTitle id="admin-add-stock-title">{mode === 'search' ? 'Add stock' : 'Add stock manually'}</DialogTitle>
      {mode === 'search' ? (
        <ProviderSearchStep {...shared} onManual={(values) => { setPrefill({ ...EMPTY_MANUAL_STOCK, ...values }); setMode('manual'); }} />
      ) : (
        <ManualEntryStep {...shared} prefill={prefill} onBack={() => setMode('search')} />
      )}
    </>
  );
}

/** Adds a stock to the catalogue: found through the provider's instrument search, or typed in manually. */
export function AddStockDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md" aria-labelledby="admin-add-stock-title">
      <AddStockFlow onClose={onClose} />
    </Dialog>
  );
}
