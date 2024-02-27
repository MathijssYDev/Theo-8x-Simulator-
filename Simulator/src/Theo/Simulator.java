package Theo;

import java.util.Arrays;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.MissingFormatArgumentException;
import java.util.Timer;
import java.util.TimerTask;
public class Simulator {
    String BOOTfile;
    String ROMfile;
    int clockspeed = 1000; // Speed in hz
    byte[][] RAM = new byte[256][256];
    byte[][] ROM;
    byte[][] BOOT;

    byte StackpointerAddressMSB = (byte)0xFF;
    byte StackpointerAddressLSB = (byte)0xEF;
    byte StackAddressMSB = (byte)0xFF;
    byte StackAddressLSB = (byte)0xF0;

    final int memoryMax = 65536;
    final int microInstructions = 16;

    boolean HALT = false;

    public Simulator(String[] args) {
        BOOTfile = args[0];
        ROMfile = args[1];
    }
    void Initialize() {
        Arrays.stream(RAM).forEach(a -> Arrays.fill(a, (byte) 0));// Fill RAM with all 0's to start

        ROM = interpetFile(ROMfile);
        BOOT = interpetFile(BOOTfile);

        System.out.println("RAM, ROM and BOOT are ready");
        System.out.println("Starting Clock... (" + clockspeed + " Hz)");

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                tick();
            }
        }, 0, 1000/(clockspeed/microInstructions));

    }
    public byte Acc = (byte) 0;
    public boolean StartBit = false;
    int ProgramCounter = 0;
    public boolean FlgC = false;
    public boolean FlgZ = false;
    public boolean FlgO = false;
    void tick() {
        byte Byte = BOOT[ProgramCounter/256][ProgramCounter%256];
        byte high_bits = (byte) (Byte>>4);
        byte low_bits = (byte) (Byte&15);

        boolean val = (high_bits & 0x1) != 0;
        boolean b2 = (high_bits & 0x2) != 0;
        boolean b3 = (high_bits & 0x4) != 0;
        boolean ROMMODE = (high_bits & 0x8) != 0;

        int msB = (ProgramCounter+1)/256;
        int lsB = (ProgramCounter+1)%256;

        int msB2 = (ProgramCounter+2)/256;
        int lsB2 = (ProgramCounter+2)%256;

        int CARRY = FlgC ? 1:0;

        int x = 0;
        byte y = 0;
        if (low_bits ==0x0) {
            StartBit = val;
        } else if (low_bits ==0x1) {
            Acc += (byte) (val ? 1 : 0);
            Acc -= (byte) (val ? 0 : 1);
        } else if (low_bits ==0x2) {
            if (ROMMODE && StartBit) {
                x = val ? ROM[msB][lsB]&0xFF: ROM[ROM[msB][lsB]&0xFF][ROM[msB2][lsB2]&0xFF]&0xFF;
            } else if(StartBit) {
                x = val ? RAM[msB][lsB]&0xFF: RAM[RAM[msB][lsB]&0xFF][RAM[msB2][lsB2]&0xFF]&0xFF;
            }
            if(!StartBit) {
                x = val ? (BOOT[msB][lsB]&0xFF)&0xFF:BOOT[BOOT[msB][lsB]&0xFF][BOOT[msB2][lsB2]&0xFF]&0xFF;
            }
            y =(byte) ((x + (b3 ? CARRY : 0)) * (b2 ? -1:1));

            // Reset Flags
            FlgC = false;
            FlgO = false;
            FlgZ = false;

            if ((Acc&0xFF) + (y) < 0) {
                Acc = (byte) (((Acc&0xFF)*-1)-1);
                FlgO = true;
            } else if ((int) (Acc&0xFF) + (y) > 255) {
                Acc = (byte)(Math.abs(Acc&0xFF-y)- 1);
                FlgC = true;
            } else {
                Acc += y;
            }
            if(val && (y) == 0) {
                FlgC = true;
            }
            if ((Acc&0xFF) == 0) {
                FlgZ = true;
            }
            ProgramCounter += val ? 1: 2;
        } else if (low_bits ==0x3) {
            Acc = (byte)(val ? ((Acc&0xFF) >> 1) | (((Acc&0xFF) & 0x01) << 7):(((Acc&0xFF) << 1) | ((Acc&0xFF) >> 7 & 0x01)));
        } else if (low_bits ==0x4) {
            if (ROMMODE && StartBit) {
                x = val ? ROM[msB][lsB]&0xFF: ROM[ROM[msB][lsB]&0xFF][ROM[msB2][lsB2]&0xFF]&0xFF;
            } else if(StartBit) {
                x = val ? RAM[msB][lsB]&0xFF: RAM[RAM[msB][lsB]&0xFF][RAM[msB2][lsB2]&0xFF]&0xFF;
            }
            if(!StartBit) {
                x = val ? (BOOT[msB][lsB]&0xFF)&0xFF:BOOT[BOOT[msB][lsB]&0xFF][BOOT[msB2][lsB2]&0xFF]&0xFF;
            }
            if(b2) {
                Acc = (byte)(Acc&0xFF | x);
            } else if (b3) {
                Acc = (byte)(Acc&0xFF ^ x);
            } else {
                Acc = (byte) (Acc & 0xFF & x);
            }
            ProgramCounter += val ? 1: 2;
        } else if (low_bits == 0x5) {
            if (ROMMODE && StartBit) {
                x = val ? ROM[msB][lsB]&0xFF: ROM[ROM[msB][lsB]&0xFF][ROM[msB2][lsB2]&0xFF]&0xFF;
            } else if(StartBit) {
                x = val ? RAM[msB][lsB]&0xFF: RAM[RAM[msB][lsB]&0xFF][RAM[msB2][lsB2]&0xFF]&0xFF;
            }
            if(!StartBit) {
                x = val ? (BOOT[msB][lsB]&0xFF)&0xFF:BOOT[BOOT[msB][lsB]&0xFF][BOOT[msB2][lsB2]&0xFF]&0xFF;
            }

            // Reset Flags
            FlgC = false;
            FlgO = false;
            FlgZ = false;

            if ((Acc&0xFF) < x) {
                FlgO = true;
            }
            if ((Acc&0xFF) > x) {
                FlgC = true;
            }
            if ((Acc&0xFF) == x) {
                FlgZ = true;
            }
            ProgramCounter += val ? 1: 2;
        } else if (low_bits == 0x6) {
            if(b3) {
                Acc = (byte) (val ? (BOOT[msB][lsB]&0xFF)&0xFF:BOOT[BOOT[msB][lsB]&0xFF][BOOT[msB2][lsB2]&0xFF]&0xFF);
            } else if (b2) {
                Acc = (byte) (val ? RAM[msB][lsB]&0xFF: RAM[RAM[msB][lsB]&0xFF][RAM[msB2][lsB2]&0xFF]&0xFF);
            } else {
                if (val) {
                    RAM[msB][lsB] = (byte)(Acc&0xFF);
                } else {
                    RAM[RAM[msB][lsB]&0xFF][RAM[msB2][lsB2]&0xFF] = (byte)(Acc&0xFF);
                }
            }

            ProgramCounter += val ? 1: 2;
        } else if (low_bits == 0x7) {

            return;
        } else if (low_bits == 0x8) {
            byte locationMSB = 0;
            byte locationLSB = 0;

            if (ROMMODE && StartBit) {
                locationMSB = (byte) (ROM[msB][lsB]&0xFF);
                locationLSB = (byte) (ROM[msB2][lsB2]&0xFF);
            } else if(StartBit) {
                locationMSB = (byte) (RAM[msB][lsB]&0xFF);
                locationLSB = (byte) (RAM[msB2][lsB2]&0xFF);
            }
            if(!StartBit) {
                locationMSB = (byte) (BOOT[msB][lsB]&0xFF);
                locationLSB = (byte) (BOOT[msB2][lsB2]&0xFF);
            }

            short Location = (short)(((locationMSB << 8) | (locationLSB & 0xFF))& 0xFFFF);

            if ((FlgC && b2) || (FlgO && b3) || (FlgZ && !b2 && !b3)){
                ProgramCounter = Location;
            }
            return;
        } else if (low_bits == 0xF) {
            System.out.println(Acc&0xFF );
        }
        ProgramCounter++;

        if (ProgramCounter == memoryMax) ProgramCounter = 0;
    }

    byte[][] interpetFile(String File) {
        byte[][] Arr = new byte[256][256];
        Arrays.stream(Arr).forEach(a -> Arrays.fill(a, (byte) 0));// Fill Arr with all 0's to start

        try {
            BufferedReader reader = new BufferedReader(new FileReader(File));
            String line;
            int position = 0;
            while ((line = reader.readLine()) != null) {
                try {
                    Arr[position / 256][position % 256] = (byte) Integer.parseInt(line);
                } catch (Exception e) {
                    Arr[position / 256][position % 256] = Integer.decode(line).byteValue();
                }
                position++;
            }
            reader.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return Arr;
    }
    public static void StringBinaryToDecimal(String s)
    {
        int ans = 0, i, p = 0;
        // length of String
        int len = s.length();

        // Traversing the String
        for (i = len - 1; i >= 0; i--) {

            if (s.charAt(i) == '1') {
                // Calculating Decimal Number
                ans += Math.pow(2, p);
            }
            // incrementing value of p
            p++;
        }
        System.out.println("Decimal Equivalent of " + s
                + " is " + ans);
    }
    public static void main(String[] args) {
        if (args.length != 2) {
            throw new MissingFormatArgumentException("Missing 1 or more arguments. Arguments needed are BOOT file location and ROM file location. BOOT.txt and ROM.txt respectively!");
        }

        Simulator simulator = new Simulator(args);

        simulator.Initialize();
    }
}