<div align="center">

<img src="docs/banner.png" alt="Wy Store" width="760">

**Kuzatuv va reklamasiz RuStore muqobili**

[![APK yuklab olish](https://img.shields.io/badge/APK%20yuklab%20olish-0B57D0?style=for-the-badge&logo=android&logoColor=white)](https://github.com/wyrtensi/wystore/releases/latest/download/wystore.apk)
[![Yuklashlar](https://img.shields.io/github/downloads/wyrtensi/wystore/total?style=for-the-badge&logo=github&logoColor=white&label=yuklashlar&labelColor=0B57D0&color=1F6FEB)](https://github.com/wyrtensi/wystore/releases)

[![Oxirgi reliz](https://img.shields.io/github/v/release/wyrtensi/wystore?style=flat-square&label=reliz&color=0B57D0)](https://github.com/wyrtensi/wystore/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/wyrtensi/wystore/build.yml?branch=main&style=flat-square&label=build)](https://github.com/wyrtensi/wystore/actions/workflows/build.yml)
[![Android 9.0+](https://img.shields.io/badge/Android-9.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#talablar)
[![Telegram-suhbat](https://img.shields.io/badge/Telegram-suhbat-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+_YytpJdDHgQ4OTYy)
[![MIT litsenziyasi](https://img.shields.io/github/license/wyrtensi/wystore?style=flat-square&label=litsenziya)](LICENSE)

[Loyiha sayti](https://wystore.ru/uz/)

[Русский](README.md) · [English](README.en.md) · [Українська](README.uk.md) · [Беларуская](README.be.md) · [Қазақша](README.kk.md) · **Oʻzbekcha** · [Latviešu](README.lv.md) · [简体中文](README.zh.md)

[Relizlar](https://github.com/wyrtensi/wystore/releases) · [Oʻzgarishlar tarixi](CHANGELOG.md) · [Maxfiylik](PRIVACY.md) · [Xavfsizlik](SECURITY.md) · [Loyiha haqida bayonot](DISCLAIMER.md) · [Litsenziya](LICENSE)

</div>

Wy Store — Android uchun RuStore katalogi klienti. U RuStore ilovalar doʻkoni koʻrsatadigan
ilovalarni koʻrsatadi, lekin ularni oʻzi yuklab oladi va tizim oʻrnatgichi orqali oʻzi oʻrnatadi,
hamda na akkaunt, na oʻrnatilgan RuStore klientini talab qiladi. Ikkinchi manba — GitHub relizlari:
shu tufayli roʻyxatga RuStore katalogida yoʻq narsalar ham tushadi.

Loyihaning oʻz serveri, akkauntlari, analitikasi va reklamasi yoʻq. Ilova faqat `rustore.ru`,
`github.com` va bu xizmatlarning oʻzi fayllarni tarqatadigan omborlarga murojaat qiladi.

Wy Store RuStore bilan bogʻliq emas va uning nomidan ish koʻrmaydi: bu yerda RuStore — maʼlumot
manbai. Tafsilotlar — [DISCLAIMER.md](DISCLAIMER.md) faylida.

| Bosh sahifa | Ilova sahifasi | Kutubxona |
|---|---|---|
| ![Bosh sahifa](docs/screenshots/home.png) | ![Ilova sahifasi](docs/screenshots/app-page.png) | ![Kutubxona](docs/screenshots/library.png) |

[Ekranlar boʻyicha tahlil](https://telegra.ph/Wy-Store-magazin-prilozhenij-kotoryj-ne-prosit-vojti-v-akkaunt-09-09) — ilova ishda qanday koʻrinishi va unda nima har doim ham ishlamasligi haqida (rus tilida).

## Imkoniyatlar

- **Bitta roʻyxatda ikki manba.** RuStore kartochkalari va tanlab olingan GitHub loyihalarining
  relizlari qidiruvda, bosh sahifada va boʻlimlarda yonma-yon turadi; har birida manbasi
  koʻrsatilgan. Oʻzingizning ochiq repozitoriyingiz havola orqali qoʻshiladi.
- **Katalog boʻlimlari.** RuStore boʻlimlari — bosh sahifada plitkalar koʻrinishida va barcha
  boʻlimlar bilan alohida ekranda. Sozlamalarda Wy Store oʻz boʻlimlar toʻplamiga oʻtish mumkin.
- **Oʻrnatishdan oldin APK tekshiruvi.** Paket nomi, versionCode, split-APK toʻplamining toʻliqligi
  va imzo sertifikati fayl oʻrnatgichga ketishidan oldin solishtiriladi. Mos kelmasa — sababi
  koʻrsatilgan rad javobi.
- **Fonda yangilanishlar.** Jadval boʻyicha tekshiruv va topilganini avtomatik yuklab olish.
  Wy Store oʻzi oʻrnatgan narsalar uchun Android 12 va undan yangi versiyalarda dialogsiz oʻrnatish
  mumkin, lekin kafolatlanmagan: tafsilotlar quyida.
- **Yuklamalar navbati.** Bazada saqlanadi, qayta yuklanish va aloqa uzilishidan omon chiqadi,
  yuklashni HTTP Range orqali uzilgan joyidan davom ettiradi.
- **Sharhlar va baholar.** RuStore ilovalari uchun — reyting, yulduzlar soni boʻyicha taqsimot va
  baho boʻyicha filtr.
- **Kutubxona.** Oʻrnatilgan ilovalar manba boʻyicha filtrlar bilan; yangilash va oʻchirish bitta
  roʻyxatdan.
- **Zaxira nusxa.** Sozlamalar, qabul qilingan ilovalar va qoʻshilgan repozitoriylarni bitta JSON
  fayl bilan eksport va import qilish.
- **Trafikni tejash.** Fondagi yuklamalar uchun «faqat Wi-Fi» rejimi; mobil internet orqali qoʻlda
  yuklashda ilova tasdiqlashni soʻraydi.
- **Interfeys.** Русский, English, Українська, Беларуская, Қазақша, Oʻzbekcha, Latviešu, 简体中文.
  Yorugʻ va qorongʻi mavzu, Material You.

## Oʻrnatish

1. [Oxirgi relizdan](https://github.com/wyrtensi/wystore/releases/latest) `wystore-<versiya>.apk`
   faylini yuklab oling.
2. Faylni oching. Android bir marta shu manbadan oʻrnatishga ruxsat soʻraydi.
3. Keyin Wy Store oʻzini oʻzi yangilaydi — u shu repozitoriydagi relizlarni kuzatib boradi.

Har bir relizda ikki fayl bor: `wystore-<versiya>.apk` va `wystore.apk`. Bu bir xil imzoli aynan
bitta yigʻma: ikkinchisi `releases/latest/download/wystore.apk` doimiy havolasi uchun kerak.

APK doimiy kalit bilan imzolangan, uning barmoq izini CI har bir nashrda chop etadi. Yuklab olingan
faylni tekshirish:

```bash
apksigner verify --print-certs wystore-<versiya>.apk
```

### Avvalgi versiyalardan 0.2.0 ga oʻtish

0.2.0 versiyasida ilova identifikatori oʻzgardi: `dev.wystore` → `app.wystore`. Android uchun bu
boshqa ilova, shuning uchun yangilanishni bir marta qoʻlda oʻrnatishga toʻgʻri keladi:

1. Eski Wy Store ilovasida: **Sozlamalar → Zaxira nusxa va koʻchirish → Eksport**, faylni saqlang.
2. Yangi APK ni oʻrnating.
3. Yangi Wy Store ilovasida: **Sozlamalar → Zaxira nusxa va koʻchirish → Import**, shu faylni
   tanlang. Sozlamalar, qabul qilingan ilovalar va qoʻshilgan GitHub repozitoriylari koʻchiriladi.
4. Eski versiyani oʻchiring, aks holda ikkita doʻkon bir xil ilovalarni tekshiraveradi.
5. Yangi ilovaga batareya optimallashtirishidan istisno bering va birinchi yangilanishda
   yangilanishlarni uzatishni tasdiqlang: ikkala ruxsat identifikatorga bogʻlangan va koʻchmaydi.

Keyin oʻzini oʻzi yangilash avvalgidek ishlaydi.

## Yangilanishlar qanday ishlaydi

Ilova yangilanishni topish va yuklab olishni har doim oʻzi uddalaydi. Ammo uni bitta ham bosishsiz
oʻrnatish hamma joyda ham chiqavermaydi va buni aniq tushuntirish kerak, chunki yoʻlda ikkita tasdiq
bor va ular bir-biridan mustaqil.

### Birinchisi: Android tizim oʻrnatgichi

Android 12 dan boshlab tizim ilovani oʻrnatgan tomonga uni oʻz oynasisiz yangilashga ruxsat beradi.
Shartlar toʻrtta va ular butunlay bajariladi yoki bajarilmaydi:

- bu yangilanish, birinchi oʻrnatish emas;
- Wy Store shu ilovaning oʻrnatgichi sifatida qayd etilgan;
- yangilanayotgan ilova API 33 (Android 13) yoki undan yangisiga moʻljallab yigʻilgan;
- ilovaning yangilanishlari boshqa egaga tegishli emas — masalan, Google Play ga.

Mos kelsa — tizim oynasi umuman koʻrsatilmaydi. Android 9, 10 va 11 da buni hech bir uchinchi
tomon doʻkoni, jumladan RuStore ham, qila olmaydi. Google Play istalgan versiyada qila oladi, lekin
sirni bilgani uchun emas: u tizimli imtiyozli ilova va oddiy ilovaga hech qachon berilmaydigan
`INSTALL_PACKAGES` ruxsatiga ega.

### Ikkinchisi: Google Play Protect

Agar telefonda Google xizmatlari boʻlsa, Play Protect ilgari koʻrmagan har bir APK ni tekshiradi va
istalgan sokin oʻrnatish ustidan **oʻzining** oynasini — «ilovani tekshiruvga yuborish» —
koʻrsatadi. U bosishni qancha kerak boʻlsa shuncha kutadi; unga javob berilmaguncha oʻrnatish
boʻlmaydi.

Play Protect ilovani emas, faylni eslab qoladi: aynan oʻsha APK ikkinchi marta indamay oʻtadi.
Bundan amaliy xulosa: kam tanilgan ilovaning yangi yigʻmasi uni birinchilardan boʻlib olgan odamdan
deyarli aniq tasdiq soʻraydi, Google allaqachon koʻrgan mashhur ilova esa — yoʻq. Google xizmatlari
yoʻq telefonlarda bu qadam umuman yoʻq.

Bu mulohaza emas, qurilmada koʻrinadigan narsa. Android 16 da tekshirilgan: yangilashda tizim oynasi
bironta ham chiqmadi, Play Protect oynasi esa har bir yangi faylda chiqdi va allaqachon
tekshirilganida chiqmadi.

### Bezaksiz xulosa

- **Root bilan** — hammasi va har doim sokin oʻrnatiladi, birinchi oʻrnatish ham: oʻrnatish tizim
  oʻrnatgichini chetlab oʻtadi, na Android, na Play Protect tomonidan oyna chiqadi. Sukut boʻyicha
  root oʻchirilgan.
- **Rootsiz** — omadga qarab. Koʻpincha ishlaydi, lekin kafolat yoʻq: soʻnggi soʻz Play Protect da,
  u esa Google shu aniq faylni koʻrgan-koʻrmaganiga bogʻliq.

Yuklab olish esa har qanday holatda va soʻroqsiz sodir boʻladi; bosish, agar kerak boʻlsa, — bu
oʻrnatishni tasdiqlash, qaytadan yuklab olish emas.

### Zanjir sozlamalari

Uchalasi ham sukut boʻyicha yoqilgan:

| Sozlama | Nima qiladi |
|---|---|
| Yangilanishlarni darhol yuklab olish | Topilgan yangilanish oʻzi yuklana boshlaydi. Fondagi tekshiruv bunda «faqat Wi-Fi» rejimini hurmat qiladi. |
| Yuklab olingandan keyin darhol oʻrnatish | Yuklab olingani qoʻshimcha bosishsiz oʻrnatishga ketadi. |
| Soʻroqsiz yangilash | Tizim ruxsat bergan joyda undan oʻz dialogini oʻtkazib yuborishni soʻrash. |

### Yana chegaralar

- Ilovaning birinchi oʻrnatilishi har doim tasdiqlanadi — root rejimidan tashqari.
- Boshqa doʻkon oʻrnatgan ilova tasdiq bilan yangilanadi. Uning yangilanishlarini kartochkadagi
  tugma orqali Wy Store ga berish mumkin: ilova ustidan qayta oʻrnatiladi, bir marta tasdiq bilan,
  shundan keyin oʻrnatish egasi Wy Store boʻladi.
- Agar katalogdagi versiya oʻrnatilganidan past boʻlsa yoki ilova boshqa kalit bilan imzolangan
  boʻlsa, ustidan oʻrnatishning hech qanday iloji yoʻq — Android rad etadi. U holda ilova oʻchirib,
  qaytadan oʻrnatishni taklif qiladi; ilova maʼlumotlari bunda, odatda, yoʻqoladi.
- Google Play dan oʻrnatilgan ilovalarga sukut boʻyicha tegilmaydi, toki bu aniq bir ilova uchun
  yoqilmaguncha.

## Xavfsizlik

- Oʻrnatishdan oldin paket nomi, versionCode, toʻplamda aynan bitta bazaviy APK borligi va imzo
  sertifikati tekshiriladi. Allaqachon oʻrnatilgan ilova uchun imzo oʻrnatilgan nusxa bilan
  solishtiriladi, versionCode esa oʻsib borishi kerak.
- Yuklamalar faqat ruxsat etilgan xostlardan boradi, qayta yoʻnaltirishlar aniq tahlil qilinadi.
- Yuklab olingan APK lar ilovaning shaxsiy katalogida yotadi va belgilangan saqlash muddati hamda
  joy limiti boʻyicha oʻchiriladi.
- Sokin oʻrnatish tekshiruvlarni zaiflashtirmaydi: begona imzoli APK bu rejimda ham rad etiladi.

Wy Store yetkazib berishni tekshiradi, ilovalar mazmunini emas. Oʻrnatilgan dastur nima qilishi —
uning ishlab chiquvchisiga savol, manba esa har bir kartochkada koʻrsatilgan.

Teshik topdingizmi — [SECURITY.md](SECURITY.md): nima zaiflik hisoblanadi, qayerga yozish kerak va
nega ochiq masalaga emas.

## Maxfiylik

- Na akkaunt, na roʻyxatdan oʻtish, na tizimga kirish.
- Analitika, reklama SDK lari va krash-reporterlar yoʻq.
- Loyihaning serveri yoʻq: soʻrovlar vositachilarsiz toʻgʻridan-toʻgʻri RuStore va GitHub ga boradi.
- Klient oʻzini `WyStore/<versiya>` deb tanishtiradi va RuStore ning `User-Token` ini yaratmaydi.
- Ilova saqlaydigan hamma narsa qurilmada qoladi. Zaxira nusxa sizning buyrugʻingiz bilan yaratiladi
  va siz koʻrsatgan joyga saqlanadi.

Toʻliq matn — [PRIVACY.md](PRIVACY.md) faylida.

## Ruxsatlar

| Ruxsat | Nima uchun |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Katalog va APK larni yuklash, «faqat Wi-Fi» rejimi uchun tarmoq turini tekshirish |
| `REQUEST_INSTALL_PACKAGES` | Yuklab olingan APK larni oʻrnatish |
| `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `ENFORCE_UPDATE_OWNERSHIP` | Wy Store oʻzi oʻrnatgan narsalarni dialogsiz yangilash |
| `REQUEST_DELETE_PACKAGES` | Ilovani kutubxonadan oʻchirish |
| `QUERY_ALL_PACKAGES` | Katalogni oʻrnatilganlari bilan solishtirish: versiyalar, holatlar, yangilanishlarni qidirish |
| `POST_NOTIFICATIONS` | Topilgan yangilanishlar va yuklash jarayoni haqida bildirishnomalar |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RUN_USER_INITIATED_JOBS` | Tizim yarim yoʻlda oʻldirmaydigan yuklash |
| `RECEIVE_BOOT_COMPLETED` | Qayta yuklashdan keyin tekshiruvlar jadvalini tiklash |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ixtiyoriy ravishda soʻraladi: istisnosiz fondagi tekshiruvlar jadval boʻyicha kelmaydi |

## Maʼlumot manbai va uning cheklovlari

Ilova RuStore ning ochiq veb-endpointlarini oʻqiydi — sayt brauzerga beradigan narsaning oʻzini. Bu
qoʻllab-quvvatlanadigan klient API emas va u ogohlantirishsiz oʻzgarishi mumkin. Integratsiya
`RuStoreSource` ichida izolyatsiya qilingan, shuning uchun manba tomonidagi oʻzgarish katalogdagi
buzilgan maʼlumot sifatida emas, format xatosi sifatida namoyon boʻladi.

Bepul ilovalar qoʻllab-quvvatlanadi. Pullik ilova, ichki xaridlar va RuStore da avtorizatsiya talab
qiladigan hamma narsa mavjud emas.

Endpointlar tahlili, HTTP 419 ning sababi va zaxira variantning xatti-harakati —
[docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md) faylida (rus tilida).

## Talablar

- **Android 9.0 (API 28)** yoki undan yangisi. Bu Wy Store ning oʻz talabi; katalogdagi ilovalarga
  koʻpincha yangiroq versiya kerak boʻladi va u har birining sahifasida koʻrsatilgan — yuklab
  olishdan oldin, keyin emas.
- Nomaʼlum ilovalarni oʻrnatishga ruxsat: Android uni birinchi oʻrnatishda soʻraydi.
- Root shart emas. Usiz tasdiqsiz oʻrnatish faqat yangilanishlar uchun, faqat Android 12 va undan
  yangisida va faqat uni Play Protect toʻxtatmaguncha mumkin. Root bilan hammasi sokin oʻrnatiladi,
  birinchi oʻrnatish ham. Tafsilotlar — «Yangilanishlar qanday ishlaydi» boʻlimida.
- Arxitektura va ekran zichligi avtomatik aniqlanadi: mos APK toʻplami tanlanadi.

## Savollar

**RuStore akkaunti kerakmi?** Yoʻq. Ilova uni yaratmaydi va undan foydalanishni bilmaydi.

**Wy Store RuStore klientining oʻrnini bosadimi?** Bepul ilovalarni oʻrnatish va yangilash uchun —
ha. Pullik kontent va xaridlar rasmiy klientda qoladi.

**Wy Store oʻchirilsa, ilovalarga nima boʻladi?** Hech narsa: ular tizim tomonidan oʻrnatilgan va
joyida qoladi. Faqat avtomatik yangilanishlar kelmay qoʻyadi.

**Oʻrnatilgan dasturlar roʻyxatiga kirish nima uchun kerak?** Katalogda haqiqiy holatni koʻrsatish
uchun: oʻrnatilgan, yangilanish mavjud, versiya mos. Roʻyxat hech qayerga yuborilmaydi.

**GitHub dan oʻz repozitoriyimni qoʻsha olamanmi?** Ha, GitHub boʻlimida havola orqali. Reliz
qoidalar boʻyicha tanlanadi: nightly teglari va yaroqli APK si yoʻq relizlar oʻtkazib yuboriladi.

## Yigʻish

JDK 17 yoki undan yangisi va Android SDK Platform 36 kerak.

```bash
./gradlew :app:assembleDebug
```

APK `app/build/outputs/apk/debug/app-debug.apk` da paydo boʻladi.

Testlar:

```bash
./gradlew :app:testDebugUnitTest
```

Instrumental testlar, ulangan qurilma yoki emulyator bilan:

```bash
./gradlew :app:connectedDebugAndroidTest
```

### Reliz imzosi

Imzo tekshiruvi Wy Store ning oʻziga ham tegishli, shuning uchun mahalliy yigʻilgan yigʻma nashr
etilgan reliz bilan yangilanmaydi va aksincha: Android uchun bular boshqa-boshqa ilovalar.

Kalit yaratish (parolni `keytool` oʻzi soʻraydi, uni buyruqda yozish shart emas):

```bash
keytool -genkeypair -v -keystore outputs/wystore-release.jks -alias wystore -keyalg RSA -keysize 4096 -validity 10000
```

Imzolash maʼlumotlari repozitoriy ildizidagi `keystore.properties` faylidan (fayl `.gitignore` da,
namuna — [keystore.properties.example](keystore.properties.example)) yoki `WYSTORE_KEYSTORE`,
`WYSTORE_KEYSTORE_PASSWORD`, `WYSTORE_KEY_ALIAS`, `WYSTORE_KEY_PASSWORD` muhit oʻzgaruvchilaridan
olinadi. Toʻrttasi ham kerak: birortasi yetishmasa, release-yigʻma imzolanmagan holicha qoladi,
indamay debug-kalit bilan imzolanmaydi.

### Nashr qilish

Avval [CHANGELOG.md](CHANGELOG.md) ga yangi versiya raqami ostida yozuv, keyin teg. GitHub dagi
reliz matni shu boʻlimdan olinadi, yozuvsiz teg esa yigʻishni yiqitadi.

```bash
git tag v0.2.0 && git push origin v0.2.0
```

Relizni CI teg boʻyicha yigʻadi — [.github/workflows/release.yml](.github/workflows/release.yml).
Repozitoriy sirlarida `WYSTORE_KEYSTORE_BASE64` (base64 dagi kalit fayli),
`WYSTORE_KEYSTORE_PASSWORD`, `WYSTORE_KEY_ALIAS` va `WYSTORE_KEY_PASSWORD` yotishi kerak. Workflow
APK imzolanganini tekshiradi va imzolanmagan boʻlsa, yiqiladi.

CI sirlaridagi imzo kaliti shu repozitoriydagi workflow ni oʻzgartira oladigan har qanday odamga
ochiq. Agar bu maqbul boʻlmasa, relizni mahalliy yigʻing va APK ni qoʻlda yuklang.

## Loyiha tuzilmasi

| Yoʻl | U yerda nima bor |
|---|---|
| `app/src/main/java/dev/wystore/data` | Manbalar, katalog, modellar, APK tekshiruvi |
| `app/src/main/java/dev/wystore/updates` | Yuklamalar navbati, oʻrnatish, rejalashtirgichlar |
| `app/src/main/java/dev/wystore/background` | Vorkerlar, bildirishnomalar va batareya siyosati |
| `app/src/main/java/dev/wystore/selfupdate` | Wy Store ning oʻzini yangilash |
| `app/src/main/java/dev/wystore/localization` | Xato kodlarini matnga oʻgirish |
| `app/src/main/java/dev/wystore/ui` | Compose ekranlari, mavzu, umumiy komponentlar |
| `app/src/test` | Yunit-testlar, jumladan manbadan olingan HTML-fiksturalar |
| `app/src/androidTest` | Instrumental testlar |

Manbalar katalogi `dev/wystore` boʻlib qoldi: bu Java paketining `namespace` i. Android uchun ilova
identifikatori alohida beriladi va u `app.wystore` ga teng.

## Qayta aloqa

Xatolar va takliflar — [Issues](https://github.com/wyrtensi/wystore/issues) ga. Muhokama —
[Telegram-suhbatda](https://t.me/+_YytpJdDHgQ4OTYy).

Xato haqidagi xabarga hisobotni ilova qilish yordam beradi: **Sozlamalar → Ilova haqida → Xato
haqida xabar uchun hisobot**. Unda Android versiyasi, qurilma modeli, root va ruxsatlar holati,
navbat va soʻnggi nosozliklar bor — akkauntlar, havolalar va fayl yoʻllarisiz.

## Litsenziya

MIT, [LICENSE](LICENSE) ga qarang.
