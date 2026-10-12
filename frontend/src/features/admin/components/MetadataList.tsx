import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { JsonValue } from '@/models/admin';
import { DetailList } from './DetailList';
import { formatJsonValue, humanizeKey, isStructured } from './metadataFormat';

/** Free-form key/value data (audit metadata, provider health details) as a readable list. */
export function MetadataList({ values, emptyText }: { values: Record<string, JsonValue>; emptyText: string }) {
  const entries = Object.entries(values);
  if (entries.length === 0) {
    return <Typography variant="body2" color="text.secondary">{emptyText}</Typography>;
  }
  return (
    <DetailList
      columns={2}
      items={entries.map(([key, value]) => ({
        label: humanizeKey(key),
        value: isStructured(value) ? (
          <Box component="pre" sx={{ m: 0, fontSize: '0.75rem', whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>
            {formatJsonValue(value)}
          </Box>
        ) : formatJsonValue(value),
      }))}
    />
  );
}
