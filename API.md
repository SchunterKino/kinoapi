# Websocket JSON API

When a websocket connection to the server on port 8641 is opened the server sends messages about the current status of the system right away, namely:

 * `dolby_connection` - If the audio processor is available.
   * `volume_changed` - Current volume level. *Only sent if the Dolby connection is available.*

## Message format
Every message is encoded as a JSON object containing at least a `msg_type` and an `action` string attribute.
```json
{"msg_type":"<type>","action":"<action>"}
```
`msg_type` specifies the command category.
Depending on the `action` there might be more attributes as listed below.

## Server -> Client
### General
#### Error
Sent if there is some kind of problem parsing or processing a message from the client.
 * `msg_type` - string: `error`
 * `action` - string: `error`
 * `error` - string: Descriptive error message of problem on the server.

### Dolby CP750 Audio
#### Connection availability
Sent when the audio processor gets connected or disconnected.
 * `msg_type` - string: `volume`
 * `action` - string: `dolby_connection`
 * `connected` - boolean: `true` if connection to Dolby CP750 is available, `false` otherwise.

#### Volume changed notification
Sent when the volume was changed (through any means).
 * `msg_type` - string: `volume`
 * `action` - string: `volume_changed`
 * `volume` - int: The new volume the audio processor was set to. Number in the range of `0` to `70`. `40` is equivalent to `4.0` on the Dolby display.

## Client -> Server
### Dolby CP750 Audio
#### Set volume to value
Change the volume to the specified level.
This triggers a `volume_changed` notification on all connected clients when the volume actually got changed.
 * `msg_type` - string: `volume`
 * `action` - string: `set_volume`
 * `volume` - int: The volume level to change to. Number in the range of `0` to `70`. `40` is equivalent to `4.0` on the Dolby display.

#### Increase volume
Increase the volume by 0.1.
 * `msg_type` - string: `volume`
 * `action` - string: `increase_volume`

#### Decrease volume
Decrease the volume by 0.1.
 * `msg_type` - string: `volume`
 * `action` - string: `decrease_volume`
