# kinoapi
Connects with the cinema hardware and provides a websocket interface to manage them.

Talks to:
 * Christie Solaria One+
 * Dolby CP750
 * Jnior 310

## Usage
Use Maven 3 to build.

To compile the tool run: `mvn clean compile`

To run the from source server: `mvn exec:java`

To create a all-in-one jar archive which contains all dependencies: `mvn package`
The package will be generated in `target/kinoapi-1.0.one-jar.jar`. 

The package can be run like any jar with: `java -jar kinoapi-1.0.one-jar.jar`

## Websocket JSON interface
The message format and available commands are documented [here](API.md).

## Requirements
The PIB uses hardware control flow on its serial port. When running this program on a Raspberry Pi, you need to [enable the respective pins](https://github.com/mholling/rpirtscts) on the RPI.
