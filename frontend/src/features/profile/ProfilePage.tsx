import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { toApiError } from '@/api/apiError';
import { useNotify } from '@/app/providers/notificationContext';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import { FormTextField } from '@/components/forms/FormTextField';
import { formatDate } from '@/utils/format';
import { useCurrentUserProfile, useUpdateProfile } from './hooks';

const schema = z.object({
  displayName: z.string().trim().min(1, { error: 'Enter your name' }).max(100, { error: 'Use at most 100 characters' }),
  timeZone: z.string().min(1, { error: 'Choose a time zone' }),
});
type FormValues = z.infer<typeof schema>;

function supportedTimeZones(): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: string) => string[] };
  return ['UTC', ...(intl.supportedValuesOf?.('timeZone') ?? [])];
}

export default function ProfilePage() {
  const profile = useCurrentUserProfile();
  const update = useUpdateProfile();
  const notify = useNotify();
  const timeZones = useMemo(() => supportedTimeZones(), []);
  const { control, handleSubmit, reset, setError, formState: { isSubmitting, isDirty } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { displayName: '', timeZone: 'UTC' },
  });

  useEffect(() => {
    if (profile.data) reset({ displayName: profile.data.displayName, timeZone: profile.data.timeZone });
  }, [profile.data, reset]);

  const submit = async (values: FormValues) => {
    try {
      const saved = await update.mutateAsync(values);
      reset({ displayName: saved.displayName, timeZone: saved.timeZone });
      notify({ message: 'Profile saved' });
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.code === 'INVALID_TIME_ZONE') setError('timeZone', { message: apiError.message });
      else setError('displayName', { message: apiError.message });
    }
  };

  return (
    <>
      <PageHeader title="Profile" subtitle="How you appear in StockLens." />
      {profile.isPending && <CardGridSkeleton count={1} label="Loading profile" />}
      {profile.isError && <ErrorState title="Unable to load your profile" error={profile.error} onRetry={() => void profile.refetch()} />}
      {profile.data && (
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Card>
              <CardContent>
                <Stack component="form" spacing={2.5} noValidate onSubmit={handleSubmit(submit)} aria-label="Profile form">
                  <FormTextField control={control} name="displayName" label="Display name" autoComplete="name" />
                  <Controller
                    name="timeZone"
                    control={control}
                    render={({ field, fieldState }) => (
                      <Autocomplete
                        options={timeZones}
                        value={field.value}
                        onChange={(_, value) => field.onChange(value ?? '')}
                        disableClearable
                        renderInput={(params) => (
                          <TextField {...params} label="Time zone" error={Boolean(fieldState.error)}
                            helperText={fieldState.error?.message ?? 'Used for dates and the start of your trading day'} />
                        )}
                      />
                    )}
                  />
                  <Stack direction="row" spacing={1}>
                    <Button type="submit" variant="contained" loading={isSubmitting} disabled={!isDirty}>Save changes</Button>
                    <Button disabled={!isDirty || isSubmitting}
                      onClick={() => reset({ displayName: profile.data.displayName, timeZone: profile.data.timeZone })}>
                      Discard
                    </Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <Card>
              <CardContent>
                <Typography variant="subtitle2" color="text.secondary">Email</Typography>
                <Typography sx={{ mb: 2 }}>{profile.data.email}</Typography>
                <Typography variant="subtitle2" color="text.secondary">Role</Typography>
                <Typography sx={{ mb: 2 }}>{profile.data.role === 'ADMIN' ? 'Administrator' : 'Member'}</Typography>
                <Typography variant="subtitle2" color="text.secondary">Member since</Typography>
                <Typography>{formatDate(profile.data.memberSince, 'long')}</Typography>
              </CardContent>
            </Card>
            <Alert severity="info" variant="outlined" sx={{ mt: 2 }}>
              Changing your email address or password is not available yet.
            </Alert>
          </Grid>
        </Grid>
      )}
    </>
  );
}
