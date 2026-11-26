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

De importer zoekt de regelingversie via `regeling.expression`, leest alle `geo:Locatie`-elementen en slaat hun GML-geometrieën (SRID 28992) en locaties op. Bestaande geometrieën worden op `basisgeo:id` hergebruikt; de bijbehorende locatie wordt dan hernoemd. Vervolgens maakt de importer een nieuwe locatiegroep met alle locaties en een `informatieobjectversie` met de FRBR-identificaties en eventuele achtergrondgegevens. Nieuwe locaties en locatiegroepen krijgen een gegenereerde IMOW-identificatie en de importdatum als begindatum.

## Configuratie

`application.properties` gebruikt de omgevingsvariabelen:

| Variabele | Betekenis |
| --- | --- |
| `INPUT_FILE` | Pad naar het GIO-XML-bestand |
| `GIO_NAME` | Naam van het GIO en de locatiegroep |
| `REGELING_EXPRESSION` | Bestaande FRBR-expression van de regelingversie in de database |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL-verbinding |
| `DB_USER`, `DB_PASS` | Database-inloggegevens |

## Aannames en beperkingen

- De bestandsnaam kent geen verplichte naamgevingsconventie. Een eventuele datum in de bestandsnaam wordt niet gebruikt.
- De XML bevat minimaal één `geo:Locatie` met `geo:naam`, `basisgeo:id` en `basisgeo:geometrie` met een GML-geometrie.
- `geo:FRBRWork` en `geo:FRBRExpression` zijn aanwezig; `gio:achtergrondVerwijzing`, `gio:achtergrondActualiteit` (ISO-datum) en `gio:nauwkeurigheid` (geheel getal) zijn optioneel.
- Geometrieën zijn geschikt voor PostGIS `ST_GeomFromGML` met SRID 28992 en zijn van het type punt, lijn of vlak. Het geometrietype van de eerste locatie wordt voor de hele groep gebruikt.
- De regelingversie, regeling, maker en eindverantwoordelijke bestaan al in de database. Bij hergebruik van een geometrie bestaat ook de locatie voor deze regelingversie.
- Normen, normwaarden en GIO-delen worden niet verwerkt. Er is geen expliciete STOP-schema-validatie.
- De importer maakt bij iedere uitvoering een nieuwe locatiegroep en informatieobjectversie; de import is niet volledig idempotent en er is geen expliciete transactie over de gehele import.

Vereist Java 11, Gradle en een bestaande PostgreSQL/PostGIS-database met het verwachte schema. De code gebruikt vaste databasecodes en STOP-schemaversie `1.3.0`.
