/**
* ArduinoWithStreams.java - Arduino/firmata library for Processing
* Copyright (C) 2006-08 David A. Mellis 
*
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or (at your option) any later version.
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General
* Public License along with this library; if not, write to the
* Free Software Foundation, Inc., 59 Temple Place, Suite 330,
* Boston, MA  02111-1307  USA
*
* Processing code to communicate with the Arduino Firmata 2 firmware.
* http://firmata.org/
*
* $Id$
*/

package com.pinkhatproductions.processing.arduino;

import processing.core.PApplet;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
* Together with the Firmata 2 firmware (an Arduino sketch uploaded to the
* Arduino board), this class allows you to control the Arduino board from
* Processing: reading from and writing to the digital pins and reading the
* analog inputs.
*/
public class ArduinoWithStreams {
    /**
    * Constant to set a pin to input mode (in a call to pinMode()).
    */
    public static final int INPUT = 0;
    /**
    * Constant to set a pin to output mode (in a call to pinMode()).
    */
    public static final int OUTPUT = 1;
    /**
    * Constant to set a pin to analog mode (in a call to pinMode()).
    */
    public static final int ANALOG = 2;
    /**
    * Constant to set a pin to PWM mode (in a call to pinMode()).
    */
    public static final int PWM = 3;
    /**
    * Constant to set a pin to servo mode (in a call to pinMode()).
    */
    public static final int SERVO = 4;
    /**
    * Constant to set a pin to shiftIn/shiftOut mode (in a call to pinMode()).
    */
    public static final int SHIFT = 5;
    /**
    * Constant to set a pin to I2C mode (in a call to pinMode()).
    */
    public static final int I2C = 6;

    /**
    * Constant to write a high value (+5 volts) to a pin (in a call to
    * digitalWrite()).
    */
    public static final int LOW = 0;
    /**
    * Constant to write a low value (0 volts) to a pin (in a call to
    * digitalWrite()).
    */
    public static final int HIGH = 1;

    private final int MAX_DATA_BYTES = 32;

    private final int DIGITAL_MESSAGE        = 0x90; // send data for a digital port
    private final int ANALOG_MESSAGE         = 0xE0; // send data for an analog pin (or PWM)
    private final int REPORT_ANALOG          = 0xC0; // enable analog input by pin #
    private final int REPORT_DIGITAL         = 0xD0; // enable digital input by port
    private final int SET_PIN_MODE           = 0xF4; // set a pin to INPUT/OUTPUT/PWM/etc
    private final int REPORT_VERSION         = 0xF9; // report firmware version
    private final int SYSTEM_RESET           = 0xFF; // reset from MIDI
    private final int START_SYSEX            = 0xF0; // start a MIDI SysEx message
    private final int END_SYSEX              = 0xF7; // end a MIDI SysEx message

    PApplet parent;
    InputStream inStream;
    OutputStream outStream;
    Thread runner;
    boolean running;
    
    int waitForData = 0;
    int executeMultiByteCommand = 0;
    int multiByteChannel = 0;
    int[] storedInputData = new int[MAX_DATA_BYTES];
    boolean parsingSysex;
    int sysexBytesRead;

    int[] digitalOutputData = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    int[] digitalInputData  = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    int[] analogInputData   = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    int majorVersion = 0;
    int minorVersion = 0;

    /**
    * Create a proxy to an Arduino board running the Firmata 2 firmware.
    *
    * @param parent the Processing sketch creating this Arduino board
    * (i.e. "this").
    * @param inStream
    * @param outStream
    */
    public ArduinoWithStreams(PApplet parent) {
        this.parent = parent;
    }
    public ArduinoWithStreams(PApplet parent, InputStream inStream, OutputStream outStream) {
        this(parent, inStream, outStream, true);
    }
    public ArduinoWithStreams(PApplet parent, InputStream inStream, OutputStream outStream, boolean runThread) {
        this.parent = parent;
        setStreams(inStream, outStream, runThread);
    }
    
    public void setStreams(InputStream inStream, OutputStream outStream, boolean runThread) {
        this.inStream = inStream;
        this.outStream = outStream;
        
        try {
            Thread.sleep(3000);
        }
        catch (InterruptedException e) {}

        for (int i = 0; i < 6; i++) {
            try {
                outStream.write(REPORT_ANALOG | i);
                outStream.write(1);
            }
            catch(IOException e) {
                //FINDME
            }
        }

        for (int i = 0; i < 2; i++) {
            try {
                outStream.write(REPORT_DIGITAL | i);
                outStream.write(1);
            }
            catch(IOException e) {
                //FINDME
            }
        }

        parent.registerMethod("dispose", this);
        
        if(runThread) {
            this.runner = new Thread(new Runnable() {
                public void run() {
                    while(running) {
                        while(available() > 0) {
                            processInput();
                        }
                        try {
                            Thread.sleep(10);
                        }
                        catch(InterruptedException e){}
                    }
                } 
            });
            running = true;
            runner.start();
        }
    }

