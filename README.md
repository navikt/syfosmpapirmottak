[![Build status](https://github.com/navikt/syfosmpapirmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmpapirmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFOsmpapirmottak
This project contains just the receiving a paper sykmelding2013 message

<img src="./src/svg/flyttdiagram.svg" alt="Image of the flow of the paper sykmelding application">

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


### Importing flowchart from gliffy confluence
1. Open a web browser and go the confluence site that has the gliffy diagram you want to import, example site:
https://confluence.adeo.no/display/KES/SyfoSmMottak.
2. Click on the gliffy diagram and the "Edit Digram" buttom
3. Then go to File -> Export... and choose the Gliffy File Format (The gliffy diagram, should now be downloaded to you computer)
4. Open a web browser and go to: https://app.diagrams.net/
5. Choose the "Open Existing Diagram", then choose the file that was downloaded from step 3.
6. Click on File -> Save (The diagram is now saved as a drawio format, store it in the source code)
7. Click on File -> Export as SVG...(The diagram is now saved as SVG, store it in the source code)
8. Commit and push the changes so its up to date

### Editing existing flowchart
1. Open a web browser and go to: https://app.diagrams.net/
2. Choose the "Open Existing Diagram", then choose the file /src/flowchart/flyttdiagram.drawio
3. Do the changes you want, and the save it as a drawio, back to /src/flowchart/flyttdiagram.drawio
4. Click on File -> Export as SVG... save the file to here: file here: /src/svg/flytdiagram.svg
5. Commit and push the changes so its up to date

### Creating a new flowchart
1. Open a web browser and go to: https://app.diagrams.net/
2. Choose the "Create New diagram",
3. Do the changes you want, and the save it as a drawio, back to /src/flowchart/flyttdiagram.drawio
4. Click on File -> Export as SVG... save the file to here: file here: /src/svg/flytdiagram.svg
5. Commit and push the changes so its up to date
