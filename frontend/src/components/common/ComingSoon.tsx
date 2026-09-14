import ConstructionOutlinedIcon from '@mui/icons-material/ConstructionOutlined';
import Card from '@mui/material/Card';
import { Disclaimer } from './Disclaimer';
import { EmptyState } from './EmptyState';
import { PageHeader } from './PageHeader';

interface ComingSoonProps {
  title: string;
  description: string;
}

/** Placeholder for features scheduled in a later delivery phase. */
export function ComingSoon({ title, description }: ComingSoonProps) {
  return (
    <>
      <PageHeader title={title} />
      <Card>
        <EmptyState icon={<ConstructionOutlinedIcon />} title="Coming in a later release" description={description} />
      </Card>
      <Disclaimer />
    </>
  );
}
