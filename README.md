[![Deploy app to dev and prod](https://github.com/navikt/syfosmpapirmottak/actions/workflows/deploy.yml/badge.svg)](https://github.com/navikt/syfosmpapirmottak/actions/workflows/deploy.yml)

# SYFOsmpapirmottak
This project contains the receiving a paper sykmelding2013 message

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest
* Jackson

#### Requirements

* JDK 17

## FlowChart
This the high level flow of the application
```mermaid
  graph LR
    papirsykmelding --> Skanner
    Skanner --> id1(Filområde skannede dokumenter)
    id1(Filområde skannede dokumenter) --> id2([Skanmot Helse])
    id2([Skanmot Helse]) --> id3([joark])
    id3([joark]) --> id4([syfosmpapirmottak])
    id4([syfosmpapirmottak]) -->|Manuell journalføring| id15([gosys])
    id15([gosys]) -->|Manuell journalføring| id5([smregistering])
    id5([smregistering]) -->|Manuell journalført| C[\teamsykmelding.ok-sykmelding/]
    id4([syfosmpapirmottak]) <--> id6([syfosmpapirregler])
    id4([syfosmpapirmottak]) -->|Automatisk journalføring| C[\teamsykmelding.ok-sykmelding/]
    C[\teamsykmelding.ok-sykmelding/] --> id7([syfosmregister]) 
```

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the GitHub Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
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
See githubs guide [creating-a-personal-access-token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) on
how to create a personal access token.

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
see https://teamsykmelding.intern.nav.no/docs/testing/registrering-av-papirsykmelding

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmpapirmottak .`


### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

```./gradlew wrapper --gradle-version $gradleVersjon```

### Contact

This project is maintained by navikt/teamsykmelding

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/syfosmpapirmottak/issues).

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
