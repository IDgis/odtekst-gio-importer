# GIO Importer

Importeert een STOP-GIO (XML) in een bestaande PostgreSQL/PostGIS-database met `bzk`-tabellen.

## Validatie

Voer voorafgaand aan de import `validate-gio.sh` uit vanuit de root van de repository:

```bash
bash validate-gio.sh bestand1.gml bestand2.gml
```

Het script controleert:

- Of de bestanden bestaan en de XML well-formed is.
- `FRBRWork`: `/join/id/regdata/mnre1153/<jaar-of-datum>/N2000_<gebiedsnaam>_(N2000|HR|VR)`.
- `FRBRExpression`: `<FRBRWork>/nld@<YYYY-MM-DD>`.
- Dezelfde `geo:naam` moet hetzelfde `basisgeo:id` hebben.
- Hetzelfde `basisgeo:id` moet dezelfde `basisgeo:geometrie` hebben (MD5-hash van gecanonicaliseerde XML).

De laatste twee controles gelden binnen en tussen alle bestanden in dezelfde uitvoering. Datums worden alleen op formaat gecontroleerd. Het script voert geen STOP-schema-validatie, geometrische validatie of databasecontrole uit en wordt niet automatisch door de importer aangeroepen.

Vereist Bash, `xmllint`, `md5sum` en `awk`. Exitcodes: `0` bij succesvolle validatie, `1` bij geconstateerde validatiefouten en `2` bij ontbrekende bestandsargumenten.

## Werking

De importer leest eerst het XML-bestand volledig in, zoekt vervolgens de regelingversie via `regeling.expression`, verwerkt alle `geo:Locatie`-elementen en slaat hun GML-geometrieën (SRID 28992) en locaties op. Bestaande geometrieën worden op `basisgeo:id` hergebruikt; de bijbehorende locatie wordt dan hernoemd. Vervolgens maakt de importer een nieuwe locatiegroep met alle locaties en een `informatieobjectversie` met de FRBR-identificaties en eventuele achtergrondgegevens. Nieuwe locaties en locatiegroepen krijgen een gegenereerde IMOW-identificatie en de importdatum als begindatum.

Alle databasehandelingen vallen binnen één Spring-transactie. Bij succes wordt deze gecommit; bij een exception wordt een rollback uitgevoerd. XML-verwerking gebeurt vóór de transactie. Een mislukte import eindigt met een niet-nul exitcode. PostgreSQL-sequences kunnen ook bij rollback zijn opgehoogd.

## Architectuur

| Package                      | Verantwoordelijkheid                                                         |
|------------------------------|------------------------------------------------------------------------------|
| `domain`                     | Immutable GIO-domeinmodel                                                    |
| `application`                | Importcoördinatie, locatieverwerking, IMOW-identificaties en transactiegrens |
| `application.port`           | Contracten voor XML-reader en repositories                                   |
| `application.model`          | Invoermodellen voor opslag                                                   |
| `infrastructure.xml`         | DOM/XPath-parser; DOCTYPE en externe XML-resources zijn uitgeschakeld        |
| `infrastructure.persistence` | Spring JDBC en PostGIS                                                       |
| `config`                     | Expliciete beanregistratie en databaseconfiguratie                           |

`Main` start `GioImporterConfig`. `GioImportService` leest de XML en roept de door Spring geproxiede `GioImportTransaction` aan. Alle repositories delen dezelfde `DataSource` en `JdbcTemplate`.

## Configuratie

`application.properties` gebruikt de omgevingsvariabelen:

| Variabele                       | Betekenis                                                      |
|---------------------------------|----------------------------------------------------------------|
| `INPUT_FILE`                    | Pad naar het GIO-XML-bestand                                   |
| `GIO_NAME`                      | Naam van het GIO en de locatiegroep                            |
| `REGELING_EXPRESSION`           | Bestaande FRBR-expression van de regelingversie in de database |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL-verbinding                                          |
| `DB_USER`, `DB_PASS`            | Database-inloggegevens                                         |

Een aanvullend properties-bestand kan worden opgegeven met de JVM-property `spring.config.location`. De bestaande propertynamen blijven ongewijzigd.

## Gebruik

Stel de bovenstaande omgevingsvariabelen in (gebruik een veilige bron voor het databasewachtwoord) en voer uit:

```bash
./gradlew run
```

Voor een zelfstandige distributie:

```bash
./gradlew installDist
```

De startscriptbestanden staan onder `build/install/`. Logging gebruikt `java.util.logging` (JUL); standaard worden INFO-meldingen naar stderr geschreven. Een succesmelding verschijnt nadat de transactionele methode succesvol is teruggekeerd.

## Aannames en beperkingen

- De bestandsnaam kent geen verplichte naamgevingsconventie. Een eventuele datum in de bestandsnaam wordt niet gebruikt.
- De XML bevat minimaal één `geo:Locatie` met `geo:naam`, `basisgeo:id` en `basisgeo:geometrie` met een GML-geometrie.
- `geo:FRBRWork` en `geo:FRBRExpression` zijn aanwezig; `gio:achtergrondVerwijzing`, `gio:achtergrondActualiteit` (ISO-datum) en `gio:nauwkeurigheid` (geheel getal) zijn optioneel.
- Geometrieën zijn geschikt voor PostGIS `ST_GeomFromGML` met SRID 28992 en zijn van het type punt, lijn of vlak. Het geometrietype van de eerste locatie wordt voor de hele groep gebruikt.
- De regelingversie, regeling, maker en eindverantwoordelijke bestaan al in de database. Bij hergebruik van een geometrie bestaat ook de locatie voor deze regelingversie.
- Normen, normwaarden en GIO-delen worden niet verwerkt. Er is geen expliciete STOP-schema-validatie.
- De importer maakt bij iedere uitvoering een nieuwe locatiegroep en informatieobjectversie; de import is niet volledig idempotent.

Vereist Java 21, Gradle 8.5 of hoger (wrapper aanbevolen: 8.14.3) en een bestaande PostgreSQL/PostGIS-database met het verwachte schema. De code gebruikt vaste databasecodes en STOP-schemaversie `1.3.0`.

## Build en tests

```bash
./gradlew clean build
./gradlew test
```

De JUnit- en Mockito-tests gebruiken geen echte database. Ze controleren onder meer XML-verwerking, identificaties, importvolgorde, JDBC-parameterbinding, Spring-configuratie en commit/rollback-aanroepen op een gemockte verbinding. Werkelijke PostgreSQL/PostGIS-interacties, constraints en rollback van opgeslagen gegevens zijn niet getest.

## Mogelijke toekomstige wijzigingen

De volgende ontwerpbeslissingen zijn bewust nog niet geïmplementeerd:

- Alleen STOP 1.3.0 accepteren (de opgeslagen schemaversie is nu vast; de invoerversie wordt niet gecontroleerd).
- Gemengde geometrietypen afwijzen (nu bepaalt de eerste locatie het groepstype).
- Bij een bestaande geometrie zonder locatie voor de regelingversie een locatie aanmaken (nu mislukt de import).
- Een bestaande geometrie met gewijzigde GML afwijzen (nu wordt de geometrie zonder vergelijking hergebruikt; de vergelijkingsmethode moet nog worden uitgewerkt).
- Een reeds bestaande FRBRExpression expliciet afwijzen (nu wordt direct een insert uitgevoerd, onder voorbehoud van databaseconstraints).

Natura 2000-specifieke validatie blijft in het afzonderlijke shellscript.
