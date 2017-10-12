# kinoapi
Connects with the cinema hardware and provides a websocket interface to manage them.

Talks to:
 * Christie Solaria One+
 * Dolby CP750
 * Jnior 310

## Usage
Use Maven 3 to build.

To compile the tool run:
`mvn clean compile`

To run the server:
`mvn exec:java`

## Websocket JSON interface
The message format and available commands are documented [here](API.md).

## Requirements
The PIB uses hardware control flow on its serial port. When running this program on a Raspberry Pi, you need to [enable the respective pins](https://github.com/mholling/rpirtscts) on the RPI.
