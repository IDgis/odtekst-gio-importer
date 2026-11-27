# GIO Importer

Importeert een STOP-GIO (XML) in een bestaande PostgreSQL/PostGIS-database met `bzk`-tabellen.

## Validatie

De Java-importer controleert vóór de databasetransactie:

- Of het bestand bestaat en de XML well-formed is.
- `FRBRWork`: `/join/id/regdata/mnre1153/<jaar-of-datum>/<rest>`, met een niet-leeg restant.
- `FRBRExpression`: de exacte FRBRWork gevolgd door `/nld@YYYY-MM-DD`.
- STOP-schemaversie 1.3.0 en minimaal één locatie.

Datums worden uitsluitend op formaat gecontroleerd. De voormalige shellscriptcontroles op gelijke locatienamen, 
identieke XML-geometrieën en meerdere bestanden voorafgaand aan import vervallen. Ruimtelijke gelijkheid bij hetzelfde 
basisgeo-ID wordt nog steeds in de database gecontroleerd met `ST_Equals`.

## Werking

De importer accepteert uitsluitend XML met `schemaversie="1.3.0"` op het root-element en minimaal één locatie. Beide 
controles vinden vóór de databasetransactie plaats. Dit is geen volledige XSD-validatie.

De importer leest eerst het XML-bestand volledig in, controleert vervolgens globaal of de FRBRExpression al bestaat en 
wijst de import in dat geval af. Anders zoekt de importer de regelingversie via `regeling.expression`, verwerkt 
alle `geo:Locatie`-elementen en slaat hun GML-geometrieën (SRID 28992) en locaties op. Bestaande geometrieën worden 
op `basisgeo:id` hergebruikt als ze ruimtelijk gelijk zijn. Een ontbrekende locatie wordt aangemaakt; een bestaande 
locatie wordt uitsluitend binnen dezelfde regeling ongewijzigd hergebruikt, ook over regelingversies heen. Bij meerdere 
gekoppelde locaties binnen dezelfde regeling wordt de meest recente hergebruikt (`datum_begin DESC, id DESC`). Zodra een 
gekoppelde locatie (ook historisch) bij een andere regeling hoort, wordt de import afgewezen. Een ruimtelijk gelijke 
geometrie met een nieuw basisgeo-ID mag worden toegevoegd. Vervolgens maakt de importer een nieuwe locatiegroep met 
alle locaties en een `informatieobjectversie` met de FRBR-identificaties en eventuele achtergrondgegevens. Nieuwe 
locaties en locatiegroepen krijgen een gegenereerde IMOW-identificatie en de importdatum als begindatum.

Alle databasehandelingen vallen binnen één Spring-transactie. Bij succes wordt deze gecommit; bij een exception wordt 
een rollback uitgevoerd. XML-verwerking gebeurt vóór de transactie. Een mislukte import eindigt met een niet-nul exitcode. 
PostgreSQL-sequences kunnen ook bij rollback zijn opgehoogd.

## Configuratie

`application.properties` gebruikt de omgevingsvariabelen:

| Variabele                       | Betekenis                                                                                               |
|---------------------------------|---------------------------------------------------------------------------------------------------------|
| `INPUT_FILE`                    | Pad naar het GIO-XML-bestand                                                                            |
| `GIO_NAME`                      | Naam van het GIO en de locatiegroep                                                                     |
| `REGELING_EXPRESSION`           | Bestaande FRBR-expression van de regelingversie in de database                                          |
| `ONTWERP`                       | Optioneel: `true` zet `procedure_status` op `ontwerp` bij nieuwe locaties en groepen; standaard `false` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL-verbinding                                                                                   |
| `DB_USER`, `DB_PASS`            | Database-inloggegevens                                                                                  |

Bij `ONTWERP=false` wordt de kolom niet opgenomen in de INSERT; bestaande locaties blijven altijd ongewijzigd. Alleen `true` en `false` (hoofdletterongevoelig) zijn geldig; andere waarden stoppen de import bij het opstarten. De interne property heet `regeling.ontwerp`.

Een aanvullend properties-bestand kan worden opgegeven met de JVM-property `spring.config.location`. De interne propertynamen staan in `src/main/resources/application.properties`.

## Gebruik

Stel de bovenstaande omgevingsvariabelen in (gebruik een veilige bron voor het databasewachtwoord) en voer uit:

```bash
./gradlew run
```

Logging gebruikt `java.util.logging` (JUL); standaard worden INFO-meldingen naar stderr geschreven. Een succesmelding verschijnt nadat de transactionele methode succesvol is teruggekeerd.

## Aannames en beperkingen

Vereist Java 21, Gradle 8.5 of hoger (wrapper aanbevolen: 8.14.3) en een bestaande PostgreSQL/PostGIS-database met het verwachte schema. De code gebruikt vaste databasecodes en STOP-schemaversie `1.3.0`.

## Build en tests

```bash
./gradlew clean build
./gradlew test
```

De JUnit- en Mockito-tests gebruiken geen echte database. Ze controleren onder meer XML-verwerking, identificaties, importvolgorde, JDBC-parameterbinding, Spring-configuratie en commit/rollback-aanroepen op een gemockte verbinding. Werkelijke PostgreSQL/PostGIS-interacties, constraints en rollback van opgeslagen gegevens zijn niet getest.
