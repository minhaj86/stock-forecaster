import Box from '@mui/material/Box';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import type { ForecastModelInfo, ForecastModelType, ForecastSettings, ForecastTarget, SmoothingVariant } from '@/models/forecast';
import {
  HORIZONS, LOOKBACK_OPTIONS, MODEL_NAMES, MODEL_ORDER, PLANNED_MODELS, VARIANT_OPTIONS, WINDOW_OPTIONS,
} from './forecastOptions';

interface ForecastControlsProps {
  settings: ForecastSettings;
  onChange: (settings: ForecastSettings) => void;
  /** From the model catalogue; until it loads, planned models are known statically. */
  models?: ForecastModelInfo[];
  horizons?: number[];
}

const AUTO = 'AUTO';

const toggleSx = { flexWrap: 'wrap', '& .MuiToggleButton-root': { px: 1.25, py: 0.25, fontWeight: 600 } } as const;

export function ForecastControls({ settings, onChange, models, horizons = HORIZONS }: ForecastControlsProps) {
  const info = (model: ForecastModelType) => models?.find((candidate) => candidate.model === model);
  const available = (model: ForecastModelType) => info(model)?.available ?? !PLANNED_MODELS.includes(model);
  const update = (patch: Partial<ForecastSettings>) => onChange({ ...settings, ...patch });

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-end' }}>
      <TextField
        select size="small" label="Forecast model" value={settings.model} sx={{ minWidth: 240 }}
        onChange={(event) => {
          if (event.target.value !== AUTO) update({ model: event.target.value as ForecastModelType });
        }}
      >
        <MenuItem value={AUTO} disabled>
          <ListItemText primary="Auto" secondary="Best historical model — coming soon" />
        </MenuItem>
        {MODEL_ORDER.map((model) => (
          <MenuItem key={model} value={model} disabled={!available(model)}>
            <ListItemText primary={info(model)?.name ?? MODEL_NAMES[model]}
              secondary={available(model) ? undefined : 'Coming soon'} />
          </MenuItem>
        ))}
      </TextField>

      {settings.model === 'MOVING_AVERAGE' && (
        <TextField select size="small" label="Moving-average window" value={settings.window} sx={{ minWidth: 180 }}
          onChange={(event) => update({ window: Number(event.target.value) })}>
          {WINDOW_OPTIONS.map((window) => <MenuItem key={window} value={window}>SMA {window}</MenuItem>)}
        </TextField>
      )}
      {settings.model === 'LINEAR_REGRESSION' && (
        <TextField select size="small" label="Trend lookback" value={settings.lookback} sx={{ minWidth: 200 }}
          onChange={(event) => update({ lookback: Number(event.target.value) })}>
          {LOOKBACK_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
        </TextField>
      )}
      {settings.model === 'EXPONENTIAL_SMOOTHING' && (
        <TextField select size="small" label="Smoothing variant" value={settings.variant} sx={{ minWidth: 200 }}
          onChange={(event) => update({ variant: event.target.value as SmoothingVariant })}>
          {VARIANT_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
        </TextField>
      )}

      <Box>
        <Typography variant="caption" color="text.secondary" component="p" id="forecast-horizon-label" sx={{ mb: 0.5 }}>
          Horizon (trading days)
        </Typography>
        <ToggleButtonGroup size="small" exclusive value={settings.horizon} aria-labelledby="forecast-horizon-label"
          onChange={(_, horizon: number | null) => horizon && update({ horizon })} sx={toggleSx}>
          {horizons.map((horizon) => (
            <ToggleButton key={horizon} value={horizon} aria-label={`${horizon} trading days`}>{horizon}D</ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>

      <Box>
        <Typography variant="caption" color="text.secondary" component="p" id="forecast-target-label" sx={{ mb: 0.5 }}>
          Target
        </Typography>
        <ToggleButtonGroup size="small" exclusive value={settings.target} aria-labelledby="forecast-target-label"
          onChange={(_, target: ForecastTarget | null) => target && update({ target })} sx={toggleSx}>
          <ToggleButton value="PRICE">Price</ToggleButton>
          <ToggleButton value="RETURN">Return</ToggleButton>
        </ToggleButtonGroup>
      </Box>
    </Box>
  );
}
