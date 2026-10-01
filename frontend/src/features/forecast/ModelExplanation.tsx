import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import type { FittedParameter, ForecastModelInfo, ForecastResult } from '@/models/forecast';
import { formatCurrency, formatNumber, formatPercent } from '@/utils/format';

function formatFitted(parameter: FittedParameter, currency: string): string {
  switch (parameter.format) {
    case 'PERCENT': return formatPercent(parameter.value, { digits: 2 });
    case 'PRICE': return formatCurrency(parameter.value, currency);
    default: return formatPlain(parameter.value);
  }
}

/** Keeps the digits that matter: smoothing weights near the 0.0001/0.9999 bounds stay distinguishable from 0 and 1. */
function formatPlain(value: number): string {
  if (Number.isInteger(value)) return formatNumber(value, 0);
  const magnitude = Math.abs(value);
  const digits = magnitude >= 100 ? 1 : magnitude >= 1 ? 3 : magnitude >= 0.01 ? 4 : 6;
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value);
}

/** What the model did, in plain language, with its fitted values and known weaknesses. */
export function ModelExplanation({ result, info }: { result: ForecastResult; info?: ForecastModelInfo }) {
  return (
    <Card component="section" aria-labelledby="model-heading">
      <CardHeader title={`About this model: ${result.modelName}`}
        slotProps={{ title: { id: 'model-heading', variant: 'h6', component: 'h2' } }} />
      <CardContent sx={{ pt: 0 }}>
        <Typography>{result.modelSummary}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Fitted model: {result.specification}
        </Typography>
        {result.fitted.length > 0 && (
          <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: 'max-content 1fr', columnGap: 3, rowGap: 0.5, mt: 2 }}>
            {result.fitted.map((parameter) => (
              <Box key={parameter.key} sx={{ display: 'contents' }}>
                <Typography component="dt" variant="body2" color="text.secondary">{parameter.label}</Typography>
                <Typography component="dd" variant="body2" sx={{ m: 0, fontVariantNumeric: 'tabular-nums' }}>
                  {formatFitted(parameter, result.currency)}
                </Typography>
              </Box>
            ))}
          </Box>
        )}
        {info && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="body2"><b>Assumes:</b> {info.assumptions}</Typography>
            <Typography variant="body2"><b>Limitations:</b> {info.limitations}</Typography>
          </Box>
        )}
        {result.notes.length > 0 && (
          <Box component="ul" sx={{ pl: 2.5, mb: 0, mt: 1.5, color: 'text.secondary' }}>
            {result.notes.map((note) => <Typography component="li" variant="body2" key={note}>{note}</Typography>)}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
