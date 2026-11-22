# Medicine recognition and the doctor's brief (task 71)

The patient photographs a package, confirms the medicine, says separately how much they took and when. The doctor
sees a short brief in the visit screen: name, version and form, the official use, every active substance per unit,
the intake as reported, and a link to the official record.

## Flow

**Patient** (Medicines → *Recognise medicine*):

1. *Bought in* — the country of purchase, chosen by hand. The app language and the language of the package do not
   decide the market.
2. *Take photo* (camera, Android) or *Choose image* (all platforms, works without a camera). *Type what's printed on
   the package* or *Describe it in my words* are always there.
3. The phone reads barcodes and text on the device (ML Kit on Android, ZXing barcodes on Desktop). Only the codes and
   the text go to the server's `/catalog/resolve`, never the photo or anything about the person.
4. The answer is one of:
   - **exact**: one registry version. The card shows the exact name and version, the form, every active substance
     with its amount per unit, the pack when the code found one, the source with its date and a link. *Confirm*.
   - **candidates**: several versions. Each shows what sets it apart ("500 mg" / "1000 mg", Forte, the form), with a
     question: which strength is printed, or a photo of that part. The app never picks a strength itself.
   - **conflict**: the barcode names one product and the label another. The patient is asked for a new photo.
   - **too little**: the patient is asked for a photo of the foil or the box, to type the label, or to use their own words.
5. After *Confirm*, a separate step asks *What did you take?*: an amount in the registry's counting unit (sachet,
   tablet, ml …), and *Now* / *Today* / *Yesterday* with a time. A photo of a box is not an intake, and the pack size
   never becomes the amount. *I didn't take it — only add it to my list* is offered too.

**Patient's own words**: free text ("cold powder, can't remember the name") and, for a tea or supplement, the
ingredients printed on its label. These are saved as written and never matched to a registry.

**Doctor** (visit screen → Entries → Medications, and the original-entry sheet):

```
FERVEX ADULTES · granulés pour solution buvable
Ce médicament est indiqué chez l’adulte et l’enfant à partir de 50 kg (environ 15 ans) dans le traitement au cours
des rhumes, rhinites, rhinopharyngites et des états grippaux.
Per sachet: acide ascorbique 200 mg, paracétamol 500 mg, maléate de phéniramine 25 mg.
Patient reports: 1 sachet, today at 08:30.
Source: official product information, France (BDPM), data of 2026-11-22   (link to the product page)
```

- The indication is quoted from the registry, not rewritten and with no LLM: for France, the sentence after the
  heading of leaflet section 1 on the official product page; for Switzerland, Swissmedic's field "Anwendungsgebiet".
  If the source has none, the line is left out.
- Substance names, numbers and units are printed from the structured fields, in the registry's own language
  (French names for France, Latin names for Switzerland).
- An entry in the patient's own words shows «their words» with the badge *Patient's own words*, any label ingredients
  marked as *not checked against a registry*, and no composition.
- Nothing is inferred about why the medicine was taken, and nothing is advised.

**Stored**: the confirmed card is a snapshot inside the entry (`Medication.product`: name, brand, form, ingredients,
unit, pack, GTIN, indication, source id, URL, date), plus `intake` (amount, unit, time). A later catalog update never
changes an old entry. The entry id is fixed when the card appears, so a second tap on *Save* records nothing new. The
entry lives in the patient's encrypted record and reaches the doctor only through the existing handover, with the
same access rules as every other entry.

## Matching rules (`core/.../catalog/Catalog.kt`)

- Barcodes: EAN-13, UPC-A, GTIN-14, GS1 DataMatrix / GS1-128 with AI (01). GTINs are compared as strings: check
  digit verified, GTIN-14 with a leading 0 and UPC-A are reduced to the 13-digit form, leading zeros are kept.
- A code a registry knows gives that pack exactly, unless the label clearly names another brand or a strength
  (in mg) the product does not have; then the answer is a **conflict**.
