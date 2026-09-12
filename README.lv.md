<div align="center">

<img src="docs/banner-en.png" alt="Wy Store" width="760">

**RuStore alternatīva bez izsekošanas un reklāmas**

[![Lejupielādēt APK](https://img.shields.io/badge/Lejupiel%C4%81d%C4%93t%20APK-0B57D0?style=for-the-badge&logo=android&logoColor=white)](https://github.com/wyrtensi/wystore/releases/latest/download/wystore.apk)
[![Lejupielādes](https://img.shields.io/github/downloads/wyrtensi/wystore/total?style=for-the-badge&logo=github&logoColor=white&label=lejupiel%C4%81des&labelColor=0B57D0&color=1F6FEB)](https://github.com/wyrtensi/wystore/releases)

[![Jaunākais laidiens](https://img.shields.io/github/v/release/wyrtensi/wystore?style=flat-square&label=laidiens&color=0B57D0)](https://github.com/wyrtensi/wystore/releases/latest)
[![Būvējums](https://img.shields.io/github/actions/workflow/status/wyrtensi/wystore/build.yml?branch=main&style=flat-square&label=b%C5%ABv%C4%93jums)](https://github.com/wyrtensi/wystore/actions/workflows/build.yml)
[![Android 9.0+](https://img.shields.io/badge/Android-9.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#prasības)
[![Telegram čats](https://img.shields.io/badge/Telegram-%C4%8Dats-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+_YytpJdDHgQ4OTYy)
[![MIT licence](https://img.shields.io/github/license/wyrtensi/wystore?style=flat-square&label=licence)](LICENSE)

[Projekta vietne](https://wystore.ru/lv/)

[Русский](README.md) · [English](README.en.md) · [Українська](README.uk.md) · [Беларуская](README.be.md) · [Қазақша](README.kk.md) · [Oʻzbekcha](README.uz.md) · **Latviešu** · [简体中文](README.zh.md)

[Laidieni](https://github.com/wyrtensi/wystore/releases) · [Izmaiņu žurnāls](CHANGELOG.md) · [Privātums](PRIVACY.md) · [Drošība](SECURITY.md) · [Paziņojums par projektu](DISCLAIMER.md) · [Licence](LICENSE)

</div>

Wy Store ir RuStore kataloga klients Android platformai. Tas rāda tās pašas lietotnes, ko izdala
RuStore lietotņu veikals, bet lejupielādē un instalē tās pats, izmantojot Android sistēmas pakotņu
instalētāju, un tam nav vajadzīgs ne konts, ne RuStore klients. Otrs avots ir GitHub laidieni — tā
sarakstā nonāk arī tas, kā RuStore nav.

Projektam nav sava servera, kontu, analītikas un reklāmas. Lietotne vēršas tikai pie `rustore.ru`,
`github.com` un krātuvēm, no kurām šie servisi paši izdala savus failus.

Katalogs ir domāts lietotājiem Krievijā, tāpēc lielākā daļa satura ir krievu valodā; pats interfeiss
ir pieejams visās iepriekš uzskaitītajās valodās.

Wy Store nav saistīts ar RuStore un nedarbojas tā vārdā: RuStore šeit ir datu avots. Sk.
[DISCLAIMER.md](DISCLAIMER.md).

| Sākums | Lietotnes lapa | Bibliotēka |
|---|---|---|
| ![Sākums](docs/screenshots/en/home.png) | ![Lietotnes lapa](docs/screenshots/en/app-page.png) | ![Bibliotēka](docs/screenshots/en/library.png) |

[Apskats pa ekrāniem](https://telegra.ph/Wy-Store-magazin-prilozhenij-kotoryj-ne-prosit-vojti-v-akkaunt-09-09) — lietotne ekrānu pēc ekrāna un tas, kas tajā ne vienmēr strādā (krievu valodā).

## Iespējas

- **Divi avoti vienā sarakstā.** RuStore ieraksti un atlasīto GitHub projektu laidieni ir blakus
  meklēšanā, sākuma ekrānā un sadaļās; katram ir norādīts tā avots. Jebkuru publisku repozitoriju
  var pievienot ar saiti.
- **Kataloga sadaļas.** RuStore sadaļas kā elementi sākuma ekrānā un atsevišķā visu sadaļu lapā.
  Iestatījumos to vietā var pārslēgties uz Wy Store paša sadaļu kopu.
- **APK pārbaude pirms instalēšanas.** Pakotnes nosaukums, versionCode, split-APK komplekta pilnība
  un paraksta sertifikāts tiek pārbaudīti, pirms fails nonāk instalētājā. Neatbilstība nozīmē
  atteikumu ar norādītu iemeslu.
- **Atjauninājumi fonā.** Pārbaude pēc grafika un atrastā automātiska lejupielāde. Instalēšana bez
  dialoga ir iespējama Android 12 un jaunākās versijās tam, ko Wy Store instalējis pats, — bet tā
  nav garantēta; sk. zemāk.
- **Lejupielāžu rinda.** Glabājas datubāzē, pārdzīvo pārstartēšanu un savienojuma zudumu un
  turpinās no pārtrauktās vietas, izmantojot HTTP Range.
- **Atsauksmes un vērtējumi.** RuStore lietotnēm — reitings, sadalījums pa zvaigžņu skaitu un
  filtrs pēc vērtējuma.
- **Bibliotēka.** Instalētās lietotnes ar filtriem pēc avota; atjaunināšana un atinstalēšana no
  viena saraksta.
- **Rezerves kopija.** Iestatījumu, pārņemto lietotņu un pievienoto repozitoriju eksports un
  imports vienā JSON failā.
- **Trafika kontrole.** Tikai Wi-Fi režīms fona lejupielādēm; manuāla lejupielāde pa mobilo
  internetu vispirms prasa apstiprinājumu.
- **Interfeiss.** Русский, English, Українська, Беларуская, Қазақша, Oʻzbekcha, Latviešu, 简体中文 —
  gaišā un tumšā tēma, Material You.

## Instalēšana

1. Lejupielādējiet `wystore-<version>.apk` no
   [jaunākā laidiena](https://github.com/wyrtensi/wystore/releases/latest).
2. Atveriet failu. Android vienu reizi prasa atļauju instalēt no šī avota.
3. Pēc tam Wy Store atjaunina sevi pats: tas seko laidieniem šajā repozitorijā.

Katrā laidienā ir divi faili, `wystore-<version>.apk` un `wystore.apk`. Tas ir viens un tas pats
būvējums ar vienu un to pašu parakstu; otrs uztur darbībā pastāvīgo saiti
`releases/latest/download/wystore.apk`.

APK ir parakstīts ar pastāvīgu atslēgu, kuras nospiedumu CI izdrukā katrā publikācijā. Lai
pārbaudītu lejupielādēto failu:

```bash
apksigner verify --print-certs wystore-<version>.apk
```

### Pāreja uz 0.2.0 no agrākas versijas

Versijā 0.2.0 mainījās lietotnes identifikators: `dev.wystore` → `app.wystore`. Android tā ir cita
lietotne, tāpēc šis viens atjauninājums jāinstalē ar roku:

1. Vecajā Wy Store: **Iestatījumi → Rezerves kopija un pārnese → Eksportēt**, saglabājiet failu.
2. Instalējiet jauno APK.
3. Jaunajā Wy Store: **Iestatījumi → Rezerves kopija un pārnese → Importēt**, izvēlieties šo failu.
   Pārnesas iestatījumi, pārņemtās lietotnes un pievienotie GitHub repozitoriji.
4. Atinstalējiet veco versiju, citādi divi veikali dzīsies pēc vieniem un tiem pašiem
   atjauninājumiem.
5. Piešķiriet jaunajai lietotnei izņēmumu no akumulatora optimizācijas vēlreiz un pirmajā
   atjauninājumā apstipriniet atjauninājumu pārņemšanu: abi ir piesaistīti lietotnes
   identifikatoram un nepārnesas.

Turpmāk pašatjaunināšanās darbojas kā iepriekš.

## Kā darbojas atjauninājumi

Atrast atjauninājumu un lejupielādēt to lietotne vienmēr spēj pati. Instalēt to bez neviena
pieskāriena izdodas ne visur, un tas ir pelnījis precīzu atbildi, jo ceļā ir divi apstiprinājumi un
tie ir neatkarīgi viens no otra.

### Pirmais: Android sistēmas instalētājs

Kopš Android 12 sistēma ļauj tam, kurš lietotni instalējis, to atjaunināt, nerādot savu dialogu.
Nosacījumi ir četri, un tie izpildās visi kopā vai nemaz:

- tas ir atjauninājums, nevis pirmā instalēšana;
- Wy Store ir reģistrēts kā šīs lietotnes instalētājs;
- atjaunināmā lietotne ir būvēta ar API 33 (Android 13) vai jaunāku mērķa versiju;
- lietotnes atjauninājumiem nav cita īpašnieka — piemēram, Google Play.

Kad tie izpildās, sistēmas dialogs neparādās vispār. Android 9, 10 un 11 to nespēj neviens trešās
puses veikals, arī RuStore ne. Google Play to spēj jebkurā versijā — ne ar triku, bet tāpēc, ka tā
ir priviliģēta sistēmas lietotne ar atļauju `INSTALL_PACKAGES`, kuru parastai lietotnei nepiešķir
nekad.

### Otrais: Google Play Protect

Telefonā ar Google servisiem Play Protect pārbauda katru APK, ko iepriekš nav redzējis, un virs
jebkuras klusās instalēšanas rāda **savu** dialogu — „nosūtīt šo lietotni Google drošības
pārbaudei“. Tas gaida pieskārienu tik ilgi, cik vajag; kamēr uz to nav atbildēts, nekas netiek
instalēts.

Play Protect atceras failu, nevis lietotni: tas pats APK otrreiz paiet cauri klusi. Praksē tas
nozīmē: maz zināmas lietotnes svaigs būvējums gandrīz noteikti prasīs apstiprinājumu tam, kurš to
saņem vienā no pirmajiem, bet populāra lietotne, ko Google jau ir redzējis, — ne. Telefonos bez
Google servisiem šī soļa nav vispār.

Tas nav prātojums, bet tas, ko dara ierīce. Pārbaudīts Android 16: sistēmas dialogs atjauninājumam
neparādījās ne reizi, bet Play Protect dialogs parādījās katram jaunam failam un neparādījās
nevienam jau pārbaudītam.

### Godīgs kopsavilkums

- **Ar root** — viss instalējas klusi, vienmēr, arī pirmās instalēšanas: instalēšana iet garām
  sistēmas instalētājam, un neko nerāda ne Android, ne Play Protect. Pēc noklusējuma root ir
  izslēgts.
- **Bez root** — kā nu kuro reizi. Parasti tas strādā, bet garantijas nav: pēdējais vārds pieder
  Play Protect, un tas ieslēdzas atkarībā no tā, vai Google ir redzējis tieši šo failu.

Lejupielāde notiek jebkurā gadījumā un neko neprasa. Pieskāriens, kur tāds ir vajadzīgs, apstiprina
instalēšanu — tas nesāk lejupielādi no jauna.

### Iestatījumi, kas veido šo ķēdi

Visi trīs pēc noklusējuma ir ieslēgti:

| Iestatījums | Ko tas dara |
|---|---|
| Lejupielādēt atjauninājumus uzreiz | Atrasts atjauninājums sāk lejupielādēties pats. Fona pārbaudes ievēro tikai Wi-Fi režīmu. |
| Instalēt uzreiz pēc lejupielādes | Lejupielādēts atjauninājums nonāk instalētājā bez vēl viena pieskāriena. |
| Atjaunināt bez jautājumiem | Lūgt sistēmai izlaist savu dialogu tur, kur sistēma to atļauj. |

### Vēl ierobežojumi

- Pirmā instalēšana vienmēr tiek apstiprināta — izņemot root režīmā.
- Lietotne, ko instalējis cits veikals, atjauninās ar apstiprinājumu. Tās atjauninājumus var nodot
  Wy Store no pašas lietotnes lapas: tā vienu reizi tiek instalēta virsū, ar apstiprinājumu, pēc kā
  Wy Store ir reģistrētais instalētājs.
- Ja kataloga versija ir zemāka par instalēto vai lietotne ir parakstīta ar citu atslēgu, virsū
  neinstalēsies vispār nekas — Android atteiks. Tad lietotne piedāvā atinstalēt un instalēt no
  jauna; lietotnes dati pie tam parasti tiek zaudēti.
- No Google Play instalētās lietotnes netiek aiztiktas, kamēr tas nav ieslēgts konkrētai lietotnei.

## Drošība

- Pirms instalēšanas: pakotnes nosaukums, versionCode, tieši viens bāzes APK komplektā un paraksta
  sertifikāts. Jau instalētai lietotnei paraksts tiek salīdzināts ar instalēto kopiju, un
  versionCode ir jāpieaug.
- Lejupielādes ir ierobežotas ar atļauto hostu sarakstu, un pāradresācijas tiek apstrādātas
  nepārprotami.
- Lejupielādētie APK glabājas lietotnes privātajā krātuvē un tiek dzēsti pēc jūsu norādītā
  glabāšanas termiņa un izmēra ierobežojuma.
- Klusā instalēšana neatslābina nevienu no pārbaudēm: APK ar svešu parakstu tiek noraidīts arī tur.

Wy Store pārbauda piegādi, nevis pašas lietotnes. Ko dara instalētā programma, ir tās izstrādātāja
lieta, un katrā lapā ir nosaukts avots.

Atradāt caurumu? [SECURITY.md](SECURITY.md) apraksta, kas skaitās ievainojamība, kur to sūtīt un
kāpēc ne publiskā uzdevumā.

## Privātums

- Nav konta, nav reģistrācijas, nav pieteikšanās.
- Nav analītikas, reklāmas SDK vai avāriju ziņotāju.
- Nav sava servera: pieprasījumi iet tieši uz RuStore un GitHub, bez nekā pa vidu.
- Klients piesakās kā `WyStore/<version>` un nekad neizveido RuStore `User-Token`.
- Viss, ko lietotne glabā, paliek ierīcē. Rezerves kopija tiek izveidota pēc jūsu komandas un
  saglabāta tur, kur jūs norādāt.

Pilns teksts ir [PRIVACY.md](PRIVACY.md).

## Atļaujas

| Atļauja | Kāpēc |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Kataloga un APK lejupielāde, tīkla veida pārbaude tikai Wi-Fi režīmam |
| `REQUEST_INSTALL_PACKAGES` | Lejupielādēto APK instalēšana |
| `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `ENFORCE_UPDATE_OWNERSHIP` | Tā atjaunināšana bez dialoga, ko Wy Store instalējis pats |
| `REQUEST_DELETE_PACKAGES` | Atinstalēšana no bibliotēkas |
| `QUERY_ALL_PACKAGES` | Kataloga salīdzināšana ar instalēto: versijas, statusi, pieejamie atjauninājumi |
| `POST_NOTIFICATIONS` | Paziņojumi par atrastajiem atjauninājumiem un lejupielādes gaitu |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RUN_USER_INITIATED_JOBS` | Lejupielādes, ko sistēma nenobeigs pusceļā |
| `RECEIVE_BOOT_COMPLETED` | Pārbaužu grafika atjaunošana pēc pārstartēšanas |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Tiek pieprasīta pēc izvēles: bez izņēmuma fona pārbaudes pienāk tad, kad sistēmai ienāk prātā |

## Datu avots un tā ierobežojumi

Lietotne lasa RuStore publiskos tīmekļa galapunktus — to pašu, ko vietne izdala pārlūkam. Tas nav
atbalstīts klienta API, un tas var mainīties bez brīdinājuma. Integrācija ir izolēta
`RuStoreSource`, tāpēc izmaiņas avota pusē parādās kā formāta kļūda, nevis kā sabojāti dati
katalogā.

Atbalstītas ir bezmaksas lietotnes. Maksas lietotnes, pirkumi lietotnē un viss, kam vajadzīga
pieteikšanās RuStore, nav.

Galapunkti, HTTP 419 iemesls un rezerves varianta uzvedība ir aprakstīti failā
[docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md) (krievu valodā).

## Prasības

- **Android 9.0 (API 28)** vai jaunāka. Tā ir paša Wy Store minimālā prasība; kataloga lietotnēm
  bieži vajag ko jaunāku, un katras lietotnes lapā nepieciešamā versija ir norādīta pirms
  lejupielādes, nevis pēc tās.
- Atļauja instalēt nezināmas lietotnes: Android to prasa pirmajā instalēšanā.
- Root nav obligāts. Bez tā instalēšana bez apstiprinājuma ir iespējama tikai atjauninājumiem,
  tikai Android 12 un jaunākās versijās un tikai tik ilgi, kamēr to neaptur Play Protect. Ar root
  viss instalējas klusi, arī pirmās instalēšanas. Sīkāk sadaļā „Kā darbojas atjauninājumi“.
- ABI un ekrāna blīvums tiek noteikti automātiski, un tiek izvēlēts atbilstošais APK komplekts.

## Jautājumi

**Vai ir vajadzīgs RuStore konts?** Nē. Lietotne to ne izveido, ne prot izmantot.

**Vai Wy Store aizstāj RuStore klientu?** Bezmaksas lietotņu instalēšanai un atjaunināšanai — jā.
Maksas saturs un pirkumi paliek oficiālajam klientam.

**Kas notiek ar instalētajām lietotnēm, ja Wy Store tiek noņemts?** Nekas: tās instalēja sistēma,
un tās paliek. Apstājas tikai automātiskie atjauninājumi.

**Kāpēc tam vajadzīgs instalēto pakotņu saraksts?** Lai katalogā rādītu īstu statusu — instalēts,
pieejams atjauninājums, versijas sakrīt. Saraksts nekur netiek sūtīts.

**Vai var pievienot savu GitHub repozitoriju?** Jā, ar saiti GitHub sadaļā. Laidieni tiek izvēlēti
pēc noteikumiem: ritošie nightly tagi un laidieni bez izmantojama APK tiek izlaisti.

## Būvēšana

JDK 17 vai jaunāks un Android SDK Platform 36.

```bash
./gradlew :app:assembleDebug
```

APK nonāk `app/build/outputs/apk/debug/app-debug.apk`.

Testi:

```bash
./gradlew :app:testDebugUnitTest
```

Instrumentētie testi, ar pievienotu ierīci vai emulatoru:

```bash
./gradlew :app:connectedDebugAndroidTest
```

### Laidiena parakstīšana

Paraksta pārbaude attiecas arī uz pašu Wy Store, tāpēc lokāli uzbūvētu versiju publicēts laidiens
neatjauninās, un otrādi: Android tās ir dažādas lietotnes.

Izveidojiet atslēgu (`keytool` paroli prasa pats — nelieciet to komandā):

```bash
keytool -genkeypair -v -keystore outputs/wystore-release.jks -alias wystore -keyalg RSA -keysize 4096 -validity 10000
```

Paraksta dati nāk no `keystore.properties` repozitorija saknē (fails ir gitignore sarakstā; veidne —
[keystore.properties.example](keystore.properties.example)) vai no vides mainīgajiem
`WYSTORE_KEYSTORE`, `WYSTORE_KEYSTORE_PASSWORD`, `WYSTORE_KEY_ALIAS`, `WYSTORE_KEY_PASSWORD`.
Vajadzīgi visi četri: ja kāda no tām trūkst, release būvējums paliek neparakstīts, nevis tiek klusi
parakstīts ar debug atslēgu.

### Publicēšana

Vispirms ieraksts [CHANGELOG.md](CHANGELOG.md) zem jaunās versijas numura, tikai tad tags. GitHub
laidiena teksts tiek ņemts no šīs sadaļas, un tags bez ieraksta nogāž būvējumu.

```bash
git tag v0.2.0 && git push origin v0.2.0
```

Laidienu no taga būvē CI — [.github/workflows/release.yml](.github/workflows/release.yml).
Repozitorija noslēpumos jāglabājas `WYSTORE_KEYSTORE_BASE64` (atslēgas fails base64 formātā),
`WYSTORE_KEYSTORE_PASSWORD`, `WYSTORE_KEY_ALIAS` un `WYSTORE_KEY_PASSWORD`. Workflow pārbauda, vai
APK ir parakstīts, un krīt, ja nav.

Paraksta atslēga CI noslēpumos ir pieejama ikvienam, kas šajā repozitorijā var mainīt workflow. Ja
tas nav pieņemami, būvējiet laidienus lokāli un augšupielādējiet APK ar roku.

## Projekta struktūra

| Ceļš | Kas tur atrodas |
|---|---|
| `app/src/main/java/dev/wystore/data` | Avoti, katalogs, modeļi, APK pārbaude |
| `app/src/main/java/dev/wystore/updates` | Lejupielāžu rinda, instalēšana, plānotāji |
| `app/src/main/java/dev/wystore/background` | Workeri, paziņojumu un akumulatora politika |
| `app/src/main/java/dev/wystore/selfupdate` | Paša Wy Store atjauninājumi |
| `app/src/main/java/dev/wystore/localization` | Tipizētu kļūdu kodu pārvēršana tekstā |
| `app/src/main/java/dev/wystore/ui` | Compose ekrāni, tēma, koplietotie komponenti |
| `app/src/test` | Vienībtesti, tostarp no avota noņemtas HTML fikstūras |
| `app/src/androidTest` | Instrumentētie testi |

Pirmkoda koks joprojām ir `dev/wystore`: tā ir Java pakotnes `namespace`. Android lietotnes
identifikators tiek iestatīts atsevišķi un ir `app.wystore`.

## Atsauksmes

Kļūdas un ieteikumi — [Issues](https://github.com/wyrtensi/wystore/issues); diskusija notiek
[Telegram čatā](https://t.me/+_YytpJdDHgQ4OTYy).

Ar pievienotu diagnostikas atskaiti kļūdas ziņojumu ir vieglāk apstrādāt: **Iestatījumi → Par
lietotni → Diagnostikas atskaite**. Tajā ir Android versija, ierīces modelis, root un atļauju
statuss, rinda un pēdējās kļūmes — bez kontiem, saitēm un failu ceļiem.

## Licence

MIT, sk. [LICENSE](LICENSE).
