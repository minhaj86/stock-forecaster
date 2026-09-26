import { zodResolver } from '@hookform/resolvers/zod';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { toApiError } from '@/api/apiError';
import { FormTextField } from '@/components/forms/FormTextField';

const schema = z.object({
  name: z.string().trim().min(1, { error: 'Enter a name' }).max(60, { error: 'Use at most 60 characters' }),
});
type FormValues = z.infer<typeof schema>;

interface WatchlistFormDialogProps {
  open: boolean;
  mode: 'create' | 'rename';
  initialName?: string;
  onClose: () => void;
  /** Persists the name; rejected promises are shown on the field. */
  onSubmit: (name: string) => Promise<unknown>;
}

export function WatchlistFormDialog({ open, mode, initialName = '', onClose, onSubmit }: WatchlistFormDialogProps) {
  const { control, handleSubmit, reset, setError, formState: { isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: initialName },
  });

  useEffect(() => {
    if (open) reset({ name: initialName });
  }, [open, initialName, reset]);

  const submit = async (values: FormValues) => {
    try {
      await onSubmit(values.name);
      onClose();
    } catch (error) {
      setError('name', { message: toApiError(error).message });
    }
  };

  const title = mode === 'create' ? 'Create watchlist' : 'Rename watchlist';
  return (
    <Dialog open={open} onClose={isSubmitting ? undefined : onClose} fullWidth maxWidth="xs" aria-labelledby="watchlist-form-title">
      <form noValidate onSubmit={handleSubmit(submit)}>
        <DialogTitle id="watchlist-form-title">{title}</DialogTitle>
        <DialogContent>
          <FormTextField control={control} name="name" label="Name" autoFocus sx={{ mt: 1 }} placeholder="e.g. Technology" />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
          <Button type="submit" variant="contained" loading={isSubmitting}>{mode === 'create' ? 'Create' : 'Save'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
