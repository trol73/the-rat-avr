/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.trolsoft.therat.avr.lexer.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author trol
 */
public class AvrInstructions {
    private static final Set<String> INSTRUCTIONS;

    static {
        String[] list = new String[] {"fmulsu", "pop", "brpl", "brhs", "sen", "out", "sbi", "lds", "lat", "clt", "mul", "spm", "bld", "sec", "adc", "andi", "brcc",
                "jmp", "sev", "subi", "sez", "com", "ld", "rcall", "brge", "neg", "clr", "asr", "add", "muls", "bset", "sbc", "swap", "sts", "eor", "brlt",
                "seh", "sbrc", "sbic", "nop", "rjmp", "sei", "fmul", "brvs", "breq", "sbiw", "in", "cbi", "cls", "las", "brbc", "ret", "brts", "brcs", "rol",
                "call", "push", "dec", "tst", "brlo", "wdr", "brmi", "ses", "and", "sub", "inc", "cli", "std", "mulsu", "brid", "brhc", "mov", "fmuls", "brie",
                "cbr", "lsl", "cpc", "clh", "icall", "adiw", "ser", "bst", "eijmp", "movw", "xch", "sleep", "sbr", "sbci", "cp", "reti", "clz", "brvc", "lpm",
                "brtc", "lsr", "clc", "lac", "brbs", "brne", "ldd", "st", "clv", "brsh", "break", "set", "cpi", "ijmp", "ror", "sbrs", "sbis", "or", "bclr",
                "eicall", "cpse", "ldi", "cln", "elpm", "ori"};
        INSTRUCTIONS = new HashSet<>();
        INSTRUCTIONS.addAll(Arrays.asList(list));
    }

    public static boolean isInstruction(String name) {
        return name != null && INSTRUCTIONS.contains(name.toLowerCase());
    }

}
