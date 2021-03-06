# Websocket JSON API

When a websocket connection to the server on port 8641 is opened the server sends messages about the current status of the system right away, namely:

 * [`volume`:`connection`](#dolby-cp750-audio) - Indicate if the audio processor is available.
   * [`volume_changed`](#volume-changed-notification) - Current volume level. *Only sent if the Dolby connection is available.*
   * [`mute_status_changed`](#mute-status-changed-notification) - Current mute status. *Only sent if the Dolby connection is available.*
   * [`input_mode_changed`](#input-mode-changed-notification) - Current active input mode. *Only sent if the Dolby connection is available.*
   * [`decode_mode_changed`](#decode-mode-changed-notification) - Current active decode mode for Digital 1 input. *Only sent if the Dolby connection is available.*
 * [`lights`:`connection`](#integ-jnior-310) - Indicate if the Jnior connection for light regulation is available.
 * [`playback`:`connection`](#christie-imb-s2) - Indicate if the Christie IMB connection for playback control is available.
 * [`projector`:`connection`](#christie-solaria-pib) - Indicate if the serial connection to the Christie PIB for projector control is available.
   * [`power_changed`](#imb-power-state-changed) - Current power state of the projector.
   * [`lamp_changed`](#lamp-turned-on-or-off) - Current state of the lamp.
   * [`douser_changed`](#douser-opened-or-closed) - Current open state of the douser.
   * [`channel_changed`](#active-channel-changed) - Current active channel/image source.
   * [`ingest_state_changed`](#ingesting-content-started-or-finished) - Current state of content ingestion into the IMB.

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
 * `action` - string: `connection`
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

#### Decode mode changed notification
Sent when the decode mode of the `Digital 1` input changed.
This is used to differentiate between 5.1 and 7.1 surround sound sources.
 * `msg_type` - string: `volume`
 * `action` - string: `decode_mode_changed`
 * `mode` - int: `0`: Auto, `1`: Invalid, `2`: N_A, `3`: 5.1 Surround, `4`: Dolby Pro Logic, `5`: Dolby Pro Logic 2, `6`: 7.1 Surround.

### Integ Jnior 310
#### Connection availability
Sent when the Jnior box for lights gets connected or disconnected.
 * `msg_type` - string: `lights`
 * `action` - string: `connection`
 * `connected` - boolean: `true` if connection to Jnior 310 is available, `false` otherwise.

### Christie IMB-S2
#### Connection availability
Sent when the Christie IMB for playback control gets connected or disconnected.
 * `msg_type` - string: `playback`
 * `action` - string: `connection`
 * `connected` - boolean: `true` if connection to Christie IMB-S2 is available, `false` otherwise.

### Christie Solaria PIB
#### Connection availability
Sent when the Christie Projector for projector control gets connected or disconnected.
 * `msg_type` - string: `projector`
 * `action` - string: `connection`
 * `connected` - boolean: `true` if connection to Christie PIB is available, `false` otherwise.

#### IMB power state changed
Sent when the IMB turned on or off.
 * `msg_type` - string: `projector`
 * `action` - string: `power_changed`
 * `state` - int: The state of the IMB encoded as:
   * `0` - IMB is off.
   * `1` - IMB is currently warming up/booting.
   * `2` - IMB is on.
 * `timestamp` - string: ISO 8601 formated point in time of when the lamp state change was observed.

#### Lamp turned on or off
Sent when the lamp turned on or off.
The lamp is still cooling if `is_on` is false and the `cooldown` field exists. The `cooldown` field is omitted if the lamp isn't being cooled.
 * `msg_type` - string: `projector`
 * `action` - string: `lamp_changed`
 * `is_on` - boolean: True if the lamp is shining bright, false if it's off.
 * `timestamp` - string: ISO 8601 formated point in time of when the lamp state change was observed.
 * `cooldown` - int: The time the lamp still has to be cooled in seconds.

#### Douser opened or closed
Sent when the douser was opened or closed.
 * `msg_type` - string: `projector`
 * `action` - string: `douser_changed`
 * `is_open` - boolean: True if the douser is open now, false if it's closed.

#### Active channel changed
Sent when the projector switched the active image source to a different channel.
 * `msg_type` - string: `projector`
 * `action` - string: `channel_changed`
 * `channel` - int: The active channel encoded as:
   * `0`: Unknown/other
   * `1`: IMB Flat
   * `2`: IMB Scope
   * `3`: DVI A (PC) Flat
   * `4`: DVI A (PC) Scope

#### Ingesting content started or finished
Sent when the IMB starts or stops ingesting content to the NAS.
 * `msg_type` - string: `projector`
 * `action` - string: `ingest_state_changed`
 * `is_ingesting` - boolean: True if the IMB is ingesting something, false otherwise.
 * `timestamp` - string: ISO 8601 formated point in time of when the ingestion state change was observed.

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

#### Set audio decode mode
Set the decode mode of the `Digital 1` input.
This triggers a `decode_mode_changed` notification on all connected clients when the input mode was changed.
This is used to differentiate between 5.1 and 7.1 surround sound sources.
 * `msg_type` - string: `volume`
 * `action` - string: `set_decode_mode`
 * `mode` - int: `0`: Auto, `1`: Invalid, `2`: N_A, `3`: 5.1 Surround, `4`: Pro Logic, `5`: Pro Logic II, `6`: 7.1 Surround.

### Integ Jnior 310
#### Change light intensity
Dim the light to one of the given intensities.
 * `msg_type` - string: `lights`
 * `action` - string: `set_light_level`
 * `level` - int: The light intensity to choose as a value from 0-3. 0 = 0%, 1 = 33%, 2 = 66% and 3 = 100%.

### Christie IMB-S2
#### Start/resume content playback
Starts playing the currently loaded content.
 * `msg_type` - string: `playback`
 * `action` - string: `play`

#### Pause content playback
Pauses playing the currently loaded content.
 * `msg_type` - string: `playback`
 * `action` - string: `pause`

#### Stop content playback
Stops playing the currently loaded content.
 * `msg_type` - string: `playback`
 * `action` - string: `stop`

#### Change input source
Select the input source to display the image from including the screen ratio.
 * `msg_type` - string: `playback`
 * `action` - string: `set_input_mode`
 * `mode` - string: The image source channel to select:
   * `cinema_flat` - IMB image in flat ratio.
   * `cinema_scope` - IMB image in scope ratio.
   * `pc_flat` - Computer image in flat ratio.
   * `pc_scope` - Computer image in scope ratio.

### Christie Solaria PIB
#### Start the IMB
Power on the IMB.
 * `msg_type` - string: `projector`
 * `action` - string: `power_on`

#### Shut down the IMB
Power off the IMB. This will start cooling the lamp too if it was on.
 * `msg_type` - string: `projector`
 * `action` - string: `power_off`

#### Turn lamp on
Turn on the lamp.
 * `msg_type` - string: `projector`
 * `action` - string: `lamp_on`

#### Turn lamp off
Start cooling the lamp.
 * `msg_type` - string: `projector`
 * `action` - string: `lamp_off`

#### Open the douser
Open the douser.
 * `msg_type` - string: `projector`
 * `action` - string: `douser_open`

#### Close the douser
Close the douser.
 * `msg_type` - string: `projector`
 * `action` - string: `douser_close`

#### Set active channel
Change the active image source of the projector.
This triggers a `channel_changed` notification on all connected clients when the channel actually got changed.
 * `msg_type` - string: `projector`
 * `action` - string: `set_channel`
 * `channel` - int: The channel to change to encoded as:
   * `1`: IMB Flat
   * `2`: IMB Scope
   * `3`: DVI A (PC) Flat
   * `4`: DVI A (PC) Scope
