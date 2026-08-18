package com.felipeftn.magnusorgue.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.felipeftn.magnusorgue.controller.OrganController

/**
 * Everything USB-MIDI: device discovery, hotplug, and feeding parsed events
 * into the controller.
 *
 * Naming gotcha worth knowing: we read from the device's OUTPUT port,
 * because ports are named from the device's point of view — notes flow OUT
 * of the keyboard, INTO us. Took me a re-read of the docs to trust it.
 * https://developer.android.com/reference/android/media/midi/package-summary
 */
class MidiInputManager(
    context: Context,
    private val controller: OrganController,
) {
    private val midiManager =
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Open connections, keyed by device id.
    private val connections = mutableMapOf<Int, Connection>()

    private class Connection(
        val device: MidiDevice,
        val port: MidiOutputPort,
        val receiver: MidiReceiver,
        val name: String,
    ) {
        fun close() {
            // Disconnect first, then close — the other order can still
            // deliver a callback into a dead receiver.
            port.disconnect(receiver)
            runCatching { port.close() }
            runCatching { device.close() }
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) = open(info)
        override fun onDeviceRemoved(info: MidiDeviceInfo) = close(info)
    }

    fun start() {
        val mm = midiManager ?: return // no MIDI support on this device at all
        mm.registerDeviceCallback(deviceCallback, mainHandler)
        // Pick up whatever was plugged in before we launched.
        @Suppress("DEPRECATION") // getDevices(): fine for minSdk 26
        mm.devices.forEach(::open)
    }

    fun stop() {
        midiManager?.unregisterDeviceCallback(deviceCallback)
        connections.values.forEach(Connection::close)
        connections.clear()
        controller.midiDeviceName = null
    }

    private fun open(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0) return // nothing to listen to (e.g. pure sound modules)
        if (info.id in connections) return

        // openDevice is async; the callback lands on mainHandler.
        midiManager?.openDevice(info, { device ->
            if (device == null) {
                Log.w(TAG, "Could not open MIDI device ${displayName(info)}")
                return@openDevice
            }
            val port = device.openOutputPort(0)
            if (port == null) {
                Log.w(TAG, "Output port 0 busy on ${displayName(info)}")
                device.close()
                return@openDevice
            }

            val receiver = NoteReceiver()
            port.connect(receiver)

            val name = displayName(info)
            connections[info.id] = Connection(device, port, receiver, name)
            controller.midiDeviceName = name
            Log.i(TAG, "MIDI connected: $name")
        }, mainHandler)
    }

    private fun close(info: MidiDeviceInfo) {
        val gone = connections.remove(info.id) ?: return
        gone.close()

        // We don't track which notes came from which device, so unplugging
        // mid-chord gets the heavy hammer. Crude, but nobody unplugs their
        // keyboard expecting the chord to keep ringing.
        controller.panic()

        controller.midiDeviceName = connections.values.lastOrNull()?.name
        Log.i(TAG, "MIDI disconnected: ${gone.name}")
    }

    private fun displayName(info: MidiDeviceInfo): String =
        info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: "MIDI device"

    /**
     * Receives raw bytes on a Binder thread and pushes events straight to
     * the controller from there — bouncing through the main thread would
     * just add latency for nothing.
     */
    private inner class NoteReceiver : MidiReceiver() {
        private val parser = MidiMessageParser() // per-connection state!

        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            for (event in parser.feed(msg, offset, count)) {
                when (event) {
                    is MidiEvent.NoteOn -> controller.noteOn(event.note)
                    is MidiEvent.NoteOff -> controller.noteOff(event.note)
                    is MidiEvent.ControlChange -> when (event.controller) {
                        // CC 120 (all sound off) / 123 (all notes off)
                        120, 123 -> controller.panic()
                        // TODO CC 64 sustain pedal — planned, see docs/02-features.md
                        else -> {}
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "MagnusOrgue"
    }
}
