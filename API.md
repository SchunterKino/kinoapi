# Websocket JSON API

When a websocket connection to the server on port 8641 is opened the server sends messages about the current status of the system right away, namely:

 * `dolby_connection` - Indicate if the audio processor is available.
   * `volume_changed` - Current volume level. *Only sent if the Dolby connection is available.*
   * `mute_status_changed` - Current mute status. *Only sent if the Dolby connection is available.*
   * `input_mode_changed` - Current active input mode. *Only sent if the Dolby connection is available.*
 * `lights_connection` - Indicate if the Jnior connection for light regulation is available.

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

#### Mute status changed notification
Sent when the sound was muted or unmuted.
 * `msg_type` - string: `volume`
 * `action` - string: `mute_status_changed`
 * `muted` - boolean: `true` if sound is muted, `false` otherwise.

#### Input mode changed notification
Sent when the audio input source changed.
 * `msg_type` - string: `volume`
 * `action` - string: `input_mode_changed`
 * `mode` - int: `0-3`: Digital 1-4, `4`: Analog, `5`: NonSync, `6`: Microphone, `7`: Last setting on power off.

### Integ Jnior 310
#### Connection availability
Sent when the jnior box for lights gets connected or disconnected.
 * `msg_type` - string: `lights`
 * `action` - string: `lights_connection`
 * `connected` - boolean: `true` if connection to Jnior 310 is available, `false` otherwise.

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

#### Mute or unmute the sound
Set the mute status to the specified value.
This triggers a `mute_status_changed` notification on all connected clients when the mute status got applied.
 * `msg_type` - string: `volume`
 * `action` - string: `set_mute_status`
 * `muted` - boolean: `true` to mute the sound, `false` to unmute.

#### Set audio input mode
Set the audio input source to the specified mode.
This triggers a `input_mode_changed` notification on all connected clients when the input mode was changed.
 * `msg_type` - string: `volume`
 * `action` - string: `set_input_mode`
 * `mode` - int: `0-3`: Digital 1-4, `4`: Analog, `5`: NonSync, `6`: Microphone, `7`: Last setting on power off.

### Integ Jnior 310
#### Change light intensity
Change the volume to the specified level.
 * `msg_type` - string: `lights`
 * `action` - string: `set_light_level`
 * `level` - int: The light intensity to choose as a value from 0-3. 0 = 0%, 1 = 33%, 2 = 66% and 3 = 100%.
