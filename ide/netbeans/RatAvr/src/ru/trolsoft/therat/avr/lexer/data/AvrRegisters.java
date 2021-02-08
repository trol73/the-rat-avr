/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.trolsoft.therat.avr.lexer.data;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author trol
 */
public class AvrRegisters {
    private static final Set<String> REGISTERS;

    static {
        REGISTERS = new HashSet<>();
        for (int i = 0; i <= 31; i++) {
            REGISTERS.add("r" + i);
        }
        REGISTERS.add("xl");
        REGISTERS.add("xh");
        REGISTERS.add("yl");
        REGISTERS.add("yh");
        REGISTERS.add("zl");
        REGISTERS.add("zh");
    }

    public static boolean isRegister(String name) {
        return name != null && REGISTERS.contains(name.toLowerCase());
    }

    public static boolean isPair(String name) {
        return "X".equals(name) || "Y".equals(name) || "Z".equals(name);
    }

}
