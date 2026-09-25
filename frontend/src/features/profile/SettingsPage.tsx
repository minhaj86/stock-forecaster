import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import SettingsBrightnessOutlinedIcon from '@mui/icons-material/SettingsBrightnessOutlined';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { PageHeader } from '@/components/common/PageHeader';
import { useAuth } from '@/features/auth/authContext';
import { useMarketDataProvider } from '@/features/stocks/hooks';
import { useColorMode, type ColorModePreference } from '@/theme/colorModeContext';

export default function SettingsPage() {
  const { preference, setPreference } = useColorMode();
  const auth = useAuth();
  const provider = useMarketDataProvider();

  return (
    <>
      <PageHeader title="Settings" />
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Card component="section" aria-labelledby="appearance-heading">
          <CardContent>
            <Typography id="appearance-heading" variant="h6" component="h2">Appearance</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Stored in this browser.</Typography>
            <ToggleButtonGroup
              exclusive
              value={preference}
              onChange={(_, next: ColorModePreference | null) => next && setPreference(next)}
              aria-label="Colour mode"
            >
              <ToggleButton value="light"><LightModeOutlinedIcon sx={{ mr: 1 }} />Light</ToggleButton>
              <ToggleButton value="dark"><DarkModeOutlinedIcon sx={{ mr: 1 }} />Dark</ToggleButton>
              <ToggleButton value="system"><SettingsBrightnessOutlinedIcon sx={{ mr: 1 }} />System</ToggleButton>
            </ToggleButtonGroup>
          </CardContent>
        </Card>
        <Card component="section" aria-labelledby="data-heading">
          <CardContent>
            <Typography id="data-heading" variant="h6" component="h2">Market data</Typography>
            <Typography variant="body2" color="text.secondary">
              {provider.data ? `${provider.data.name}: ${provider.data.description}` : 'Loading provider information…'}
            </Typography>
          </CardContent>
        </Card>
        <Card component="section" aria-labelledby="session-heading">
          <CardContent>
            <Typography id="session-heading" variant="h6" component="h2">Session</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Signing out ends your session on this device.
            </Typography>
            <Button variant="outlined" color="error" startIcon={<LogoutIcon />} onClick={() => void auth.logout()}>
              Sign out
            </Button>
          </CardContent>
        </Card>
      </Stack>
    </>
  );
}
