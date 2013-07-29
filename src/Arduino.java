package com.pinkhatproductions.processing.arduino;

import processing.core.PApplet;
import processing.serial.Serial;

public class Arduino extends ArduinoWithStreams {
    Serial serial;
    SerialProxy serialProxy;
    
    // We need a class descended from PApplet so that we can override the
    // serialEvent() method to capture serial data.  We can't use the Arduino
    // class itself, because PApplet defines a list() method that couldn't be
    // overridden by the static list() method we use to return the available
    // serial ports.  This class needs to be public so that the Serial class
    // can access its serialEvent() method.
    public class SerialProxy extends PApplet {
        public SerialProxy() {
            // Create the container for the registered dispose() methods, so that
            // our Serial instance can register its dispose() method (which it does
            // automatically).
            //disposeMethods = new RegisteredMethods();
        }

        public void serialEvent(Serial which) {
            // Notify the Arduino class that there's serial data for it to process.
            while (available() > 0)
                processInput();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        this.serial.dispose();
    }

    /**
    * Get a list of the available Arduino boards; currently all serial devices
    * (i.e. the same as Serial.list()).  In theory, this should figure out
    * what's an Arduino board and what's not.
    */
    public static String[] list() {
        return Serial.list();
    }

    /**
    * Create a proxy to an Arduino board running the Firmata 2 firmware at the
    * default baud rate of 57600.
    *
    * @param parent the Processing sketch creating this Arduino board
    * (i.e. "this").
    * @param iname the name of the serial device associated with the Arduino
    * board (e.g. one the elements of the array returned by Arduino.list())
    */
    public Arduino(PApplet parent, String iname) {
        this(parent, iname, 57600);
    }

    /**
    * Create a proxy to an Arduino board running the Firmata 2 firmware.
    *
    * @param parent the Processing sketch creating this Arduino board
    * (i.e. "this").
    * @param iname the name of the serial device associated with the Arduino
    * board (e.g. one the elements of the array returned by Arduino.list())
    * @param irate the baud rate to use to communicate with the Arduino board
    * (the firmata library defaults to 57600, and the examples use this rate,
    * but other firmwares may override it)
    */
    public Arduino(PApplet parent, String iname, int irate) {
        super(parent);
        this.serialProxy = new SerialProxy();
        this.serial = new Serial(serialProxy, iname, irate);

        setStreams(serial.input, serial.output, false);
    }
}