- An unknown code falls back to the printed text.
- Text: folded for search (accents, ß, Ё/Й, decimal comma → dot); the original text stays for the person to compare.
  The brand word must be on the label. Variant words (Forte, Max, Junior, Adultes, Enfants, Sans sucre, retard, LP,
  SR, XR …) printed on the box must be part of the product. Printed strengths must match the registry amounts.
- A strength never chosen: if no strength is readable and the brand's versions differ in strength, the answer is
  **candidates**.

## Sources and real coverage

| Market | Source | What it gives | Status |
|---|---|---|---|
| Switzerland | Swissmedic "Zugelassene Packungen" (xlsx, public) | Each authorised pack. The GTIN is built as `7680` + authorisation + pack code + check digit. Active substances, full composition with "pro compresso / pro charta / pro 5 ml", "Anwendungsgebiet" | **imported**: 10,308 versions (data of 2026-10-31) |
| Switzerland | Refdata SAI monthly export (`SAI-Packungen.XML`) | GTINs in trade, the article names printed on boxes (DE/FR) | **imported**: names for 9,162 versions |
| Switzerland | swissmedicinfo (AIPS) | Official professional information | **linked** per authorisation (`ShowText.aspx?textType=FI&authNr=…`); texts are not imported |
| France | BDPM: `CIS_bdpm.txt`, `CIS_COMPO_bdpm.txt`, `CIS_CIP_bdpm.txt` joined by CIS | Products, substances with dosage and reference unit ("un sachet"), CIP13 packs | **imported**: 15,845 products (files of 2026-11-22) |
| France | BDPM product page `/medicament/{CIS}/extrait` | Official indication sentence | **fetched on demand** by the server, cached in `indications-fr.json` |
| EU/EEA | EMA Article 57 product data (xlsx) | Name, active substances, route, country of authorisation | **imported**: 153,932 rows, France excluded. **No strength per unit**, so these cards say "check the strength" and never show amounts |
| Ukraine | Державний реєстр лікарських засобів: the State Service's `reestr.csv` (Windows-1251), the file behind the MoH open-data set on data.gov.ua | Records with the per-unit composition sentence ("1 таблетка містить парацетамолу 500 мг, …"), pharmacotherapeutic group, registration number, link to the record's official instruction file | **imported**: 13,690 records (amounts split for 6,964; the others show the sentence's parts as written) |
| Russia | ГРЛС one-file export (`GetGRLS.ashx`, zip of xlsx lists; the link carries a fresh FileGUID and is read from the register page each time) | Valid, EAEU, foreign-pack and pending registrations; release forms with doses paired with the international names in order; pharmacotherapeutic group | **imported**: 41,994 versions (all amounts for 37,067), data of 2026-11-20. **No per-record link**: the card names the registration number and links to the register search |
| Belarus | rceth.by Refbank public search, paged like its own page (national + EAEU registration, 100 rows a page, one request a second); rows kept in `rceth-rows.tsv` | Trade and international names, release forms with doses, holder, registration number, link to the record page | **imported**: 5,705 versions (all amounts for 4,946). No download or API is offered, so this depends on the page layout; no indication field |
| Turkey | TİTCK "Ruhsatlı Beşeri Tıbbi Ürünler Listesi" (weekly xlsx; the newest link is read from titck.gov.tr/dinamikmodul/85) | One row per pack with its **barcode** (GTIN 869…), name with strength, form and pack, active substances, ATC, license number, suspension status. Strength per unit is read from the official name and paired with the substances | **imported**: 16,738 versions with 23,049 barcodes (all amounts for 10,261; 598 suspended, kept and marked), list of 2026-11-13. The card links to the agency's KÜB/KT (SmPC / leaflet) search for the product. titck.gov.tr is signed by the Turkish TUBITAK Kamu SM root, which the JDK does not ship: downloads verify against the JDK roots or the operating system's roots |

`GET /catalog/coverage` returns the loaded sources with their dates and their load status.

Ukraine, Russia and Belarus publish **no barcodes**: packages from these markets are found by the printed name and
strength only, and Android's on-device text recognition (ML Kit, Latin script) does not read Cyrillic. On these
packages the patient types the name; the registry search then works as for any other market.

Licences: BDPM allows reuse when the source is named and the data is not altered. Swissmedic lists are public.
Refdata SAI is used under its terms of use. EMA Article 57 data is public; its pharmacovigilance contacts are not
stored.

## Updating the catalog

```bash
# download the official files and rebuild every source (about 30 s with a fast connection)
EASYPAPER_CATALOG_DIR=server/data/catalog ./gradlew :server:importCatalog --args="fr ch eu ua ru by tr"
# one source, or files already downloaded
./gradlew :server:importCatalog --args="fr --from /path/to/dumps"   # expects /path/to/dumps/fr/CIS_*.txt
```

- One gzip'd snapshot per source: `fr-bdpm.json.gz`, `ch-swissmedic.json.gz`, `eu-ema-art57.json.gz`, `ua-drlz.json.gz`,
  `ru-grls.json.gz`, `by-rceth.json.gz`, `tr-titck.json.gz`. The Russian and Ukrainian hosts drop connections now and then: every download
  is retried up to 6 times. Each holds
  the registry's record id, country, URL, data date and import time.
- Each run rebuilds a source from its files and replaces its snapshot atomically, keyed by registry ids, so a repeated
  import never duplicates anything. A failed source keeps its previous snapshot; the others are still imported.
- The server loads every snapshot on its own at start (restart after an import). A broken snapshot is reported in the
  status and the other sources keep answering.

## Reproducing the scenario

1. Import the catalog (above) and start the server with the same `EASYPAPER_CATALOG_DIR` and, for example,
   `PORT=8099 ./gradlew :server:run`.
2. In the patient app's developer menu, set the server to `http://<host>:8099`. Open a request with a Medicines widget
   (the demo plan has one) → Medicines → *Recognise medicine*.
3. Choose *France*, then *Choose image* with a photo of a FERVEX ADULTES box (barcode 3400932705917). On Android the
   camera and text recognition work too. On Desktop, *Type what's printed on the package*: `FERVEX ADULTES`.
4. Check the card, tap *Confirm*, keep *1 sachet* and *Now*, then *Save intake*.
5. Share the report with the doctor (Sharing → QR) and open it in the doctor app. The brief is under Entries →
   Medications.

Automated: `server/src/test/.../catalog/CatalogTest.kt` runs the matching on verbatim rows of the official files.
`app/shared/src/jvmTest/.../MedicineRecognitionUiTest.kt` runs the six scenarios through the real patient and doctor
screens.

## Not verified on real data

- **Cyrillic packages are not read by the camera**; their markets have no barcodes in the registries (see above).
- **Belarus depends on the search page's layout** (no download or documented API). The import stops with an error,
  keeping the previous snapshot, when the columns change. Its terms of use for automated reading were not found.
- **Russian cards link to the register's search page**, not to the record (the export has no record link).
- **Ukrainian composition** is split into amounts for 6,964 of 13,690 records; for the rest the card shows each substance
  part exactly as the registry wrote it.

- **Camera photos of real packages were not taken.** Recognition was tested with the texts and codes a reader
  returns; ML Kit's text quality on foil, curved bottles and small print is not measured.
- **Desktop and Web have no text recognition.** Desktop reads barcodes from an image (ZXing). Web reads nothing yet.
  On both, the patient types the label.
- **The AI is not used for label reading in this version.** The task allowed it, but the patient app has no AI
  access (the AI routes need the clinic token), and sending package photos to a cloud model would break "nothing
  leaves without Share".
- **Indications are only in the registry's language** (French for France; German, French or Italian for Switzerland).
  They are not translated.
- **Swiss amounts come from the composition text.** For 1,118 of 10,308 versions (complex biologicals, plant
  preparations) no amount could be read; their cards say "amount not stated in the registry".
- **The French indication needs the BDPM page at first use.** If the page cannot be fetched, the card has no
  indication line, and the server tries again on the next request.
