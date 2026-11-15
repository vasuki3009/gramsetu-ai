// English text for the "who can apply" and "benefits" lines of the government schemes.
//
// The backend returns these two lines in Tamil only. In English mode the scheme card shows the text below instead,
// matched by the scheme's official link (unique per scheme and the same in both database seeds). A scheme that is
// not listed here simply keeps the Tamil text the backend sent.
//
// REVIEW: drafted from the existing Tamil text (same amounts, no new facts); please check the wording.
export const SCHEME_EN = {
  'https://pmkisan.gov.in': {
    eligibility: 'Small and marginal farmers who own land up to 2 hectares (about 5 acres)',
    benefits: '₹6,000 a year, paid directly to your bank account in 3 instalments of ₹2,000',
  },
  'https://pmfby.gov.in': {
    eligibility: 'All farmers who have taken a crop loan, or who join voluntarily',
    benefits:
      'Insurance if the crop is damaged by a natural disaster, pests or disease. For the Kar season (June–September) crop, the insurance premium is only 2%',
  },
  'https://www.tn.gov.in/scheme/data_view/11736': {
    eligibility: 'All farmers who hold a Tamil Nadu farmer identity card (Farmer ID)',
    benefits: '₹2 lakh insurance for death in an accident, ₹1 lakh for disability, and a pension of ₹1,000 a month',
  },
  'https://www.tangedco.gov.in': {
    eligibility: 'Registered farmers in Tamil Nadu who use a motor pump of up to 10 horsepower (HP)',
    benefits: '3,600 units of free electricity a year for agricultural motor pumps',
  },
  'https://soilhealth.dac.gov.in': {
    eligibility: 'All farmers in India',
    benefits:
      'Free soil testing every 2 years, with a personal recommendation on which fertiliser to use and how much',
  },
  'https://pmkmy.gov.in': {
    eligibility: 'Small and marginal farmers aged 18 to 40',
    benefits: 'A pension of ₹3,000 a month after the age of 60. The monthly contribution is only ₹55 to ₹200',
  },
  'https://pmkisan.gov.in/kcc': {
    eligibility: 'All farmers, sharecroppers and tenant farmers are eligible',
    benefits: 'Short-term crop loans of up to ₹3 lakh at just 4% interest, so you do not need high-interest loans',
  },
}
