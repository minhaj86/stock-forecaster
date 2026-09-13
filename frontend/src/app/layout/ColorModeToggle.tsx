import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { useColorMode } from '@/theme/colorModeContext';

export function ColorModeToggle() {
  const { mode, setPreference } = useColorMode();
  const next = mode === 'dark' ? 'light' : 'dark';
  return (
    <Tooltip title={`Switch to ${next} mode`}>
      <IconButton onClick={() => setPreference(next)} aria-label={`Switch to ${next} mode`}>
        {mode === 'dark' ? <LightModeOutlinedIcon /> : <DarkModeOutlinedIcon />}
      </IconButton>
    </Tooltip>
  );
}
