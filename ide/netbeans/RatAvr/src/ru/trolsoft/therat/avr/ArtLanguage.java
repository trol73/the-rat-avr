/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.trolsoft.therat.avr;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;
import ru.trolsoft.therat.avr.lexer.ArtTokenId;

/**
 *
 * @author trol
 */
@LanguageRegistration(mimeType = "text/x-art")
public class ArtLanguage extends DefaultLanguageConfig {

	@Override
	public Language getLexerLanguage() {
		return ArtTokenId.getLanguage();
	}

	@Override
	public String getDisplayName() {
		return "TheRat AVR";
	}

}
