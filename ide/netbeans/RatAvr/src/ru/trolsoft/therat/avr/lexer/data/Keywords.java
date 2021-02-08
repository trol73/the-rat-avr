/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.trolsoft.therat.avr.lexer.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author trol
 */
public class Keywords {
	private static final Set<String> KEYWORDS = set(
        "proc", "var", "pin", "extern", "if", "else", "goto", "loop", "break", "continue", "as", "do", "while",
        "return", "vectors", "use", "as", "inline", "saveregs"
	);

	private static final Set<String> C_PREPROCESSOR = set(
			"#define", "#undef", "#include", "#ifdef", "#ifndef",
			"#if", "#else", "#elif", "#endif", "#warning", "#error", "#message"

	);

	private static final Set<String> DATATYPES = set("byte", "word", "dword", "ptr", "prgptr");

	private static final Set<String> ARRAYS = set("mem", "prg");

	private static final Set<String> BUILTINS = set("sizeof", "low", "high", "bitmask");




	 private static Set<String> set(String ...s) {
		 Set<String> result = new HashSet<>();
		 result.addAll(Arrays.asList(s));
		 return result;
	 }

	 private static Set<String> setCi(String ...s) {
		 Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		 result.addAll(Arrays.asList(s));
		 return result;
	 }



	 public static boolean isKeyword(String s) {
		 return KEYWORDS.contains(s);
	 }

	 public static boolean isDataType(String s) {
		 return DATATYPES.contains(s);
	 }

	 public static boolean isArray(String s) {
		 return ARRAYS.contains(s);
	 }

	 public static boolean isPreprocessor(String s) {
		 return C_PREPROCESSOR.contains(s);
	 }

	 public static boolean isBuiltIn(String s) {
		 return BUILTINS.contains(s);
	 }
}
