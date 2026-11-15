// Text for the home-page sections. Tamil first.
//
// REVIEW markers: strings tagged "REVIEW" were written by an AI model, not a native speaker.
// They are meant to be natural, farmer-friendly Tamil, but please have them checked.

export const LANDING = {
  tamil: {
    hero: {
      // REVIEW: alt text for the hero illustration
      alt: 'விவசாயி நெல் வயலில் நின்று தொலைபேசியில் தன் கேள்வியைக் கேட்கும் காட்சி',
      bubble: 'வணக்கம்!',
    },

    how: {
      title: 'எப்படி வேலை செய்கிறது?', // REVIEW
      steps: [
        {
          title: 'பேசுங்கள்', // REVIEW
          text: 'உங்கள் கேள்வியை தமிழில் சொல்லுங்கள் அல்லது எழுதுங்கள். பயிரின் படமும் எடுக்கலாம்.', // REVIEW
        },
        {
          title: 'புரிந்துகொள்கிறோம்', // REVIEW
          text: 'கொங்கு, மதுரை, நெல்லை, சென்னை என உங்கள் வட்டார தமிழை அடையாளம் காண்போம்.', // REVIEW
        },
        {
          title: 'ஆலோசனை', // REVIEW
          text: 'எளிய தமிழில் பதில் கிடைக்கும். விரும்பினால் குரலிலும் கேட்கலாம்.', // REVIEW
        },
      ],
    },

    chat: {
      title: 'WhatsApp-இலும் கேளுங்கள்',
      text: 'எழுத்து, குரல் செய்தி, பயிர் படம் என எதை வேண்டுமானாலும் அனுப்புங்கள்.', // REVIEW
      sample: 'மாதிரி உரையாடல்', // REVIEW
      botName: 'GramSetu',
      typing: 'GramSetu பதில் எழுதுகிறது',
      voiceNote: 'குரல் பதில்', // REVIEW
      // The sample is generic on purpose: no doses, and it ends by sending the farmer to the local office.
      question: 'என் தக்காளி இலைகள் சுருண்டு மஞ்சளாகுது. என்ன செய்யலாம்?', // REVIEW
      answer:
        'இது வெள்ளை ஈ மூலம் பரவும் இலை சுருள் நோயாக இருக்கலாம். பாதிக்கப்பட்ட இலைகளை அகற்றுங்கள்; வயலைச் சுத்தமாக வைத்திருங்கள். மருந்து போடுவதற்கு முன் உங்கள் உள்ளூர் வேளாண்மை அலுவலகத்தில் உறுதி செய்யுங்கள்.', // REVIEW
    },

    features: {
      title: 'உங்களுக்கான வசதிகள்', // REVIEW
      items: {
        voice: { title: 'குரல் உதவி', text: 'பேசினால் போதும்; தமிழில் பதில் வரும்.' }, // REVIEW
        crop: { title: 'பயிர் நோய்', text: 'இலையின் படம் எடுத்தால் என்ன நோய் என்று சொல்வோம்.' }, // REVIEW
        schemes: { title: 'அரசு திட்டங்கள்', text: 'உங்கள் நிலத்துக்குப் பொருந்தும் அரசு உதவிகள்.' }, // REVIEW
        calendar: { title: 'பயிர் நாட்காட்டி', text: 'இந்த மாதம் என்ன செய்யலாம் என்று பாருங்கள்.' }, // REVIEW
        dialect: { title: 'வட்டார தமிழ்', text: 'கொங்கு, மதுரை, நெல்லை, சென்னை பேச்சு வழக்கு புரியும்.' }, // REVIEW
      },
    },

    why: {
      title: 'ஏன் GramSetu?', // REVIEW
      items: [
        { title: 'வட்டார தமிழ் புரியும்', text: 'கொங்கு தமிழ் உட்பட நான்கு பேச்சு வழக்குகள்.' }, // REVIEW
        { title: 'WhatsApp-இல் கிடைக்கும்', text: 'புதிய செயலி எதுவும் தேவையில்லை.' }, // REVIEW
        { title: 'இலவசம்', text: 'கேள்வி கேட்பதற்குக் கட்டணம் இல்லை.' }, // REVIEW
        { title: 'தனியுரிமைக்கு முன்னுரிமை', text: 'கணக்கு தேவையில்லை; தொலைபேசி எண்கள் பதிவுகளில் மறைக்கப்படும்.' }, // REVIEW
      ],
    },

    footer: {
      lead: 'உழவனுக்கு AI துணை',
      about: 'தமிழில் பேசி, எழுதி அல்லது படம் எடுத்துக் கேளுங்கள்; உங்கள் வட்டார தமிழிலேயே பதில் பெறுங்கள்.', // REVIEW
      disclaimer: 'AI பதில்கள் பொது வழிகாட்டுதல் மட்டுமே. வேளாண் அலுவலரிடம் உறுதி செய்யுங்கள்.',
      githubLabel: 'GitHub-இல் பாருங்கள்', // REVIEW
      stackTitle: 'உருவாக்கத் தொழில்நுட்பம்', // REVIEW
      built: 'Built by Vasuki',
    },
  },

  english: {
    hero: {
      alt: 'A farmer standing in a paddy field, asking a question on a phone',
      bubble: 'Hello!',
    },

    how: {
      title: 'How it works',
      steps: [
        { title: 'Speak', text: 'Say or type your question in Tamil or English. You can also photograph your crop.' },
        { title: 'Understand', text: 'We recognise your regional Tamil: Kongu, Madurai, Nellai or Chennai.' },
        { title: 'Advise', text: 'You get a simple answer in Tamil or English, and you can listen to it too.' },
      ],
    },

    chat: {
      title: 'Ask on WhatsApp too',
      text: 'Send text, a voice note or a crop photo. Anything works.',
      sample: 'Sample chat',
      botName: 'GramSetu',
      typing: 'GramSetu is typing',
      voiceNote: 'Voice reply',
      question: 'My tomato leaves are curling and turning yellow. What can I do?',
      answer:
        'This may be leaf curl disease, which whiteflies spread. Remove the affected leaves and keep the field clean. Before you use any spray, confirm with your local agriculture office.',
    },

    features: {
      title: 'What you can do',
      items: {
        voice: { title: 'Voice help', text: 'Just speak, and the answer comes in Tamil or English.' },
        crop: { title: 'Crop disease', text: 'Photograph a leaf and we will tell you what the problem may be.' },
        schemes: { title: 'Government schemes', text: 'Help from the government that fits your land.' },
        calendar: { title: 'Crop calendar', text: 'See what to do on the farm this month.' },
        dialect: { title: 'Regional Tamil', text: 'Understands Kongu, Madurai, Nellai and Chennai speech.' },
      },
    },

    why: {
      title: 'Why GramSetu?',
      items: [
        { title: 'Understands regional Tamil', text: 'Four spoken dialects, including Kongu Tamil.' },
        { title: 'Works on WhatsApp', text: 'No new app to install.' },
        { title: 'Free', text: 'No charge for asking a question.' },
        { title: 'Privacy first', text: 'No account needed; phone numbers are masked in logs.' },
      ],
    },

    footer: {
      lead: 'An AI companion for farmers',
      about: 'Ask by voice, text or a crop photo, and get answers in your own regional Tamil or in English.',
      disclaimer: 'AI answers are general guidance. Confirm with a local agriculture officer.',
      githubLabel: 'View on GitHub',
      stackTitle: 'Built with',
      built: 'Built by Vasuki',
    },
  },
}

export const TECH_BADGES = [
  'Spring Boot',
  'React',
  'Tailwind CSS',
  'Vite',
  'Gemini',
  'Groq',
  'Sarvam AI',
  'Twilio',
  'Cloudinary',
]

export const GITHUB_URL = 'https://github.com/vasuki3009/gramsetu-ai'
