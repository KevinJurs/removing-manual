# Rental Tracker

Rental Tracker is a Java command-line application for listing items, recording rentals, and tracking returns. It stores accounts, listings, and rental history in a local SQLite database.

## Getting started

You need: 
- Java 21 to run the program.
- JDK 21 and Maven to run the tests, build a new jar and generate a fresh JaCoCo report.
- Docker to build and run from the committed Dockerfile 

To start the program run the following command from the directory containing `rentaltracker.jar`.

```sh
java -jar rentaltracker.jar
```

The app uses `rental-tracker.db` in the working directory.

The included seeded database already contains 3 sample accounts with some listed items for testing:
- username: name password: password
- username: nimi password: wordpass
- username: k    password: 1

## Tests and coverage

The latest JaCoCo report, `index.html` is included with the program.

To run the full test suite, build the program and generate a fresh JaCoCo report in `rentaltracker/target/site/jacoco/index.html` navigate to the rentaltracker subdirectory and run:

```sh
mvn clean verify
```

Repository and service tests use temporary SQLite databases. Check the JaCoCo report for coverage results.

## Database schema

See [RentalTrackerERdiagram.svg](RentalTrackerERdiagram.svg) for the relationships between users, listed items, and rentals.

## Docker

With Docker running, build the image from the directory containing the Dockerfile:

```sh
docker build -t rentaltracker .
```

Start the interactive CLI with persistent storage:

```sh
docker run --rm -it --mount source=rentaltracker-data,target=/data rentaltracker
```

The `-it` option connects the CLI to your terminal. The named volume stores the database and log file under `/data`.

Exit through the application menu. Running the same command again reuses the saved data, even though `--rm` removes the container after exit.

On its first use, this volume starts with an empty database. The sample accounts listed above are available only when using the included seeded database.