import TextField from '@mui/material/TextField';

interface ConfirmPhraseFieldProps {
  /** The exact, case-sensitive word to type, e.g. `DELETE`. */
  phrase: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

/** Typing a word before a destructive action prevents accidental confirmation. */
export function ConfirmPhraseField({ phrase, value, onChange, disabled }: ConfirmPhraseFieldProps) {
  return (
    <TextField
      label={`Type ${phrase} to confirm`}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      disabled={disabled}
      fullWidth
      autoComplete="off"
      helperText="Case-sensitive"
      slotProps={{ htmlInput: { spellCheck: false, autoCapitalize: 'off', autoCorrect: 'off' } }}
    />
  );
}
