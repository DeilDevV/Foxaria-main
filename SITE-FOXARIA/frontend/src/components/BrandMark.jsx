export default function BrandMark({ compact = false }) {
  return (
    <div className={`brand-mark ${compact ? 'brand-mark-compact' : ''}`}>
      <span className="brand-mark-eye" />
      <span className="brand-mark-snarl" />
    </div>
  );
}
