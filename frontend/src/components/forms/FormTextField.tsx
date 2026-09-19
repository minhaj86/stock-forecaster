import TextField, { type TextFieldProps } from '@mui/material/TextField';
import { Controller, type Control, type FieldPath, type FieldValues } from 'react-hook-form';

type FormTextFieldProps<T extends FieldValues> = Omit<TextFieldProps, 'name' | 'value' | 'onChange' | 'error'> & {
  name: FieldPath<T>;
  control: Control<T>;
};

/** MUI text field bound to React Hook Form; validation messages appear as helper text. */
export function FormTextField<T extends FieldValues>({ name, control, helperText, ...props }: FormTextFieldProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...props}
          {...field}
          value={field.value ?? ''}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error?.message ?? helperText}
          fullWidth
        />
      )}
    />
  );
}