    public void dispose() {
        running = false;
    }
    /**
    * Returns the last known value read from the digital pin: HIGH or LOW.
    *
    * @param pin the digital pin whose value should be returned (from 2 to 13,
    * since pins 0 and 1 are used for serial communication)
    */
    public int digitalRead(int pin) {
        return (digitalInputData[pin >> 3] >> (pin & 0x07)) & 0x01;
    }

    /**
    * Returns the last known value read from the analog pin: 0 (0 volts) to
    * 1023 (5 volts).
    *
    * @param pin the analog pin whose value should be returned (from 0 to 5)
    */
    public int analogRead(int pin) {
        return analogInputData[pin];
    }

    /**
    * Set a digital pin to input or output mode.
    *
    * @param pin the pin whose mode to set (from 2 to 13)
    * @param mode either Arduino.INPUT or Arduino.OUTPUT
    */
    public void pinMode(int pin, int mode) {
        try {
            outStream.write(SET_PIN_MODE);
            outStream.write(pin);
            outStream.write(mode);
        }
        catch(IOException e) {
            //FINDME
        }
    }

    /**
    * Write to a digital pin (the pin must have been put into output mode with
    * pinMode()).
    *
    * @param pin the pin to write to (from 2 to 13)
    * @param value the value to write: Arduino.LOW (0 volts) or Arduino.HIGH
    * (5 volts)
    */
    public void digitalWrite(int pin, int value) {
        int portNumber = (pin >> 3) & 0x0F;

        if (value == 0)
            digitalOutputData[portNumber] &= ~(1 << (pin & 0x07));
        else
            digitalOutputData[portNumber] |= (1 << (pin & 0x07));

        try {
            outStream.write(DIGITAL_MESSAGE | portNumber);
            outStream.write(digitalOutputData[portNumber] & 0x7F);
            outStream.write(digitalOutputData[portNumber] >> 7);
        }
        catch(IOException e) {
            //FINDME
        }
    }

    /**
    * Write an analog value (PWM-wave) to a digital pin.
    *
    * @param pin the pin to write to (must be 9, 10, or 11, as those are they
    * only ones which support hardware pwm)
    * @param the value: 0 being the lowest (always off), and 255 the highest
    * (always on)
    */
    public void analogWrite(int pin, int value) {
        pinMode(pin, PWM);
        try {
            outStream.write(ANALOG_MESSAGE | (pin & 0x0F));
            outStream.write(value & 0x7F);
            outStream.write(value >> 7);
        }
        catch(IOException e) {
            //FINDME
        }
    }

    private void setDigitalInputs(int portNumber, int portData) {
        //System.out.println("digital port " + portNumber + " is " + portData);
        digitalInputData[portNumber] = portData;
    }

    private void setAnalogInput(int pin, int value) {
        //System.out.println("analog pin " + pin + " is " + value);
        analogInputData[pin] = value;
    }

    private void setVersion(int majorVersion, int minorVersion) {
        //System.out.println("version is " + majorVersion + "." + minorVersion);
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public int available() {
        try {
            return inStream.available();
        }
        catch(IOException e) {
            return 0;
        }
    }

    public void processInput() {
        try {
            int inputData = inStream.read();
            int command;

            if (parsingSysex) {
                if (inputData == END_SYSEX) {
                    parsingSysex = false;
                    //processSysexMessage();
                } else {
                    storedInputData[sysexBytesRead] = inputData;
                    sysexBytesRead++;
                }
            } else if (waitForData > 0 && inputData < 128) {
                waitForData--;
                storedInputData[waitForData] = inputData;

                if (executeMultiByteCommand != 0 && waitForData == 0) {
                    //we got everything
                    switch(executeMultiByteCommand) {
                        case DIGITAL_MESSAGE:
                        setDigitalInputs(multiByteChannel, (storedInputData[0] << 7) + storedInputData[1]);
                        break;
                        case ANALOG_MESSAGE:
                        setAnalogInput(multiByteChannel, (storedInputData[0] << 7) + storedInputData[1]);
                        break;
                        case REPORT_VERSION:
                        setVersion(storedInputData[1], storedInputData[0]);
                        break;
                    }
                }
            } else {
                if(inputData < 0xF0) {
                    command = inputData & 0xF0;
                    multiByteChannel = inputData & 0x0F;
                } else {
                    command = inputData;
                    // commands in the 0xF* range don't use channel data
                }
                switch (command) {
                    case DIGITAL_MESSAGE:
                    case ANALOG_MESSAGE:
                    case REPORT_VERSION:
                    waitForData = 2;
                    executeMultiByteCommand = command;
                    break;      
                }
            }
        }
        catch(IOException e) {
            //FINDME
        }
    }
}
