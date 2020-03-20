[![Build status](https://github.com/navikt/syfosmpapirmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmpapirmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFO mottak
This project contains just the receving a paper sykmelding2013 message

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Jackson
* Vault

#### Requirements

* JDK 12

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

## Testing the whole flow for handling paper sykmelding in preprod
### Submitting sykmelding:
The orginal guide can be found here: https://confluence.adeo.no/display/KES/Testing+av+papirsykemlinger
1. Log into your utvikler image (vmware horizon client)
2. Open a web-browser go to https://security-token-service.nais.preprod.local/api.html#/default/getOidcToken
3. Then Authorize as srvsyfosmpapirmottak, the password is in this vault path: https://vault.adeo.no/ui/vault/secrets/kv%2Fpreprod%2Ffss/show/syfosmpapirmottak/default
4. Then open the api: get/v1/sts/token and try out, the input values should be like this:
grant_type = client_credentials 
scope = openid
5. Then press Execute buttom
6. Copy the value that is inside of the access_token value
7. Open a web-browser and got to https://dokarkiv-q1.nais.preprod.local/swagger-ui.html#/arkiver-og-journalfoer-rest-controller/opprettJournalpostUsingPOST
8. Then click on Authorize and fill inn the "Authorization  (apiKey)" and put inn Bearer $access_token value
9. Go to the api: post /rest/journalpostapi/v1/journalpost and try out, and fill inn the test values you want example:
```{
       "avsenderMottaker": {
           "id": "983975240",
           "idType": "ORGNR",
           "land": "string",
           "navn": "Sorlandet sykehus"
       },
       "bruker": {
           "id": "18026902092",
           "idType": "FNR"
       },
       "dokumenter": [
           {
               "dokumentKategori": "B",
               "dokumentvarianter": [
                   {
                       "filtype": "PDFA",
                       "fysiskDokument": "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zqkpw6YdN8OOwqrDicOzS1M54oiC4oKsTFg84oiCwrRdD1DPgE48w5pKW+KAoWnCtMOhw5rLhuKAnuKAmcK4w6sX4oGETMK2wrDDqBIhGR15wrBKS8OR4oCmHg8fUMOJxpLDq8uc76yCwqXGkjgeWzIsNkVW4oGEN+KIj+KIhgvCtWvigJ7DjeKIq14fNw/CqsudGl1Xw59CPUDFksOkw7PDi+KJoMO2InDCusOVKsaSYcO5wrF9w6I0w4Z8wrvigKFeRSTDol/ihKJVWcOHw4YXwrTiiaVPF8Ozw6PDs1rDisuHAOKAlCBtw4QKZW5kc3RyZWFtCmVuZG9iago1IDAgb2JqCjw8L1BhcmVudCA0IDAgUi9Db250ZW50cyAzIDAgUi9UeXBlL1BhZ2UvUmVzb3VyY2VzPDwvRm9udDw8L0YxIDEgMCBSL0YyIDIgMCBSPj4+Pi9NZWRpYUJveFswIDAgNTk1IDg0Ml0+PgplbmRvYmoKMSAwIG9iago8PC9CYXNlRm9udC9IZWx2ZXRpY2EtQm9sZC9UeXBlL0ZvbnQvRW5jb2RpbmcvV2luQW5zaUVuY29kaW5nL1N1YnR5cGUvVHlwZTE+PgplbmRvYmoKMiAwIG9iago8PC9CYXNlRm9udC9IZWx2ZXRpY2EvVHlwZS9Gb250L0VuY29kaW5nL1dpbkFuc2lFbmNvZGluZy9TdWJ0eXBlL1R5cGUxPj4KZW5kb2JqCjQgMCBvYmoKPDwvVHlwZS9QYWdlcy9Db3VudCAxL0tpZHNbNSAwIFJdPj4KZW5kb2JqCjYgMCBvYmoKPDwvVHlwZS9DYXRhbG9nL1BhZ2VzIDQgMCBSPj4KZW5kb2JqCjcgMCBvYmoKPDwvUHJvZHVjZXIoaVRleHTDhiA1LjQuNCDCqTIwMDAtMjAxMyAxVDNYVCBCVkJBIFwoQUdQTC12ZXJzaW9uXCkpL01vZERhdGUoRDoyMDE5MDIwMTA4MzQ1OSswMScwMCcpL0NyZWF0aW9uRGF0ZShEOjIwMTkwMjAxMDgzNDU5KzAxJzAwJyk+PgplbmRvYmoKeHJlZgowIDgKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAzMDY1IDAwMDAwIG4gCjAwMDAwMDMxNTggMDAwMDAgbiAKMDAwMDAwMDAxNSAwMDAwMCBuIAowMDAwMDAzMjQ2IDAwMDAwIG4gCjAwMDAwMDI5NDQgMDAwMDAgbiAKMDAwMDAwMzI5NyAwMDAwMCBuIAowMDAwMDAzMzQyIDAwMDAwIG4gCnRyYWlsZXIKPDwvUm9vdCA2IDAgUi9JRCBbPGI0NDM3ZWExMmZkZDQ0OTY1MjM5NjhiNDY2ZjQxZWFhPjxkZWY0YzM2MzUyNDA0Y2FhN2NiODcyZWFkOGQwMjQ5Yj5dL0luZm8gNyAwIFIvU2l6ZSA4Pj4KJWlUZXh0LTUuNC40CnN0YXJ0eHJlZgozNDk1CiUlRU9GCg==",
                       "variantformat": "ARKIV"
                   }
               ],
               "tittel": "pdfsykemlding"
           },
           {
               "dokumentKategori": "B",
               "dokumentvarianter": [
                   {
                       "filtype": "XML",
                       "fysiskDokument": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHNrYW5uaW5nbWV0YWRhdGEgeG1sbnM6eHNpPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYS1pbnN0YW5jZSIgeHNpOm5vTmFtZXNwYWNlU2NoZW1hTG9jYXRpb249InNrYW5uaW5nX21ldGEueHNkIj4KICAgIDxzeWtlbWVsZGluZ2VyPgogICAgICAgIDxwYXNpZW50PgogICAgICAgICAgICA8Zm5yPjE4MDI2OTAyMDkyPC9mbnI+CiAgICAgICAgPC9wYXNpZW50PgogICAgICAgIDxtZWRpc2luc2tWdXJkZXJpbmc+CiAgICAgICAgICAgIDxob3ZlZERpYWdub3NlPgogICAgICAgICAgICAgICAgPGRpYWdub3Nla29kZT5TNTIuNTwvZGlhZ25vc2Vrb2RlPgogICAgICAgICAgICA8L2hvdmVkRGlhZ25vc2U+CiAgICAgICAgPC9tZWRpc2luc2tWdXJkZXJpbmc+CiAgICAgICAgPGFrdGl2aXRldD4KICAgICAgICAgICAgPGFrdGl2aXRldElra2VNdWxpZz4KICAgICAgICAgICAgICAgIDxwZXJpb2RlRk9NRGF0bz4yMDIwLTAzLTE1PC9wZXJpb2RlRk9NRGF0bz4KICAgICAgICAgICAgICAgIDxwZXJpb2RlVE9NRGF0bz4yMDIwLTAzLTIwPC9wZXJpb2RlVE9NRGF0bz4KICAgICAgICAgICAgPC9ha3Rpdml0ZXRJa2tlTXVsaWc+CiAgICAgICAgPC9ha3Rpdml0ZXQ+CiAgICAgICAgPGJlaGFuZGxlcj4KICAgICAgICAgICAgPEhQUj43MTI1MTg2PC9IUFI+CiAgICAgICAgPC9iZWhhbmRsZXI+CiAgICA8L3N5a2VtZWxkaW5nZXI+Cjwvc2thbm5pbmdtZXRhZGF0YT4=",
                       "variantformat": "ORIGINAL"
                   }
               ],
               "tittel": "ocr sykemlding"
           },
           {
               "dokumentKategori": "B",
               "dokumentvarianter": [
                   {
                       "filtype": "XML",
                       "fysiskDokument": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48c2thbm5pbmdtZXRhZGF0YSB4bWxuczp4c2k9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hLWluc3RhbmNlIiB4c2k6bm9OYW1lc3BhY2VTY2hlbWFMb2NhdGlvbj0ic2thbm5pbmdfbWV0YS54c2QiPgogICA8c3lrZW1lbGRpbmdlcj4KICAgICAgPHBhc2llbnQ+CiAgICAgICAgIDxmbnI+MDQwNTkwMzIxMzA8L2Zucj4KICAgICAgPC9wYXNpZW50PgogICAgICA8bWVkaXNpbnNrVnVyZGVyaW5nPgogICAgICAgICA8aG92ZWREaWFnbm9zZT4KICAgICAgICAgICAgPGRpYWdub3Nla29kZT45MTMuNDwvZGlhZ25vc2Vrb2RlPgogICAgICAgICAgICA8ZGlhZ25vc2U+Rm9yc3R1dmluZyBvZyBmb3JzdHJla2tpbmcgaSBjZXJ2aWthbGtvbHVtbmE8L2RpYWdub3NlPgogICAgICAgICA8L2hvdmVkRGlhZ25vc2U+CiAgICAgIDwvbWVkaXNpbnNrVnVyZGVyaW5nPgogICAgICA8YWt0aXZpdGV0PgogICAgICAgICA8YWt0aXZpdGV0SWtrZU11bGlnPgogICAgICAgICAgICA8cGVyaW9kZUZPTURhdG8+MjAxOS0wMS0xMDwvcGVyaW9kZUZPTURhdG8+CiAgICAgICAgICAgIDxwZXJpb2RlVE9NRGF0bz4yMDE5LTAxLTE0PC9wZXJpb2RlVE9NRGF0bz4KICAgICAgICAgICAgPG1lZGlzaW5za2VBcnNha2VyPgogICAgICAgICAgICAgICA8bWVkQXJzYWtlckhpbmRyZXI+MTwvbWVkQXJzYWtlckhpbmRyZXI+CiAgICAgICAgICAgIDwvbWVkaXNpbnNrZUFyc2FrZXI+CiAgICAgICAgIDwvYWt0aXZpdGV0SWtrZU11bGlnPgogICAgICA8L2FrdGl2aXRldD4KICAgICAgPHRpbGJha2VkYXRlcmluZz4KICAgICAgICAgPHRpbGJha2ViZWdydW5uZWxzZT5Ta2FkZWxlZ2V2YWt0ZW4KT3J0b3BlZGlzayBhdmRlbGluZzwvdGlsYmFrZWJlZ3J1bm5lbHNlPgogICAgICA8L3RpbGJha2VkYXRlcmluZz4KICAgICAgPGtvbnRha3RNZWRQYXNpZW50PgogICAgICAgICA8YmVoYW5kbGV0RGF0bz4yMDE5LTAxLTExPC9iZWhhbmRsZXREYXRvPgogICAgICA8L2tvbnRha3RNZWRQYXNpZW50PgogICAgICA8YmVoYW5kbGVyPgogICAgICAgICA8SFBSPjEwMDIzMjQ1PC9IUFI+CiAgICAgIDwvYmVoYW5kbGVyPgogICA8L3N5a2VtZWxkaW5nZXI+Cjwvc2thbm5pbmdtZXRhZGF0YT4=",
                       "variantformat": "SKANNING_META"
                   }
               ],
               "tittel": "xml sykemlding metadata"
           }
       ],
       "eksternReferanseId": "1231155",
       "journalfoerendeEnhet": 9999,
       "journalpostType": "INNGAAENDE",
       "kanal": "SKAN_NETS",
       "tema": "SYM",
       "tilleggsopplysninger": [
           {
               "nokkel": "string",
               "verdi": "string"
           }
       ],
       "tittel": "Papir sykemdling fra Sorlandet sykehus"
   }
```
10. Save the "journalpostId" value

### Verification in the sykmelding applications:
1. Log in at https://logs.adeo.no and use the following search string: application: x_journalpostId: "$journalpostId"
2. Verify that what you expect to happen with a sykmelding actually happens. It should then be Ok | Manual processing
   What you look for are items: status = OK, status = MANUAL_PROCESSING 

### Verification in Gosys:
1. Login User (Case managers / supervisors):
   Z992389
2. Check that the sykmelding is placed in gosys:
   - Log in at https://gosys-nais-q1.nais.preprod.local/gosys
   - Search for user with fnr
3. Verify that there is a sykmelding under "Dokumentoversikt", see that the pdf is correct


### Verification in «ditt sykefravær»:
1. Check that the sykmelding is on ditt sykefravær
2. Go to https://tjenester-q1.nav.no/sykefravaer
3. Log in with the fnr for the user as the username and a password
3. Then select "Uten IDPorten"
4. Enter the user's fnr again and press sign-in
5. Verify that a new task has appeared for the user

### Verification in Modia:
1. Log in to the modes, https://app-q1.adeo.no/sykefravaer/
2. Log in with Case Manager: User: Z990625, password for testing can be found here (NAV internal sites):
   https://confluence.adeo.no/display/KES/Generell+testing+av+sykemelding+2013+i+preprod under "Verifisering i Modia"
3. See "Sykmeldt enkeltperson" verifying that the sykmelding that is there is correct

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmpapirmottak .`

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`
* Jonas Henie, `jonas.henie@nav.no`
* Mathias Hellevang, `mathias.hellevang@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding
