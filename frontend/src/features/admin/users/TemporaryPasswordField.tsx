import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import { useState, type Ref } from 'react';
import { useNotify } from '@/app/providers/notificationContext';
import { generatePassword } from './passwordGenerator';

interface TemporaryPasswordFieldProps {
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  inputRef?: Ref<HTMLInputElement>;
  error?: string;
}

/** Password input for administrator-issued passwords, with a secure generator, a visibility toggle and copying. */
export function TemporaryPasswordField({ value, onChange, onBlur, inputRef, error }: TemporaryPasswordFieldProps) {
  const notify = useNotify();
  const [visible, setVisible] = useState(false);

  const generate = () => {
    onChange(generatePassword());
    setVisible(true);
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      notify({ message: 'Temporary password copied to the clipboard', severity: 'info' });
    } catch {
      notify({ message: 'The browser did not allow copying. Select the password and copy it manually.', severity: 'warning' });
    }
  };

  return (
    <Box>
      <TextField
        label="Temporary password"
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        inputRef={inputRef}
        error={Boolean(error)}
        helperText={error ?? '12 to 72 characters. Share it with the user through a secure channel.'}
        autoComplete="new-password"
        fullWidth
        slotProps={{
          htmlInput: { spellCheck: false, autoCapitalize: 'off', autoCorrect: 'off' },
          input: {
            sx: { fontFamily: visible ? 'monospace' : undefined },
            endAdornment: (
              <InputAdornment position="end">
                <Tooltip title={visible ? 'Hide password' : 'Show password'}>
                  <IconButton edge="end" onClick={() => setVisible((current) => !current)}
                    aria-label={visible ? 'Hide password' : 'Show password'}>
                    {visible ? <VisibilityOffIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
                  </IconButton>
                </Tooltip>
              </InputAdornment>
            ),
          },
        }}
      />
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1 }}>
        <Button size="small" startIcon={<KeyOutlinedIcon />} onClick={generate}>Generate secure password</Button>
        <Button size="small" startIcon={<ContentCopyOutlinedIcon />} onClick={() => void copy()} disabled={!value}>
          Copy password
        </Button>
      </Box>
    </Box>
  );
}
