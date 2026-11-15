-- Dialect words
INSERT IGNORE INTO dialect_words (word, dialect, weight) VALUES
-- KONGU (Hosur / Coimbatore)
('ஆகுது','KONGU',0.90),('பண்ணலாம்','KONGU',0.85),
('வேணும்','KONGU',0.80),('நல்லாருக்கு','KONGU',0.90),
('என்ன பண்ணலாம்','KONGU',0.95),
('ஆகுதுடா','KONGU',0.92),('என்னாச்சு','KONGU',0.85),
('பாத்துக்கோ','KONGU',0.80),('சொல்லிட்டே','KONGU',0.75),
('நல்லாயிருக்கு','KONGU',0.88),('எப்படி ஆகுது','KONGU',0.87),
('பண்ணிட்டே','KONGU',0.82),('வேணுமா','KONGU',0.78),
-- MADURAI (Madurai / Dindigul)
('என்னாங்க','MADURAI',0.90),('ஆகுதுங்க','MADURAI',0.90),
('சொல்லுங்க','MADURAI',0.80),('குடுங்க','MADURAI',0.80),
('பண்றீங்களா','MADURAI',0.85),
('என்னாங்கடா','MADURAI',0.92),('வாங்குங்க','MADURAI',0.80),
('போங்க','MADURAI',0.75),('சரியா பண்ணுங்க','MADURAI',0.88),
('கேளுங்க','MADURAI',0.78),('பாருங்க','MADURAI',0.77),
('வருவீங்களா','MADURAI',0.83),('தெரியுமா','MADURAI',0.72),
-- NELLAI (Tirunelveli / Thoothukudi)
('செய்யணும்டா','NELLAI',0.95),('ஆகும்டா','NELLAI',0.90),
('போயிட்டான்','NELLAI',0.90),('சரியில்ல','NELLAI',0.85),
('பாரு','NELLAI',0.70),
('என்னடா','NELLAI',0.88),('பண்ணுவோம்டா','NELLAI',0.92),
('கேக்குறேன்டா','NELLAI',0.85),('வர்றேன்டா','NELLAI',0.80),
('சரிதான்டா','NELLAI',0.82),('தெரியுமாடா','NELLAI',0.79),
('போயிட்டேன்டா','NELLAI',0.88),('வந்திட்டான்டா','NELLAI',0.86);

-- Crop advisories (Hosur/Krishnagiri belt crops)
INSERT IGNORE INTO crop_advisories (disease_name, verified_solution, crop_type, source) VALUES
('Tomato Leaf Curl Virus','Remove infected leaves. Spray neem oil 5ml/L water. Control whiteflies with yellow sticky traps. Apply imidacloprid 0.5ml/L.','Tomato','TNAU 2023'),
('Mango Hopper','Spray imidacloprid 0.3ml/L or carbaryl 2g/L during flowering. Remove weeds around tree base.','Mango','TNAU 2023'),
('Groundnut Leaf Spot','Apply mancozeb 2.5g/L or chlorothalonil 2g/L. Maintain proper spacing for airflow.','Groundnut','TNAU 2023'),
('Turmeric Rhizome Rot','Treat seed rhizomes with Trichoderma 4g/kg. Improve drainage. Apply copper oxychloride 3g/L.','Turmeric','TNAU 2023'),
('Brinjal Shoot Borer','Remove and destroy affected shoots. Apply spinosad 0.75ml/L. Use pheromone traps.','Brinjal','TNAU 2023');

-- Government schemes (INSERT IGNORE is idempotent — safe to run on every startup)
INSERT IGNORE INTO government_schemes (scheme_name, eligibility, benefits, official_link) VALUES
('PM Kisan Samman Nidhi',
 'சிறிய மற்றும் குறு விவசாயிகள் — நிலம் 2 ஹெக்டேருக்கு குறைவாக உள்ளவர்கள்',
 'ஆண்டுக்கு ₹6,000 — ₹2,000 வீதம் 3 தவணைகளில் நேரடியாக வங்கி கணக்கில்',
 'https://pmkisan.gov.in'),

('Pradhan Mantri Fasal Bima Yojana',
 'பயிர் கடன் பெற்ற அல்லது தன்னார்வமாக சேரும் அனைத்து விவசாயிகளும்',
 'இயற்கை சீரழிவு, பூச்சி, நோய்களால் பயிர் சேதத்திற்கு காப்பீடு. கரீஃப் பயிருக்கு பிரீமியம் 2%',
 'https://pmfby.gov.in'),

('Uzhavar Padhukappu Thittam',
 'தமிழ்நாடு விவசாயி அடையாள அட்டை (விவசாயி ID) உடைய அனைவரும்',
 'விபத்து காப்பீடு ₹2 லட்சம், ஊனமுற்றால் ₹1 லட்சம், மாதாந்திர ஓய்வூதியம் ₹1,000',
 'https://www.tn.gov.in/scheme/data_view/11736'),

('Free Electricity for Farmers (Tamil Nadu)',
 'தமிழ்நாட்டில் 10 HP வரை பம்புசெட் பயன்படுத்தும் பதிவுசெய்யப்பட்ட விவசாயிகள்',
 'விவசாய பம்புசெட்டுகளுக்கு ஆண்டுக்கு 3,600 யூனிட்கள் இலவச மின்சாரம்',
 'https://www.tangedco.gov.in'),

('Soil Health Card Scheme',
 'இந்தியாவில் உள்ள அனைத்து விவசாயிகளும்',
 'ஒவ்வொரு 2 ஆண்டுகளுக்கும் இலவச மண் பரிசோதனை. உரம் பயன்பாட்டிற்கு தனிப்பட்ட பரிந்துரை',
 'https://soilhealth.dac.gov.in'),

('PM Kisan Maan Dhan Yojana',
 '18–40 வயது சிறிய மற்றும் குறு விவசாயிகள்',
 '60 வயதிற்கு பிறகு மாதம் ₹3,000 ஓய்வூதியம். சந்தா ₹55–₹200/மாதம்',
 'https://pmkmy.gov.in'),

('Kisan Credit Card',
 'அனைத்து விவசாயிகள், பங்குதாரர்கள், குத்தகை விவசாயிகள்',
 '₹3 லட்சம் வரை 4% வட்டியில் குறுகிய கால பயிர் கடன்',
 'https://pmkisan.gov.in/kcc');
