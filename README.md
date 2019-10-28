[![Build status](https://github.com/navikt/syfosmpapirmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmpapirmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFO mottak
This project contains just the receving a paper sykmelding2013 message

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Jackson

## Getting started
## Running locally

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmpapirmottak .`

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding
