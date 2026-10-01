import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import type { BaselineComparison, ForecastResult } from '@/models/forecast';
import { formatCurrency, formatDate, formatDateTime, formatNumber, formatPercent } from '@/utils/format';

const COMPARISON: Record<BaselineComparison, { label: string; color: 'success' | 'default' | 'warning' }> = {
  BETTER: { label: 'More accurate than the naive baseline', color: 'success' },
  SIMILAR: { label: 'Similar to the naive baseline', color: 'default' },
  WORSE: { label: 'Less accurate than the naive baseline', color: 'warning' },
  NOT_APPLICABLE: { label: 'This is the baseline model', color: 'default' },
};

function Stat({ label, value, hint }: { label: string; value: ReactNode; hint: string }) {
  return (
    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
      <Tooltip title={hint}>
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>{label}</Typography>
      </Tooltip>
      <Typography sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{value}</Typography>
    </Grid>
  );
}

/**
 * Walk-forward accuracy of exactly the configuration being shown. Labelled as historical performance: a model that
 * did well in the past is not guaranteed to do well next.
 */
export function ModelPerformancePanel({ result, unavailableReason }: { result: ForecastResult; unavailableReason?: string }) {
  const backtest = result.backtest;
  const byReturn = result.target === 'RETURN';
  const error = (value: number) => (byReturn ? formatPercent(value, { signed: false, digits: 2 }) : formatCurrency(value, result.currency));
  const ratio = (value: number | null) => formatPercent(value, { signed: false, digits: 1 });

  return (
    <Card component="section" aria-labelledby="backtest-heading">
      <CardHeader title="Historical backtest performance"
        subheader={`${result.modelName} · ${result.horizonTradingDays} trading days ahead · walk-forward validation`}
        slotProps={{ title: { id: 'backtest-heading', variant: 'h6', component: 'h2' } }} />
      <CardContent sx={{ pt: 0 }}>
        {!backtest ? (
          <Alert severity="info">{unavailableReason ?? 'A backtest is not available for this configuration.'}</Alert>
        ) : (
          <>
            <Grid container spacing={2}>
              <Stat label="MAE" value={error(backtest.metrics.mae)} hint="Mean absolute error of the forecasts" />
              <Stat label="RMSE" value={error(backtest.metrics.rmse)}
                hint="Root-mean-square error: like MAE, but penalises large misses more" />
              {backtest.metrics.mape !== null && (
                <Stat label="MAPE" value={ratio(backtest.metrics.mape)} hint="Mean absolute percentage error" />
              )}
              <Stat label="Direction accuracy"
                value={backtest.metrics.directionAccuracy === null ? 'n/a' : ratio(backtest.metrics.directionAccuracy)}
                hint={backtest.metrics.directionAccuracy === null
                  ? 'The model predicts no change, so it makes no up/down calls'
                  : `Correct up/down calls out of ${backtest.metrics.directionalCalls}. The price rose in `
                    + `${ratio(backtest.metrics.upMoveShare)} of cases, the score of always predicting "up".`} />
              <Stat label="Range coverage" value={ratio(backtest.metrics.intervalCoverage)}
                hint={`Share of outcomes inside the forecast range; nominally ${ratio(result.confidenceLevel)}`} />
              <Stat label="RMSE vs naive"
                value={backtest.metrics.skillVsNaive === null ? 'n/a' : formatPercent(-backtest.metrics.skillVsNaive, { digits: 1 })}
                hint="Change in RMSE compared with carrying the last close forward; negative is better" />
            </Grid>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, alignItems: 'center', mt: 2 }}>
              <Chip size="small" variant="outlined" label={COMPARISON[backtest.metrics.baselineComparison].label}
                color={COMPARISON[backtest.metrics.baselineComparison].color} />
              <Typography variant="body2" color="text.secondary">
                Evaluation period {formatDate(backtest.evaluationStart)} – {formatDate(backtest.evaluationEnd)} ·{' '}
                {formatNumber(backtest.observations, 0)} forecasts, one every {backtest.originStepDays} trading days ·
                each made with data up to its own date only
              </Typography>
            </Box>
          </>
        )}
        <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1.5 }}>
          Data last updated {formatDateTime(result.lastSyncedAt)} · Best historical backtest performance does not
          guarantee future accuracy.
        </Typography>
      </CardContent>
    </Card>
  );
}
