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
https://confluence.adeo.no/display/KES/Testing+av+papirsykemlinger#Testingavpapirsykemlinger-request
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
