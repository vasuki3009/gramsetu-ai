// Which Tamil the answer is written in. The caller only renders it for Tamil answers: an English answer has no dialect.
const LABELS = {
  KONGU: 'கொங்கு தமிழில் பதில்',
  MADURAI: 'மதுரை தமிழில் பதில்',
  NELLAI: 'நெல்லை தமிழில் பதில்',
  CHENNAI: 'சென்னை தமிழில் பதில்',
  STANDARD: 'பொது தமிழில் பதில்',
}

export default function DialectBadge({ dialect }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-full bg-paddy-soft px-4 py-1.5 text-small font-bold text-paddy-deep">
      <span>{LABELS[dialect] || LABELS.STANDARD}</span>
    </span>
  )
}
