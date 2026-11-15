// Shared UI text, Tamil first. Screens keep their own feature strings; this file holds the
// text used by the shell, the home screen, the WhatsApp page and the Ask side panel.

export const STR = {
  tamil: {
    skip: 'முக்கிய பகுதிக்குச் செல்',
    langBtn: 'English',
    langLabel: 'Switch to English',
    tagline: 'உழவனுக்கு AI துணை',
    region: 'ஹோசூர் / கிருஷ்ணகிரி · தமிழ்நாடு',
    navLabel: 'முக்கிய பகுதிகள்',
    nav: {
      home: 'முகப்பு',
      ask: 'கேளுங்கள்',
      crop: 'பயிர் நோய்',
      schemes: 'திட்டங்கள்',
      calendar: 'நாட்காட்டி',
      whatsapp: 'WhatsApp',
    },
    wake: {
      title: 'சர்வர் எழுந்துகொண்டிருக்கிறது…',
      body: 'இலவச சர்வர் தூங்கியிருந்தால், முதல் பதிலுக்கு ஒரு நிமிடம் வரை ஆகலாம். காத்திருங்கள், பதில் வரும்.',
    },
    common: { retry: 'மீண்டும் முயற்சி', back: 'திரும்பு' },

    home: {
      greeting: 'வணக்கம்! என்ன உதவி வேண்டும்?',
      heroTitle: 'தமிழில் கேளுங்கள்',
      heroText:
        'பேசி, தட்டச்சு செய்து அல்லது பயிர் படம் எடுத்து கேளுங்கள். உங்கள் வட்டார தமிழிலேயே பதில் கிடைக்கும்.',
      ctaVoice: 'பேசி கேளுங்கள்',
      ctaType: 'தட்டச்சு செய்யுங்கள்',
      examplesTitle: 'இப்படி கேட்கலாம்',
      examples: [
        'தக்காளி இலை சுருண்டு போகுது, என்ன பண்ணலாம்?',
        'நெல் நடவுக்கு எந்த மாதம் நல்லது?',
        'இந்த மாதம் எந்த உரம் போட வேண்டும்?',
        'PM Kisan திட்டத்தில் எப்படி சேருவது?',
      ],
      tiles: {
        crop: { title: 'பயிர் நோய்', hint: 'இலையைப் படம் எடுத்து நோயை அறியுங்கள்' },
        schemes: { title: 'திட்டங்கள்', hint: 'உங்களுக்கான அரசு உதவிகள்' },
        calendar: { title: 'நாட்காட்டி', hint: 'இந்த மாதம் செய்ய வேண்டிய வேலைகள்' },
      },
      aboutTitle: 'தகவல்',
      aboutLead: 'GramSetu — உழவனுக்கு AI துணை',
      aboutItems: [
        'தமிழ் வட்டார குரல் கேள்வி-பதில் (கொங்கு / மதுரை / நெல்லை / சென்னை)',
        'புகைப்படம் மூலம் பயிர் நோய் கண்டறிதல்',
        'அரசு திட்டங்கள் தேடல்',
      ],
      aboutRegion: 'இலக்கு பகுதி: ஹோசூர் / கிருஷ்ணகிரி, தமிழ்நாடு',
      aboutDisclaimer: 'AI பதில்கள் பொது வழிகாட்டுதல் மட்டுமே. வேளாண் அலுவலரிடம் உறுதி செய்யுங்கள்.',
    },

    whatsapp: {
      title: 'WhatsApp-இல் கேளுங்கள்',
      hint: 'இலவசம் · குரல் செய்தி',
      pageIntro: 'எழுத்து, குரல் செய்தி அல்லது பயிர் புகைப்படத்தை WhatsApp-இல் அனுப்புங்கள். பதில் தமிழில் வரும்.',
      stepsTitle: 'இணைவது எப்படி',
      steps: [
        'கீழே உள்ள பொத்தானை அழுத்துங்கள்',
        'WhatsApp திறந்ததும் "Send" அழுத்துங்கள்',
        'இப்போது கேள்விகளை தமிழில் கேளுங்கள்!',
      ],
      dialects: 'கொங்கு, மதுரை, நெல்லை, சென்னை வட்டார தமிழ் புரியும்.',
      join: 'WhatsApp-இல் இணை',
      qr: 'கணினியில் இருந்தால், QR குறியீட்டை ஸ்கேன் செய்யுங்கள்',
      guide: 'முழு வழிகாட்டி',
      noLink: 'WhatsApp இணைப்பு இன்னும் அமைக்கப்படவில்லை. நிர்வாகியிடம் கேளுங்கள்.',
      sandboxTitle: 'குறிப்பு',
      sandbox:
        'இது சோதனை (Twilio sandbox) சேவை. சேர்ந்தவர்கள் மட்டுமே பேச முடியும்; சில நாட்களுக்குப் பிறகு மீண்டும் "join" அனுப்ப வேண்டியிருக்கலாம்.',
    },

    ask: {
      examplesTitle: 'இப்படி கேட்கலாம்',
      tipsTitle: 'குறிப்புகள்',
      tips: [
        'மைக் பொத்தானை அழுத்தி, அருகில் இருந்து மெதுவாகப் பேசுங்கள்.',
        'பயிர், இடம், பிரச்சனையை ஒரே வாக்கியத்தில் சொல்லுங்கள்.',
        'AI பதில் பொது வழிகாட்டுதல் மட்டுமே; வேளாண் அலுவலரிடம் உறுதி செய்யுங்கள்.',
      ],
      schemesTitle: 'பிரபல திட்டங்கள்',
      viewAll: 'அனைத்து திட்டங்கள்',
    },
  },

  english: {
    skip: 'Skip to main content',
    langBtn: 'தமிழ்',
    langLabel: 'தமிழுக்கு மாறு',
    tagline: 'An AI companion for farmers',
    region: 'Hosur / Krishnagiri · Tamil Nadu',
    navLabel: 'Main sections',
    nav: {
      home: 'Home',
      ask: 'Ask',
      crop: 'Crop',
      schemes: 'Schemes',
      calendar: 'Calendar',
      whatsapp: 'WhatsApp',
    },
    wake: {
      title: 'Waking up the server…',
      body: 'The free server sleeps when idle, so the first answer can take up to a minute. Please wait.',
    },
    common: { retry: 'Try again', back: 'Back' },

    home: {
      greeting: 'Hello! How can we help?',
      heroTitle: 'Ask in Tamil or English',
      heroText:
        'Speak, type or take a photo of your crop. Get answers in your own regional Tamil, or in English.',
      ctaVoice: 'Speak your question',
      ctaType: 'Type your question',
      examplesTitle: 'Try asking',
      examples: [
        'My tomato leaves are curling. What should I do?',
        'Which month is best for planting paddy?',
        'Which fertiliser should I use this month?',
        'How do I join the PM Kisan scheme?',
      ],
      tiles: {
        crop: { title: 'Crop disease', hint: 'Photograph a leaf to find the disease' },
        schemes: { title: 'Schemes', hint: 'Government help for you' },
        calendar: { title: 'Calendar', hint: 'What to do on the farm this month' },
      },
      aboutTitle: 'About',
      aboutLead: 'GramSetu — an AI companion for farmers',
      aboutItems: [
        'Regional Tamil voice Q&A (Kongu / Madurai / Nellai / Chennai)',
        'Crop disease detection from a photo',
        'Government scheme search',
      ],
      aboutRegion: 'Target region: Hosur / Krishnagiri, Tamil Nadu',
      aboutDisclaimer: 'AI answers are general guidance. Confirm with a local agriculture officer.',
    },

    whatsapp: {
      title: 'Ask on WhatsApp',
      hint: 'Free · voice notes too',
      pageIntro: 'Send text, a voice note or a crop photo on WhatsApp. Answers come back in Tamil.',
      stepsTitle: 'How to connect',
      steps: ['Press the button below', 'When WhatsApp opens, press "Send"', 'Now ask your questions in Tamil!'],
      dialects: 'Understands Kongu, Madurai, Nellai and Chennai Tamil.',
      join: 'Join on WhatsApp',
      qr: 'On a computer? Scan the QR code',
      guide: 'Full guide',
      noLink: 'The WhatsApp link is not set up yet. Please ask the administrator.',
      sandboxTitle: 'Good to know',
      sandbox:
        'This is a test service (Twilio sandbox). Only people who have joined can chat, and you may need to send "join" again after a few days.',
    },

    ask: {
      examplesTitle: 'Try asking',
      tipsTitle: 'Tips',
      tips: [
        'Press the mic and speak slowly, close to the phone.',
        'Say the crop, the place and the problem in one sentence.',
        'AI answers are general guidance; confirm with an agriculture officer.',
      ],
      schemesTitle: 'Popular schemes',
      viewAll: 'All schemes',
    },
  },
}
