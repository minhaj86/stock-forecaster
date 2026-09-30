import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Disclaimer } from '@/components/common/Disclaimer';
import { ErrorState } from '@/components/common/ErrorState';
import { ChartSkeleton } from '@/components/common/Skeletons';
import { FORECAST_DISCLAIMER, type ForecastSettings } from '@/models/forecast';
import { ForecastChart } from './ForecastChart';
import { ForecastControls } from './ForecastControls';
import { DEFAULT_SETTINGS } from './forecastOptions';
import { ForecastSummaryCards } from './ForecastSummaryCards';
import { historyShortfall, useForecast, useForecastModels } from './hooks';
import { InsufficientHistory } from './InsufficientHistory';
import { ModelExplanation } from './ModelExplanation';
import { ModelPerformancePanel } from './ModelPerformancePanel';

const BACKTEST_UNAVAILABLE = 'Not enough history to backtest';

/**
 * Forecast workspace for one stock: controls, chart with the uncertainty range, headline estimates, backtest
 * performance and a plain-language model explanation. Always ends with the forecasting disclaimer.
 */
export function ForecastPanel({ symbol }: { symbol: string }) {
  const [settings, setSettings] = useState<ForecastSettings>(DEFAULT_SETTINGS);
  const catalog = useForecastModels(symbol);
  const forecast = useForecast(symbol, settings);
  const result = forecast.data;
  const shortfall = historyShortfall(forecast.error);
  const caveats = result?.warnings.filter((warning) => !warning.startsWith(BACKTEST_UNAVAILABLE)) ?? [];
  const backtestUnavailable = result?.warnings.find((warning) => warning.startsWith(BACKTEST_UNAVAILABLE));

  return (
    <Stack spacing={3}>
      <Card component="section" aria-labelledby="forecast-heading">
        <CardHeader title="Forecast"
          subheader="A statistical estimate with an uncertainty range, not a prediction of what will happen"
          slotProps={{ title: { id: 'forecast-heading', variant: 'h6', component: 'h2' } }} />
        <CardContent sx={{ pt: 0 }}>
          <ForecastControls settings={settings} onChange={setSettings} models={catalog.data?.models}
            horizons={catalog.data?.defaults.horizons} />
          <Box sx={{ mt: 3 }}>
            {forecast.isFetching && result && <LinearProgress aria-label="Updating forecast" sx={{ mb: 1 }} />}
            {forecast.isPending && <ChartSkeleton height={360} label="Loading forecast" />}
            {shortfall && <InsufficientHistory symbol={symbol} shortfall={shortfall} />}
            {forecast.isError && !shortfall && (
              <ErrorState title="Unable to generate the forecast" error={forecast.error}
                onRetry={() => void forecast.refetch()} />
            )}
            {result && !forecast.isError && (
              <Stack spacing={2} sx={{ opacity: forecast.isPlaceholderData ? 0.6 : 1 }}>
                <ForecastSummaryCards result={result} />
                <ForecastChart result={result} />
                {caveats.length > 0 && (
                  <Alert severity="info">
                    <AlertTitle>Caveats</AlertTitle>
                    <Box component="ul" sx={{ pl: 2.5, m: 0 }}>
                      {caveats.map((warning) => <Typography component="li" variant="body2" key={warning}>{warning}</Typography>)}
                    </Box>
                  </Alert>
                )}
              </Stack>
            )}
          </Box>
        </CardContent>
      </Card>
      {result && !forecast.isError && (
        <>
          <ModelPerformancePanel result={result} unavailableReason={backtestUnavailable} />
          <ModelExplanation result={result}
            info={catalog.data?.models.find((model) => model.model === result.model)} />
        </>
      )}
      <Disclaimer text={result?.disclaimer ?? FORECAST_DISCLAIMER} />
    </Stack>
  );
}
